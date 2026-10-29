package org.sableos.reader.layout

/**
 * Capability-driven layout profile derived only from the available window size; never from a device name.
 *
 * Compact and square-ish windows keep the reading surface maximized: no persistent side navigation, and top/bottom
 * chrome is transient (shown by tap or by Activate/Menu, hidden again by Escape).
 */
data class ReaderLayoutProfile(val widthDp: Float, val heightDp: Float) {
    private val shortSide: Float get() = minOf(widthDp, heightDp)
    private val longSide: Float get() = maxOf(widthDp, heightDp)

    val isSquareish: Boolean get() = longSide > 0f && shortSide / longSide >= SQUARE_RATIO
    val isCompact: Boolean get() = shortSide < COMPACT_SHORT_SIDE_DP

    /** Persistent side navigation is never used in Reader v2. */
    val persistentSideNavigation: Boolean get() = false

    /** Reading content always uses the full window; chrome overlays it. */
    val contentMaximized: Boolean get() = true

    val transientChrome: Boolean get() = isCompact || isSquareish

    /** Chrome is visible on first open only when it is persistent for this profile. */
    val chromeInitiallyVisible: Boolean get() = !transientChrome

    /**
     * Too little height for full-size chrome: on a 720x720 keyboard phone the window is about 411 x 387 dp, and the
     * library header, tabs and filter row would otherwise leave less than one row of covers.
     */
    val isShort: Boolean get() = heightDp > 0f && heightDp < SHORT_HEIGHT_DP

    /** Library cover width: narrower on short windows so a full row of covers fits under the chrome. */
    val libraryCardWidthDp: Float get() = if (isShort) SHORT_LIBRARY_CARD_DP else LIBRARY_CARD_DP

    fun libraryColumns(availableWidthDp: Float): Int =
        maxOf(MIN_LIBRARY_COLUMNS, (availableWidthDp / libraryCardWidthDp).toInt())

    companion object {
        /** Width/height ratio at or above which a window counts as square-ish (4:3 and squarer). */
        const val SQUARE_RATIO: Float = 0.75f

        /** Windows whose short side is below this are compact (the Material compact-width boundary). */
        const val COMPACT_SHORT_SIDE_DP: Float = 600f

        /** Windows lower than this use the short library and audiobook layouts. */
        const val SHORT_HEIGHT_DP: Float = 480f

        const val LIBRARY_CARD_DP: Float = 110f
        const val SHORT_LIBRARY_CARD_DP: Float = 88f
        const val MIN_LIBRARY_COLUMNS: Int = 3
    }
}
