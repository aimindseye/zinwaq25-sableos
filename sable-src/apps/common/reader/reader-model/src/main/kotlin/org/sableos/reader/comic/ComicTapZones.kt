package org.sableos.reader.comic

/** What a tap on the page area does. */
enum class ComicTapAction { PREVIOUS_PAGE, NEXT_PAGE, TOGGLE_CHROME }

/** Touch fallback for the keyboard map: tap the page edges to turn pages, the middle to show or hide controls. */
object ComicTapZones {
    /** Fraction of the width (or height) at each edge that turns pages. */
    const val EDGE_FRACTION: Float = 0.25f

    fun resolve(mode: ComicReadingMode, x: Float, y: Float, width: Float, height: Float): ComicTapAction = when {
        width <= 0f || height <= 0f || mode.isContinuous -> ComicTapAction.TOGGLE_CHROME
        mode == ComicReadingMode.VERTICAL_PAGER -> edge(y / height, towardsStart = ComicTapAction.PREVIOUS_PAGE)
        else -> {
            val start = if (mode.isRightToLeft) ComicTapAction.NEXT_PAGE else ComicTapAction.PREVIOUS_PAGE
            edge(x / width, towardsStart = start)
        }
    }

    private fun edge(position: Float, towardsStart: ComicTapAction): ComicTapAction {
        val startIsPrevious = towardsStart == ComicTapAction.PREVIOUS_PAGE
        val towardsEnd = if (startIsPrevious) ComicTapAction.NEXT_PAGE else ComicTapAction.PREVIOUS_PAGE
        return when {
            position < EDGE_FRACTION -> towardsStart
            position > 1f - EDGE_FRACTION -> towardsEnd
            else -> ComicTapAction.TOGGLE_CHROME
        }
    }
}
