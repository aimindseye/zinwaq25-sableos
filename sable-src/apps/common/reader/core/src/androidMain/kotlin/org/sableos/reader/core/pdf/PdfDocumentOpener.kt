package org.sableos.reader.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sableos.reader.engine.pdf.PdfDocumentSession
import timber.log.Timber

enum class PdfOpenFailure { UNREADABLE, PASSWORD_PROTECTED, CORRUPT }

sealed interface PdfOpenResult {
    data class Opened(val session: PdfDocumentSession<Bitmap>) : PdfOpenResult

    data class Failed(val reason: PdfOpenFailure) : PdfOpenResult
}

/**
 * Opens a PDF from a SAF/content or file URI. If the provider hands out a non-seekable descriptor (which
 * `PdfRenderer` cannot use) the file is copied once into the app cache and opened from there. Nothing is ever
 * written to shared storage.
 */
@Singleton
class PdfDocumentOpener @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun open(uri: Uri): PdfOpenResult = withContext(Dispatchers.IO) {
        val direct = tryOpen { context.contentResolver.openFileDescriptor(uri, READ_MODE) }
        if (direct is PdfOpenResult.Opened || direct == PdfOpenResult.Failed(PdfOpenFailure.PASSWORD_PROTECTED)) {
            direct
        } else {
            val cached = copyToCache(uri)
            if (cached == null) {
                PdfOpenResult.Failed(PdfOpenFailure.UNREADABLE)
            } else {
                tryOpen { ParcelFileDescriptor.open(cached, ParcelFileDescriptor.MODE_READ_ONLY) }
            }
        }
    }

    private fun tryOpen(descriptor: () -> ParcelFileDescriptor?): PdfOpenResult {
        val pfd = acquire(descriptor) ?: return PdfOpenResult.Failed(PdfOpenFailure.UNREADABLE)
        return try {
            PdfOpenResult.Opened(PdfDocumentSession(AndroidPdfRenderer(pfd)))
        } catch (e: SecurityException) {
            pfd.close()
            Timber.w(e, "PDF is password protected")
            PdfOpenResult.Failed(PdfOpenFailure.PASSWORD_PROTECTED)
        } catch (e: IOException) {
            pfd.close()
            Timber.w(e, "PDF is corrupt")
            PdfOpenResult.Failed(PdfOpenFailure.CORRUPT)
        } catch (e: IllegalArgumentException) {
            pfd.close()
            Timber.w(e, "PDF is invalid")
            PdfOpenResult.Failed(PdfOpenFailure.CORRUPT)
        }
    }

    private fun acquire(descriptor: () -> ParcelFileDescriptor?): ParcelFileDescriptor? = try {
        descriptor()
    } catch (e: IOException) {
        Timber.w(e, "PDF descriptor unavailable")
        null
    } catch (e: SecurityException) {
        Timber.w(e, "PDF access denied")
        null
    }

    private fun copyToCache(uri: Uri): File? {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val target = File(dir, "${uri.toString().hashCode()}.pdf")
        val reusable = target.isFile && target.length() > 0L
        return if (reusable || copy(uri, target)) target else null
    }

    private fun copy(uri: Uri, target: File): Boolean = try {
        val input = context.contentResolver.openInputStream(uri)
        input?.use { stream -> target.outputStream().use { stream.copyTo(it) } }
        input != null
    } catch (e: IOException) {
        Timber.w(e, "PDF cache copy failed")
        target.delete()
        false
    }

    private companion object {
        const val READ_MODE = "r"
        const val CACHE_DIR = "pdf"
    }
}
