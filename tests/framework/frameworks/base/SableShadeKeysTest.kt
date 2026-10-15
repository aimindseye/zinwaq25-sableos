package org.sableos.test.frameworks.base

import com.android.server.policy.sable.SableShadeKeys
import org.junit.Assert.assertEquals
import org.junit.Test

class SableShadeKeysTest {
    private val dpadDown = 20
    private val dpadUp = 19
    private val altLeft = 0x02 or 0x10
    private val altRight = 0x02 or 0x20
    private val navDown = SableShadeKeys.KEYCODE_SYSTEM_NAVIGATION_DOWN

    @Test
    fun altDownIsTheShadeChord() {
        assertEquals(navDown, SableShadeKeys.systemKeyFor(dpadDown, altLeft, false))
        assertEquals(navDown, SableShadeKeys.systemKeyFor(dpadDown, altRight, false))
        assertEquals(281, navDown)
    }

    @Test
    fun plainDownAndOtherKeysAreLeftToApps() {
        assertEquals(SableShadeKeys.NONE, SableShadeKeys.systemKeyFor(dpadDown, 0, false))
        assertEquals(SableShadeKeys.NONE, SableShadeKeys.systemKeyFor(dpadUp, altLeft, false))
        // Alt+letter types digits and symbols on the Q25.
        assertEquals(SableShadeKeys.NONE, SableShadeKeys.systemKeyFor(51, altLeft, false))
    }

    @Test
    fun anyOtherModifierKeepsTheKeysOwnMeaning() {
        listOf(0x01, 0x04, 0x08, 0x1000, 0x10000).forEach { other ->
            assertEquals(SableShadeKeys.NONE, SableShadeKeys.systemKeyFor(dpadDown, altLeft or other, false))
        }
    }

    @Test
    fun lockStatesAreIgnored() {
        val capsNumScrollAltLock = 0x100000 or 0x200000 or 0x400000 or 0x200
        assertEquals(navDown, SableShadeKeys.systemKeyFor(dpadDown, altLeft or capsNumScrollAltLock, false))
        // Alt lock alone is not Alt held.
        assertEquals(SableShadeKeys.NONE, SableShadeKeys.systemKeyFor(dpadDown, 0x200, false))
    }

    @Test
    fun softwareKeyboardsNeverOpenTheShade() {
        assertEquals(SableShadeKeys.NONE, SableShadeKeys.systemKeyFor(dpadDown, altLeft, true))
    }
}
