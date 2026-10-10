package org.sableos.calculator

/** What a hardware key does on the calculator keypad. */
internal sealed interface CalcKey {
    data class Digit(
        val digit: String,
    ) : CalcKey

    /** 0 +, 1 −, 2 ×, 3 ÷, the same codes the keypad and the native bridge use. */
    data class Operation(
        val code: Int,
    ) : CalcKey {
        companion object {
            const val ADD = 0
            const val SUBTRACT = 1
            const val MULTIPLY = 2
            const val DIVIDE = 3
        }
    }

    data object Decimal : CalcKey

    data object Equals : CalcKey

    data object Backspace : CalcKey

    data object Clear : CalcKey
}

/**
 * Hardware keyboard input for the keypad. Pure, so it is unit-tested on the JVM.
 *
 * Keyboards without a number row (Q25, Titan 2) put digits and operators on the Alt layer. The calculator has no
 * text field, so a plain key press also counts as its Alt character, the way a phone-style keypad works: on the
 * Q25 W E R give 1 2 3 without holding Alt. A printable plain character wins when it means something here, so a
 * number row or a numeric keypad behaves as printed.
 */
internal object CalculatorKeys {
    // android.view.KeyEvent key codes (stable public ABI).
    const val KEYCODE_ENTER = 66
    const val KEYCODE_DEL = 67
    const val KEYCODE_ESCAPE = 111
    const val KEYCODE_FORWARD_DEL = 112
    const val KEYCODE_NUMPAD_ENTER = 160

    fun resolve(
        keyCode: Int,
        plain: Char?,
        alt: Char?,
        ctrlOrMeta: Boolean,
    ): CalcKey? {
        // Shortcuts stay with the system and the app.
        if (ctrlOrMeta) return null
        return when (keyCode) {
            KEYCODE_ENTER, KEYCODE_NUMPAD_ENTER -> CalcKey.Equals
            KEYCODE_DEL -> CalcKey.Backspace
            KEYCODE_ESCAPE, KEYCODE_FORWARD_DEL -> CalcKey.Clear
            else -> plain?.let(::forChar) ?: alt?.let(::forChar)
        }
    }

    private fun forChar(c: Char): CalcKey? =
        when (c) {
            in '0'..'9' -> CalcKey.Digit(c.toString())
            '.', ',' -> CalcKey.Decimal
            '+' -> CalcKey.Operation(CalcKey.Operation.ADD)
            '-', '−' -> CalcKey.Operation(CalcKey.Operation.SUBTRACT)
            '*', '×' -> CalcKey.Operation(CalcKey.Operation.MULTIPLY)
            '/', '÷' -> CalcKey.Operation(CalcKey.Operation.DIVIDE)
            '=' -> CalcKey.Equals
            else -> null
        }
}
