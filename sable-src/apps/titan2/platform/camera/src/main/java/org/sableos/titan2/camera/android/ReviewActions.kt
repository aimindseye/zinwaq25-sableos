package org.sableos.titan2.camera.android

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Size
import org.sableos.titan2.camera.core.Capture

/**
 * The platform side of quick review. Every call here is the result of an explicit key press or
 * tap; nothing is shared, opened or deleted on its own. Only files this app saved are touched, so
 * no storage permission is needed.
 */
class ReviewActions(private val context: Context) {
    /** Share sheet for one capture, with read access granted to the chosen app only. */
    fun share(c: Capture<Uri>): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = c.mime
            putExtra(Intent.EXTRA_STREAM, c.ref)
            clipData = ClipData.newRawUri(c.name, c.ref)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return start(Intent.createChooser(send, c.name))
    }

    /** Files (DocumentsUI) at the folder the camera saves into. */
    fun showInFiles(): Boolean {
        val folder = DocumentsContract.buildDocumentUri(
            EXTERNAL_STORAGE_AUTHORITY,
            "primary:$CAPTURE_FOLDER"
        )
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(folder, DocumentsContract.Document.MIME_TYPE_DIR)
        }
        return start(view)
    }

    /** Hand the capture itself to the gallery or video player. */
    fun open(c: Capture<Uri>): Boolean = start(
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(c.ref, c.mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    )

    /** Remove the MediaStore row and its file. Callers confirm first. */
    fun delete(c: Capture<Uri>): Boolean =
        runCatching { context.contentResolver.delete(c.ref, null, null) > 0 }.getOrDefault(false)

    /** A decoded preview of at most [edgePx] square, or null (for example a RAW the HAL cannot preview). */
    fun thumbnail(c: Capture<Uri>, edgePx: Int): Bitmap? = runCatching {
        context.contentResolver.loadThumbnail(c.ref, Size(edgePx, edgePx), null)
    }.getOrNull()

    private fun start(i: Intent): Boolean = runCatching {
        context.startActivity(i)
    }.isSuccess

    companion object {
        /** Same folder [MediaStoreSaver] writes to. */
        const val CAPTURE_FOLDER = "DCIM/SableCamera"
        private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    }
}
