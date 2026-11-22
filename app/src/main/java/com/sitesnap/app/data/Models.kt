package com.sitesnap.app.data

import android.net.Uri
import kotlinx.serialization.Serializable

/** A job is a folder of photos for one property. Its photos live in MediaStore under Pictures/SiteSnap/<folderName>/. */
@Serializable
data class Job(
    val id: String,
    val name: String,
    val address: String = "",
    val workOrder: String = "",
    val createdAt: Long,
    val syncToDrive: Boolean = true,
    val driveFolderId: String? = null,
) {
    val folderName: String get() = name.toFolderName()
}

@Serializable
data class SyncSettings(
    val autoSync: Boolean = true,
    val wifiOnly: Boolean = true,
)

@Serializable
data class AppState(
    val onboarded: Boolean = false,
    val driveConnected: Boolean = false,
    val accountEmail: String? = null,
    val driveRootId: String? = null,
    val lastSyncAt: Long? = null,
    val settings: SyncSettings = SyncSettings(),
    val jobs: List<Job> = emptyList(),
    /** MediaStore image id -> Google Drive file id, for photos already uploaded. */
    val uploaded: Map<Long, String> = emptyMap(),
    /** Drive file/folder ids to move to Drive's trash on the next sync (deleted photos and jobs). */
    val driveTrash: List<String> = emptyList(),
    /** Drive file id -> new name, for photos renamed after they were uploaded. */
    val driveRenames: Map<String, String> = emptyMap(),
)

data class Photo(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val relativePath: String,
    val takenAt: Long,
    val size: Long,
)

/** Folder names must be valid on device storage and in Drive. */
fun String.toFolderName(): String =
    trim().replace(Regex("[\\\\/:*?\"<>|]"), "-").replace(Regex("\\s+"), " ").take(80)
