package org.sableos.reader.engine.audio

/**
 * Parser for the payload of a Nero `chpl` atom. Writers disagree on the few bytes between the version/flags word and
 * the chapter list, so each known layout is tried and only one that consumes the payload *exactly*, with a plausible
 * count and non-decreasing start times, is accepted. Anything else yields no chapters.
 */
internal object ChplLayouts {
    private class Layout(val skip: Int, val countBytes: Int)

    private val layouts = listOf(Layout(8, 1), Layout(8, 4), Layout(5, 1), Layout(4, 1))
    private const val ENTRY_FIXED = 9
    private const val BYTE_MASK = 0xff

    fun parse(payload: ByteArray, limits: AudioLimits): List<AudioChapter> =
        layouts.firstNotNullOfOrNull { attempt(payload, it, limits) }.orEmpty()

    private fun attempt(payload: ByteArray, layout: Layout, limits: AudioLimits): List<AudioChapter>? {
        val count = readCount(payload, layout)
        val chapters = if (count != null && count in 1..limits.maxChapters) {
            readEntries(payload, layout.skip + layout.countBytes, count, limits)
        } else {
            null
        }
        val starts = chapters?.map { it.startMs }.orEmpty()
        return chapters?.takeIf { starts.zipWithNext().all { (a, b) -> a <= b } && starts.first() >= 0 }
    }

    private fun readCount(payload: ByteArray, layout: Layout): Int? {
        val end = layout.skip + layout.countBytes
        if (payload.size < end) return null
        return if (layout.countBytes == 1) {
            payload[layout.skip].toInt() and BYTE_MASK
        } else {
            Mp4ChapterReader.u32(payload, layout.skip).takeIf { it <= Int.MAX_VALUE }?.toInt()
        }
    }

    /** Null unless exactly [count] entries end exactly at the end of the payload. */
    private fun readEntries(payload: ByteArray, from: Int, count: Int, limits: AudioLimits): List<AudioChapter>? {
        val out = ArrayList<AudioChapter>(count)
        var at = from
        var valid = true
        while (valid && out.size < count) {
            val entry = readEntry(payload, at, out.size, limits)
            if (entry == null) {
                valid = false
            } else {
                out += entry.first
                at = entry.second
            }
        }
        return out.takeIf { valid && at == payload.size }
    }

    /** One entry at [at]: the chapter and the offset just past it, or null when it does not fit the payload. */
    private fun readEntry(payload: ByteArray, at: Int, index: Int, limits: AudioLimits): Pair<AudioChapter, Int>? {
        val fits = at + ENTRY_FIXED <= payload.size
        val start = if (fits) Mp4ChapterReader.u64(payload, at) else -1L
        val titleLength = if (fits) payload[at + Long.SIZE_BYTES].toInt() and BYTE_MASK else 0
        val titleStart = at + ENTRY_FIXED
        val valid = fits && start >= 0 && titleStart + titleLength <= payload.size
        return if (valid) {
            val raw = String(payload, titleStart, titleLength, Charsets.UTF_8)
            AudioChapter(cleanTitle(raw, index, limits), Mp4ChapterReader.toMs(start)) to titleStart + titleLength
        } else {
            null
        }
    }

    private fun cleanTitle(raw: String, index: Int, limits: AudioLimits): String {
        val cleaned = raw.filter { !it.isISOControl() }.trim().take(limits.maxTitleLength)
        return cleaned.ifEmpty { "Chapter ${index + 1}" }
    }
}
