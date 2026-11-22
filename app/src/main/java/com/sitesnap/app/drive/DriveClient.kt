package com.sitesnap.app.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** The file was deleted in Drive, or SiteSnap can't see it any more. */
class DriveNotFoundException : IOException("Not found in Drive")

/** Minimal Google Drive v3 REST client: find/create folders, upload images, trash and rename. */
class DriveClient(private val accessToken: String) {

    suspend fun findOrCreateFolder(name: String, parentId: String?): String = withContext(Dispatchers.IO) {
        findFolder(name, parentId) ?: createFolder(name, parentId)
    }

    suspend fun uploadImage(
        name: String,
        parentId: String,
        mimeType: String,
        length: Long,
        open: () -> InputStream,
    ): String = withContext(Dispatchers.IO) {
        val metadata = buildJsonObject {
            put("name", name)
            putJsonArray("parents") { add(parentId) }
        }
        val media = object : RequestBody() {
            override fun contentType(): MediaType = mimeType.toMediaType()
            override fun contentLength(): Long = if (length > 0) length else -1
            override fun writeTo(sink: BufferedSink) {
                open().use { sink.writeAll(it.source()) }
            }
        }
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toString().toRequestBody(JSON))
            .addPart(media)
            .build()
        idOf(execute(Request.Builder().url("$UPLOAD_API/files?uploadType=multipart&fields=id").post(body)))
    }

    /** Moves a file or folder to Drive's trash (recoverable for 30 days). False if it's already gone. */
    suspend fun trash(fileId: String): Boolean = patch(fileId, buildJsonObject { put("trashed", true) })

    /** Renames a file. False if it's gone. */
    suspend fun rename(fileId: String, name: String): Boolean = patch(fileId, buildJsonObject { put("name", name) })

    private suspend fun patch(fileId: String, body: kotlinx.serialization.json.JsonObject): Boolean = withContext(Dispatchers.IO) {
        try {
            execute(Request.Builder().url("$API/files/$fileId?fields=id").patch(body.toString().toRequestBody(JSON)))
            true
        } catch (e: DriveNotFoundException) {
            false
        }
    }

    private fun findFolder(name: String, parentId: String?): String? {
        val parent = parentId ?: "root"
        val q = "mimeType='$FOLDER_MIME' and trashed=false and name='${name.escapeQuery()}' and '$parent' in parents"
        val url = "$API/files".toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            .addQueryParameter("fields", "files(id)")
            .addQueryParameter("spaces", "drive")
            .build()
        val response = execute(Request.Builder().url(url).get())
        return json.parseToJsonElement(response).jsonObject["files"]
            ?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
    }

    private fun createFolder(name: String, parentId: String?): String {
        val metadata = buildJsonObject {
            put("name", name)
            put("mimeType", FOLDER_MIME)
            if (parentId != null) putJsonArray("parents") { add(parentId) }
        }
        return idOf(execute(Request.Builder().url("$API/files?fields=id").post(metadata.toString().toRequestBody(JSON))))
    }

    private fun execute(builder: Request.Builder): String {
        val request = builder.header("Authorization", "Bearer $accessToken").build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401) throw DriveAuthException()
            if (response.code == 404) throw DriveNotFoundException()
            if (!response.isSuccessful) throw IOException("Drive request failed (${response.code}): $text")
            return text
        }
    }

    private fun idOf(response: String): String =
        json.parseToJsonElement(response).jsonObject["id"]?.jsonPrimitive?.content
            ?: throw IOException("Drive response had no id: $response")

    private fun String.escapeQuery() = replace("\\", "\\\\").replace("'", "\\'")

    private companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD_API = "https://www.googleapis.com/upload/drive/v3"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        val JSON = "application/json; charset=UTF-8".toMediaType()
        val json = Json { ignoreUnknownKeys = true }
        val http: OkHttpClient = OkHttpClient.Builder()
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
