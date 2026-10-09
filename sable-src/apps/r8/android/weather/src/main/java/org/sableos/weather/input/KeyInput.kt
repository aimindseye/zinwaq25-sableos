package org.sableos.weather.input

// Ported from sable-src/reference/sable-start-type-to-find (P1 normalized keys) into canonical Weather.

/** Normalized key meaning. Device scan codes and device names never reach this layer. */
enum class KeyKind {
    Up,
    Down,
    Left,
    Right,
    Activate,
    Space,
    Back,
    Escape,
    MoveHome,
    MoveEnd,
    PageUp,
    PageDown,
    Tab,
    Backspace,
    Printable,
    Other
}

/**
 * One normalized key event. [char] is the character the platform key map already produced for the active
 * layout (so Alt/Sym composition is the keyboard's business), and [editableFocused] says that a text field owns
 * focus, in which case the model leaves text keys to the normal InputConnection/IME path.
 */
data class KeyInput(
    val kind: KeyKind,
    val char: Char = NO_CHAR,
    val shift: Boolean = false,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false,
    val repeat: Boolean = false,
    val editableFocused: Boolean = false
) {
    val hasCommandModifier: Boolean get() = ctrl || meta

    companion object {
        const val NO_CHAR = '\u0000'
    }
}

/** A platform key event reduced to integers so the mapper stays pure (no framework types). */
data class RawKey(val keyCode: Int, val unicode: Int, val metaState: Int, val repeat: Boolean)

/**
 * Maps public Android key codes and meta flags to [KeyInput]. Only documented, device-independent codes appear
 * here: no scan codes, no per-device tables, no Build.* values.
 */
object KeyMapper {
    const val KEYCODE_HOME = 3
    const val KEYCODE_BACK = 4
    const val KEYCODE_DPAD_UP = 19
    const val KEYCODE_DPAD_DOWN = 20
    const val KEYCODE_DPAD_LEFT = 21
    const val KEYCODE_DPAD_RIGHT = 22
    const val KEYCODE_DPAD_CENTER = 23
    const val KEYCODE_PAGE_UP = 92
    const val KEYCODE_PAGE_DOWN = 93
    const val KEYCODE_TAB = 61
    const val KEYCODE_SPACE = 62
    const val KEYCODE_ENTER = 66
    const val KEYCODE_DEL = 67
    const val KEYCODE_ESCAPE = 111
    const val KEYCODE_MOVE_HOME = 122
    const val KEYCODE_MOVE_END = 123
    const val KEYCODE_NUMPAD_ENTER = 160
    const val META_SHIFT_ON = 0x1
    const val META_ALT_ON = 0x2
    const val META_CTRL_ON = 0x1000
    const val META_META_ON = 0x10000
    private const val COMBINING_ACCENT_MASK = 0x80000000.toInt()

    fun map(raw: RawKey, editableFocused: Boolean = false): KeyInput {
        val kind = kindOf(raw)
        val char = if (kind == KeyKind.Printable) raw.unicode.toChar() else KeyInput.NO_CHAR
        return KeyInput(
            kind = kind,
            char = char,
            shift = raw.metaState and META_SHIFT_ON != 0,
            ctrl = raw.metaState and META_CTRL_ON != 0,
            alt = raw.metaState and META_ALT_ON != 0,
            meta = raw.metaState and META_META_ON != 0,
            repeat = raw.repeat,
            editableFocused = editableFocused
        )
    }

    private fun kindOf(raw: RawKey): KeyKind = when (raw.keyCode) {
        KEYCODE_DPAD_UP -> KeyKind.Up
        KEYCODE_DPAD_DOWN -> KeyKind.Down
        KEYCODE_DPAD_LEFT -> KeyKind.Left
        KEYCODE_DPAD_RIGHT -> KeyKind.Right
        KEYCODE_DPAD_CENTER, KEYCODE_ENTER, KEYCODE_NUMPAD_ENTER -> KeyKind.Activate
        KEYCODE_SPACE -> KeyKind.Space
        KEYCODE_BACK -> KeyKind.Back
        KEYCODE_ESCAPE -> KeyKind.Escape
        KEYCODE_HOME -> KeyKind.Other
        KEYCODE_MOVE_HOME -> KeyKind.MoveHome
        KEYCODE_MOVE_END -> KeyKind.MoveEnd
        KEYCODE_PAGE_UP -> KeyKind.PageUp
        KEYCODE_PAGE_DOWN -> KeyKind.PageDown
        KEYCODE_TAB -> KeyKind.Tab
        KEYCODE_DEL -> KeyKind.Backspace
        else -> if (isPrintable(raw.unicode)) KeyKind.Printable else KeyKind.Other
    }

    private fun isPrintable(unicode: Int): Boolean =
        unicode > 0 && unicode and COMBINING_ACCENT_MASK == 0 && unicode <= Char.MAX_VALUE.code &&
            !unicode.toChar().isISOControl() && !unicode.toChar().isWhitespace()
}
