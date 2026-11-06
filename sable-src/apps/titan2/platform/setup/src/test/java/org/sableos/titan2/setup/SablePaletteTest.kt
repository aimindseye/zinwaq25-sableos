package org.sableos.titan2.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.titan2.theme.SablePalette

class SablePaletteTest {
    @Test fun darkAndLightFollowTheDarkTheme() {
        val dark = SablePalette.of(dark = true, accent = "blue")
        val light = SablePalette.of(dark = false, accent = "blue")
        assertTrue(dark.dark)
        assertFalse(light.dark)
        assertNotEquals(dark.window, light.window)
        assertNotEquals(dark.text, light.text)
    }

    @Test fun oneBlueAccentInEveryTheme() {
        // 2.0 is black and white with one blue; a 1.0 preset still stored reads as it.
        assertEquals(0xFF2E9BD6.toInt(), SablePalette.of(dark = true, accent = "green").accent)
        assertEquals(0xFF1A6FA0.toInt(), SablePalette.of(dark = false, accent = "purple").accent)
    }

    @Test fun surfacesAreTheSableSystemTokens() {
        // Same values as SableSystemTokens.Dark/Light in sabledesign: Start, Settings and Quick Settings.
        assertEquals(0xFF000000.toInt(), SablePalette.of(dark = true, accent = null).window)
        assertEquals(0xFF141414.toInt(), SablePalette.of(dark = true, accent = null).surface)
        assertEquals(0xFFF5F7F9.toInt(), SablePalette.of(dark = false, accent = null).window)
        assertEquals(0xFF1A6FA0.toInt(), SablePalette.of(dark = false, accent = "blue").accent)
    }

    @Test fun unknownOrMissingAccentIsSableBlue() {
        val blue = SablePalette.of(dark = true, accent = "blue").accent
        assertEquals(blue, SablePalette.of(dark = true, accent = null).accent)
        assertEquals(blue, SablePalette.of(dark = true, accent = "magenta").accent)
    }
}
