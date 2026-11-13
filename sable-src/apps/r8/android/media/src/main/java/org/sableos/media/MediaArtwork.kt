package org.sableos.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.Locale

/**
 * Pictures for Media's rows and Now playing (owner, 2026-11-13): a song's cover art embedded in the file
 * (MP3, FLAC, M4A, OGG), a podcast's cover from its feed, and a radio station's logo from the station's
 * own website. Remote pictures are fetched once over HTTPS, kept in the app's cache and read from there
 * afterwards; nothing is asked of any directory service. Whatever has no picture keeps its colour tile.
 */
internal sealed interface ArtworkSource {
    val key: String

    /** A local audio file's embedded picture. */
    data class Embedded(
        val uri: String,
    ) : ArtworkSource {
        override val key: String get() = "file:$uri"
    }

    /** A podcast's cover, named by its feed. */
    data class Remote(
        val url: String,
    ) : ArtworkSource {
        override val key: String get() = "web:$url"
    }

    /** A station's logo, looked up on the website behind its stream address. */
    data class StationLogo(
        val streamUrl: String,
    ) : ArtworkSource {
        override val key: String get() = "station:$streamUrl"
    }
}

internal object MediaArtwork {
    private val memory = LruCache<String, Bitmap>(MEMORY_ENTRIES)
    private val missing = mutableSetOf<String>()

    fun cached(
        source: ArtworkSource,
        sizePx: Int,
    ): Bitmap? = memory.get(memoryKey(source, sizePx))

    /** Loads off the main thread; null when the source has no picture. */
    suspend fun load(
        context: Context,
        source: ArtworkSource,
        sizePx: Int,
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            val memoryKey = memoryKey(source, sizePx)
            memory.get(memoryKey)?.let { return@withContext it }
            if (synchronized(missing) { memoryKey in missing }) return@withContext null
            val bitmap =
                runCatching {
                    when (source) {
                        is ArtworkSource.Embedded -> {
                            embedded(context, source.uri)
                        }

                        is ArtworkSource.Remote -> {
                            remote(context, source.key, listOf(source.url))
                        }

                        is ArtworkSource.StationLogo -> {
                            remote(
                                context,
                                source.key,
                                stationLogoCandidates(source.streamUrl),
                            )
                        }
                    }
                }.getOrNull()?.let { decodeSampled(it, sizePx) }
            if (bitmap == null) {
                synchronized(missing) { missing += memoryKey }
            } else {
                memory.put(memoryKey, bitmap)
            }
            bitmap
        }

    private fun memoryKey(
        source: ArtworkSource,
        sizePx: Int,
    ) = "${source.key}@${sizeBucket(sizePx)}"

    private fun sizeBucket(sizePx: Int) = if (sizePx <= SMALL_BUCKET_PX) SMALL_BUCKET_PX else LARGE_BUCKET_PX

    private fun embedded(
        context: Context,
        uri: String,
    ): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(uri))
            retriever.embeddedPicture
        } finally {
            retriever.release()
        }
    }

    /**
     * The first candidate that answers with a picture, from the disk cache when it was fetched before.
     * A source with no picture is remembered for a week, so a station without a logo is not asked again
     * each time the list is drawn.
     */
    private fun remote(
        context: Context,
        key: String,
        candidates: List<String>,
    ): ByteArray? {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val name = cacheName(key)
        val file = File(dir, name)
        val none = File(dir, "$name.none")
        val recentlyMissing = none.isFile && System.currentTimeMillis() - none.lastModified() < RETRY_AFTER_MS
        return when {
            file.isFile -> {
                file.readBytes()
            }

            recentlyMissing -> {
                null
            }

            else -> {
                val bytes = candidates.asSequence().mapNotNull(::download).firstOrNull()
                if (bytes == null) {
                    none.writeText("")
                } else {
                    file.writeBytes(bytes)
                    none.delete()
                }
                bytes
            }
        }
    }

    private fun download(url: String): ByteArray? {
        if (!PodcastUrlPolicy.isHttpsUrl(url)) return null
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "image/*")
            val isImage = connection.contentType?.lowercase(Locale.US)?.startsWith("image/") != false
            if (connection.responseCode == HttpURLConnection.HTTP_OK && isImage) {
                connection.inputStream.use(::readLimited)?.takeIf(::isPicture)
            } else {
                null
            }
        } catch (_: java.io.IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /** The whole stream, or null when it is larger than [MAX_IMAGE_BYTES]. */
    private fun readLimited(input: java.io.InputStream): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        var read = input.read(buffer)
        while (read >= 0 && out.size() <= MAX_IMAGE_BYTES) {
            out.write(buffer, 0, read)
            read = input.read(buffer)
        }
        return out.toByteArray().takeIf { it.size <= MAX_IMAGE_BYTES }
    }

    private fun isPicture(bytes: ByteArray): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return bounds.outWidth >= MIN_LOGO_PX && bounds.outHeight >= MIN_LOGO_PX
    }

    private fun decodeSampled(
        bytes: ByteArray,
        sizePx: Int,
    ): Bitmap? {
        val target = sizeBucket(sizePx)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }

    private fun cacheName(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private const val MEMORY_ENTRIES = 96
    private const val SMALL_BUCKET_PX = 96
    private const val LARGE_BUCKET_PX = 320
    private const val CACHE_DIR = "artwork"
    private const val RETRY_AFTER_MS = 7L * 24 * 60 * 60 * 1_000
    private const val TIMEOUT_MS = 8_000
    private const val BUFFER_BYTES = 8_192
    private const val MAX_IMAGE_BYTES = 2 * 1_024 * 1_024
    private const val MIN_LOGO_PX = 16
}

/**
 * Where a station's logo may be: the touch icon, then the favicon, on the stream's host and then on its
 * parent domains (stream.example.com, then example.com). At most [MAX_LOGO_REQUESTS] addresses, all HTTPS,
 * all on the station's own domain.
 */
internal fun stationLogoCandidates(streamUrl: String): List<String> {
    val host =
        runCatching { URI(streamUrl.trim()).host }
            .getOrNull()
            ?.lowercase(Locale.US)
            ?.takeIf { it.contains('.') && !it.all { c -> c.isDigit() || c == '.' } }
            ?: return emptyList()
    val labels = host.split('.')
    val hosts =
        (0 until labels.size - 1)
            .map { labels.drop(it).joinToString(".") }
            // Stop before a bare public suffix such as co.uk or com.au.
            .filter { candidate -> candidate.split('.').let { it.size > 2 || it[0].length > PUBLIC_SUFFIX_MAX_LABEL } }
            .distinct()
            .take(MAX_LOGO_HOSTS)
    return hosts
        .flatMap { listOf("https://$it/apple-touch-icon.png", "https://$it/favicon.ico") }
        .take(MAX_LOGO_REQUESTS)
}

private const val MAX_LOGO_HOSTS = 2
private const val MAX_LOGO_REQUESTS = 4
private const val PUBLIC_SUFFIX_MAX_LABEL = 3

/**
 * A square picture for a row or Now playing: the source's picture when it has one, else a colour tile
 * with the name's first letter.
 */
@Composable
internal fun MediaArt(
    source: ArtworkSource?,
    name: String,
    fallback: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sizePx = (size.value * context.resources.displayMetrics.density).toInt()
    val bitmap: ImageBitmap? by produceState(
        initialValue = source?.let { MediaArtwork.cached(it, sizePx)?.asImageBitmap() },
        key1 = source?.key,
        key2 = sizePx,
    ) {
        if (value == null &&
            source != null
        ) {
            value = MediaArtwork.load(context.applicationContext, source, sizePx)?.asImageBitmap()
        }
    }
    val shape = RoundedCornerShape(if (size < LARGE_ART) 3.dp else 6.dp)
    val picture = bitmap
    if (picture != null) {
        Image(
            bitmap = picture,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape),
        )
    } else {
        Box(
            modifier = modifier.size(size).clip(shape).background(fallback),
            contentAlignment = Alignment.Center,
        ) {
            val letter = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
            if (letter != null && size >= LETTER_MIN) {
                Text(
                    text = letter.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (size.value * LETTER_SCALE).sp,
                )
            }
        }
    }
}

private val LARGE_ART = 48.dp
private val LETTER_MIN = 20.dp
private const val LETTER_SCALE = 0.5f
