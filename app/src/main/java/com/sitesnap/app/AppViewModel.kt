package com.sitesnap.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.sitesnap.app.data.AppState
import com.sitesnap.app.data.Job
import com.sitesnap.app.data.Photo
import com.sitesnap.app.data.toFolderName
import com.sitesnap.app.drive.SyncWorker
import com.sitesnap.app.update.Release
import com.sitesnap.app.update.UpdateState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class SyncStatus { Synced, Pending, Syncing, LocalOnly }

data class JobSummary(
    val job: Job,
    val photoCount: Int,
    val pendingCount: Int,
    val lastPhotoAt: Long?,
    val status: SyncStatus,
    /** Newest first, at most 3, used for card covers. */
    val recentPhotos: List<Uri> = emptyList(),
) {
    val lastActivity: Long get() = lastPhotoAt ?: job.createdAt
}

data class StorageSummary(val photos: Int, val bytes: Long, val backedUp: Int, val backupEligible: Int)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SiteSnapApplication
    private val store = app.store
    val photoRepo = app.photos

    val state: StateFlow<AppState> = store.state

    val updater = app.updater
    val update: StateFlow<UpdateState> = updater.state
    val dismissedUpdate: StateFlow<String?> = updater.dismissed

    init {
        // One quiet check per app start; failures only show up if the user checks from Settings.
        viewModelScope.launch { updater.check() }
    }

    fun checkForUpdate() { viewModelScope.launch { updater.check() } }
    fun installUpdate(release: Release) { viewModelScope.launch { updater.update(release) } }
    fun dismissUpdate(release: Release) = updater.dismiss(release)

    private val allPhotos: StateFlow<List<Photo>> = photoRepo.changes()
        .map { photoRepo.allPhotos() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val syncing: StateFlow<Boolean> = WorkManager.getInstance(application)
        .getWorkInfosForUniqueWorkFlow(SyncWorker.UNIQUE_NAME)
        .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val summaries: StateFlow<List<JobSummary>> = combine(state, allPhotos, syncing) { s, photos, isSyncing ->
        val byPath = photos.groupBy { it.relativePath }
        s.jobs.map { job ->
            val jobPhotos = byPath[photoRepo.relativePath(job)].orEmpty().sortedByDescending { it.takenAt }
            val pending = jobPhotos.count { it.id !in s.uploaded }
            JobSummary(
                job = job,
                photoCount = jobPhotos.size,
                pendingCount = pending,
                lastPhotoAt = jobPhotos.firstOrNull()?.takenAt,
                status = statusFor(s, job, pending, isSyncing),
                recentPhotos = jobPhotos.take(3).map { it.uri },
            )
        }.sortedByDescending { it.lastActivity }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalPending: StateFlow<Int> = summaries
        .map { list -> list.filter { it.job.syncToDrive }.sumOf { it.pendingCount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val storage: StateFlow<StorageSummary> = combine(allPhotos, summaries) { photos, list ->
        val eligible = list.filter { it.job.syncToDrive }
        StorageSummary(
            photos = photos.size,
            bytes = photos.sumOf { it.size },
            backedUp = eligible.sumOf { it.photoCount - it.pendingCount },
            backupEligible = eligible.sumOf { it.photoCount },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StorageSummary(0, 0, 0, 0))

    fun photos(job: Job): Flow<List<Photo>> = photoRepo.changes().map { photoRepo.photosFor(job) }

    fun statusFor(s: AppState, job: Job, pending: Int, isSyncing: Boolean): SyncStatus = when {
        !s.driveConnected || !job.syncToDrive -> SyncStatus.LocalOnly
        pending > 0 && isSyncing -> SyncStatus.Syncing
        pending > 0 -> SyncStatus.Pending
        else -> SyncStatus.Synced
    }

    fun createJob(name: String, address: String, workOrder: String, syncToDrive: Boolean, onCreated: (Job) -> Unit) {
        viewModelScope.launch {
            var created: Job? = null
            store.update { s ->
                val taken = s.jobs.map { it.folderName.lowercase() }.toSet()
                var finalName = name.trim()
                var n = 2
                while (finalName.toFolderName().lowercase() in taken) finalName = "${name.trim()} ($n)".also { n++ }
                val job = Job(
                    id = UUID.randomUUID().toString(),
                    name = finalName,
                    address = address.trim(),
                    workOrder = workOrder.trim(),
                    createdAt = System.currentTimeMillis(),
                    syncToDrive = syncToDrive,
                )
                created = job
                s.copy(jobs = s.jobs + job)
            }
            created?.let(onCreated)
        }
    }

    fun onDriveConnected(email: String?) {
        viewModelScope.launch {
            store.update { it.copy(onboarded = true, driveConnected = true, accountEmail = email ?: it.accountEmail) }
            syncNow()
        }
    }

    fun skipOnboarding() {
        viewModelScope.launch { store.update { it.copy(onboarded = true) } }
    }

    fun disconnectDrive() {
        viewModelScope.launch {
            WorkManager.getInstance(app).cancelUniqueWork(SyncWorker.UNIQUE_NAME)
            store.update { it.copy(driveConnected = false, accountEmail = null) }
        }
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch { store.update { it.copy(settings = it.settings.copy(autoSync = enabled)) } }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch { store.update { it.copy(settings = it.settings.copy(wifiOnly = enabled)) } }
    }

    /** User asked explicitly, so sync on any network. */
    fun syncNow() {
        if (state.value.driveConnected) SyncWorker.enqueue(app, wifiOnly = false)
    }

    fun onPhotoSaved() {
        val s = state.value
        if (s.driveConnected && s.settings.autoSync) SyncWorker.enqueue(app, s.settings.wifiOnly)
    }
}
