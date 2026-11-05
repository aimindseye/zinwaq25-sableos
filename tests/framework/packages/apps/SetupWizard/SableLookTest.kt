package org.lineageos.setupwizard.sable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SableLookTest {
    @Test
    fun valuesMatchSableStartsLooks() {
        assertEquals("bb10", SableLook.valueOf(SableLook.BB10))
        assertEquals("bb7", SableLook.valueOf(SableLook.BB7))
        assertEquals("plain", SableLook.valueOf(SableLook.PLAIN))
        assertEquals("org.sableos.start.action.SET_LOOK", SableLook.ACTION_SET_LOOK)
        assertEquals("org.sableos.launcher", SableLook.START_PACKAGE)
    }

    @Test
    fun theChoiceIsRememberedAndAnUnknownValueMeansBlackBerry10Style() {
        listOf(SableLook.BB10, SableLook.BB7, SableLook.PLAIN).forEach {
            assertEquals(it, SableLook.lookOf(SableLook.valueOf(it)))
        }
        assertEquals(SableLook.BB10, SableLook.lookOf(null))
        assertEquals(SableLook.BB10, SableLook.lookOf("bb6"))
        assertEquals("bb10", SableLook.valueOf(7))
    }

    @Test
    fun bothBlackBerryStylesArePureBlackAndPlainIsNot() {
        assertTrue(SableLook.pureBlack(SableLook.BB10))
        assertTrue(SableLook.pureBlack(SableLook.BB7))
        assertFalse(SableLook.pureBlack(SableLook.PLAIN))
    }
}
