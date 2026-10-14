package org.sableos.media

import android.view.KeyEvent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged

/**
 * Which text field, if any, has focus. Single-key shortcuts are off while one does
 * (TEXT_INPUT_ALWAYS_WINS / SINGLE_KEY_SHORTCUTS_DISABLED_WHILE_TYPING).
 */
internal class MediaTextInputFocus {
    private var owner: Any? = null

    val active: Boolean
        get() = owner != null

    fun update(
        token: Any,
        focused: Boolean,
    ) {
        if (focused) {
            owner = token
        } else if (owner === token) {
            owner = null
        }
    }
}

internal val LocalMediaTextInputFocus = staticCompositionLocalOf { MediaTextInputFocus() }

/** Marks a text field so that typing in it never triggers a Media shortcut. */
internal fun Modifier.mediaTextInput(): Modifier =
    composed {
        val focus = LocalMediaTextInputFocus.current
        val token = remember { Any() }
        DisposableEffect(focus, token) {
            onDispose { focus.update(token, focused = false) }
        }
        onFocusChanged { focus.update(token, it.isFocused) }
    }

internal fun KeyEvent.toMediaKeyPress(): MediaKeyPress {
    val unicode = getUnicodeChar(metaState)
    return MediaKeyPress(
        keyCode = keyCode,
        char = if (unicode > 0) unicode.toChar() else null,
        shift = isShiftPressed,
        alt = isAltPressed,
        ctrl = isCtrlPressed || isMetaPressed,
        repeatCount = repeatCount,
    )
}
