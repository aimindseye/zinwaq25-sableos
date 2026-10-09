package org.sableos.reader.engine.audio

/** A position inside one file. */
data class TrackPosition(val trackIndex: Int, val offsetMs: Long)

/**
 * The whole-book timeline over a manifest: converts between a global position and (file, offset), and answers every
 * chapter question. All positions are clamped, so callers never need range checks.
 */
class AudiobookTimeline(private val manifest: AudioManifest) {
    init {
        require(manifest.tracks.isNotEmpty() && manifest.chapters.isNotEmpty()) { "needs tracks and chapters" }
    }

    private val trackStarts: LongArray = LongArray(manifest.tracks.size).also { starts ->
        var acc = 0L
        manifest.tracks.forEachIndexed { i, track ->
            starts[i] = acc
            acc += track.durationMs
        }
    }

    val durationMs: Long = manifest.durationMs
    val chapterCount: Int get() = manifest.chapters.size

    fun clamp(globalMs: Long): Long = globalMs.coerceIn(0L, durationMs)

    fun globalPosition(trackIndex: Int, offsetMs: Long): Long {
        val i = trackIndex.coerceIn(0, trackStarts.lastIndex)
        return clamp(trackStarts[i] + offsetMs.coerceAtLeast(0L))
    }

    fun locate(globalMs: Long): TrackPosition {
        val position = clamp(globalMs)
        var index = trackStarts.indexOfLast { it <= position }.coerceAtLeast(0)
        // A position exactly on a boundary belongs to the next file, except at the very end of the book.
        if (position == durationMs && index > 0 && trackStarts[index] == position) index--
        val offset = position - trackStarts[index]
        return TrackPosition(index, offset.coerceAtMost(manifest.tracks[index].durationMs))
    }

    fun chapterIndexAt(globalMs: Long): Int {
        val position = clamp(globalMs)
        return manifest.chapters.indexOfLast { it.startMs <= position }.coerceAtLeast(0)
    }

    fun chapterStart(index: Int): Long = manifest.chapters.getOrNull(index)?.startMs ?: 0L

    fun chapterEnd(index: Int): Long = manifest.chapters.getOrNull(index + 1)?.startMs ?: durationMs

    fun nextChapterStart(globalMs: Long): Long? {
        val next = chapterIndexAt(globalMs) + 1
        return if (next < chapterCount) chapterStart(next) else null
    }

    /**
     * The usual player rule: pressing "previous chapter" more than [restartThresholdMs] into a chapter restarts that
     * chapter; within the threshold it goes to the chapter before (or the start of the book).
     */
    fun previousChapterStart(globalMs: Long, restartThresholdMs: Long = RESTART_THRESHOLD_MS): Long {
        val current = chapterIndexAt(globalMs)
        val start = chapterStart(current)
        return if (clamp(globalMs) - start > restartThresholdMs || current == 0) start else chapterStart(current - 1)
    }

    fun fraction(globalMs: Long): Double = if (durationMs <= 0L) 0.0 else clamp(globalMs).toDouble() / durationMs

    companion object {
        const val RESTART_THRESHOLD_MS: Long = 3_000L
    }
}
