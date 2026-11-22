package com.sitesnap.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.sitesnap.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** A published GitHub release that carries an installable APK. */
data class Release(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    /** Lowercase hex SHA-256 from GitHub's asset digest, when GitHub provides one. */
    val sha256: String?,
    val pageUrl: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    /** Android needs the user to allow SiteSnap to install apps before we can continue. */
    data class NeedsPermission(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Installing(val release: Release) : UpdateState
    data class Failed(val message: String, val release: Release?) : UpdateState
}

/**
 * Checks the GitHub repo in [BuildConfig.GITHUB_REPO] for a newer release, downloads its APK and hands it
 * to the system installer. Updates install only when the APK is signed with the same key as this build.
 */
class AppUpdater(private val context: Context) {
    val currentVersion: String = BuildConfig.VERSION_NAME
    val enabled: Boolean = BuildConfig.GITHUB_REPO.matches(Regex("[\\w.-]+/[\\w.-]+"))

    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Version the user tapped "Later" on; the home card stays hidden for it until the next app start. */
    private val _dismissed = MutableStateFlow<String?>(null)
    val dismissed: StateFlow<String?> = _dismissed.asStateFlow()

    fun dismiss(release: Release) { _dismissed.value = release.version }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    suspend fun check() {
        if (!enabled) return
        when (_state.value) {
            is UpdateState.Checking, is UpdateState.Downloading, is UpdateState.Installing -> return
            else -> Unit
        }
        _state.value = UpdateState.Checking
        _state.value = try {
            val release = fetchLatest()
            if (release != null && isNewer(release.version, currentVersion)) UpdateState.Available(release)
            else UpdateState.UpToDate
        } catch (e: Exception) {
            UpdateState.Failed("Couldn't check for updates. ${e.message.orEmpty()}".trim(), null)
        }
    }

    /** Downloads [release] and starts the install. Asks for the install permission first if needed. */
    suspend fun update(release: Release) {
        if (!canInstall()) {
            _state.value = UpdateState.NeedsPermission(release)
            return
        }
        try {
            val apk = download(release)
            _state.value = UpdateState.Installing(release)
            withContext(Dispatchers.IO) { install(apk) }
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(e.message ?: "Update failed", release)
        }
    }

    /** Called by [InstallResultReceiver] when the installer reports a failure or the user cancels. */
    internal fun onInstallFinished(status: Int, message: String?) {
        val release = (_state.value as? UpdateState.Installing)?.release ?: return
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> UpdateState.UpToDate
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateState.Available(release)
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                UpdateState.Failed("This update is signed with a different key, so Android won't install it over this app.", release)
            else -> UpdateState.Failed(message ?: "Install failed", release)
        }
    }

    private suspend fun fetchLatest(): Release? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${BuildConfig.UPDATE_API_BASE.trimEnd('/')}/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "SiteSnap-Android")
            .build()
        http.newCall(request).execute().use { response ->
            // 404 means the repo has no published (non-draft, non-prerelease) release yet, or it's private.
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) throw IOException("GitHub returned ${response.code}")
            val gh = json.decodeFromString<GhRelease>(response.body!!.string())
            val apk = gh.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return@withContext null
            Release(
                version = gh.tagName.trimStart('v', 'V'),
                notes = gh.body.orEmpty().trim(),
                apkUrl = apk.url,
                apkSize = apk.size,
                sha256 = apk.digest?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase(),
                pageUrl = gh.htmlUrl,
            )
        }
    }

    private suspend fun download(release: Release): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // keep only the APK we're about to fetch
        val target = File(dir, "SiteSnap-${release.version}.apk")
        _state.value = UpdateState.Downloading(release, 0f)

        val request = Request.Builder().url(release.apkUrl).header("User-Agent", "SiteSnap-Android").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed (${response.code})")
            val body = response.body!!
            val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize
            val digest = MessageDigest.getInstance("SHA-256")
            var read = 0L
            var lastReported = 0f
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        read += n
                        if (total > 0) {
                            val p = read.toFloat() / total
                            if (p - lastReported >= 0.01f) {
                                lastReported = p
                                _state.value = UpdateState.Downloading(release, p.coerceAtMost(1f))
                            }
                        }
                    }
                }
            }
            release.sha256?.let { expected ->
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (actual != expected) {
                    target.delete()
                    throw IOException("The downloaded file didn't match the release checksum.")
                }
            }
        }
        target
    }

    private fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = Intent(context, InstallResultReceiver::class.java).setAction(InstallResultReceiver.ACTION)
            val pending = PendingIntent.getBroadcast(
                context, sessionId, callback,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
    }

    companion object {
        /** True when [candidate] is a higher dotted version than [current] ("1.10.0" > "1.9.2"). */
        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(candidate)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}

@Serializable
private data class GhRelease(
    @SerialName("tag_name") val tagName: String,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GhAsset> = emptyList(),
)

@Serializable
private data class GhAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long = 0,
    val digest: String? = null,
)
