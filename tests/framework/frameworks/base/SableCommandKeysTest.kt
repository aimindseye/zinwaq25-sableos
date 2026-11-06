package org.sableos.test.frameworks.base

import com.android.server.policy.sable.SableCommandKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SableCommandKeysTest {
    private val space = 62
    private val altLeft = 0x02 or 0x10
    private val altRight = 0x02 or 0x20 // Sym on the Q25

    private fun chord(key: Int, meta: Int, virtual: Boolean = false) =
        SableCommandKeys.isCommandChord(key, meta, virtual)

    @Test
    fun altSpaceWithEitherAltOpensCommand() {
        assertTrue(chord(space, altLeft))
        assertTrue(chord(space, altRight))
        assertEquals(62, SableCommandKeys.KEYCODE_SPACE)
    }

    @Test
    fun plainSpaceAndOtherAltKeysAreLeftAlone() {
        assertFalse(chord(space, 0))
        assertFalse(chord(46, altLeft)) // Alt+R types 3
        assertFalse(chord(20, altLeft)) // Alt+Down is the shade
    }

    @Test
    fun anyOtherModifierKeepsSpacesOwnMeaning() {
        // Shift, Sym-as-symbol, Fn, Ctrl (Ctrl+Space switches input language), Meta.
        listOf(0x01, 0x04, 0x08, 0x1000, 0x10000).forEach { other ->
            assertFalse(chord(space, altLeft or other))
        }
    }

    @Test
    fun lockStatesAreIgnoredAndSoftwareKeyboardsNeverTrigger() {
        val capsNumScroll = 0x100000 or 0x200000 or 0x400000
        assertTrue(chord(space, altLeft or capsNumScroll))
        assertFalse(chord(space, altLeft, virtual = true))
    }

    @Test
    fun theRequestGoesToSableStartsCommand() {
        assertEquals("org.sableos.start.action.COMMAND", SableCommandKeys.ACTION_COMMAND)
        assertEquals("org.sableos.launcher", SableCommandKeys.START_PACKAGE)
    }
}
