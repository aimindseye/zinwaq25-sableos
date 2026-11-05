package org.sableos.start.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartLookTest {
    @Test
    fun eachLookSetsTheHomeStyle() {
        assertEquals(HomeStyle.Bb10, StartLook.Bb10.homeStyle)
        assertEquals(HomeStyle.Bb7, StartLook.Bb7.homeStyle)
    }

    @Test
    fun plainAndUnknownLooksChangeNothing() {
        assertEquals(StartLook.Bb7, StartLook.fromStableValue("bb7"))
        assertNull(StartLook.fromStableValue("plain"))
        assertNull(StartLook.fromStableValue("bb6"))
        assertNull(StartLook.fromStableValue(null))
    }
}
