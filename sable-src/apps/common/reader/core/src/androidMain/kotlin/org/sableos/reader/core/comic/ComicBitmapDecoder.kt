package org.sableos.reader.core.comic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.sableos.reader.engine.comic.ComicLimits
import org.sableos.reader.engine.comic.ImageDecodePlanner

/** Outcome of decoding one page image. */
sealed interface PageDecode {
    data class Decoded(val bitmap: Bitmap) : PageDecode

    /** The image is not a decodable picture (corrupt, truncated or an unsupported format). */
    data object Undecodable : PageDecode

    /** The declared size is beyond [ComicLimits.maxImagePixels]: refused before a single pixel is decoded. */
    data object TooManyPixels : PageDecode

    data object OutOfMemory : PageDecode
}

/**
 * Decodes page bytes with `BitmapFactory`, planning the sub-sampling from the declared dimensions first so that
 * neither a decompression-bomb image nor a very large scan can exhaust memory.
 */
object ComicBitmapDecoder {
    fun decode(bytes: ByteArray, targetWidth: Int, targetHeight: Int, limits: ComicLimits = ComicLimits()): PageDecode {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val declared = bounds.takeIf { it.outWidth > 0 && it.outHeight > 0 }
        val plan = declared?.let {
            ImageDecodePlanner.plan(it.outWidth, it.outHeight, targetWidth, targetHeight, limits)
        }
        return when {
            declared == null -> PageDecode.Undecodable
            plan == null -> PageDecode.TooManyPixels
            else -> decodeSampled(bytes, plan.sampleSize)
        }
    }

    private fun decodeSampled(bytes: ByteArray, sampleSize: Int): PageDecode {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val attempt = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }
        return when (attempt.exceptionOrNull()) {
            null -> attempt.getOrNull()?.let { PageDecode.Decoded(it) } ?: PageDecode.Undecodable
            is OutOfMemoryError -> PageDecode.OutOfMemory
            else -> PageDecode.Undecodable
        }
    }
}
