package org.sableos.reference.setupflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SquareLayoutTest {
    @Test fun classifiesByRatioNotByDevice() {
        assertEquals(LayoutClass.Portrait, SquareLayout.classify(720, 1280))
        assertEquals(LayoutClass.Square, SquareLayout.classify(1000, 1000))
        assertEquals(LayoutClass.Square, SquareLayout.classify(1080, 1240))
        assertEquals(LayoutClass.Landscape, SquareLayout.classify(1280, 720))
    }

    @Test fun ratioBoundariesAreInclusiveOfSquare() {
        assertEquals(LayoutClass.Square, SquareLayout.classify(80, 100))
        assertEquals(LayoutClass.Square, SquareLayout.classify(125, 100))
        assertEquals(LayoutClass.Portrait, SquareLayout.classify(79, 100))
        assertEquals(LayoutClass.Landscape, SquareLayout.classify(126, 100))
    }

    @Test fun aNonPositiveWindowIsRejected() {
        listOf(0 to 10, 10 to 0, -1 to 5).forEach {
            val failed = runCatching { SquareLayout.classify(it.first, it.second) }.isFailure
            assertTrue(failed)
        }
    }

    @Test fun visibleRowsNeverGoNegative() {
        assertEquals(0, SquareLayout.visibleRows(100, 400))
        assertEquals(5, SquareLayout.visibleRows(400, 120))
        assertEquals(0, SquareLayout.visibleRows(400, 120, rowDp = 0).coerceAtMost(0))
    }

    @Test fun contentThatDoesNotFitScrollsAndTheFocusFollows() {
        assertTrue(SquareLayout.needsScroll(contentRows = 9, heightDp = 400, headerDp = 120))
        assertFalse(SquareLayout.needsScroll(contentRows = 4, heightDp = 400, headerDp = 120))
        assertTrue(SquareLayout.SCROLL_FOCUSED_INTO_VIEW)
    }

    @Test fun targetsAndFocusRingMeetMinimums() {
        assertEquals(48, SquareLayout.touchTargetDp(32))
        assertEquals(56, SquareLayout.touchTargetDp(56))
        assertTrue(SquareLayout.MIN_FOCUS_RING_DP >= 2)
    }

    @Test fun orientationIsNotLockedAndNothingScrollsSideways() {
        assertFalse(SquareLayout.ORIENTATION_LOCKED)
        assertFalse(SquareLayout.HORIZONTAL_SCROLL_ALLOWED)
    }
}
