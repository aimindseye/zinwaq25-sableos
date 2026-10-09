package org.sableos.reader.engine.comic

import org.junit.Assert.assertEquals
import org.junit.Test

class ComicEntryPolicyTest {
    private fun verdict(name: String) = ComicEntryPolicy.classify(name)

    @Test
    fun imagesAreRecognisedByExtensionCaseInsensitively() {
        listOf("1.jpg", "a/b/2.JPEG", "3.Png", "4.webp", "5.gif", "6.bmp").forEach {
            assertEquals(it, EntryVerdict.IMAGE, verdict(it))
        }
    }

    @Test
    fun harmlessNonPagesAreIgnored() {
        listOf(
            "ComicInfo.xml", "notes.txt", "inner.cbz", "inner.zip", "tool.exe", "dir/", "__MACOSX/1.jpg",
            "x/._1.jpg", ".DS_Store", "sub/Thumbs.db", "noextension", "page.jpg.exe",
        ).forEach { assertEquals(it, EntryVerdict.IGNORED, verdict(it)) }
    }

    @Test
    fun pathTraversalAndOddNamesAreUnsafe() {
        listOf(
            "../evil.png", "a/../../evil.png", "a/..", "/abs/1.png", "\\abs\\1.png", "C:\\x\\1.png", "c:/1.png",
            "..\\evil.png", "ok/\u0000.png", "tab\t.png", "x".repeat(600) + ".png",
        ).forEach { assertEquals(it, EntryVerdict.UNSAFE, verdict(it)) }
    }

    @Test
    fun backslashSeparatorsFromWindowsToolsAreAccepted() {
        assertEquals(EntryVerdict.IMAGE, verdict("Chapter 1\\001.jpg"))
        assertEquals("Chapter 1/001.jpg", ComicEntryPolicy.normalize("Chapter 1\\001.jpg"))
    }

    @Test
    fun dotsInsideNamesAreNotTraversal() {
        assertEquals(EntryVerdict.IMAGE, verdict("vol..1/page..2.png"))
        assertEquals(EntryVerdict.IMAGE, verdict("..hidden.png"))
    }
}
