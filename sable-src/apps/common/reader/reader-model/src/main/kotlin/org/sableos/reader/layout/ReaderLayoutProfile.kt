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

    companion object {
        /** Width/height ratio at or above which a window counts as square-ish (4:3 and squarer). */
        const val SQUARE_RATIO: Float = 0.75f

        /** Windows whose short side is below this are compact (the Material compact-width boundary). */
        const val COMPACT_SHORT_SIDE_DP: Float = 600f
    }
}
