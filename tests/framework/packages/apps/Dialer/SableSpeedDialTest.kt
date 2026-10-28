package com.android.dialer.sable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure tests for BlackBerry speed dial on letter keys (Dialer patch 0710). */
class SableSpeedDialTest {
    private val keyA = 29
    private val keyK = 39
    private val keyZ = 54

    @Test
    fun aHeldPlainLetterSpeedDials() {
        assertEquals('k', SableSpeedDial.heldLetter(keyK, 0, 1))
        assertEquals('a', SableSpeedDial.heldLetter(keyA, 0, 1))
        assertEquals('z', SableSpeedDial.heldLetter(keyZ, 0, 1))
    }

    @Test
    fun shortPressesLaterRepeatsChordsAndOtherKeysDoNot() {
        assertEquals("first press", 0.toChar(), SableSpeedDial.heldLetter(keyK, 0, 0))
        assertEquals("later repeat", 0.toChar(), SableSpeedDial.heldLetter(keyK, 0, 2))
        listOf(0x41, 0x12, 0x4, 0x8, 0x3000, 0x30000).forEach { meta ->
            assertEquals("meta $meta", 0.toChar(), SableSpeedDial.heldLetter(keyK, meta, 1))
        }
        assertEquals("digit 7", 0.toChar(), SableSpeedDial.heldLetter(14, 0, 1))
        assertEquals("space", 0.toChar(), SableSpeedDial.heldLetter(62, 0, 1))
    }

    @Test
    fun storedListRoundTripsAndSkipsBadLines() {
        val stored = "K\t+1 555 0100\tKim\nbad line\n7\t123\tx\nz\t\tnobody\nm\t+44 20\t"
        val parsed = SableSpeedDial.parse(stored)
        assertEquals(setOf('k', 'm'), parsed.keys)
        assertEquals(SableSpeedDial.Entry("+1 555 0100", "Kim"), parsed['k'])
        assertEquals("no name: the number is shown", "+44 20", parsed['m']!!.label)
        assertEquals("k\t+1 555 0100\tKim\nm\t+44 20\t+44 20", SableSpeedDial.format(parsed))
        assertEquals(parsed, SableSpeedDial.parse(SableSpeedDial.format(parsed)))
        assertTrue(SableSpeedDial.parse(null).isEmpty())
    }

    @Test
    fun namesCannotBreakTheStoredForm() {
        val entries = mapOf('a' to SableSpeedDial.Entry("100", "Ann\tB\nC"))
        assertEquals(mapOf('a' to SableSpeedDial.Entry("100", "Ann B C")), SableSpeedDial.parse(SableSpeedDial.format(entries)))
    }
}
