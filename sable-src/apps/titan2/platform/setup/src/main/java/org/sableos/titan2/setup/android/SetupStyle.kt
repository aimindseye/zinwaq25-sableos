package org.sableos.titan2.setup.android

import androidx.core.graphics.toColorInt

/** Sizes (dp, sp) and colours shared by the checklist and the Weather city step. */
internal object SetupStyle {
    const val PAD_H = 16
    const val PAD_V = 18
    const val SPACING = 10
    const val TITLE_SP = 26f
    const val SUMMARY_SP = 15f
    const val ROW_TITLE_SP = 17f
    const val DETAIL_SP = 13f
    val BG = "#0C1E2D".toColorInt()
    val KEY = "#1E3A52".toColorInt()
    val FOCUS = "#2F6690".toColorInt()
    val DIM = "#8FA9BF".toColorInt()
    val WARN = "#FFB347".toColorInt()
}
