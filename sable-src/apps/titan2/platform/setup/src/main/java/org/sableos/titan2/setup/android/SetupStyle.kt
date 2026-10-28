package org.sableos.titan2.setup.android

/** Sizes (dp, sp) and colours shared by the checklist and the Weather city step; colours follow [palette]. */
internal object SetupStyle {
    const val PAD_H = 16
    const val PAD_V = 18
    const val SPACING = 10
    const val TITLE_SP = 26f
    const val SUMMARY_SP = 15f
    const val ROW_TITLE_SP = 17f
    const val DETAIL_SP = 13f

    /** Set from the Sable theme when the activity is created (and re-created on a Dark theme change). */
    var palette: SablePalette = SablePalette.of(dark = true, accent = null)
    val BG get() = palette.window
    val KEY get() = palette.surface
    val FOCUS get() = palette.focus
    val TEXT get() = palette.text
    val DIM get() = palette.muted
    val ACCENT get() = palette.accent
    val WARN get() = palette.warn
}
