package org.sableos.phone.core

/**
 * What a hardware key types on the dial screen and what it does elsewhere in Sable Phone, for a BlackBerry-style
 * keyboard such as the Q25's. Pure Kotlin on raw key codes and meta bits (the android.view.KeyEvent values), so the
 * host tests can check every case.
 *
 * The phone pad is the BlackBerry one: W E R / S D F / Z X C are 1-9. The key that carries 0, * and # differs by
 * keyboard, so the key's own number character from the key character map (KeyEvent.getNumber) is used too.
 */
object DialKeys {
    const val KEYCODE_CALL = 5
    const val KEYCODE_ENDCALL = 6
    const val KEYCODE_0 = 7
    const val KEYCODE_9 = 16
    const val KEYCODE_STAR = 17
    const val KEYCODE_POUND = 18
    const val KEYCODE_A = 29
    const val KEYCODE_Z = 54
    const val KEYCODE_SPACE = 62
    const val KEYCODE_ENTER = 66
    const val KEYCODE_DEL = 67
    const val KEYCODE_PLUS = 81
    const val KEYCODE_NUMPAD_0 = 144
    const val KEYCODE_NUMPAD_9 = 153
    const val KEYCODE_DPAD_CENTER = 23

    /** W E R S D F Z X C: 1-9 on a BlackBerry phone pad. */
    private val PHONE_PAD = intArrayOf(51, 33, 46, 47, 32, 34, 54, 52, 31)

    const val META_SHIFT_MASK = 0xc1
    const val META_ALT_MASK = 0x32
    const val META_SYM_ON = 0x4
    const val META_FUNCTION_ON = 0x8
    const val META_CTRL_MASK = 0x7000
    const val META_META_MASK = 0x70000

    /** Ctrl, Meta and Fn chords are shortcuts, never digits. */
    private const val SHORTCUT_META = META_FUNCTION_ON or META_CTRL_MASK or META_META_MASK

    /** Shift, Alt, Sym, Fn, Ctrl and Meta: a chord is never a speed dial or an in-call key. */
    const val CHORD_META = META_SHIFT_MASK or META_ALT_MASK or META_SYM_ON or SHORTCUT_META

    /**
     * The dial character [keyCode] types, or null. [unicodeChar] is what the key types with [metaState] (so Alt
     * gives the Alt layer), [numberChar] the key's number from the key character map (0 when it has none). On the
     * dial screen ([dialScreen]) a plain phone-pad letter is its digit; elsewhere only Alt makes it one.
     */
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    fun dialChar(
        keyCode: Int,
        unicodeChar: Int,
        numberChar: Int,
        metaState: Int,
        dialScreen: Boolean
    ): Char? {
        if (metaState and SHORTCUT_META != 0) return null
        val shift = metaState and META_SHIFT_MASK != 0
        val alt = metaState and META_ALT_MASK != 0
        return when {
            keyCode in KEYCODE_NUMPAD_0..KEYCODE_NUMPAD_9 -> '0' + (keyCode - KEYCODE_NUMPAD_0)

            !shift && keyCode in KEYCODE_0..KEYCODE_9 && unicodeChar == 0 ->
                '0' +
                    (keyCode - KEYCODE_0)

            keyCode == KEYCODE_STAR -> '*'

            keyCode == KEYCODE_POUND -> '#'

            keyCode == KEYCODE_PLUS -> '+'

            isDialChar(unicodeChar) -> unicodeChar.toChar()

            metaState and (META_SHIFT_MASK or META_SYM_ON) != 0 -> null

            alt || dialScreen -> padDigit(keyCode)
                ?: numberChar.takeIf { dialScreen && isDialChar(it) }?.toChar()

            else -> null
        }
    }

    /** True when a first press (not a repeat) on a list screen should open the dial pad with that digit. */
    fun opensDialPad(keyCode: Int, unicodeChar: Int, metaState: Int, repeatCount: Int): Boolean {
        if (repeatCount > 0) return false
        val c = dialChar(keyCode, unicodeChar, 0, metaState, dialScreen = false)
        return c != null && c != '+'
    }

    /** The letter a held plain A-Z key speed dials: its first auto-repeat, sent once the long-press time passed. */
    fun heldLetter(keyCode: Int, metaState: Int, repeatCount: Int): Char? =
        if (repeatCount != 1 || metaState and CHORD_META != 0) null else letterFor(keyCode)

    fun letterFor(keyCode: Int): Char? = if (keyCode in
        KEYCODE_A..KEYCODE_Z
    ) {
        'a' + (keyCode - KEYCODE_A)
    } else {
        null
    }

    private fun padDigit(keyCode: Int): Char? = PHONE_PAD.indexOf(keyCode).takeIf { it >= 0 }?.let {
        '1' +
            it
    }

    fun isDialChar(c: Int): Boolean =
        c in '0'.code..'9'.code || c == '*'.code || c == '#'.code || c == '+'.code

    /** The letters printed under a dial pad digit (ITU E.161), for the on-screen keys. */
    fun lettersFor(digit: Char): String = when (digit) {
        '2' -> "ABC"
        '3' -> "DEF"
        '4' -> "GHI"
        '5' -> "JKL"
        '6' -> "MNO"
        '7' -> "PQRS"
        '8' -> "TUV"
        '9' -> "WXYZ"
        '0' -> "+"
        else -> ""
    }
}
