package org.sableos.start.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Edit Home's grid size and Active Frames options, and hidden apps. */
class HomeOptionsTest {
    @Test
    fun gridShapeCyclesColumnsAndRows() {
        val shape = GridShape()
        val six = shape.nextColumns()
        assertEquals(6, six.columns)
        assertEquals(4, six.nextColumns().columns)
        assertEquals(5, six.nextColumns().nextColumns().columns)
        var rows = shape
        val seen = mutableListOf<Int>()
        repeat(4) {
            rows = rows.nextRows()
            seen += rows.rows
        }
        assertEquals(listOf(3, 4, 5, GridShape.FIT), seen)
        assertEquals(4, shape.rowsFor(4))
        assertEquals(3, shape.copy(rows = 3).rowsFor(5))
    }

    @Test
    fun gridShapeRoundTripsAndFallsBackOnBadText() {
        val shape = GridShape(columns = 6, rows = 4, names = false)
        assertEquals(shape, GridShape.decode(shape.encode()))
        assertEquals(GridShape(), GridShape.decode(null))
        assertEquals(GridShape(), GridShape.decode("9:9:1"))
        assertTrue(GridShape.decode("5:0:1").names)
    }

    @Test
    fun framesOptionsStepByTwoAndRoundTrip() {
        val options = FramesOptions()
        assertEquals(ActiveFrames.MAX, options.max)
        assertEquals(FramesOptions.STEP, options.nextMax().max)
        assertEquals(FramesOptions.STEP * 2, options.nextMax().nextMax().max)
        val off = options.toggleLive()
        assertFalse(off.live)
        assertEquals(off, FramesOptions.decode(off.encode()))
        assertEquals(FramesOptions(), FramesOptions.decode("3:1"))
    }

    @Test
    fun hiddenAppsShowForPrivateOrHiddenSearches() {
        assertTrue(HiddenApps.reveals(" Private "))
        assertTrue(HiddenApps.reveals("hidden"))
        assertFalse(HiddenApps.reveals("priv"))
        assertEquals(setOf("a"), HiddenApps.toggle(emptySet(), "a"))
        assertEquals(emptySet<String>(), HiddenApps.toggle(setOf("a"), "a"))
    }
}
