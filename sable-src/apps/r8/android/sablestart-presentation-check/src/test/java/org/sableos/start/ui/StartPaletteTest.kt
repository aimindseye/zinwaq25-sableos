package org.sableos.start.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.design.AccentPreset

/** Start's pages follow Android's Dark theme and the Sable accent. */
class StartPaletteTest {
    @Test
    fun defaultBlueKeepsTheBb10BlueAndOtherAccentsAreUsedAsIs() {
        assertEquals(Color(0xFF00A8DF), startPalette(dark = true, accent = AccentPreset.Blue).accent)
        for (accent in AccentPreset.entries.filter { it != AccentPreset.Blue }) {
            assertEquals(accent.color, startPalette(dark = true, accent = accent).accent)
            assertEquals(accent.onColor, startPalette(dark = false, accent = accent).onAccent)
        }
    }

    @Test
    fun darkIsPureBlackAndLightUsesLightSurfacesWithDarkText() {
        val dark = startPalette(dark = true, accent = AccentPreset.Green)
        val light = startPalette(dark = false, accent = AccentPreset.Green)
        assertEquals(Color.Black, dark.background)
        assertTrue(dark.card.luminance() < 0.02f)
        assertEquals(Color.White, dark.text)
        assertTrue(light.background.luminance() > 0.8f)
        assertTrue(light.card.luminance() > 0.8f)
        assertTrue(light.text.luminance() < 0.1f)
        assertTrue(light.muted.luminance() < 0.3f)
    }
}
