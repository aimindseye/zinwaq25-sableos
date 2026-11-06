package org.sableos.start.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.design.AccentPreset

/** Start's pages follow Android's Dark theme; the one accent is the 2.0 blue. */
class StartPaletteTest {
    @Test
    fun oneBlueForFocusAndSelection() {
        // 2.0 is black and white with one blue (UI direction, 2026-11-04).
        assertEquals(Color(0xFF2E9BD6), startPalette(dark = true, accent = AccentPreset.Blue).accent)
        assertEquals(Color(0xFF2E9BD6), startPalette(dark = false, accent = AccentPreset.Blue).accent)
    }

    @Test
    fun darkIsPureBlackAndLightUsesLightSurfacesWithDarkText() {
        val dark = startPalette(dark = true, accent = AccentPreset.Blue)
        val light = startPalette(dark = false, accent = AccentPreset.Blue)
        assertEquals(Color.Black, dark.background)
        assertTrue(dark.card.luminance() < 0.02f)
        assertEquals(Color.White, dark.text)
        assertTrue(light.background.luminance() > 0.8f)
        assertTrue(light.card.luminance() > 0.8f)
        assertTrue(light.text.luminance() < 0.1f)
        assertTrue(light.muted.luminance() < 0.3f)
    }
}
