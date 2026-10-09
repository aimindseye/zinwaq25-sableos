package org.sableos.titan2.camera.android

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.OutputStream

/**
 * Saves into DCIM/SableCamera (same folder as the Titan 2 camera MVP) through MediaStore: no
 * storage permission is needed for files the app creates itself.
 */
class MediaStoreSaver(private val context: Context) {
    fun save(displayName: String, mime: String, write: (OutputStream) -> Unit): Uri? {
        val cr = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/SableCamera")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return runCatching {
            val stream = cr.openOutputStream(uri)
            check(stream != null) { "no output stream" }
            stream.use { write(it) }
            cr.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                },
                null,
                null
            )
            uri
        }.getOrElse {
            cr.delete(uri, null, null)
            null
        }
    }

    /**
     * A pending video row plus a writable descriptor for MediaRecorder. Call [finishVideo] on
     * success or [discard] on failure.
     */
    class VideoTarget(val uri: Uri, val pfd: ParcelFileDescriptor)

    fun createVideo(displayName: String): VideoTarget? {
        val cr = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/SableCamera")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = cr.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        val pfd = runCatching { cr.openFileDescriptor(uri, "w") }.getOrNull()
        return if (pfd == null) {
            cr.delete(uri, null, null)
            null
        } else {
            VideoTarget(uri, pfd)
        }
    }

    fun finishVideo(t: VideoTarget) {
        runCatching { t.pfd.close() }
        context.contentResolver.update(
            t.uri,
            ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            },
            null,
            null
        )
    }

    fun discard(t: VideoTarget) {
        runCatching { t.pfd.close() }
        context.contentResolver.delete(t.uri, null, null)
    }
}
