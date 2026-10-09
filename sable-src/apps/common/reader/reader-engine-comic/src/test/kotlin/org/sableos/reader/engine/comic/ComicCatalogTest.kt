package org.sableos.reader.engine.comic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ComicCatalogTest {
    private fun raw(vararg names: String) = names.asSequence().map { RawEntry(it, 10, 10) }

    private fun failure(block: () -> Unit): ComicOpenFailure {
        try {
            block()
        } catch (e: ComicOpenException) {
            return e.failure
        }
        fail("expected ComicOpenException")
        error("unreachable")
    }

    @Test
    fun pagesAreFilteredDeduplicatedAndNaturallyOrdered() {
        val catalog = ComicCatalogBuilder.build(
            raw("p10.png", "p2.png", "P2.PNG", "../x.png", "notes.txt", "ComicInfo.xml", "p1.png", "__MACOSX/p1.png"),
        )
        assertEquals(listOf("p1.png", "p2.png", "p10.png"), catalog.pages.map { it.path })
        assertEquals(listOf(0, 1, 2), catalog.pages.map { it.index })
        assertEquals(1, catalog.skippedUnsafe)
        assertEquals(1, catalog.skippedDuplicates)
        assertEquals("ComicInfo.xml", catalog.comicInfoHandle)
    }

    @Test
    fun anArchiveWithoutPagesIsRefused() {
        assertEquals(ComicOpenFailure.NO_PAGES, failure { ComicCatalogBuilder.build(raw("a.txt", "b/")) })
        assertEquals(ComicOpenFailure.NO_PAGES, failure { ComicCatalogBuilder.build(raw("../a.png")) })
    }

    @Test
    fun entryCountIsEnforcedWhileListing() {
        val limits = ComicLimits(maxEntries = 5)
        var produced = 0
        val endless = generateSequence { RawEntry("n${produced++}.txt", 1, 1) }
        assertEquals(ComicOpenFailure.TOO_MANY_ENTRIES, failure { ComicCatalogBuilder.build(endless, limits) })
        assertTrue("listing must stop at the limit, produced $produced", produced <= 6)
    }

    @Test
    fun pageAndTotalSizeLimitsFailTheOpen() {
        assertEquals(
            ComicOpenFailure.TOO_MANY_PAGES,
            failure { ComicCatalogBuilder.build(raw("1.png", "2.png", "3.png"), ComicLimits(maxPages = 2)) },
        )
        val big = sequenceOf(RawEntry("1.png", 600, 600), RawEntry("2.png", 600, 600))
        assertEquals(
            ComicOpenFailure.ARCHIVE_TOO_LARGE,
            failure { ComicCatalogBuilder.build(big, ComicLimits(maxTotalBytes = 1000)) },
        )
    }

    @Test
    fun chaptersComeFromFoldersAndOnlyWhenThereAreSeveral() {
        val flat = ComicCatalogBuilder.build(raw("1.png", "2.png"))
        assertTrue(flat.chapters.isEmpty())
        val single = ComicCatalogBuilder.build(raw("ch1/1.png", "ch1/2.png"))
        assertTrue(single.chapters.isEmpty())
        val multi = ComicCatalogBuilder.build(raw("Ch 2/1.png", "Ch 1/2.png", "Ch 1/1.png", "Ch 10/1.png"))
        assertEquals(listOf("Ch 1", "Ch 2", "Ch 10"), multi.chapters.map { it.title })
        assertEquals(listOf(0, 2, 3), multi.chapters.map { it.firstPage })
    }

    @Test
    fun rootPagesBeforeFoldersFormAStartChapter() {
        val catalog = ComicCatalogBuilder.build(raw("0.png", "ch1/1.png"))
        assertEquals(listOf("Start", "ch1"), catalog.chapters.map { it.title })
    }

    @Test
    fun perPageGuardsRejectOversizeAndCompressionBombsButNotNormalPages() {
        val limits = ComicLimits()
        val ok = ComicPage(0, "1.png", 5_000_000, 4_900_000, "1.png")
        ComicCatalogBuilder.checkReadable(ok, limits)
        val huge = ok.copy(sizeBytes = limits.maxPageBytes + 1)
        assertEquals(PageFailure.TOO_LARGE, pageFailure { ComicCatalogBuilder.checkReadable(huge, limits) })
        val bomb = ok.copy(sizeBytes = 50_000_000, compressedBytes = 5_000)
        assertEquals(PageFailure.COMPRESSION_BOMB, pageFailure { ComicCatalogBuilder.checkReadable(bomb, limits) })
        val smallButRatioy = ok.copy(sizeBytes = 900_000, compressedBytes = 100)
        ComicCatalogBuilder.checkReadable(smallButRatioy, limits)
        assertNull(runCatching { ComicCatalogBuilder.checkReadable(ok, limits) }.exceptionOrNull())
    }

    private fun pageFailure(block: () -> Unit): PageFailure? =
        (runCatching(block).exceptionOrNull() as? ComicPageException)?.failure
}
