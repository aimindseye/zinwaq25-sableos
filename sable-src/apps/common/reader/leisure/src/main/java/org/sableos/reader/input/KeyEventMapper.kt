package org.sableos.reader.input

import android.view.KeyEvent

/**
 * Maps Android key events to platform-independent [KeyInput]s. The Reader's keys are a surface subset (consumer) of
 * the canonical normalized-key contract (platform_sable docs/NORMALIZED_KEY_INPUT_CONTRACT.md @ 80e3097b): D-pad
 * centre, Enter and keypad Enter are Activate. Only those semantic keys are mapped; volume, media, Back and every
 * unlisted key (including KEYCODE_MOVE_HOME/MOVE_END and KEYCODE_HOME) return null so the editor/platform keeps them
 * (the Reader never hijacks volume keys, invents Home/End behaviour, or turns system Home into a Reader command).
 * Back is deliberately not mapped: it travels through the normal back dispatcher.
 */
object KeyEventMapper {
    fun toKeyInput(event: KeyEvent): KeyInput? {
        val key = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> ReaderKey.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> ReaderKey.RIGHT
            KeyEvent.KEYCODE_DPAD_UP -> ReaderKey.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> ReaderKey.DOWN
            KeyEvent.KEYCODE_PAGE_UP -> ReaderKey.PAGE_UP
            KeyEvent.KEYCODE_PAGE_DOWN -> ReaderKey.PAGE_DOWN
            KeyEvent.KEYCODE_SPACE -> ReaderKey.SPACE
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> ReaderKey.ACTIVATE
            KeyEvent.KEYCODE_MENU -> ReaderKey.MENU
            KeyEvent.KEYCODE_ESCAPE -> ReaderKey.ESCAPE
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> ReaderKey.CHARACTER
            else -> return null
        }
        val character = if (key == ReaderKey.CHARACTER) event.unicodeChar.takeIf { it != 0 }?.toChar() else null
        return KeyInput(
            key = key,
            character = character,
            shift = event.isShiftPressed,
            ctrl = event.isCtrlPressed,
            alt = event.isAltPressed,
            meta = event.isMetaPressed,
            repeated = event.repeatCount > 0,
        )
    }
}
