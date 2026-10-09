package org.sableos.titan2.keyboard.android

import android.view.KeyEvent
import org.sableos.titan2.keyboard.core.Key
import org.sableos.titan2.keyboard.core.Mod

/**
 * Standard Android keycodes only. Device-specific keys (Fn, Func1/2, programmable key, touch-keyboard gestures)
 * are deliberately NOT mapped here until their scan codes are captured on each device; they arrive as [Key.Other]
 * and pass through untouched.
 */
object KeyMapper {
    private val punct = mapOf(
        KeyEvent.KEYCODE_COMMA to ',',
        KeyEvent.KEYCODE_PERIOD to '.',
        KeyEvent.KEYCODE_SLASH to '/',
        KeyEvent.KEYCODE_SEMICOLON to ';',
        KeyEvent.KEYCODE_APOSTROPHE to '\'', KeyEvent.KEYCODE_MINUS to '-',
        KeyEvent.KEYCODE_EQUALS to '=',
        KeyEvent.KEYCODE_LEFT_BRACKET to '[', KeyEvent.KEYCODE_RIGHT_BRACKET to ']',
        KeyEvent.KEYCODE_BACKSLASH to '\\', KeyEvent.KEYCODE_GRAVE to '`'
    )

    private val special: Map<Int, Key> = mapOf(
        KeyEvent.KEYCODE_SHIFT_LEFT to Key.Modifier(Mod.Shift),
        KeyEvent.KEYCODE_SHIFT_RIGHT to Key.Modifier(Mod.Shift),
        KeyEvent.KEYCODE_ALT_LEFT to Key.Modifier(Mod.Alt),
        KeyEvent.KEYCODE_ALT_RIGHT to Key.Modifier(Mod.Alt),
        KeyEvent.KEYCODE_SYM to Key.Modifier(Mod.Sym),
        KeyEvent.KEYCODE_CTRL_LEFT to Key.Ctrl,
        KeyEvent.KEYCODE_CTRL_RIGHT to Key.Ctrl,
        KeyEvent.KEYCODE_SPACE to Key.Space,
        KeyEvent.KEYCODE_ENTER to Key.Enter,
        KeyEvent.KEYCODE_NUMPAD_ENTER to Key.Enter,
        KeyEvent.KEYCODE_DEL to Key.Backspace,
        KeyEvent.KEYCODE_FORWARD_DEL to Key.Delete,
        KeyEvent.KEYCODE_TAB to Key.Tab,
        KeyEvent.KEYCODE_ESCAPE to Key.Escape,
        KeyEvent.KEYCODE_DPAD_UP to Key.Up,
        KeyEvent.KEYCODE_DPAD_DOWN to Key.Down,
        KeyEvent.KEYCODE_DPAD_LEFT to Key.Left,
        KeyEvent.KEYCODE_DPAD_RIGHT to Key.Right,
        KeyEvent.KEYCODE_MOVE_HOME to Key.Home,
        KeyEvent.KEYCODE_MOVE_END to Key.End,
        KeyEvent.KEYCODE_PAGE_UP to Key.PageUp,
        KeyEvent.KEYCODE_PAGE_DOWN to Key.PageDown
    )

    fun map(code: Int): Key = when (code) {
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> Key.Letter('a' + (code - KeyEvent.KEYCODE_A))
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> Key.Digit('0' + (code - KeyEvent.KEYCODE_0))
        else -> special[code] ?: punct[code]?.let { Key.Punct(it) } ?: Key.Other(code)
    }

    private val keyCodes: Map<Key, Int> = mapOf(
        Key.Up to KeyEvent.KEYCODE_DPAD_UP,
        Key.Down to KeyEvent.KEYCODE_DPAD_DOWN,
        Key.Left to KeyEvent.KEYCODE_DPAD_LEFT,
        Key.Right to KeyEvent.KEYCODE_DPAD_RIGHT,
        Key.Home to KeyEvent.KEYCODE_MOVE_HOME,
        Key.End to KeyEvent.KEYCODE_MOVE_END,
        Key.PageUp to KeyEvent.KEYCODE_PAGE_UP,
        Key.PageDown to KeyEvent.KEYCODE_PAGE_DOWN,
        Key.Tab to KeyEvent.KEYCODE_TAB,
        Key.Escape to KeyEvent.KEYCODE_ESCAPE,
        Key.Delete to KeyEvent.KEYCODE_FORWARD_DEL,
        Key.Backspace to KeyEvent.KEYCODE_DEL,
        Key.Enter to KeyEvent.KEYCODE_ENTER
    )

    /**
     * Android keycode for a core navigation key, for
     * [android.inputmethodservice.InputMethodService.sendDownUpKeyEvents].
     */
    fun toKeyCode(k: Key): Int? = keyCodes[k]
}
