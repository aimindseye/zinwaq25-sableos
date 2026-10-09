package org.sableos.reference.setupflow

/**
 * Layout requirements for a roughly square display. This is a specification and a classifier, not a fact about
 * any device: the host passes the live window size, and nothing here names a device or a profile.
 */
enum class LayoutClass { Portrait, Square, Landscape }

object SquareLayout {
    /** Width/height between these counts as square. */
    const val SQUARE_MIN_RATIO = 0.8f
    const val SQUARE_MAX_RATIO = 1.25f
    const val MIN_TOUCH_TARGET_DP = 48
    const val MIN_FOCUS_RING_DP = 2
    const val ROW_DP = 56

    /** Setup must keep working after rotation or a window resize, with focus restored by id. */
    const val ORIENTATION_LOCKED = false
    const val SCROLL_FOCUSED_INTO_VIEW = true
    const val HORIZONTAL_SCROLL_ALLOWED = false

    fun classify(widthPx: Int, heightPx: Int): LayoutClass {
        require(widthPx > 0 && heightPx > 0) { "window size must be positive" }
        val r = widthPx.toFloat() / heightPx
        return when {
            r < SQUARE_MIN_RATIO -> LayoutClass.Portrait
            r > SQUARE_MAX_RATIO -> LayoutClass.Landscape
            else -> LayoutClass.Square
        }
    }

    /** Rows that fit under a header of [headerDp] in a window [heightDp] tall. Never negative. */
    fun visibleRows(heightDp: Int, headerDp: Int, rowDp: Int = ROW_DP): Int =
        ((heightDp - headerDp) / rowDp.coerceAtLeast(1)).coerceAtLeast(0)

    /** If the content does not fit, the step scrolls and the focused control must be scrolled into view. */
    fun needsScroll(contentRows: Int, heightDp: Int, headerDp: Int): Boolean =
        contentRows > visibleRows(heightDp, headerDp)

    /** A control's touch target is padded up to the minimum; the visible size can be smaller. */
    fun touchTargetDp(visibleDp: Int): Int = visibleDp.coerceAtLeast(MIN_TOUCH_TARGET_DP)
}
