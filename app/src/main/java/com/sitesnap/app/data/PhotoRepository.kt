package com.sitesnap.app.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.database.ContentObserver
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * All photo storage goes through MediaStore. Each job maps to the relative path
 * Pictures/SiteSnap/<job folder>/, so photos also show up in the phone's gallery.
 */
class PhotoRepository(private val context: Context) {
    private val resolver get() = context.contentResolver
    private val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    fun relativePath(job: Job): String = "$ROOT_PATH${job.folderName}/"

    /**
     * Default photo name: date, property and a running number, e.g. "2026-11-22 1420 Oak St 007".
     * Uses the street part of the address, or the job name when there's no address.
     */
    fun defaultName(job: Job, number: Int, at: Date = Date()): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(at)
        val place = job.address.substringBefore(",").ifBlank { job.name }.toFolderName().take(50)
        return "$date $place ${"%03d".format(Locale.US, number)}"
    }

    /** Next running number for [job]: one past the highest number on its photos (gaps from deletes aren't reused). */
    suspend fun nextPhotoNumber(job: Job): Int {
        val photos = photosFor(job)
        val highest = photos.mapNotNull { NUMBER_SUFFIX.find(it.displayName.substringBeforeLast('.'))?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull()
        return maxOf(highest ?: 0, photos.size) + 1
    }

    /** Output target for CameraX: it inserts into MediaStore and handles IS_PENDING for us. */
    fun captureOptions(job: Job, number: Int): ImageCapture.OutputFileOptions {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, defaultName(job, number))
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath(job))
        }
        return ImageCapture.OutputFileOptions.Builder(resolver, collection, values).build()
    }

    suspend fun photosFor(job: Job): List<Photo> =
        query("${MediaStore.Images.Media.RELATIVE_PATH} = ?", arrayOf(relativePath(job)))

    suspend fun allPhotos(): List<Photo> =
        query("${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?", arrayOf("$ROOT_PATH%"))

    private suspend fun query(selection: String, args: Array<String>): List<Photo> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
        )
        resolver.query(collection, projection, selection, args, "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            buildList {
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val taken = c.getLong(takenCol).takeIf { it > 0 } ?: (c.getLong(addedCol) * 1000)
                    add(
                        Photo(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id),
                            displayName = c.getString(nameCol) ?: "photo_$id.jpg",
                            mimeType = c.getString(mimeCol) ?: "image/jpeg",
                            relativePath = c.getString(pathCol) ?: "",
                            takenAt = taken,
                            size = c.getLong(sizeCol),
                        )
                    )
                }
            }
        } ?: emptyList()
    }

    /**
     * Deletes [photos]. Returns the ones Android wouldn't let us delete without asking, which are photos
     * this install of SiteSnap didn't take (for example from before a reinstall); see [deleteConsent].
     */
    suspend fun delete(photos: List<Photo>): List<Photo> = withContext(Dispatchers.IO) {
        photos.filter { photo ->
            try {
                resolver.delete(photo.uri, null, null)
                false
            } catch (e: SecurityException) {
                true
            }
        }
    }

    /** Renames [photo] to [name] (no extension), keeping its file type. False if Android needs the user's OK first. */
    suspend fun rename(photo: Photo, name: String): Boolean = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, fileNameFor(photo, name)) }
        try {
            resolver.update(photo.uri, values, null, null)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun fileNameFor(photo: Photo, name: String): String {
        val ext = photo.displayName.substringAfterLast('.', "").ifBlank { "jpg" }
        return "${name.toFolderName().take(120)}.$ext"
    }

    /** System dialog asking the user to let SiteSnap delete photos it doesn't own (Android 11+). */
    fun deleteConsent(photos: List<Photo>): IntentSender? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) MediaStore.createDeleteRequest(resolver, photos.map { it.uri }).intentSender else null

    /** System dialog asking the user to let SiteSnap change photos it doesn't own (Android 11+). */
    fun writeConsent(photos: List<Photo>): IntentSender? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) MediaStore.createWriteRequest(resolver, photos.map { it.uri }).intentSender else null

    /** Emits once immediately, then whenever MediaStore images change. */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(collection, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()

    companion object {
        const val ROOT_FOLDER = "SiteSnap"
        val ROOT_PATH = "${Environment.DIRECTORY_PICTURES}/$ROOT_FOLDER/"
        /** Trailing photo number, ignoring the " (1)" MediaStore adds to duplicate names. */
        private val NUMBER_SUFFIX = Regex("""\s(\d{1,5})(?:\s\(\d+\))?$""")
    }
}
