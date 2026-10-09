package org.sableos.reader.engine.comic

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.sableos.reader.engine.comic.ComicFixtures.Entry

/** Every hostile container shape the P5C contract names, executed against the real zip reader. */
class HostileArchiveTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(name: String, bytes: ByteArray): File = ComicFixtures.write(tmp.root, name, bytes)

    private fun zip(vararg entries: Entry): ByteArray = ComicFixtures.zipBytes(entries.toList())

    private fun png(): ByteArray = ComicFixtures.png()

    private fun openFailure(f: File, limits: ComicLimits = ComicLimits()): ComicOpenFailure {
        try {
            ZipComicSource.open(f, limits).close()
        } catch (e: ComicOpenException) {
            return e.failure
        }
        fail("expected the archive to be refused")
        error("unreachable")
    }

    private fun pageFailure(source: ComicSource, index: Int): PageFailure {
        try {
            source.readPage(index)
        } catch (e: ComicPageException) {
            return e.failure
        }
        fail("expected page $index to be refused")
        error("unreachable")
    }

    @Test
    fun aWellFormedCbzOpensInNaturalOrderAndPagesDecode() {
        val f = ComicFixtures.cbz(tmp.root, "ok.cbz", "10.png", "2.png", "1.png")
        ZipComicSource.open(f).use { source ->
            assertEquals(listOf("1.png", "2.png", "10.png"), source.catalog.pages.map { it.path })
            assertEquals(3, source.pageCount)
            assertTrue(source.readPage(0).isNotEmpty())
        }
    }

    @Test
    fun pathTraversalNamesAreSkippedNeverOpened() {
        val names = listOf("../../evil.png", "/etc/passwd.png", "C:\\win.png", "ok/../../up.png", "good.png")
        val f = file("traversal.cbz", ComicFixtures.zipBytes(names.map { Entry(it, png()) }))
        ZipComicSource.open(f).use { source ->
            assertEquals(listOf("good.png"), source.catalog.pages.map { it.path })
            assertEquals(4, source.catalog.skippedUnsafe)
        }
    }

    @Test
    fun noWriteOutsideTheArchiveEverHappens() {
        val before = tmp.root.parentFile.listFiles()?.map { it.name }?.toSet().orEmpty()
        val f = file("t.cbz", zip(Entry("../escape.png", png()), Entry("a.png", png())))
        ZipComicSource.open(f).use { it.readPage(0) }
        assertEquals(before, tmp.root.parentFile.listFiles()?.map { it.name }?.toSet().orEmpty())
        assertTrue(!File(tmp.root.parentFile, "escape.png").exists())
    }

    @Test
    fun aCompressionBombIsRefusedWithoutInflatingIt() {
        val f = file("bomb.cbz", ComicFixtures.compressionBomb(size = 80 * 1024 * 1024))
        assertTrue("fixture must be small on disk", f.length() < 1_000_000)
        ZipComicSource.open(f).use { source ->
            val started = System.nanoTime()
            assertEquals(PageFailure.COMPRESSION_BOMB, pageFailure(source, 0))
            assertTrue("refused from metadata, not by inflating", System.nanoTime() - started < 1_000_000_000L)
        }
    }

    @Test
    fun aHeaderThatLiesAboutItsSizeIsStillCappedOnActualBytes() {
        val data = ByteArray(3 * 1024 * 1024) { 7 }
        val honest = ComicFixtures.zipBytes(listOf(Entry("1.png", data)))
        val lying = ComicFixtures.lieAboutSize(honest, "1.png", declared = 100)
        ZipComicSource.open(file("lie.cbz", lying), ComicLimits(maxPageBytes = 1024 * 1024)).use { source ->
            assertEquals(PageFailure.TOO_LARGE, pageFailure(source, 0))
        }
    }

    @Test
    fun anOversizePageFailsOnlyThatPage() {
        val big = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }
        val f = file("mixed.cbz", zip(Entry("1.png", png()), Entry("2.png", big, stored = true)))
        ZipComicSource.open(f, ComicLimits(maxPageBytes = 1024 * 1024)).use { source ->
            assertTrue(source.readPage(0).isNotEmpty())
            assertEquals(PageFailure.TOO_LARGE, pageFailure(source, 1))
            assertTrue("other pages stay readable", source.readPage(0).isNotEmpty())
        }
    }

    @Test
    fun tooManyEntriesIsRefused() {
        val entries = (0 until ComicLimits.DEFAULT_MAX_ENTRIES + 5).map { Entry("junk/$it.txt", ByteArray(0)) } +
            Entry("1.png", ComicFixtures.png())
        assertEquals(ComicOpenFailure.TOO_MANY_ENTRIES, openFailure(file("many.cbz", ComicFixtures.zipBytes(entries))))
    }

    @Test
    fun tooManyPagesIsRefused() {
        val f = ComicFixtures.cbz(tmp.root, "pages.cbz", *Array(12) { "$it.png" })
        assertEquals(ComicOpenFailure.TOO_MANY_PAGES, openFailure(f, ComicLimits(maxPages = 10)))
    }

    @Test
    fun caseVariantDuplicatesKeepOnlyTheFirst() {
        val f = file("dup.cbz", zip(Entry("Page1.PNG", png()), Entry("page1.png", png()), Entry("page2.png", png())))
        ZipComicSource.open(f).use { source ->
            assertEquals(2, source.pageCount)
            assertEquals(1, source.catalog.skippedDuplicates)
        }
    }

    @Test
    fun nestedArchivesAndNonImagesAreNeverReadAsPages() {
        val inner = ComicFixtures.zipBytes(listOf(Entry("deep.png", ComicFixtures.png())))
        val f = file(
            "nested.cbz",
            zip(
                Entry("inner.cbz", inner),
                Entry("inner.zip", inner),
                Entry("run.exe", byteArrayOf(1)),
                Entry("1.png", png()),
            ),
        )
        ZipComicSource.open(f).use { assertEquals(listOf("1.png"), it.catalog.pages.map { p -> p.path }) }
    }

    @Test
    fun anArchiveWithNoImagesIsRefused() {
        val f = file("empty.cbz", ComicFixtures.zipBytes(listOf(Entry("readme.txt", byteArrayOf(65)))))
        assertEquals(ComicOpenFailure.NO_PAGES, openFailure(f))
    }

    @Test
    fun encryptedEntriesAreReportedNotMisread() {
        val encrypted = ComicFixtures.markEncrypted(ComicFixtures.zipBytes(listOf(Entry("1.png", ComicFixtures.png()))))
        assertEquals(ComicOpenFailure.ENCRYPTED, openFailure(file("enc.cbz", encrypted)))
    }

    @Test
    fun truncatedGarbageAndEmptyFilesAreCorrupt() {
        val good = ComicFixtures.zipBytes(listOf(Entry("1.png", ComicFixtures.png())))
        assertEquals(ComicOpenFailure.CORRUPT, openFailure(file("trunc.cbz", good.copyOf(good.size / 2))))
        assertEquals(ComicOpenFailure.CORRUPT, openFailure(file("garbage.cbz", ByteArray(2048) { (it * 31).toByte() })))
        assertEquals(ComicOpenFailure.CORRUPT, openFailure(file("empty.cbz", ByteArray(0))))
    }

    @Test
    fun aMissingFileIsNotFound() {
        assertEquals(ComicOpenFailure.NOT_FOUND, openFailure(File(tmp.root, "nope.cbz")))
    }

    @Test
    fun aHostileComicInfoNeverBlocksReading() {
        val evil = ("<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY a SYSTEM \"file:///etc/passwd\">]>" +
            "<ComicInfo><Title>&a;</Title></ComicInfo>").toByteArray()
        val f = file("evilinfo.cbz", zip(Entry("ComicInfo.xml", evil), Entry("1.png", png())))
        ZipComicSource.open(f).use { source ->
            assertEquals(1, source.pageCount)
            assertEquals(null, source.info)
        }
    }

    @Test
    fun aGoodComicInfoIsParsed() {
        val xml = ComicFixtures.comicInfoXml("<Title>Hi</Title><Series>S</Series><Manga>YesAndRightToLeft</Manga>")
        val f = file("info.cbz", zip(Entry("ComicInfo.xml", xml), Entry("1.png", png())))
        ZipComicSource.open(f).use { assertNotNull(it.info); assertEquals("Hi", it.info?.title) }
    }
}
