package org.sableos.test.frameworks.base

import com.android.server.policy.sable.SablePrivacyKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SablePrivacyKeysTest {
    private val p = 44
    private val altShift = 0x02 or 0x10 or 0x01 or 0x40
    private val symShift = 0x02 or 0x20 or 0x01 or 0x80 // Sym is Right Alt on the Q25

    private fun chord(key: Int, meta: Int, virtual: Boolean = false) =
        SablePrivacyKeys.isPrivacyChord(key, meta, virtual)

    @Test
    fun altShiftPWithEitherAltSwitchesThePrivacyScreen() {
        assertTrue(chord(p, altShift))
        assertTrue(chord(p, symShift))
        assertEquals(44, SablePrivacyKeys.KEYCODE_P)
    }

    @Test
    fun altPAloneStillTypesTheAtSign() {
        assertFalse(chord(p, 0x02 or 0x10))
        assertFalse(chord(p, 0x01))
        assertFalse(chord(p, 0))
        assertFalse(chord(46, altShift)) // Alt+Shift+R is the Tall canvas cycle
    }

    @Test
    fun anyOtherModifierKeepsPsOwnMeaning() {
        listOf(0x04, 0x08, 0x1000, 0x10000).forEach { other ->
            assertFalse(chord(p, altShift or other))
        }
    }

    @Test
    fun lockStatesAreIgnoredAndSoftwareKeyboardsNeverTrigger() {
        assertTrue(chord(p, altShift or 0x100000 or 0x200000))
        assertFalse(chord(p, altShift, virtual = true))
    }

    @Test
    fun onlyAnAnswerThatItSwitchedSkipsAccessibility() {
        assertTrue(SablePrivacyKeys.switched(-1))
        assertFalse(SablePrivacyKeys.switched(SablePrivacyKeys.RESULT_NOT_RUNNING))
        assertEquals(
            "org.sableos.textreader.action.TOGGLE_PRIVACY_SCREEN",
            SablePrivacyKeys.ACTION_TOGGLE
        )
        assertEquals("org.sableos.textreader", SablePrivacyKeys.TEXT_READER_PACKAGE)
    }
}
