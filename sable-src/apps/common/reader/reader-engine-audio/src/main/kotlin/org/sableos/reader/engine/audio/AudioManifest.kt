package org.sableos.reader.engine.audio

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One playable file. [uri] is a `content:` or `file:` URI the user chose; nothing here ever points at a network. */
@Serializable
data class AudioTrack(val uri: String, val title: String, val durationMs: Long, val sizeBytes: Long = 0L)

/** A chapter start on the whole-book timeline. A chapter ends where the next begins (the last at the book's end). */
@Serializable
data class AudioChapter(val title: String, val startMs: Long)

/** Everything the player needs to know about an audiobook; derived from the files and cached, never user data. */
@Serializable
data class AudioManifest(
    val version: Int = VERSION,
    val title: String,
    val author: String? = null,
    val tracks: List<AudioTrack>,
    val chapters: List<AudioChapter>,
) {
    val durationMs: Long get() = tracks.sumOf { it.durationMs }

    companion object {
        const val VERSION: Int = 1

        /** One chapter per file, in track order: the right chapter model for folders of mp3/m4a files. */
        fun fromTracks(title: String, author: String?, tracks: List<AudioTrack>): AudioManifest {
            var start = 0L
            val chapters = tracks.map { track ->
                AudioChapter(track.title, start).also { start += track.durationMs }
            }
            return AudioManifest(title = title, author = author, tracks = tracks, chapters = chapters)
        }
    }
}

object AudioManifestCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(manifest: AudioManifest): String = json.encodeToString(AudioManifest.serializer(), manifest)

    /**
     * Returns null for blank, malformed, empty or internally inconsistent content (chapters out of order or outside the
     * book, no tracks, a non-local URI) so the caller rebuilds the manifest from the files.
     */
    fun decode(text: String?, limits: AudioLimits = AudioLimits()): AudioManifest? {
        val manifest = text?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString(AudioManifest.serializer(), it) }.getOrNull() }
        return manifest?.takeIf { isValid(it, limits) }
    }

    fun isValid(manifest: AudioManifest, limits: AudioLimits = AudioLimits()): Boolean {
        val duration = manifest.durationMs
        val starts = manifest.chapters.map { it.startMs }
        return manifest.version == AudioManifest.VERSION &&
            manifest.tracks.isNotEmpty() && manifest.tracks.size <= limits.maxTracks &&
            manifest.chapters.isNotEmpty() && manifest.chapters.size <= limits.maxChapters &&
            manifest.tracks.all { it.durationMs >= 0 && PlaybackSourcePolicy.isLocal(it.uri) } &&
            starts.first() == 0L && starts.zipWithNext().all { (a, b) -> a <= b } && starts.all { it in 0..duration }
    }
}
