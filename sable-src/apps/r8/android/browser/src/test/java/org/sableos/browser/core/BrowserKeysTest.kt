package org.sableos.browser.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserKeysTest {
    @Test
    fun bb10Keys() {
        assertEquals(BrowserCommand.Address, BrowserKeys.command(40, shift = false, otherModifier = false))
        assertEquals(BrowserCommand.PageDown, BrowserKeys.command(62, shift = false, otherModifier = false))
        assertEquals(BrowserCommand.PageUp, BrowserKeys.command(62, shift = true, otherModifier = false))
        assertEquals(BrowserCommand.Top, BrowserKeys.command(48, shift = false, otherModifier = false))
        assertEquals(BrowserCommand.Bottom, BrowserKeys.command(30, shift = false, otherModifier = false))
        assertEquals(BrowserCommand.Back, BrowserKeys.command(67, shift = false, otherModifier = false))
        assertEquals(BrowserCommand.Forward, BrowserKeys.command(67, shift = true, otherModifier = false))
        assertEquals(BrowserCommand.TogglePointer, BrowserKeys.command(41, shift = false, otherModifier = false))
        // Alt+letter types a symbol on the Q25: never a browser key.
        assertNull(BrowserKeys.command(40, shift = false, otherModifier = true))
        assertNull(BrowserKeys.command(29, shift = false, otherModifier = false))
    }

    @Test
    fun trackpadStrokesAndClicks() {
        assertEquals(0 to -1, BrowserKeys.arrow(19))
        assertEquals(1 to 0, BrowserKeys.arrow(22))
        assertNull(BrowserKeys.arrow(40))
        assertTrue(BrowserKeys.isClick(23))
        assertTrue(BrowserKeys.isClick(66))
        assertFalse(BrowserKeys.isClick(62))
    }
}
