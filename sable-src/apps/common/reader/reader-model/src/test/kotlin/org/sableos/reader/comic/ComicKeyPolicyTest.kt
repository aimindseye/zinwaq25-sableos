package org.sableos.reader.comic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.sableos.reader.comic.ComicKeyCommand.NEXT_PAGE
import org.sableos.reader.comic.ComicKeyCommand.PREVIOUS_PAGE
import org.sableos.reader.comic.ComicKeyCommand.SCROLL_LINE_DOWN
import org.sableos.reader.comic.ComicKeyCommand.SCROLL_LINE_UP
import org.sableos.reader.comic.ComicKeyCommand.SCROLL_PAGE_DOWN
import org.sableos.reader.comic.ComicKeyCommand.SCROLL_PAGE_UP
import org.sableos.reader.input.KeyInput
import org.sableos.reader.input.ReaderKey

class ComicKeyPolicyTest {
    private fun key(k: ReaderKey, shift: Boolean = false) = KeyInput(k, shift = shift)

    private fun letter(c: Char) = KeyInput(ReaderKey.CHARACTER, character = c)

    private fun decide(
        input: KeyInput,
        mode: ComicReadingMode,
        editing: Boolean = false,
        overlay: Boolean = false,
    ) = ComicKeyPolicy.decide(input, mode, editing, overlay)

    @Test
    fun leftToRightPagedFollowsTheArrows() {
        val m = ComicReadingMode.PAGED_LTR
        assertEquals(NEXT_PAGE, decide(key(ReaderKey.RIGHT), m))
        assertEquals(PREVIOUS_PAGE, decide(key(ReaderKey.LEFT), m))
        assertEquals(NEXT_PAGE, decide(key(ReaderKey.PAGE_DOWN), m))
        assertEquals(NEXT_PAGE, decide(key(ReaderKey.SPACE), m))
        assertEquals(PREVIOUS_PAGE, decide(key(ReaderKey.SPACE, shift = true), m))
        assertNull(decide(key(ReaderKey.DOWN), m))
    }

    @Test
    fun rightToLeftPagedReversesTheArrowsOnly() {
        val m = ComicReadingMode.PAGED_RTL
        assertEquals(NEXT_PAGE, decide(key(ReaderKey.LEFT), m))
        assertEquals(PREVIOUS_PAGE, decide(key(ReaderKey.RIGHT), m))
        assertEquals(NEXT_PAGE, decide(key(ReaderKey.PAGE_DOWN), m))
        assertEquals(NEXT_PAGE, decide(key(ReaderKey.SPACE), m))
    }

    @Test
    fun verticalPagerPagesWithUpAndDown() {
        val m = ComicReadingMode.VERTICAL_PAGER
        assertEquals(NEXT_PAGE, decide(key(ReaderKey.DOWN), m))
        assertEquals(PREVIOUS_PAGE, decide(key(ReaderKey.UP), m))
        assertEquals(NEXT_PAGE, decide(key(ReaderKey.PAGE_DOWN), m))
        assertEquals(NEXT_PAGE, decide(key(ReaderKey.SPACE), m))
        assertNull(decide(key(ReaderKey.LEFT), m))
    }

    @Test
    fun continuousModesScrollInsteadOfPaging() {
        listOf(ComicReadingMode.CONTINUOUS_VERTICAL, ComicReadingMode.WEBTOON).forEach { m ->
            assertEquals(SCROLL_LINE_DOWN, decide(key(ReaderKey.DOWN), m))
            assertEquals(SCROLL_LINE_UP, decide(key(ReaderKey.UP), m))
            assertEquals(SCROLL_PAGE_DOWN, decide(key(ReaderKey.PAGE_DOWN), m))
            assertEquals(SCROLL_PAGE_UP, decide(key(ReaderKey.PAGE_UP), m))
            assertEquals(SCROLL_PAGE_DOWN, decide(key(ReaderKey.SPACE), m))
            assertEquals(SCROLL_PAGE_UP, decide(key(ReaderKey.SPACE, shift = true), m))
            assertNull(decide(key(ReaderKey.LEFT), m))
        }
    }

    @Test
    fun everyModeOffersBookmarkContentsAppearanceAndNoFind() {
        ComicReadingMode.entries.forEach { m ->
            assertEquals(ComicKeyCommand.TOGGLE_BOOKMARK, decide(letter('b'), m))
            assertEquals(ComicKeyCommand.OPEN_CONTENTS, decide(letter('t'), m))
            assertEquals(ComicKeyCommand.OPEN_APPEARANCE, decide(letter('a'), m))
            assertNull("no text search for comics", decide(letter('f'), m))
            assertEquals(ComicKeyCommand.TOGGLE_CHROME, decide(key(ReaderKey.ACTIVATE), m))
        }
    }

    @Test
    fun overlaysAndTextFieldsOnlyEverSeeEscape() {
        ComicReadingMode.entries.forEach { m ->
            assertNull(decide(key(ReaderKey.RIGHT), m, overlay = true))
            assertNull(decide(letter('b'), m, editing = true))
            assertEquals(ComicKeyCommand.ESCAPE, decide(key(ReaderKey.ESCAPE), m, overlay = true))
            assertEquals(ComicKeyCommand.ESCAPE, decide(key(ReaderKey.ESCAPE), m, editing = true))
            assertNull(decide(key(ReaderKey.BACK), m))
        }
    }

    @Test
    fun chordsAreNeverHandled() {
        assertNull(decide(KeyInput(ReaderKey.CHARACTER, character = 'b', ctrl = true), ComicReadingMode.PAGED_LTR))
    }

    @Test
    fun repeatedKeyDownsNeverRunOneShotComicCommands() {
        ComicReadingMode.entries.forEach { m ->
            listOf(ReaderKey.ACTIVATE, ReaderKey.MENU, ReaderKey.ESCAPE, ReaderKey.SPACE, ReaderKey.PAGE_DOWN)
                .forEach { assertNull("$it in $m", decide(KeyInput(it, repeated = true), m)) }
            listOf('b', 't', 'a').forEach {
                assertNull("$it in $m", decide(KeyInput(ReaderKey.CHARACTER, it, repeated = true), m))
            }
            assertEquals(ComicKeyCommand.TOGGLE_CHROME, decide(key(ReaderKey.ACTIVATE), m))
            assertEquals(ComicKeyCommand.ESCAPE, decide(key(ReaderKey.ESCAPE), m))
        }
    }

    @Test
    fun onlyLineScrollingRepeatsInTheContinuousModes() {
        listOf(ComicReadingMode.CONTINUOUS_VERTICAL, ComicReadingMode.WEBTOON).forEach { m ->
            assertEquals(SCROLL_LINE_DOWN, decide(KeyInput(ReaderKey.DOWN, repeated = true), m))
            assertEquals(SCROLL_LINE_UP, decide(KeyInput(ReaderKey.UP, repeated = true), m))
        }
        assertNull(decide(KeyInput(ReaderKey.RIGHT, repeated = true), ComicReadingMode.PAGED_LTR))
        assertNull(decide(KeyInput(ReaderKey.DOWN, repeated = true), ComicReadingMode.VERTICAL_PAGER))
    }
}
