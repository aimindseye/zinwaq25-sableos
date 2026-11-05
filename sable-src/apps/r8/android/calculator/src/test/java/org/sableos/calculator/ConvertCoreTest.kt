package org.sableos.calculator

import org.junit.Assert.assertEquals
import org.junit.Test
import org.sableos.convert.ConvertCore

class ConvertCoreTest {
    @Test
    fun referenceConversions() {
        assertEquals("OK:8047", ConvertCore.convertMilli(5_000, 6, 3))
        assertEquals("OK:1000000", ConvertCore.convertMilli(1_000, 8, 7))
        assertEquals("OK:1000000", ConvertCore.convertMilli(1_000, 15, 14))
        assertEquals("OK:-8047", ConvertCore.convertMilli(-5_000, 6, 3))
    }

    @Test
    fun temperaturesAndBounds() {
        assertEquals("OK:32000", ConvertCore.convertMilli(0, 11, 12))
        assertEquals("OK:0", ConvertCore.convertMilli(273_150, 13, 11))
        assertEquals("OK:100000", ConvertCore.convertMilli(212_000, 12, 11))
        assertEquals("ERR:OUT_OF_RANGE", ConvertCore.convertMilli(-1, 13, 11))
        assertEquals("ERR:OUT_OF_RANGE", ConvertCore.convertMilli(-273_151, 11, 12))
    }

    @Test
    fun unitCodesAndCategoriesFailClosed() {
        for (code in 0..17) {
            assertEquals(code.toString(), false, ConvertCore.convertMilli(0, code, code).startsWith("ERR"))
        }
        assertEquals("ERR:INVALID_UNIT", ConvertCore.convertMilli(1_000, -1, 3))
        assertEquals("ERR:INVALID_UNIT", ConvertCore.convertMilli(1_000, 0, 18))
        assertEquals("ERR:INCOMPATIBLE_UNITS", ConvertCore.convertMilli(1_000, 0, 8))
        assertEquals("ERR:OUT_OF_RANGE", ConvertCore.convertMilli(Long.MAX_VALUE, 6, 0))
        assertEquals("ERR:OUT_OF_RANGE", ConvertCore.convertMilli(Long.MIN_VALUE, 9, 9))
    }
}
