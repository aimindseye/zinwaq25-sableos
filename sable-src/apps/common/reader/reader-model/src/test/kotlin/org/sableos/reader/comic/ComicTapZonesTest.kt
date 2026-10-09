package org.sableos.reader.comic

import org.junit.Assert.assertEquals
import org.junit.Test

class ComicTapZonesTest {
    private fun tap(mode: ComicReadingMode, x: Float, y: Float) = ComicTapZones.resolve(mode, x, y, 100f, 200f)

    @Test
    fun pagedLeftToRightTurnsByEdge() {
        val m = ComicReadingMode.PAGED_LTR
        assertEquals(ComicTapAction.PREVIOUS_PAGE, tap(m, 10f, 100f))
        assertEquals(ComicTapAction.NEXT_PAGE, tap(m, 90f, 100f))
        assertEquals(ComicTapAction.TOGGLE_CHROME, tap(m, 50f, 100f))
    }

    @Test
    fun pagedRightToLeftSwapsTheEdges() {
        val m = ComicReadingMode.PAGED_RTL
        assertEquals(ComicTapAction.NEXT_PAGE, tap(m, 10f, 100f))
        assertEquals(ComicTapAction.PREVIOUS_PAGE, tap(m, 90f, 100f))
    }

    @Test
    fun verticalPagerTurnsByTopAndBottomEdges() {
        val m = ComicReadingMode.VERTICAL_PAGER
        assertEquals(ComicTapAction.PREVIOUS_PAGE, tap(m, 50f, 10f))
        assertEquals(ComicTapAction.NEXT_PAGE, tap(m, 50f, 190f))
        assertEquals(ComicTapAction.TOGGLE_CHROME, tap(m, 5f, 100f))
    }

    @Test
    fun continuousModesAndDegenerateSizesOnlyToggleChrome() {
        listOf(ComicReadingMode.CONTINUOUS_VERTICAL, ComicReadingMode.WEBTOON).forEach {
            assertEquals(ComicTapAction.TOGGLE_CHROME, tap(it, 1f, 1f))
        }
        assertEquals(ComicTapAction.TOGGLE_CHROME, ComicTapZones.resolve(ComicReadingMode.PAGED_LTR, 1f, 1f, 0f, 0f))
    }
}
