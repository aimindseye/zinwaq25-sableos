package org.sableos.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarPaletteTest {
    @Test
    fun nextStepsThroughThePaletteAndWraps() {
        val colors = CalendarPalette.COLORS
        assertEquals(colors[1], CalendarPalette.next(colors[0]))
        assertEquals(colors[0], CalendarPalette.next(colors.last()))
    }

    @Test
    fun aProviderColourOutsideThePaletteStartsAtTheFirst() {
        assertEquals(CalendarPalette.COLORS[0], CalendarPalette.next(0x123456))
    }
}
