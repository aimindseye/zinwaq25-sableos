package org.sableos.titan2.displaycompat.ui

import androidx.compose.ui.graphics.Color

/**
 * The Sable theme's colours: dark or light from the system Dark theme, the accent from the Sable
 * appearance authority. [MainActivity] reads [palette] in onCreate; a Dark theme change re-creates it.
 */
object C {
    internal var palette: SablePalette = SablePalette.of(dark = true, accent = null)
    val window get() = Color(palette.window)
    val surface get() = Color(palette.surface)
    val text get() = Color(palette.text)
    val muted get() = Color(palette.muted)
    val accent get() = Color(palette.accent)
    val onAccent get() = Color(palette.onAccent)
    val warn get() = Color(palette.warn)
    val danger get() = Color(palette.danger)
    val ok get() = Color(palette.ok)
}
