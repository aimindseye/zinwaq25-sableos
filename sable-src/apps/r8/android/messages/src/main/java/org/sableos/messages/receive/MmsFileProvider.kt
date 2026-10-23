package org.sableos.messages.receive

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Files MmsService writes a downloaded PDU into, or reads the M-NotifyResp.ind from. Not
 * exported: the framework grants the phone process a one-off URI permission per transfer.
 */
class MmsFileProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor {
        val file = fileFor(requireNotNull(context), uri) ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun getType(uri: Uri): String = "application/vnd.wap.mms-message"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        private const val AUTHORITY = "org.sableos.messages.mmsfiles"
        private val SAFE_NAME = Regex("[a-z]+-[0-9]+\\.pdu")
        private const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1000

        fun uriFor(name: String): Uri =
            Uri
                .Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(name)
                .build()

        fun directory(context: Context): File = File(context.cacheDir, "mms").apply { mkdirs() }

        /** Only flat names Sable created ("download-12.pdu"); nothing outside the cache folder. */
        fun fileFor(
            context: Context,
            uri: Uri,
        ): File? {
            val name = uri.lastPathSegment.orEmpty()
            val valid = uri.authority == AUTHORITY && uri.pathSegments.size == 1 && SAFE_NAME.matches(name)
            return if (valid) File(directory(context), name) else null
        }

        /** Removes transfer files left behind by a transfer that never reported back. */
        fun deleteStale(context: Context) {
            val cutoff = System.currentTimeMillis() - MAX_AGE_MILLIS
            directory(context).listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        }
    }
}
