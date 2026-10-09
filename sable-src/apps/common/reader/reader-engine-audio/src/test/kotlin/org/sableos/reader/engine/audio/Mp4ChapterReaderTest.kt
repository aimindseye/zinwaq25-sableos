package org.sableos.reader.engine.audio

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp4ChapterReaderTest {
    private fun box(type: String, payload: ByteArray): ByteArray =
        ByteBuffer.allocate(8 + payload.size).putInt(8 + payload.size)
            .put(type.toByteArray(Charsets.ISO_8859_1)).put(payload).array()

    private fun mvhd(timescale: Int, duration: Int): ByteArray {
        val body = ByteBuffer.allocate(100).putInt(0).putInt(0).putInt(0).putInt(timescale).putInt(duration).array()
        return box("mvhd", body)
    }

    private enum class Layout(val skip: Int, val countBytes: Int) {
        PADDED(8, 1),
        WIDE(8, 4),
        SHORT(5, 1),
        MINIMAL(4, 1),
    }

    private fun chplPayload(layout: Layout, chapters: List<Pair<Long, String>>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(1, 0, 0, 0))
        out.write(ByteArray(layout.skip - 4))
        if (layout.countBytes == 1) {
            out.write(chapters.size)
        } else {
            out.write(ByteBuffer.allocate(4).putInt(chapters.size).array())
        }
        chapters.forEach { (startMs, title) ->
            out.write(ByteBuffer.allocate(8).putLong(startMs * 10_000).array())
            val bytes = title.toByteArray()
            out.write(bytes.size)
            out.write(bytes)
        }
        return out.toByteArray()
    }

    private fun mp4(chpl: ByteArray?, durationSeconds: Int = 3600): ByteArray {
        val udta = if (chpl == null) ByteArray(0) else box("udta", box("chpl", chpl))
        val moov = box("moov", mvhd(1000, durationSeconds * 1000) + udta)
        return box("ftyp", "M4B \u0000\u0000\u0000\u0000".toByteArray()) + moov + box("mdat", ByteArray(64))
    }

    private val chapters = listOf(0L to "Opening", 60_000L to "Chapter 1", 3_600_000L to "Ünïcode ✓")

    @Test
    fun everyKnownWriterLayoutIsRead() {
        Layout.entries.forEach { layout ->
            val info = Mp4ChapterReader.read(ByteArraySource(mp4(chplPayload(layout, chapters))))
            assertEquals(layout.name, listOf("Opening", "Chapter 1", "Ünïcode ✓"), info.chapters.map { it.title })
            assertEquals(layout.name, listOf(0L, 60_000L, 3_600_000L), info.chapters.map { it.startMs })
            assertEquals(3_600_000L, info.durationMs)
        }
    }

    @Test
    fun filesWithoutChaptersYieldNone() {
        val info = Mp4ChapterReader.read(ByteArraySource(mp4(null)))
        assertTrue(info.chapters.isEmpty())
        assertEquals(3_600_000L, info.durationMs)
    }

    @Test
    fun garbageTruncatedAndEmptyInputNeverCrash() {
        val truncated = mp4(chplPayload(Layout.PADDED, chapters)).copyOf(40)
        listOf(ByteArray(0), ByteArray(7), ByteArray(4096) { (it * 7).toByte() }, truncated).forEach {
            val info = Mp4ChapterReader.read(ByteArraySource(it))
            assertTrue(info.chapters.isEmpty())
        }
    }

    @Test
    fun aBoxClaimingMoreThanTheFileHoldsEndsTheScan() {
        val good = mp4(chplPayload(Layout.PADDED, chapters))
        val lying = good.copyOf()
        ByteBuffer.wrap(lying).putInt(0, Int.MAX_VALUE)
        assertTrue(Mp4ChapterReader.read(ByteArraySource(lying)).chapters.isEmpty())
        val tooSmall = good.copyOf()
        ByteBuffer.wrap(tooSmall).putInt(0, 3)
        assertTrue(Mp4ChapterReader.read(ByteArraySource(tooSmall)).chapters.isEmpty())
    }

    @Test
    fun inconsistentChapterListsAreRejected() {
        val ok = chplPayload(Layout.PADDED, chapters)
        val extraByte = ok + byteArrayOf(0)
        assertTrue(Mp4ChapterReader.read(ByteArraySource(mp4(extraByte))).chapters.isEmpty())
        val moreThanPresent = ok.copyOf().also { it[8] = 9 }
        assertTrue(Mp4ChapterReader.read(ByteArraySource(mp4(moreThanPresent))).chapters.isEmpty())
        val decreasing = chplPayload(Layout.PADDED, listOf(0L to "a", 5000L to "b", 1000L to "c"))
        assertTrue(Mp4ChapterReader.read(ByteArraySource(mp4(decreasing))).chapters.isEmpty())
        val zeroCount = ok.copyOf().also { it[8] = 0 }
        assertTrue(Mp4ChapterReader.read(ByteArraySource(mp4(zeroCount))).chapters.isEmpty())
    }

    @Test
    fun anOversizedChapterAtomIsNotLoaded() {
        val huge = chplPayload(Layout.PADDED, (0 until 200).map { it * 1000L to "c$it" })
        val limits = AudioLimits(maxChapterAtomBytes = 100)
        assertTrue(Mp4ChapterReader.read(ByteArraySource(mp4(huge)), limits).chapters.isEmpty())
        assertEquals(200, Mp4ChapterReader.read(ByteArraySource(mp4(huge))).chapters.size)
    }

    @Test
    fun theChapterCountIsBounded() {
        val many = chplPayload(Layout.WIDE, (0 until 300).map { it * 10L to "c" })
        assertTrue(Mp4ChapterReader.read(ByteArraySource(mp4(many)), AudioLimits(maxChapters = 100)).chapters.isEmpty())
    }

    @Test
    fun titlesAreCleanedAndNeverEmpty() {
        val payload = chplPayload(Layout.PADDED, listOf(0L to "  \u0007Bell\u0000 ", 10L to "", 20L to "x".repeat(250)))
        val titles = Mp4ChapterReader.read(ByteArraySource(mp4(payload))).chapters.map { it.title }
        assertEquals("Bell", titles[0])
        assertEquals("Chapter 2", titles[1])
        assertEquals(AudioLimits.DEFAULT_MAX_TITLE_LENGTH, titles[2].length)
    }

    @Test
    fun deeplyNestedBoxesStopAtTheDepthLimit() {
        var nested = box("chpl", chplPayload(Layout.PADDED, chapters))
        repeat(20) { nested = box("udta", nested) }
        val file = box("moov", nested)
        assertTrue(Mp4ChapterReader.read(ByteArraySource(file)).chapters.isEmpty())
        assertNull(Mp4ChapterReader.read(ByteArraySource(file)).durationMs)
    }

    @Test
    fun aBoxCountFloodIsBounded() {
        val flood = ByteArray(8) .let { box("free", ByteArray(0)) }
        val file = ByteArray(0).let { _ -> (0 until 5000).fold(ByteArray(0)) { acc, _ -> acc + flood } }
        val info = Mp4ChapterReader.read(ByteArraySource(file), AudioLimits(maxBoxes = 100))
        assertTrue(info.chapters.isEmpty())
    }

    @Test
    fun theFileSourceReadsRealFiles() {
        val file = java.io.File.createTempFile("m4b", ".m4b").also { it.deleteOnExit() }
        file.writeBytes(mp4(chplPayload(Layout.PADDED, chapters)))
        assertEquals(3, Mp4ChapterReader.read(FileSource(file)).chapters.size)
    }
}
