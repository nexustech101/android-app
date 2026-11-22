package com.sitesnap.app.drive

import android.content.Context
import com.sitesnap.app.data.AppStore
import com.sitesnap.app.data.PhotoRepository
import java.io.IOException

/**
 * Mirrors local job folders to Drive:  My Drive / SiteSnap / <job folder> / <photo>.jpg
 * Only photos not yet recorded in [com.sitesnap.app.data.AppState.uploaded] are sent.
 */
class DriveSync(
    private val context: Context,
    private val store: AppStore,
    private val photos: PhotoRepository,
) {
    /** Returns the number of photos uploaded. */
    suspend fun syncAll(): Int {
        if (!store.state.value.driveConnected) return 0
        val token = DriveAuth.silentToken(context) ?: throw DriveAuthException()
        val drive = DriveClient(token)

        val rootId = store.state.value.driveRootId
            ?: drive.findOrCreateFolder(PhotoRepository.ROOT_FOLDER, null)
                .also { id -> store.update { it.copy(driveRootId = id) } }

        // Changes made on the phone since the last sync: deleted photos/jobs go to Drive's trash, renames follow.
        for (id in store.state.value.driveTrash) {
            drive.trash(id)
            store.update { it.copy(driveTrash = it.driveTrash - id) }
        }
        for ((id, name) in store.state.value.driveRenames) {
            drive.rename(id, name)
            store.update { s -> if (s.driveRenames[id] == name) s.copy(driveRenames = s.driveRenames - id) else s }
        }

        var count = 0
        for (job in store.state.value.jobs.filter { it.syncToDrive }) {
            val pending = photos.photosFor(job).filter { it.id !in store.state.value.uploaded }
            if (pending.isEmpty()) continue

            val folderId = job.driveFolderId
                ?: drive.findOrCreateFolder(job.folderName, rootId).also { id ->
                    store.update { s -> s.copy(jobs = s.jobs.map { if (it.id == job.id) it.copy(driveFolderId = id) else it }) }
                }

            for (photo in pending) {
                val fileId = drive.uploadImage(photo.displayName, folderId, photo.mimeType, photo.size) {
                    context.contentResolver.openInputStream(photo.uri) ?: throw IOException("Photo ${photo.id} is gone")
                }
                store.update { it.copy(uploaded = it.uploaded + (photo.id to fileId)) }
                count++
            }
        }
        store.update { it.copy(lastSyncAt = System.currentTimeMillis()) }
        return count
    }
}
