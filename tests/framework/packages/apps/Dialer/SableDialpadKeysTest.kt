package com.android.dialer.sable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure tests for dial-screen hardware keys and Recents privacy (Dialer patches 0705, 0706). */
class SableDialpadKeysTest {
    private val alt = 0x12 // META_ALT_ON | META_ALT_LEFT_ON
    private val shift = 0x41
    private val ctrl = 0x3000
    private val phonePad = intArrayOf(51, 33, 46, 47, 32, 34, 54, 52, 31) // W E R S D F Z X C

    private fun dial(
        key: Int,
        unicode: Int = 0,
        meta: Int = 0,
        focused: Boolean = true,
    ) = SableDialpadKeys.dialChar(key, unicode, meta, focused)

    @Test fun androidConstantsArePinned() {
        assertEquals(7, SableDialpadKeys.KEYCODE_0)
        assertEquals(17, SableDialpadKeys.KEYCODE_STAR)
        assertEquals(18, SableDialpadKeys.KEYCODE_POUND)
        assertEquals(81, SableDialpadKeys.KEYCODE_PLUS)
        assertEquals(144, SableDialpadKeys.KEYCODE_NUMPAD_0)
        assertEquals(0x32, SableDialpadKeys.META_ALT_MASK)
        assertEquals(0xc1, SableDialpadKeys.META_SHIFT_MASK)
    }

    @Test fun plainPhonePadLettersAreDigitsOnlyInTheDialPad() {
        phonePad.forEachIndexed { i, key ->
            assertEquals('1' + i, dial(key, unicode = 'w'.code))
            assertEquals(0.toChar(), dial(key, unicode = 'w'.code, focused = false))
        }
        assertEquals(0.toChar(), dial(29, unicode = 'a'.code)) // A is not on the pad
    }

    @Test fun altLayerTypesDigitsFromTheKeyMapOrByKeyCode() {
        assertEquals('1', dial(51, unicode = '1'.code, meta = alt, focused = false))
        // A layout overlay without Alt digits: Alt+R is still 3.
        assertEquals('3', dial(46, unicode = 'r'.code, meta = alt, focused = false))
        // The Q25 0 key is the key map's Alt 0.
        assertEquals('0', dial(0, unicode = '0'.code, meta = alt))
        assertEquals('#', dial(0, unicode = '#'.code, meta = alt))
    }

    @Test fun shiftAndShortcutChordsNeverDial() {
        assertEquals(0.toChar(), dial(51, unicode = 'W'.code, meta = shift))
        assertEquals(0.toChar(), dial(51, unicode = '1'.code, meta = ctrl or alt))
        assertEquals(0.toChar(), dial(8, unicode = '1'.code, meta = ctrl))
    }

    @Test fun numberRowNumpadAndSymbolKeys() {
        assertEquals('5', dial(12, unicode = '5'.code, focused = false))
        assertEquals('9', dial(153, focused = false))
        assertEquals('*', dial(17))
        assertEquals('#', dial(18))
        assertEquals('+', dial(81))
    }

    @Test fun mainScreenOpensDialPadForDigitsOnce() {
        assertTrue(SableDialpadKeys.opensDialpad(51, '1'.code, alt, 0))
        assertFalse(SableDialpadKeys.opensDialpad(51, '1'.code, alt, 1))
        assertFalse(SableDialpadKeys.opensDialpad(51, 'w'.code, 0, 0))
        assertFalse(SableDialpadKeys.opensDialpad(81, '+'.code, 0, 0))
    }

    @Test fun keyCodesForDialCharacters() {
        assertEquals(7, SableDialpadKeys.keyCodeFor('0'))
        assertEquals(16, SableDialpadKeys.keyCodeFor('9'))
        assertEquals(17, SableDialpadKeys.keyCodeFor('*'))
        assertEquals(18, SableDialpadKeys.keyCodeFor('#'))
        assertEquals(81, SableDialpadKeys.keyCodeFor('+'))
        assertEquals(0, SableDialpadKeys.keyCodeFor('a'))
    }

    @Test fun recentsPrivacyFollowsLockAndPrivateMode() {
        assertFalse(SableRecentsPrivacy.privateMode(null))
        assertFalse(SableRecentsPrivacy.privateMode(0))
        assertTrue(SableRecentsPrivacy.privateMode(1))
        assertTrue(SableRecentsPrivacy.privateMode(7))
        assertTrue(SableRecentsPrivacy.hideDetails(true, false))
        assertTrue(SableRecentsPrivacy.hideDetails(false, true))
        assertFalse(SableRecentsPrivacy.hideDetails(false, false))
        assertEquals("org.sableos.hub.privacy", SableRecentsPrivacy.PROVIDER_AUTHORITY)
        assertEquals("private_mode", SableRecentsPrivacy.COLUMN_PRIVATE_MODE)
    }
}
