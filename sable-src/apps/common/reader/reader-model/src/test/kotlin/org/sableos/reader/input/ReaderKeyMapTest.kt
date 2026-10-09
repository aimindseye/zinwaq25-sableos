package org.sableos.reader.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.reader.capability.ReaderCapabilities
import org.sableos.reader.input.ReaderAction.ADVANCE
import org.sableos.reader.input.ReaderAction.DISMISS_OR_BACK
import org.sableos.reader.input.ReaderAction.OPEN_APPEARANCE
import org.sableos.reader.input.ReaderAction.OPEN_CONTENTS
import org.sableos.reader.input.ReaderAction.OPEN_FIND
import org.sableos.reader.input.ReaderAction.PAGE_NEXT
import org.sableos.reader.input.ReaderAction.PAGE_PREVIOUS
import org.sableos.reader.input.ReaderAction.REVERSE
import org.sableos.reader.input.ReaderAction.SCROLL_DOWN
import org.sableos.reader.input.ReaderAction.SCROLL_PAGE_DOWN
import org.sableos.reader.input.ReaderAction.SCROLL_PAGE_UP
import org.sableos.reader.input.ReaderAction.SCROLL_UP
import org.sableos.reader.input.ReaderAction.TOGGLE_BOOKMARK
import org.sableos.reader.input.ReaderAction.TOGGLE_CHROME

class ReaderKeyMapTest {
    private val epubPaged = KeyContext(ReaderSurface.EPUB_PAGED, ReaderCapabilities.EPUB)
    private val epubScroll = KeyContext(ReaderSurface.EPUB_SCROLL, ReaderCapabilities.EPUB)
    private val pdf = KeyContext(ReaderSurface.PDF_PAGED, ReaderCapabilities.PDF)

    private fun key(k: ReaderKey, shift: Boolean = false) = KeyInput(k, shift = shift)

    private fun letter(c: Char, shift: Boolean = false) = KeyInput(ReaderKey.CHARACTER, character = c, shift = shift)

    private fun resolved(input: KeyInput, context: KeyContext): ReaderAction? =
        (ReaderKeyMap.resolve(input, context) as? KeyResolution.Handled)?.action

    @Test
    fun leftRightAndPageKeysNavigatePagedEpubAndPdf() {
        listOf(epubPaged, pdf).forEach {
            assertEquals(PAGE_NEXT, resolved(key(ReaderKey.RIGHT), it))
            assertEquals(PAGE_PREVIOUS, resolved(key(ReaderKey.LEFT), it))
            assertEquals(PAGE_NEXT, resolved(key(ReaderKey.PAGE_DOWN), it))
            assertEquals(PAGE_PREVIOUS, resolved(key(ReaderKey.PAGE_UP), it))
        }
    }

    @Test
    fun spaceAdvancesAndShiftSpaceReverses() {
        listOf(epubPaged, epubScroll, pdf).forEach {
            assertEquals(ADVANCE, resolved(key(ReaderKey.SPACE), it))
            assertEquals(REVERSE, resolved(key(ReaderKey.SPACE, shift = true), it))
        }
    }

    @Test
    fun scrollingEpubUsesUpDownAndPageScrolling() {
        assertEquals(SCROLL_DOWN, resolved(key(ReaderKey.DOWN), epubScroll))
        assertEquals(SCROLL_UP, resolved(key(ReaderKey.UP), epubScroll))
        assertEquals(SCROLL_PAGE_DOWN, resolved(key(ReaderKey.PAGE_DOWN), epubScroll))
        assertEquals(SCROLL_PAGE_UP, resolved(key(ReaderKey.PAGE_UP), epubScroll))
    }

    @Test
    fun upDownPassThroughInPagedSurfacesAndLeftRightInScroll() {
        assertEquals(KeyResolution.PassThrough, ReaderKeyMap.resolve(key(ReaderKey.UP), epubPaged))
        assertEquals(KeyResolution.PassThrough, ReaderKeyMap.resolve(key(ReaderKey.DOWN), pdf))
        assertEquals(KeyResolution.PassThrough, ReaderKeyMap.resolve(key(ReaderKey.LEFT), epubScroll))
    }

    @Test
    fun letterShortcutsMapToSemanticActionsCaseInsensitively() {
        assertEquals(TOGGLE_BOOKMARK, resolved(letter('b'), epubPaged))
        assertEquals(TOGGLE_BOOKMARK, resolved(letter('B', shift = true), epubPaged))
        assertEquals(OPEN_CONTENTS, resolved(letter('t'), epubPaged))
        assertEquals(OPEN_FIND, resolved(letter('f'), epubPaged))
        assertEquals(OPEN_APPEARANCE, resolved(letter('a'), epubPaged))
    }

    @Test
    fun pdfDoesNotHandleFindOrAppearanceBecauseTheyDoNotExist() {
        assertEquals(KeyResolution.PassThrough, ReaderKeyMap.resolve(letter('f'), pdf))
        assertEquals(KeyResolution.PassThrough, ReaderKeyMap.resolve(letter('a'), pdf))
        assertEquals(TOGGLE_BOOKMARK, resolved(letter('b'), pdf))
        assertEquals(OPEN_CONTENTS, resolved(letter('t'), pdf))
    }

    @Test
    fun unknownLettersAreNeverConsumed() {
        assertEquals(KeyResolution.PassThrough, ReaderKeyMap.resolve(letter('z'), epubPaged))
        assertEquals(KeyResolution.PassThrough, ReaderKeyMap.resolve(KeyInput(ReaderKey.CHARACTER), epubPaged))
    }

    @Test
    fun escapeAndBackAlwaysDismissThenLeave() {
        listOf(epubPaged, epubScroll, pdf, pdf.copy(editableFocused = true)).forEach {
            assertEquals(DISMISS_OR_BACK, resolved(key(ReaderKey.ESCAPE), it))
            assertEquals(DISMISS_OR_BACK, resolved(key(ReaderKey.BACK), it))
        }
    }

    @Test
    fun editableTextOwnsEverythingExceptEscape() {
        val editing = epubPaged.copy(editableFocused = true)
        listOf(
            key(ReaderKey.SPACE), key(ReaderKey.LEFT), key(ReaderKey.RIGHT), key(ReaderKey.PAGE_DOWN),
            key(ReaderKey.ACTIVATE), letter('b'), letter('t'), letter('f'), letter('a'),
        ).forEach { assertEquals("$it", KeyResolution.PassThrough, ReaderKeyMap.resolve(it, editing)) }
    }

    @Test
    fun commandModifiedKeysAreNotOurs() {
        val ctrlB = KeyInput(ReaderKey.CHARACTER, 'b', ctrl = true)
        assertEquals(KeyResolution.PassThrough, ReaderKeyMap.resolve(ctrlB, epubPaged))
        assertEquals(KeyResolution.PassThrough, ReaderKeyMap.resolve(KeyInput(ReaderKey.RIGHT, alt = true), epubPaged))
        assertEquals(KeyResolution.PassThrough, ReaderKeyMap.resolve(KeyInput(ReaderKey.SPACE, meta = true), epubPaged))
    }

    @Test
    fun enterAndMenuToggleChromeSoControlsAreNeverTouchOnly() {
        assertEquals(TOGGLE_CHROME, resolved(key(ReaderKey.ACTIVATE), epubPaged))
        assertEquals(TOGGLE_CHROME, resolved(key(ReaderKey.MENU), pdf))
    }

    @Test
    fun rightToLeftReadingSwapsNextAndPrevious() {
        val manga = KeyContext(ReaderSurface.COMIC_PAGED, ReaderCapabilities.COMIC, ReadingDirection.RIGHT_TO_LEFT)
        assertEquals(PAGE_NEXT, resolved(key(ReaderKey.LEFT), manga))
        assertEquals(PAGE_PREVIOUS, resolved(key(ReaderKey.RIGHT), manga))
        val western = manga.copy(direction = ReadingDirection.LEFT_TO_RIGHT)
        assertEquals(PAGE_NEXT, resolved(key(ReaderKey.RIGHT), western))
    }

    @Test
    fun comicVerticalPagerAndWebtoonUseUpDown() {
        val pager = KeyContext(ReaderSurface.COMIC_VERTICAL_PAGER, ReaderCapabilities.COMIC)
        val webtoon = KeyContext(ReaderSurface.COMIC_CONTINUOUS, ReaderCapabilities.COMIC)
        assertEquals(PAGE_NEXT, resolved(key(ReaderKey.DOWN), pager))
        assertEquals(PAGE_PREVIOUS, resolved(key(ReaderKey.UP), pager))
        assertEquals(SCROLL_DOWN, resolved(key(ReaderKey.DOWN), webtoon))
        assertEquals(SCROLL_PAGE_UP, resolved(key(ReaderKey.PAGE_UP), webtoon))
    }

    @Test
    fun audioKeysAreSemanticAndLeaveVolumeKeysAlone() {
        val audio = KeyContext(ReaderSurface.AUDIO, ReaderCapabilities.AUDIOBOOK)
        assertEquals(ReaderAction.PLAY_PAUSE, resolved(key(ReaderKey.SPACE), audio))
        assertEquals(ReaderAction.SEEK_FORWARD, resolved(key(ReaderKey.RIGHT), audio))
        assertEquals(ReaderAction.SEEK_BACKWARD, resolved(key(ReaderKey.LEFT), audio))
        assertEquals(ReaderAction.CHAPTER_NEXT, resolved(key(ReaderKey.PAGE_DOWN), audio))
        assertEquals(ReaderAction.CHAPTER_PREVIOUS, resolved(key(ReaderKey.PAGE_UP), audio))
        assertEquals(OPEN_CONTENTS, resolved(letter('t'), audio))
        assertEquals(TOGGLE_BOOKMARK, resolved(letter('b'), audio))
        assertEquals(KeyResolution.PassThrough, ReaderKeyMap.resolve(key(ReaderKey.ACTIVATE), audio))
        assertFalse(ReaderKey.entries.any { it.name.contains("VOLUME") })
    }

    @Test
    fun hintsOnlyAdvertiseSupportedActions() {
        val pdfHints = KeyHints.forContext(pdf).map { it.keys }
        assertTrue("B" in pdfHints && "T" in pdfHints)
        assertFalse("F" in pdfHints)
        assertFalse("A" in pdfHints)
        val epubHints = KeyHints.forContext(epubPaged).map { it.keys }
        assertTrue("F" in epubHints && "A" in epubHints)
        assertTrue(KeyHints.forContext(pdf).last().keys.startsWith("Esc"))
    }

    @Test
    fun scrollSurfacesDoNotAdvertiseLeftRightPaging() {
        val hints = KeyHints.forContext(epubScroll).map { it.keys }
        assertFalse("Left / Right" in hints)
        assertTrue("Up / Down" in hints)
    }
}
