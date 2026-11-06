package org.sableos.phone.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedDialTest {
    @Test
    fun roundTripsAToZ() {
        val entries = mapOf(
            'm' to SpeedDial.Entry("+1 555 0101", "Mum"),
            'a' to SpeedDial.Entry("123", "Alex")
        )
        val stored = SpeedDial.format(entries)
        assertEquals("a\t123\tAlex\nm\t+1 555 0101\tMum", stored)
        assertEquals(entries, SpeedDial.parse(stored))
    }

    @Test
    fun badLinesAreSkippedAndAMissingLabelIsTheNumber() {
        val parsed = SpeedDial.parse("B\t555\t\nxx\t1\tbad\n1\t2\t3\nc\t \tnobody\nd\t9")
        assertEquals(mapOf('b' to SpeedDial.Entry("555", "555")), parsed)
    }

    @Test
    fun separatorsNeverReachTheStore() {
        val stored = SpeedDial.format(mapOf('z' to SpeedDial.Entry("1", "Line\none\ttwo")))
        assertEquals("z\t1\tLine one two", stored)
        assertTrue(SpeedDial.parse(null).isEmpty())
    }

    @Test
    fun contractIsTheOneSableStartReads() {
        assertEquals("org.sableos.dialer.speeddial", SpeedDial.AUTHORITY)
        assertEquals("org.sableos.dialer.action.SPEED_DIAL", SpeedDial.ACTION_SPEED_DIAL)
        assertEquals("org.sableos.dialer.extra.LETTER", SpeedDial.EXTRA_LETTER)
        assertEquals("letter", SpeedDial.COLUMN_LETTER)
    }
}
