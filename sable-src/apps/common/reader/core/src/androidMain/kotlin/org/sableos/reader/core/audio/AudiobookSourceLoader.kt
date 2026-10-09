package org.sableos.reader.core.audio

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton
import org.sableos.reader.core.documents.DocumentTreeLister
import org.sableos.reader.core.documents.TreeListingException
import org.sableos.reader.engine.audio.AudioCatalog
import org.sableos.reader.engine.audio.AudioCatalogException
import org.sableos.reader.engine.audio.AudioCatalogFailure
import org.sableos.reader.engine.audio.AudioChapter
import org.sableos.reader.engine.audio.AudioEntry
import org.sableos.reader.engine.audio.AudioLimits
import org.sableos.reader.engine.audio.AudioManifest
import org.sableos.reader.engine.audio.AudioManifestCodec
import org.sableos.reader.engine.audio.AudioTrack
import org.sableos.reader.engine.audio.Mp4ChapterReader
import org.sableos.reader.engine.audio.PlaybackSourcePolicy
import org.vaachak.reader.core.domain.model.BookEntity

enum class AudiobookLoadFailure { UNREADABLE, NO_AUDIO, TOO_MANY_FILES, NOT_LOCAL }

class AudiobookLoadException(val failure: AudiobookLoadFailure, cause: Throwable? = null) :
    Exception(failure.name, cause)

/**
 * Builds and caches the [AudioManifest] of a library audiobook: one file (chapters from an MP4 `chpl` atom when it has
 * one) or a folder of files (one chapter per file, natural order). The cache under `files/audiobooks` is derived data:
 * deleting it only costs a rebuild. Everything stays local; only `content:` and `file:` sources are accepted.
 */
@Singleton
class AudiobookSourceLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadata: AudioMetadataReader,
) {
    /** Blocking; call off the main thread. Null when the audiobook can no longer be read. */
    fun loadOrBuild(book: BookEntity, limits: AudioLimits = AudioLimits()): AudioManifest? {
        val cache = cacheFile(book.bookHash)
        val cached = cache.takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }
            ?.let { AudioManifestCodec.decode(it, limits) }
        val built = cached ?: book.localUri?.let { uri ->
            runCatching { build(uri, book.title, book.author.takeIf { it.isNotBlank() }, limits) }.getOrNull()
        }
        if (cached == null && built != null) writeCache(cache, built)
        return built
    }

    /** Blocking. Throws [AudiobookLoadException]. */
    fun build(localUri: String, title: String, author: String?, limits: AudioLimits = AudioLimits()): AudioManifest {
        if (!PlaybackSourcePolicy.isLocal(localUri)) throw AudiobookLoadException(AudiobookLoadFailure.NOT_LOCAL)
        val uri = Uri.parse(localUri)
        return if (DocumentsContract.isTreeUri(uri)) {
            buildFolder(uri, title, author, limits)
        } else {
            buildFile(uri, title, author, limits)
        }
    }

    fun deleteCache(bookHash: String) {
        cacheFile(bookHash).delete()
    }

    private fun buildFile(uri: Uri, title: String, author: String?, limits: AudioLimits): AudioManifest {
        val meta = metadata.read(uri) ?: throw AudiobookLoadException(AudiobookLoadFailure.UNREADABLE)
        val mp4 = openSource(uri)?.use { source ->
            val isMp4 = source.read(FTYP_AT, FTYP.size)?.contentEquals(FTYP) == true
            if (isMp4) Mp4ChapterReader.read(source, limits) else null
        }
        val duration = meta.durationMs.takeIf { it > 0 } ?: mp4?.durationMs ?: 0L
        if (duration <= 0L) throw AudiobookLoadException(AudiobookLoadFailure.UNREADABLE)
        val bookTitle = meta.album ?: meta.title ?: title
        val chapters = withStart(mp4?.chapters.orEmpty().filter { it.startMs < duration }, bookTitle)
        return AudioManifest(
            title = bookTitle,
            author = meta.artist ?: author,
            tracks = listOf(AudioTrack(uri.toString(), bookTitle, duration)),
            chapters = chapters,
        )
    }

    /** A chapter list always starts at zero, so a book whose first chapter starts later gets a leading "Start". */
    private fun withStart(chapters: List<AudioChapter>, title: String): List<AudioChapter> = when {
        chapters.isEmpty() -> listOf(AudioChapter(title, 0L))
        chapters.first().startMs > 0L -> listOf(AudioChapter("Start", 0L)) + chapters
        else -> chapters
    }

    private fun buildFolder(tree: Uri, title: String, author: String?, limits: AudioLimits): AudioManifest {
        val ordered = orderedEntries(tree, limits)
        val tracks = ordered.mapNotNull { entry ->
            metadata.read(Uri.parse(entry.uri))?.takeIf { it.durationMs > 0 }?.let {
                val trackTitle = it.title ?: AudioCatalog.titleFromName(entry.name)
                AudioTrack(entry.uri, trackTitle, it.durationMs, entry.sizeBytes)
            }
        }
        if (tracks.isEmpty()) throw AudiobookLoadException(AudiobookLoadFailure.NO_AUDIO)
        return AudioManifest.fromTracks(title, author, tracks)
    }

    private fun orderedEntries(tree: Uri, limits: AudioLimits): List<AudioEntry> {
        val files = try {
            DocumentTreeLister(context.contentResolver, tree, MAX_FOLDER_ENTRIES, limits.maxFolderDepth).list()
        } catch (e: TreeListingException) {
            throw AudiobookLoadException(AudiobookLoadFailure.UNREADABLE, e)
        }
        return try {
            AudioCatalog.order(files.map { AudioEntry(it.path, it.uri, it.sizeBytes) }, limits)
        } catch (e: AudioCatalogException) {
            throw AudiobookLoadException(failureOf(e.failure), e)
        }
    }

    private fun failureOf(failure: AudioCatalogFailure): AudiobookLoadFailure = when (failure) {
        AudioCatalogFailure.NO_AUDIO -> AudiobookLoadFailure.NO_AUDIO
        AudioCatalogFailure.TOO_MANY_FILES -> AudiobookLoadFailure.TOO_MANY_FILES
        AudioCatalogFailure.NOT_LOCAL -> AudiobookLoadFailure.NOT_LOCAL
    }

    private fun openSource(uri: Uri): ChannelSource? {
        val pfd = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull() ?: return null
        return ChannelSource(FileInputStream(pfd.fileDescriptor).channel) { pfd.close() }
    }

    private fun cacheFile(bookHash: String): File = File(File(context.filesDir, CACHE_DIR), "$bookHash.json")

    private fun writeCache(file: File, manifest: AudioManifest) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(AudioManifestCodec.encode(manifest))
        }
    }

    private companion object {
        const val CACHE_DIR = "audiobooks"
        const val MAX_FOLDER_ENTRIES = 20_000
        const val FTYP_AT = 4L
        val FTYP = byteArrayOf('f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
    }
}
