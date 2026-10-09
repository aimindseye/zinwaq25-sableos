package org.sableos.reader.engine.audio

import org.sableos.reader.model.NaturalOrder

/** One audio file found in a folder or picked by the user, before its duration is known. */
data class AudioEntry(val name: String, val uri: String, val sizeBytes: Long)

enum class AudioCatalogFailure { NO_AUDIO, TOO_MANY_FILES, NOT_LOCAL }

class AudioCatalogException(val failure: AudioCatalogFailure) : Exception(failure.name)

/** Classifies and orders audiobook files. Pure name logic, shared by single-file and folder imports. */
object AudioCatalog {
    private val extensions = setOf("mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "flac", "wav")
    private const val HIDDEN_PREFIX = "._"

    fun isAudioName(name: String): Boolean {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        return !base.startsWith(HIDDEN_PREFIX) && base.substringAfterLast('.', "").lowercase() in extensions
    }

    fun isChapteredContainer(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in setOf("m4b", "mp4")

    /**
     * Keeps audio files with a local URI, orders them naturally by name (`2.mp3` before `10.mp3`) and enforces the
     * track limit. Throws [AudioCatalogException] when nothing playable remains or the folder is oversized.
     */
    fun order(entries: List<AudioEntry>, limits: AudioLimits = AudioLimits()): List<AudioEntry> {
        if (entries.any { !PlaybackSourcePolicy.isLocal(it.uri) }) refuse(AudioCatalogFailure.NOT_LOCAL)
        val audio = entries.filter { isAudioName(it.name) }
        if (audio.isEmpty()) refuse(AudioCatalogFailure.NO_AUDIO)
        if (audio.size > limits.maxTracks) refuse(AudioCatalogFailure.TOO_MANY_FILES)
        return audio.sortedWith { a, b -> NaturalOrder.compare(a.name, b.name) }
    }

    private fun refuse(failure: AudioCatalogFailure): Nothing = throw AudioCatalogException(failure)

    /** Title shown for a track with no tag: the file name without directory or extension. */
    fun titleFromName(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        return base.substringBeforeLast('.', base).ifBlank { base }.trim()
    }
}
