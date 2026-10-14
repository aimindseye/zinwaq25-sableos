package org.lineageos.setupwizard.sable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lineageos.setupwizard.sable.SableKeyTest.Check
import org.lineageos.setupwizard.sable.SableKeyTest.State

/** Pure tests for the keyboard test step carried in patches/framework/packages/apps/SetupWizard/0404. */
class SableKeyTestTest {
    private fun type(t: SableKeyTest, text: String) {
        text.forEach { t.onText(it.toString(), 0) }
    }

    @Test fun startsWithNothingRegisteredAndQ25HasNoFnCheck() {
        val t = SableKeyTest()
        SableKeyTest.checks().forEach { assertEquals(State.WAITING, t.state(it)) }
        assertEquals(0, t.passedCount())
        assertFalse(t.allPassed())
        assertEquals(listOf("LETTERS", "CAPITALS", "NUMBERS", "ALT", "SYM", "SPACE", "BACKSPACE"),
            SableKeyTest.checks().map { it.name })
    }

    @Test fun lettersSpaceNumbersFromText() {
        val t = SableKeyTest()
        type(t, "meet at 7")
        assertEquals(State.PASS, t.state(Check.LETTERS))
        assertEquals(State.PASS, t.state(Check.SPACE))
        assertEquals(State.PASS, t.state(Check.NUMBERS))
        assertEquals(State.WAITING, t.state(Check.CAPITALS))
        // A digit or symbol with no modifier key seen gives Alt and Sym no credit.
        assertEquals(State.WAITING, t.state(Check.ALT))
        assertEquals(State.WAITING, t.state(Check.SYM))
    }

    @Test fun shiftKeyIsPressedThenCapitalPasses() {
        val t = SableKeyTest()
        assertTrue(t.onKeyDown(SableKeyTest.KEYCODE_SHIFT_LEFT, SableKeyTest.META_SHIFT_ON))
        assertEquals(State.PRESSED, t.state(Check.CAPITALS))
        assertFalse(t.onKeyDown(SableKeyTest.KEYCODE_SHIFT_RIGHT, 0)) // already pressed
        t.onText("M", 0)
        assertEquals(State.PASS, t.state(Check.CAPITALS))
        assertEquals(State.PASS, t.state(Check.LETTERS))
        // Pressing Shift again never takes a pass back.
        assertFalse(t.onKeyDown(SableKeyTest.KEYCODE_SHIFT_LEFT, 0))
        assertEquals(State.PASS, t.state(Check.CAPITALS))
    }

    @Test fun capitalsFromTheOnScreenKeyboardPassWithoutKeyEvents() {
        val t = SableKeyTest()
        t.onText("Ok", 0)
        assertEquals(State.PASS, t.state(Check.CAPITALS))
    }

    @Test fun altKeyThenItsCharacterPassesAlt() {
        val t = SableKeyTest()
        t.onKeyDown(SableKeyTest.KEYCODE_ALT_LEFT, SableKeyTest.META_ALT_ON)
        assertEquals(State.PRESSED, t.state(Check.ALT))
        t.onKeyDown(32 /* D */, SableKeyTest.META_ALT_ON)
        t.onText("5", 0)
        assertEquals(State.PASS, t.state(Check.ALT))
        assertEquals(State.PASS, t.state(Check.NUMBERS))
        assertEquals(State.WAITING, t.state(Check.SYM))
    }

    @Test fun heldAltWithoutAnAltKeyEventStillCounts() {
        // Meta state on the character key alone arms the Alt layer.
        val t = SableKeyTest()
        t.onKeyDown(51 /* W */, SableKeyTest.META_ALT_ON)
        t.onText("?", 0)
        assertEquals(State.PASS, t.state(Check.ALT))
    }

    @Test fun altFollowedByALetterGetsNoCredit() {
        val t = SableKeyTest()
        t.onKeyDown(SableKeyTest.KEYCODE_ALT_RIGHT, 0)
        t.onText("q", 0)
        t.onText("!", 0) // the Alt press was used up by the letter
        assertEquals(State.PRESSED, t.state(Check.ALT))
    }

    @Test fun altCreditIsOneCharacterOnly() {
        val t = SableKeyTest()
        t.onKeyDown(SableKeyTest.KEYCODE_ALT_LEFT, 0)
        t.onText(" ", 0)
        t.onText("#", 0)
        assertEquals(State.PRESSED, t.state(Check.ALT))
        assertEquals(State.PASS, t.state(Check.SPACE))
    }

    @Test fun symKeyThenAPickedSymbolPassesSym() {
        val t = SableKeyTest()
        assertTrue(t.onKeyDown(SableKeyTest.KEYCODE_SYM, 0))
        assertEquals(State.PRESSED, t.state(Check.SYM))
        t.onText("€", 0)
        assertEquals(State.PASS, t.state(Check.SYM))
        assertEquals(State.WAITING, t.state(Check.ALT))
    }

    @Test fun symMetaOnAKeyArmsSymNotAlt() {
        val t = SableKeyTest()
        t.onKeyDown(40 /* L */, SableKeyTest.META_SYM_ON or SableKeyTest.META_ALT_ON)
        t.onText("\"", 0)
        assertEquals(State.PASS, t.state(Check.SYM))
        assertEquals(State.WAITING, t.state(Check.ALT))
    }

    @Test fun lastModifierPressedGetsTheCredit() {
        val t = SableKeyTest()
        t.onKeyDown(SableKeyTest.KEYCODE_SYM, 0)
        t.onKeyDown(SableKeyTest.KEYCODE_ALT_LEFT, 0)
        t.onText("&", 0)
        assertEquals(State.PASS, t.state(Check.ALT))
        assertEquals(State.PRESSED, t.state(Check.SYM))
    }

    @Test fun backspaceFromTheKeyOrFromADeletion() {
        val key = SableKeyTest()
        assertTrue(key.onKeyDown(SableKeyTest.KEYCODE_DEL, 0))
        assertEquals(State.PASS, key.state(Check.BACKSPACE))
        val del = SableKeyTest()
        del.onReplace("t", "")
        assertEquals(State.PASS, del.state(Check.BACKSPACE))
        val fwd = SableKeyTest()
        fwd.onKeyDown(SableKeyTest.KEYCODE_FORWARD_DEL, 0)
        assertEquals(State.PASS, fwd.state(Check.BACKSPACE))
    }

    @Test fun composingRewritesOnlyCountTheNewTail() {
        val t = SableKeyTest()
        t.onReplace("", "he")
        t.onKeyDown(SableKeyTest.KEYCODE_ALT_LEFT, 0)
        // The IME reports its whole composing word again with the Alt character appended.
        t.onReplace("he", "he1")
        assertEquals(State.PASS, t.state(Check.ALT))
        // A correction that shortens the word is not a Backspace when it also inserts text.
        val c = SableKeyTest()
        c.onReplace("teh", "the")
        assertEquals(State.WAITING, c.state(Check.BACKSPACE))
        // Pure shortening is.
        c.onReplace("the", "th")
        assertEquals(State.PASS, c.state(Check.BACKSPACE))
    }

    @Test fun surrogatePairsAreOneCharacter() {
        val t = SableKeyTest()
        t.onKeyDown(SableKeyTest.KEYCODE_SYM, 0)
        t.onReplace("", "😀") // an emoji from the symbol picker
        assertEquals(State.PASS, t.state(Check.SYM))
        t.onReplace(null, null)
    }

    @Test fun theExampleSentenceWithModifiersPassesEverything() {
        val t = SableKeyTest()
        t.onKeyDown(SableKeyTest.KEYCODE_SHIFT_LEFT, SableKeyTest.META_SHIFT_ON)
        type(t, "Meet at ")
        t.onKeyDown(SableKeyTest.KEYCODE_ALT_LEFT, SableKeyTest.META_ALT_ON)
        type(t, "7")
        t.onKeyDown(SableKeyTest.KEYCODE_SYM, 0)
        type(t, "&")
        t.onKeyDown(SableKeyTest.KEYCODE_DEL, 0)
        assertTrue(t.allPassed())
        assertEquals(SableKeyTest.checks().size, t.passedCount())
        assertFalse(t.onText("more", 0)) // nothing left to change
        t.reset()
        assertEquals(0, t.passedCount())
    }

    @Test fun controlCharactersNeverCreditALayer() {
        val t = SableKeyTest()
        t.onKeyDown(SableKeyTest.KEYCODE_ALT_LEFT, 0)
        t.onText("\n", 0)
        t.onText("1", 0)
        assertEquals(State.PRESSED, t.state(Check.ALT))
    }
}
