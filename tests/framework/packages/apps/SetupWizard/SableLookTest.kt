package org.lineageos.setupwizard.sable

import org.junit.Assert.assertEquals
import org.junit.Test

class SableLookTest {
    @Test
    fun valuesMatchSableStartsLooks() {
        assertEquals("bb10", SableLook.valueOf(SableLook.BB10))
        assertEquals("bb7", SableLook.valueOf(SableLook.BB7))
        assertEquals("org.sableos.start.action.SET_LOOK", SableLook.ACTION_SET_LOOK)
        assertEquals("org.sableos.launcher", SableLook.START_PACKAGE)
    }

    @Test
    fun theChoiceIsRememberedAndAnUnknownValueMeansBlackBerry10Style() {
        listOf(SableLook.BB10, SableLook.BB7).forEach {
            assertEquals(it, SableLook.lookOf(SableLook.valueOf(it)))
        }
        assertEquals(SableLook.BB10, SableLook.lookOf(null))
        assertEquals(SableLook.BB10, SableLook.lookOf("plain"))
        assertEquals("bb10", SableLook.valueOf(7))
    }
}
