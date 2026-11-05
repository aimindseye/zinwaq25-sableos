package org.sableos.calculator

import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorCoreTest {
    @Test
    fun decimalInputsAreExact() {
        assertEquals("OK:3/10", CalculatorCore.applyBinary("0.1", 0, "0.2"))
        assertEquals("OK:1/4", CalculatorCore.applyBinary("1", 3, "4"))
        assertEquals("OK:-3/2", CalculatorCore.applyBinary("-2.50", 0, "1"))
        assertEquals("OK:1/1", CalculatorCore.applyBinary("0.25", 2, "4"))
        assertEquals("OK:3/4", CalculatorCore.applyBinary("1", 1, "0.25"))
    }

    @Test
    fun errorsFailClosed() {
        assertEquals("ERR:INVALID_OPERATION", CalculatorCore.applyBinary("1", 99, "2"))
        assertEquals("ERR:INVALID_NUMBER", CalculatorCore.applyBinary("invalid", 0, "2"))
        assertEquals("ERR:INVALID_NUMBER", CalculatorCore.applyBinary("1.2.3", 0, "2"))
        assertEquals("ERR:DIVIDE_BY_ZERO", CalculatorCore.applyBinary("1", 3, "0"))
        // One past the signed 128-bit range the exact domain keeps.
        assertEquals("ERR:OVERFLOW", CalculatorCore.applyBinary("170141183460469231731687303715884105728", 0, "1"))
        assertEquals("ERR:OVERFLOW", CalculatorCore.applyBinary("170141183460469231731687303715884105727", 0, "1"))
    }

    @Test
    fun scientificFunctionsUseDegrees() {
        assertEquals("OK:3", CalculatorCore.applyScientific("9", 0))
        assertEquals("OK:144", CalculatorCore.applyScientific("12", 1))
        assertEquals("OK:0.5", CalculatorCore.applyScientific("30", 3))
        assertEquals("OK:2", CalculatorCore.applyScientific("100", 7))
    }

    @Test
    fun scientificDomainErrors() {
        assertEquals("ERR:DOMAIN", CalculatorCore.applyScientific("-1", 0))
        assertEquals("ERR:DIVIDE_BY_ZERO", CalculatorCore.applyScientific("0", 2))
        assertEquals("ERR:DOMAIN", CalculatorCore.applyScientific("0", 6))
        assertEquals("ERR:OVERFLOW", CalculatorCore.applyScientific("inf", 1))
        assertEquals("ERR:INVALID_NUMBER", CalculatorCore.applyScientific("0x10", 1))
    }

    @Test
    fun largeAndTinyResultsUseExponentForm() {
        assertEquals("1.0000000000e12", CalculatorCore.formatScientific(1.0e12))
        assertEquals("-1.2345678900e-10", CalculatorCore.formatScientific(-1.23456789e-10))
        assertEquals("0.333333333333", CalculatorCore.formatScientific(1.0 / 3.0))
    }
}
