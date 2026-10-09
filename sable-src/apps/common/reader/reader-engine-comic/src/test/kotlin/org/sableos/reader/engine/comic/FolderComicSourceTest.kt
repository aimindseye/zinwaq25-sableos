package org.sableos.reader.engine.comic

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FolderComicSourceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun page(path: String): File = File(tmp.root, path).also {
        it.parentFile.mkdirs()
        it.writeBytes(ComicFixtures.png())
    }

    @Test
    fun foldersOfImagesBecomeOrderedChapteredComics() {
        page("Ch 2/1.png")
        page("Ch 1/10.png")
        page("Ch 1/2.png")
        File(tmp.root, "ComicInfo.xml").writeBytes(ComicFixtures.comicInfoXml("<Title>T</Title>"))
        FolderComicSource.open(tmp.root).use { source ->
            assertEquals(listOf("Ch 1/2.png", "Ch 1/10.png", "Ch 2/1.png"), source.catalog.pages.map { it.path })
            assertEquals(listOf("Ch 1", "Ch 2"), source.catalog.chapters.map { it.title })
            assertEquals("T", source.info?.title)
            assertTrue(source.readPage(0).isNotEmpty())
        }
    }

    @Test
    fun symbolicLinksAreNeverFollowed() {
        page("real/1.png")
        val outside = tmp.newFolder("outside").also { File(it, "secret.png").writeBytes(ComicFixtures.png()) }
        Files.createSymbolicLink(File(tmp.root, "real/link.png").toPath(), File(outside, "secret.png").toPath())
        Files.createSymbolicLink(File(tmp.root, "loop").toPath(), tmp.root.toPath())
        FolderComicSource.open(File(tmp.root, "real")).use { source ->
            assertEquals(listOf("1.png"), source.catalog.pages.map { it.path })
        }
        FolderComicSource.open(tmp.root).use { source ->
            assertEquals(setOf("real/1.png", "outside/secret.png"), source.catalog.pages.map { it.path }.toSet())
        }
    }

    @Test
    fun depthIsBounded() {
        val deep = (1..20).joinToString("/") { "d$it" }
        page("top.png"); page("$deep/buried.png")
        FolderComicSource.open(tmp.root, ComicLimits(maxFolderDepth = 3)).use {
            assertEquals(listOf("top.png"), it.catalog.pages.map { p -> p.path })
        }
    }

    @Test
    fun resolveInsideRefusesEscapesAndLinks() {
        page("1.png")
        assertNull(FolderComicSource.resolveInside(tmp.root, "../etc/passwd"))
        assertNull(FolderComicSource.resolveInside(tmp.root, "missing.png"))
        assertTrue(FolderComicSource.resolveInside(tmp.root, "1.png") != null)
        Files.createSymbolicLink(File(tmp.root, "l.png").toPath(), File(tmp.root, "1.png").toPath())
        assertNull(FolderComicSource.resolveInside(tmp.root, "l.png"))
    }

    @Test
    fun anEmptyOrMissingFolderIsRefused() {
        try {
            FolderComicSource.open(tmp.root).close(); fail("empty folder")
        } catch (e: ComicOpenException) {
            assertEquals(ComicOpenFailure.NO_PAGES, e.failure)
        }
        try {
            FolderComicSource.open(File(tmp.root, "nope")).close(); fail("missing folder")
        } catch (e: ComicOpenException) {
            assertEquals(ComicOpenFailure.NOT_FOUND, e.failure)
        }
    }

    @Test
    fun oversizeFilesFailOnlyTheirPage() {
        page("1.png")
        File(tmp.root, "2.png").writeBytes(ByteArray(2048))
        FolderComicSource.open(tmp.root, ComicLimits(maxPageBytes = 1024)).use { source ->
            val failure = runCatching { source.readPage(1) }.exceptionOrNull() as? ComicPageException
            assertEquals(PageFailure.TOO_LARGE, failure?.failure)
        }
    }
}
