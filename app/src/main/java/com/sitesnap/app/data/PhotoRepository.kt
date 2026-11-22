package com.sitesnap.app.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
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

    /** Output target for CameraX: it inserts into MediaStore and handles IS_PENDING for us. */
    fun captureOptions(job: Job): ImageCapture.OutputFileOptions {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${job.folderName.take(40)}_$stamp")
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
    }
}
