package org.sableos.reader.core.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** What the platform media framework reports about one audio file. All fields are best effort. */
data class AudioMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val cover: Bitmap?,
)

/** Reads tags, duration and embedded cover with `MediaMetadataRetriever` (no third-party tag library). */
@Singleton
class AudioMetadataReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Blocking; call off the main thread. Null when the framework cannot open the file at all. */
    fun read(uri: Uri, wantCover: Boolean = false): AudioMetadata? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            AudioMetadata(
                title = retriever.text(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.text(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    ?: retriever.text(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: retriever.text(MediaMetadataRetriever.METADATA_KEY_AUTHOR),
                album = retriever.text(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                durationMs = retriever.text(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                cover = if (wantCover) decodeCover(retriever.embeddedPicture) else null,
            )
        } catch (e: IllegalArgumentException) {
            timber.log.Timber.d(e, "Audio metadata unavailable")
            null
        } catch (e: SecurityException) {
            timber.log.Timber.d(e, "Audio metadata not permitted")
            null
        } finally {
            retriever.release()
        }
    }

    private fun MediaMetadataRetriever.text(key: Int): String? =
        extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_TEXT)

    private fun decodeCover(bytes: ByteArray?): Bitmap? {
        if (bytes == null || bytes.isEmpty() || bytes.size > MAX_COVER_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= COVER_TARGET && bounds.outHeight / (sample * 2) >= COVER_TARGET) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private companion object {
        const val MAX_TEXT = 300
        const val MAX_COVER_BYTES = 8 * 1024 * 1024
        const val COVER_TARGET = 600
    }
}
