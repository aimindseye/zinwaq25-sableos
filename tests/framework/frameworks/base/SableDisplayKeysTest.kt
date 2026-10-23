package org.sableos.test.frameworks.base

import com.android.server.policy.sable.SableDisplayKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SableDisplayKeysTest {
    private val r = 46
    private val altLeft = 0x02 or 0x10
    private val altRight = 0x02 or 0x20 // Sym on the Q25
    private val shiftLeft = 0x01 or 0x40
    private val shiftRight = 0x01 or 0x80

    private fun chord(key: Int, meta: Int, virtual: Boolean = false) =
        SableDisplayKeys.isCanvasCycleChord(key, meta, virtual)

    @Test
    fun altShiftRIsTheResolutionHotkey() {
        assertTrue(chord(r, altLeft or shiftLeft))
        assertTrue(chord(r, altRight or shiftRight))
        assertTrue(chord(r, altLeft or shiftRight))
        assertEquals(46, SableDisplayKeys.KEYCODE_R)
    }

    @Test
    fun altROrShiftRStillType() {
        assertFalse(chord(r, altLeft)) // 3
        assertFalse(chord(r, shiftLeft)) // R
        assertFalse(chord(r, 0))
        assertFalse(chord(33, altLeft or shiftLeft)) // E
    }

    @Test
    fun anyOtherModifierKeepsTheKeysOwnMeaning() {
        listOf(0x04, 0x08, 0x1000, 0x10000).forEach { other ->
            assertFalse(chord(r, altLeft or shiftLeft or other))
        }
    }

    @Test
    fun lockStatesAreIgnoredAndSoftwareKeyboardsNeverTrigger() {
        val capsNumScroll = 0x100000 or 0x200000 or 0x400000
        assertTrue(chord(r, altLeft or shiftLeft or capsNumScroll))
        assertFalse(chord(r, altLeft or shiftLeft, virtual = true))
    }

    @Test
    fun theBroadcastGoesToDisplayCompat() {
        assertEquals("org.sableos.titan2.displaycompat", SableDisplayKeys.DISPLAY_COMPAT_PACKAGE)
        assertEquals(
            "org.sableos.titan2.displaycompat.action.CYCLE_CANVAS",
            SableDisplayKeys.ACTION_CYCLE_CANVAS
        )
    }
}
