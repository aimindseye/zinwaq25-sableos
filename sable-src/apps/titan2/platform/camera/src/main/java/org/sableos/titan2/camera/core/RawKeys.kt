package org.sableos.titan2.camera.core

/**
 * Raw Android key codes live here and nowhere else in the camera core. Everything downstream works
 * on core key names and [CameraAction]s, so the physical-key layer can be swapped or extended
 * without touching camera behaviour, and the table is JVM-testable. The numbers are the stable
 * `android.view.KeyEvent.KEYCODE_*` values.
 */
object RawKeys {
    private const val KEYCODE_BACK = 4
    private const val KEYCODE_DPAD_UP = 19
    private const val KEYCODE_DPAD_DOWN = 20
    private const val KEYCODE_DPAD_LEFT = 21
    private const val KEYCODE_DPAD_RIGHT = 22
    private const val KEYCODE_DPAD_CENTER = 23
    private const val KEYCODE_VOLUME_UP = 24
    private const val KEYCODE_VOLUME_DOWN = 25
    private const val KEYCODE_CAMERA = 27
    private const val KEYCODE_A = 29
    private const val KEYCODE_Z = 54
    private const val KEYCODE_TAB = 61
    private const val KEYCODE_SPACE = 62
    private const val KEYCODE_ENTER = 66
    private const val KEYCODE_MINUS = 69
    private const val KEYCODE_EQUALS = 70
    private const val KEYCODE_SLASH = 76
    private const val KEYCODE_PLUS = 81
    private const val KEYCODE_ESCAPE = 111
    private const val KEYCODE_NUMPAD_ENTER = 160

    private val named: Map<Int, String> = mapOf(
        KEYCODE_SPACE to "space",
        KEYCODE_ENTER to "enter",
        KEYCODE_NUMPAD_ENTER to "enter",
        KEYCODE_DPAD_CENTER to "enter",
        KEYCODE_CAMERA to "camera",
        KEYCODE_VOLUME_UP to "volume_up",
        KEYCODE_VOLUME_DOWN to "volume_down",
        KEYCODE_DPAD_UP to "up",
        KEYCODE_DPAD_DOWN to "down",
        KEYCODE_DPAD_LEFT to "left",
        KEYCODE_DPAD_RIGHT to "right",
        KEYCODE_TAB to "tab",
        KEYCODE_ESCAPE to "escape",
        KEYCODE_BACK to "back",
        KEYCODE_PLUS to "plus",
        KEYCODE_EQUALS to "plus",
        KEYCODE_MINUS to "minus",
        KEYCODE_SLASH to "slash"
    )

    /** Core-level key name for an Android key code, or null when the camera does not use it. */
    fun nameOf(keyCode: Int): String? = when (keyCode) {
        in KEYCODE_A..KEYCODE_Z -> ('a' + (keyCode - KEYCODE_A)).toString()
        else -> named[keyCode]
    }

    /** Every core key name this table can produce (for tests and the legend). */
    fun allNames(): Set<String> = named.values.toSet() + ('a'..'z').map { it.toString() }
}
