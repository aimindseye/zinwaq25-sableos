package org.sableos.reader.engine.audio

import java.io.File
import java.nio.ByteBuffer
import java.io.RandomAccessFile

/** Random access to the bytes of one file; [read] returns null when the range is not fully inside the file. */
interface RandomAccessSource {
    val length: Long

    fun read(offset: Long, length: Int): ByteArray?
}

class ByteArraySource(private val bytes: ByteArray) : RandomAccessSource {
    override val length: Long get() = bytes.size.toLong()

    override fun read(offset: Long, length: Int): ByteArray? {
        val end = offset + length
        val valid = offset >= 0 && length >= 0 && end <= bytes.size
        return if (valid) bytes.copyOfRange(offset.toInt(), end.toInt()) else null
    }
}

class FileSource(private val file: File) : RandomAccessSource {
    override val length: Long get() = file.length()

    override fun read(offset: Long, length: Int): ByteArray? {
        val valid = offset >= 0 && length >= 0 && offset + length <= file.length()
        if (!valid) return null
        return RandomAccessFile(file, "r").use { raf ->
            ByteArray(length).also {
                raf.seek(offset)
                raf.readFully(it)
            }
        }
    }
}

/** What was found in an MP4/M4B container. [chapters] is empty when the file carries none we understand. */
data class Mp4Info(val durationMs: Long?, val chapters: List<AudioChapter>)

/**
 * Reads the duration (`moov/mvhd`) and Nero chapter list (`moov/udta/chpl`) of an MP4/M4B file, touching only box
 * headers and the chapter atom, with bounds everywhere: a box that claims more than the file holds, a box that nests
 * too deep, too many boxes, an oversized chapter atom or an inconsistent chapter list all end in "no chapters" rather
 * than a crash or a large allocation. QuickTime text-track chapters are not read (see the stage documentation).
 */
object Mp4ChapterReader {
    private const val HEADER = 8
    private const val TYPE_AT = 4
    private const val TYPE_LENGTH = 4
    private const val LARGE_HEADER = 16
    private const val TICKS_100NS_PER_MS = 10_000L
    private const val MS_PER_SECOND = 1000L
    private val containers = setOf("moov", "udta")

    fun read(source: RandomAccessSource, limits: AudioLimits = AudioLimits()): Mp4Info =
        Walker(source, limits).walk()

    private class Walker(private val source: RandomAccessSource, private val limits: AudioLimits) {
        private var boxes = 0
        private var durationMs: Long? = null
        private var chapters: List<AudioChapter> = emptyList()

        fun walk(): Mp4Info {
            scan(0L, source.length, 0)
            return Mp4Info(durationMs, chapters)
        }

        private fun scan(start: Long, end: Long, depth: Int) {
            var offset = start
            while (offset + HEADER <= end && boxes < limits.maxBoxes && depth < limits.maxBoxDepth) {
                boxes++
                val header = boxHeaderAt(offset, end) ?: return
                val payloadStart = offset + header.headerSize
                val boxEnd = offset + header.size
                when (header.type) {
                    "mvhd" -> if (durationMs == null) durationMs = readMvhd(payloadStart, boxEnd)
                    "chpl" -> if (chapters.isEmpty()) chapters = readChpl(payloadStart, boxEnd)
                    in containers -> scan(payloadStart, boxEnd, depth + 1)
                }
                offset = boxEnd
            }
        }

        private class BoxHeader(val type: String, val size: Long, val headerSize: Int)

        /** Null for anything malformed: a size smaller than its header or larger than the space that is left. */
        private fun boxHeaderAt(offset: Long, end: Long): BoxHeader? {
            val head = source.read(offset, HEADER) ?: return null
            val declared = u32(head, 0)
            val type = String(head, TYPE_AT, TYPE_LENGTH, Charsets.ISO_8859_1)
            val large = declared == 1L
            val headerSize = if (large) LARGE_HEADER else HEADER
            val size = when {
                declared == 0L -> end - offset
                large -> source.read(offset + HEADER, HEADER)?.let { u64(it, 0) } ?: -1L
                else -> declared
            }
            val valid = size >= headerSize && offset + size <= end
            return if (valid) BoxHeader(type, size, headerSize) else null
        }

        private fun readMvhd(start: Long, end: Long): Long? {
            val body = source.read(start, (end - start).coerceAtMost(MVHD_BYTES.toLong()).toInt())
            val fields = body?.let(::mvhdFields)
            val valid = fields != null && fields.first > 0 && fields.second >= 0
            return if (valid) fields.second * MS_PER_SECOND / fields.first else null
        }

        /** (timescale, duration) of a version 0 or 1 `mvhd`; null when the box is too short or another version. */
        private fun mvhdFields(body: ByteArray): Pair<Long, Long>? = when (body.firstOrNull()?.toInt()) {
            0 -> if (body.size >= MVHD_V0_BYTES) u32(body, V0_TIMESCALE_AT) to u32(body, V0_DURATION_AT) else null
            1 -> if (body.size >= MVHD_V1_BYTES) u32(body, V1_TIMESCALE_AT) to u64(body, V1_DURATION_AT) else null
            else -> null
        }

        private fun readChpl(start: Long, end: Long): List<AudioChapter> {
            val size = end - start
            val payload = if (size in 1..limits.maxChapterAtomBytes) source.read(start, size.toInt()) else null
            return payload?.let { ChplLayouts.parse(it, limits) }.orEmpty()
        }
    }

    private const val UINT_MASK = 0xffffffffL
    private const val MVHD_BYTES = 32
    private const val MVHD_V0_BYTES = 20
    private const val MVHD_V1_BYTES = 32
    private const val V0_TIMESCALE_AT = 12
    private const val V0_DURATION_AT = 16
    private const val V1_TIMESCALE_AT = 20
    private const val V1_DURATION_AT = 24

    internal fun toMs(ticks100ns: Long): Long = ticks100ns / TICKS_100NS_PER_MS

    internal fun u32(b: ByteArray, at: Int): Long = ByteBuffer.wrap(b).getInt(at).toLong() and UINT_MASK

    internal fun u64(b: ByteArray, at: Int): Long = ByteBuffer.wrap(b).getLong(at)
}
