package org.sableos.browser.core

/** What a key does on the page while you are not typing in a field or the address bar. */
enum class BrowserCommand {
    Address,
    PageDown,
    PageUp,
    Top,
    Bottom,
    Reload,
    Back,
    Forward,
    ZoomIn,
    ZoomOut,
    TogglePointer,
    Share,
}

/**
 * BB10-style browser keys: L address, Space page down (Shift+Space up), T top, B bottom, R reload,
 * Backspace back (Shift+Backspace forward), I and O zoom, M pointer on or off, S share. Alt,
 * Ctrl or Meta held means the key is not a browser key. Pure.
 */
object BrowserKeys {
    private const val KEYCODE_B = 30
    private const val KEYCODE_I = 37
    private const val KEYCODE_L = 40
    private const val KEYCODE_M = 41
    private const val KEYCODE_O = 43
    private const val KEYCODE_R = 46
    private const val KEYCODE_S = 47
    private const val KEYCODE_T = 48
    private const val KEYCODE_SPACE = 62
    private const val KEYCODE_DEL = 67

    private val PLAIN =
        mapOf(
            KEYCODE_L to BrowserCommand.Address,
            KEYCODE_SPACE to BrowserCommand.PageDown,
            KEYCODE_T to BrowserCommand.Top,
            KEYCODE_B to BrowserCommand.Bottom,
            KEYCODE_R to BrowserCommand.Reload,
            KEYCODE_DEL to BrowserCommand.Back,
            KEYCODE_I to BrowserCommand.ZoomIn,
            KEYCODE_O to BrowserCommand.ZoomOut,
            KEYCODE_M to BrowserCommand.TogglePointer,
            KEYCODE_S to BrowserCommand.Share,
        )
    private val SHIFTED =
        mapOf(
            KEYCODE_SPACE to BrowserCommand.PageUp,
            KEYCODE_DEL to BrowserCommand.Forward,
        )

    fun command(
        keyCode: Int,
        shift: Boolean,
        otherModifier: Boolean,
    ): BrowserCommand? =
        when {
            otherModifier -> null
            shift -> SHIFTED[keyCode]
            else -> PLAIN[keyCode]
        }

    /** A trackpad stroke or arrow key: the pointer's direction as (dx, dy), each -1, 0 or 1. */
    fun arrow(keyCode: Int): Pair<Int, Int>? =
        when (keyCode) {
            KEYCODE_DPAD_UP -> 0 to -1
            KEYCODE_DPAD_DOWN -> 0 to 1
            KEYCODE_DPAD_LEFT -> -1 to 0
            KEYCODE_DPAD_RIGHT -> 1 to 0
            else -> null
        }

    /** The trackpad's click, the D-pad centre or Enter: presses at the pointer. */
    fun isClick(keyCode: Int): Boolean = keyCode in CLICK_KEYS

    private const val KEYCODE_DPAD_UP = 19
    private const val KEYCODE_DPAD_DOWN = 20
    private const val KEYCODE_DPAD_LEFT = 21
    private const val KEYCODE_DPAD_RIGHT = 22
    private val CLICK_KEYS = setOf(23, 66, 160)
}
