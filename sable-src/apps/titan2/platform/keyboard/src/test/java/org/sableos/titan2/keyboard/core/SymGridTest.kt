package org.sableos.titan2.keyboard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SymGridTest {
    @Test fun everyPageHasOneSymbolPerLetterAndNoDuplicates() {
        SymGrid.PAGES.forEach {
            assertEquals(SymGrid.LETTERS.length, it.size)
            assertEquals(it.size, it.toSet().size)
        }
    }

    @Test fun firstPageHasTheProgrammerSymbols() {
        val p = SymGrid.PAGES[0].toSet()
        listOf("[", "]", "{", "}", "<", ">", "\\", "|", "~", "`", "^").forEach {
            assertTrue(
                it,
                it in p
            )
        }
    }

    @Test fun letterPicksItsSymbolAndCloses() {
        assertEquals(SymGrid.Action.Pick("["), SymGrid.action(SymGrid.KEYCODE_A + ('q' - 'a'), 0))
        assertEquals("]", SymGrid.symbolFor(0, 'w'))
        assertEquals("€", SymGrid.symbolFor(1, 'Q'))
        assertNull(SymGrid.symbolFor(0, '1'))
    }

    @Test fun symAdvancesAndWraps() {
        assertEquals(SymGrid.Action.NextPage, SymGrid.action(SymGrid.KEYCODE_SYM, 0))
        assertEquals(SymGrid.Action.NextPage, SymGrid.action(SymGrid.KEYCODE_ALT_RIGHT, 0))
        assertEquals(0, SymGrid.nextPage(SymGrid.PAGES.size - 1))
    }

    @Test fun backClosesOtherKeysCloseAndPass() {
        assertEquals(SymGrid.Action.Close, SymGrid.action(SymGrid.KEYCODE_BACK, 0))
        assertEquals(SymGrid.Action.Ignore, SymGrid.action(59, 0))
        assertEquals(SymGrid.Action.ClosePass, SymGrid.action(62, 0))
    }

    @Test fun tapNeedsQuickReleaseWithoutChord() {
        assertTrue(SymGrid.isTap(100, false))
        assertFalse(SymGrid.isTap(100, true))
        assertFalse(SymGrid.isTap(900, false))
    }

    @Test fun softKeyboardShortcutIsAltShiftK() {
        assertTrue(SymGrid.togglesSoftKeyboard(SymGrid.KEYCODE_K, true, true, false))
        assertFalse(SymGrid.togglesSoftKeyboard(SymGrid.KEYCODE_K, true, false, false))
        assertFalse(SymGrid.togglesSoftKeyboard(46, true, true, false))
    }

    @Test fun chipsLabelEachLetter() {
        val c = SymGrid.chips(0)
        assertEquals("Sym 1/3", c[0])
        assertEquals("q [", c[1])
        assertEquals("[", SymGrid.symbolForChip(1, 0))
        assertNull(SymGrid.symbolForChip(0, 0))
    }
}

class SymGridSessionTest {
    private val sym = SymGrid.KEYCODE_ALT_RIGHT

    @Test fun loneTapOpensThenLetterPicksAndCloses() {
        val s = SymGridSession()
        assertNull(s.onDown(sym, 0, 1000))
        assertTrue(s.onUp(sym, 1100, true))
        assertEquals(0, s.page)
        assertEquals(SymGrid.Action.Pick("["), s.onDown(SymGrid.KEYCODE_A + ('q' - 'a'), 0, 1200))
        assertNull(s.page)
    }

    @Test fun symTurnsThePageAndBackCloses() {
        val s = SymGridSession()
        s.onDown(sym, 0, 0)
        s.onUp(sym, 50, true)
        s.onDown(sym, 0, 100)
        assertEquals(1, s.page)
        assertTrue(s.swallowsUp(sym))
        assertFalse(s.swallowsUp(sym))
        s.onDown(SymGrid.KEYCODE_BACK, 0, 200)
        assertNull(s.page)
    }

    @Test fun chordedOrHeldOrNonEditableDoesNotOpen() {
        val s = SymGridSession()
        s.onDown(sym, 0, 0)
        s.onDown(SymGrid.KEYCODE_A, 0, 10)
        assertFalse(s.onUp(sym, 50, true))
        s.onDown(sym, 0, 100)
        assertFalse(s.onUp(sym, 900, true))
        s.onDown(sym, 0, 1000)
        assertFalse(s.onUp(sym, 1050, false))
    }
}
