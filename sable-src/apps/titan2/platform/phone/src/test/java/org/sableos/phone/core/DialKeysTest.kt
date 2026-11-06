package org.sableos.phone.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DialKeysTest {
    private val w = 51
    private val c = 31
    private val q = 45
    private val alt = 0x12

    @Test
    fun phonePadLettersAreDigitsOnTheDialScreen() {
        assertEquals('1', DialKeys.dialChar(w, 'w'.code, 0, 0, dialScreen = true))
        assertEquals('9', DialKeys.dialChar(c, 'c'.code, 0, 0, dialScreen = true))
        assertNull(DialKeys.dialChar(q, 'q'.code, 0, 0, dialScreen = true))
    }

    @Test
    fun theKeysOwnNumberCoversZeroStarAndHash() {
        assertEquals('0', DialKeys.dialChar(q, 'q'.code, '0'.code, 0, dialScreen = true))
        assertNull(DialKeys.dialChar(q, 'q'.code, '0'.code, 0, dialScreen = false))
    }

    @Test
    fun elsewhereOnlyAltMakesALetterADigit() {
        assertNull(DialKeys.dialChar(w, 'w'.code, 0, 0, dialScreen = false))
        assertEquals('1', DialKeys.dialChar(w, '1'.code, 0, alt, dialScreen = false))
        assertEquals('1', DialKeys.dialChar(w, 0, 0, alt, dialScreen = false))
    }

    @Test
    fun numberRowNumpadAndSymbols() {
        assertEquals('5', DialKeys.dialChar(12, 0, 0, 0, dialScreen = false))
        assertEquals('7', DialKeys.dialChar(151, 0, 0, 0, dialScreen = false))
        assertEquals('*', DialKeys.dialChar(DialKeys.KEYCODE_STAR, 0, 0, 0, dialScreen = false))
        assertEquals('#', DialKeys.dialChar(DialKeys.KEYCODE_POUND, 0, 0, 0, dialScreen = false))
    }

    @Test
    fun shortcutChordsNeverType() {
        assertNull(DialKeys.dialChar(w, 'w'.code, 0, DialKeys.META_CTRL_MASK, dialScreen = true))
        assertNull(DialKeys.dialChar(w, 'W'.code, 0, 0x41, dialScreen = true))
    }

    @Test
    fun opensDialPadOnFirstPressOfADigitButNotPlus() {
        assertTrue(DialKeys.opensDialPad(w, '1'.code, alt, 0))
        assertFalse(DialKeys.opensDialPad(w, '1'.code, alt, 1))
        assertFalse(DialKeys.opensDialPad(w, 'w'.code, 0, 0))
        assertFalse(DialKeys.opensDialPad(DialKeys.KEYCODE_PLUS, 0, 0, 0))
    }

    @Test
    fun heldLetterIsTheFirstRepeatOfAPlainLetter() {
        assertEquals('a', DialKeys.heldLetter(DialKeys.KEYCODE_A, 0, 1))
        assertNull(DialKeys.heldLetter(DialKeys.KEYCODE_A, 0, 0))
        assertNull(DialKeys.heldLetter(DialKeys.KEYCODE_A, 0, 2))
        assertNull(DialKeys.heldLetter(DialKeys.KEYCODE_A, alt, 1))
        assertNull(DialKeys.heldLetter(DialKeys.KEYCODE_SPACE, 0, 1))
    }

    @Test
    fun lettersUnderTheKeys() {
        assertEquals("ABC", DialKeys.lettersFor('2'))
        assertEquals("WXYZ", DialKeys.lettersFor('9'))
        assertEquals("", DialKeys.lettersFor('1'))
    }
}
