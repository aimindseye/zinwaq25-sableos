package org.sableos.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorKeysTest {
    private fun key(
        plain: Char?,
        alt: Char? = null,
        keyCode: Int = 0,
        ctrl: Boolean = false,
    ) = CalculatorKeys.resolve(keyCode, plain, alt, ctrl)

    @Test fun numberRowAndKeypadDigitsWorkAsPrinted() {
        assertEquals(CalcKey.Digit("7"), key('7', '&'))
        assertEquals(CalcKey.Operation(CalcKey.Operation.ADD), key('+'))
        assertEquals(CalcKey.Operation(CalcKey.Operation.DIVIDE), key('/'))
        assertEquals(CalcKey.Decimal, key('.'))
    }

    @Test fun lettersUseTheirAltDigitWithoutHoldingAlt() {
        // Q25 / BlackBerry phone pad: W E R = 1 2 3, the mic key = 0.
        assertEquals(CalcKey.Digit("1"), key('w', '1'))
        assertEquals(CalcKey.Digit("3"), key('r', '3'))
        assertEquals(CalcKey.Digit("0"), key(null, '0'))
        // A letter is never an operator by itself: X is 8 on the phone pad, not multiply.
        assertEquals(CalcKey.Digit("8"), key('x', '8'))
        assertNull(key('q', null))
    }

    @Test fun editingKeys() {
        assertEquals(CalcKey.Equals, key(null, keyCode = CalculatorKeys.KEYCODE_ENTER))
        assertEquals(CalcKey.Equals, key(null, keyCode = CalculatorKeys.KEYCODE_NUMPAD_ENTER))
        assertEquals(CalcKey.Equals, key('='))
        assertEquals(CalcKey.Backspace, key(null, keyCode = CalculatorKeys.KEYCODE_DEL))
        assertEquals(CalcKey.Clear, key(null, keyCode = CalculatorKeys.KEYCODE_ESCAPE))
    }

    @Test fun shortcutsAreLeftAlone() {
        assertNull(key('c', '9', ctrl = true))
        assertNull(key(null, keyCode = CalculatorKeys.KEYCODE_ENTER, ctrl = true))
    }
}
