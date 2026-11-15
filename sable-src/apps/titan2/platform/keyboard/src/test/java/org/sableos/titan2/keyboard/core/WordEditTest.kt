package org.sableos.titan2.keyboard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordEditTest {
    @Test fun deletesTheLastWordAndTrailingSpaces() {
        assertEquals(5, WordEdit.lastWordLength("hello"))
        assertEquals(6, WordEdit.lastWordLength("say hello "))
        assertEquals(5, WordEdit.lastWordLength("say don't"))
        assertEquals(0, WordEdit.lastWordLength(""))
        assertEquals(3, WordEdit.lastWordLength("   "))
    }

    @Test fun punctuationRunIsItsOwnWord() {
        assertEquals(3, WordEdit.lastWordLength("ok..."))
        assertEquals(1, WordEdit.lastWordLength("a (x)"))
    }

    @Test fun doubleSpaceNeedsAWordThenOneSpace() {
        assertTrue(WordEdit.endsWordSpace("hi "))
        assertFalse(WordEdit.endsWordSpace("hi  "))
        assertFalse(WordEdit.endsWordSpace(" "))
        assertFalse(WordEdit.endsWordSpace("hi. "))
    }

    @Test fun chords() {
        assertTrue(WordEdit.deletesWord(WordEdit.KEYCODE_DEL, true, false, false))
        assertTrue(WordEdit.deletesWord(WordEdit.KEYCODE_DEL, false, false, true))
        assertFalse(WordEdit.deletesWord(WordEdit.KEYCODE_DEL, false, false, false))
        assertFalse(WordEdit.deletesWord(WordEdit.KEYCODE_DEL, true, true, false))
        assertTrue(WordEdit.togglesIncognito(WordEdit.KEYCODE_I, true, true, false))
        assertFalse(WordEdit.togglesIncognito(WordEdit.KEYCODE_I, true, false, false))
    }
}

class SpaceDragTest {
    private fun drag() = SpaceDrag(10f, 100f).also { it.down(0f, 0) }

    @Test fun quickTapTypes() = assertEquals(SpaceDrag.Outcome.Tap, drag().up(2f, 100))

    @Test fun heldWithoutMovingIsALongPress() =
        assertEquals(SpaceDrag.Outcome.LongPress, drag().up(0f, 500))

    @Test fun quickSwipeSwitchesLanguage() {
        val d = drag()
        d.move(-120f, 80)
        assertEquals(SpaceDrag.Outcome.SwipeLeft, d.up(-120f, 90))
        val r = drag()
        r.move(120f, 80)
        assertEquals(SpaceDrag.Outcome.SwipeRight, r.up(120f, 90))
    }

    @Test fun holdThenDragMovesTheCursorInSteps() {
        val d = drag()
        assertEquals(0, d.move(30f, 100))
        assertEquals(3, d.move(30f, 400))
        assertEquals(-2, d.move(10f, 450))
        assertEquals(SpaceDrag.Outcome.Done, d.up(10f, 500))
    }
}
