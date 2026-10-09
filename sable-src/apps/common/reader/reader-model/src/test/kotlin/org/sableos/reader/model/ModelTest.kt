package org.sableos.reader.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicationKindTest {
    @Test
    fun exactlyTheFourOwnedKindsExist() {
        assertEquals(
            listOf("EPUB", "PDF", "COMIC", "AUDIOBOOK"),
            PublicationKind.entries.map { it.stableValue },
        )
    }

    @Test
    fun stableValueRoundTripsAndLegacyFallsBackToEpub() {
        PublicationKind.entries.forEach {
            assertEquals(it, PublicationKind.fromStableValue(it.stableValue))
        }
        assertEquals(PublicationKind.EPUB, PublicationKind.fromStableValue(null))
        assertEquals(PublicationKind.EPUB, PublicationKind.fromStableValue("TEXT"))
    }
}

class LocatorTest {
    @Test
    fun epubFractionIsClamped() {
        assertEquals(1.0, EpubLocator("{}", 7.0).fraction, 0.0)
        assertEquals(0.0, EpubLocator("{}", -1.0).fraction, 0.0)
        assertEquals(0.25, EpubLocator("{}", 0.25).fraction, 0.0)
    }

    @Test
    fun pageFractionCountsTheCurrentPage() {
        assertEquals(0.1, PageLocator(0, 10).fraction, 1e-9)
        assertEquals(1.0, PageLocator(9, 10).fraction, 1e-9)
        assertEquals(0.0, PageLocator(3, 0).fraction, 0.0)
    }

    @Test
    fun timeFractionUsesPositionOverDuration() {
        assertEquals(0.5, TimeLocator(500, 1000).fraction, 1e-9)
        assertEquals(0.0, TimeLocator(500, 0).fraction, 0.0)
        assertEquals(1.0, TimeLocator(5000, 1000).fraction, 0.0)
    }

    @Test
    fun codecRoundTripsEveryLocatorType() {
        val all = listOf(
            EpubLocator("""{"href":"c1.xhtml"}""", 0.4),
            PageLocator(12, 300),
            TimeLocator(65_000, 3_600_000, 3),
        )
        all.forEach { assertEquals(it, LocatorCodec.decode(LocatorCodec.encode(it))) }
    }

    @Test
    fun codecDiscriminatorIsStable() {
        assertTrue(LocatorCodec.encode(PageLocator(1, 2)).contains("\"type\":\"page\""))
        assertTrue(LocatorCodec.encode(TimeLocator(1, 2)).contains("\"type\":\"time\""))
        assertTrue(LocatorCodec.encode(EpubLocator("{}", 0.0)).contains("\"type\":\"epub\""))
    }

    @Test
    fun codecRejectsGarbageWithoutThrowing() {
        assertNull(LocatorCodec.decode(null))
        assertNull(LocatorCodec.decode(""))
        assertNull(LocatorCodec.decode("not json"))
        assertNull(LocatorCodec.decode("""{"type":"unknown"}"""))
    }
}

class ReadingProgressTest {
    @Test
    fun epubIsFinishedAtThreshold() {
        assertFalse(ReadingProgress.epub("{}", 0.98, 1L).isFinished)
        assertTrue(ReadingProgress.epub("{}", FinishedPolicy.EPUB_FRACTION, 1L).isFinished)
    }

    @Test
    fun pagedIsFinishedOnLastPageOnly() {
        assertFalse(ReadingProgress.page(8, 10, 1L).isFinished)
        assertTrue(ReadingProgress.page(9, 10, 1L).isFinished)
        assertFalse(ReadingProgress.page(0, 0, 1L).isFinished)
    }

    @Test
    fun audioIsFinishedWithinRemainingWindow() {
        val duration = 3_600_000L
        assertFalse(ReadingProgress.time(duration - FinishedPolicy.AUDIO_REMAINING_MS - 1, duration, 0, 1L).isFinished)
        assertTrue(ReadingProgress.time(duration - FinishedPolicy.AUDIO_REMAINING_MS, duration, 0, 1L).isFinished)
        assertFalse(ReadingProgress.time(0, 0, 0, 1L).isFinished)
    }

    @Test
    fun eachFormatKeepsItsOwnTypedState() {
        val epub = ReadingProgress.epub("""{"href":"a"}""", 0.5, 1L).locator as EpubLocator
        val pdf = ReadingProgress.page(4, 20, 1L).locator as PageLocator
        val audio = ReadingProgress.time(1000, 9000, 2, 1L).locator as TimeLocator
        assertEquals("""{"href":"a"}""", epub.locatorJson)
        assertEquals(20, pdf.pageCount)
        assertEquals(2, audio.chapterIndex)
    }
}

class LibraryItemTest {
    private fun item(kind: PublicationKind, progress: ReadingProgress? = null) = LibraryItem(
        id = "id-$kind",
        profileId = "default",
        kind = kind,
        title = "T",
        authors = listOf("A", "B"),
        progress = progress,
    )

    @Test
    fun everyKindCanBeRepresentedWithCommonMetadata() {
        PublicationKind.entries.forEach {
            val value = item(it)
            assertEquals(it, value.kind)
            assertEquals(0.0, value.progressFraction, 0.0)
            assertFalse(value.hasBeenOpened)
        }
    }

    @Test
    fun progressFractionIsNormalizedAcrossFormats() {
        assertEquals(0.5, item(PublicationKind.EPUB, ReadingProgress.epub("{}", 0.5, 1)).progressFraction, 1e-9)
        assertEquals(0.5, item(PublicationKind.PDF, ReadingProgress.page(4, 10, 1)).progressFraction, 1e-9)
        assertEquals(0.25, item(PublicationKind.AUDIOBOOK, ReadingProgress.time(25, 100, 0, 1)).progressFraction, 1e-9)
    }

    @Test
    fun authorsRoundTripThroughTheSingleColumn() {
        assertEquals("A; B", item(PublicationKind.EPUB).authorLine)
        assertEquals(listOf("A", "B"), AuthorList.split("A; B"))
        assertEquals(listOf("Solo"), AuthorList.split("Solo"))
        assertEquals(emptyList<String>(), AuthorList.split(null))
        assertEquals(emptyList<String>(), AuthorList.split(" ; ;"))
    }
}

class CollectionAndBookmarkTest {
    @Test
    fun collectionMembershipIsByItemId() {
        val shelf = Collection("c1", "default", "Favorites", 1L, itemIds = setOf("a", "b"))
        assertTrue(shelf.contains("a"))
        assertFalse(shelf.contains("z"))
    }

    @Test
    fun collectionsMayMixPublicationKinds() {
        val mixed = Collection("c2", "default", "Mixed", 1L, itemIds = setOf("epub-1", "cbz-1", "m4b-1"))
        assertEquals(3, mixed.itemIds.size)
    }

    @Test
    fun playbackBookmarkConvertsToAndFromGenericBookmark() {
        val playback = PlaybackBookmark("b1", "item", "default", 90_000L, 2, "Nice", 5L)
        val generic = playback.toBookmark(durationMs = 600_000L)
        assertEquals(TimeLocator(90_000L, 600_000L, 2), generic.locator)
        assertEquals(playback, PlaybackBookmark.from(generic))
    }

    @Test
    fun nonTimeBookmarkIsNotAPlaybackBookmark() {
        val page = Bookmark("b2", "item", "default", PageLocator(3, 10), "p", 5L)
        assertNull(PlaybackBookmark.from(page))
    }
}
