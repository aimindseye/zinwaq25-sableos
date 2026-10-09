package org.sableos.reader.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudiobookTimelineTest {
    private fun track(i: Int, ms: Long) = AudioTrack("content://t/$i", "Track $i", ms)

    private val folder = AudiobookTimeline(
        AudioManifest.fromTracks("Book", null, listOf(track(1, 60_000), track(2, 90_000), track(3, 30_000))),
    )

    private val m4b = AudiobookTimeline(
        AudioManifest(
            title = "One file",
            tracks = listOf(track(1, 300_000)),
            chapters = listOf(AudioChapter("A", 0), AudioChapter("B", 100_000), AudioChapter("C", 250_000)),
        ),
    )

    @Test
    fun foldersMakeOneChapterPerFile() {
        assertEquals(3, folder.chapterCount)
        assertEquals(180_000L, folder.durationMs)
        assertEquals(60_000L, folder.chapterStart(1))
        assertEquals(150_000L, folder.chapterStart(2))
        assertEquals(150_000L, folder.chapterEnd(1))
        assertEquals(180_000L, folder.chapterEnd(2))
    }

    @Test
    fun globalPositionsMapToFilesAndBack() {
        assertEquals(TrackPosition(0, 0), folder.locate(0))
        assertEquals(TrackPosition(0, 59_999), folder.locate(59_999))
        assertEquals(TrackPosition(1, 0), folder.locate(60_000))
        assertEquals(TrackPosition(2, 15_000), folder.locate(165_000))
        assertEquals(TrackPosition(2, 30_000), folder.locate(180_000))
        assertEquals(TrackPosition(2, 30_000), folder.locate(9_999_999))
        assertEquals(TrackPosition(0, 0), folder.locate(-5))
        assertEquals(165_000L, folder.globalPosition(2, 15_000))
        assertEquals(60_000L, folder.globalPosition(1, -10))
        assertEquals(180_000L, folder.globalPosition(99, 99_999_999))
    }

    @Test
    fun chaptersInsideASingleFileSeekWithinIt() {
        assertEquals(1, m4b.chapterIndexAt(100_000))
        assertEquals(0, m4b.chapterIndexAt(99_999))
        assertEquals(2, m4b.chapterIndexAt(10_000_000))
        assertEquals(100_000L, m4b.nextChapterStart(10_000))
        assertNull(m4b.nextChapterStart(260_000))
    }

    @Test
    fun previousChapterRestartsThenGoesBack() {
        assertEquals(100_000L, m4b.previousChapterStart(110_000))
        assertEquals(0L, m4b.previousChapterStart(101_000))
        assertEquals(0L, m4b.previousChapterStart(2_000))
        assertEquals(250_000L, m4b.previousChapterStart(260_000))
        assertEquals(100_000L, m4b.previousChapterStart(251_000))
    }

    @Test
    fun seekingClampsToTheBook() {
        assertEquals(0L, folder.clamp(5_000 - 60_000))
        assertEquals(180_000L, folder.clamp(170_000 + 60_000))
        assertEquals(35_000L, folder.clamp(5_000 + 30_000))
    }

    @Test
    fun fractionIsSafeForEmptyAndNormalBooks() {
        assertEquals(0.5, folder.fraction(90_000), 0.0)
        val silent = AudiobookTimeline(AudioManifest.fromTracks("x", null, listOf(track(1, 0))))
        assertEquals(0.0, silent.fraction(10), 0.0)
    }
}
