package org.sableos.messages.core

import android.view.KeyEvent

enum class ConversationCommand {
    None,
    Send,
    ReturnToLatest,
    Back,
    OlderPage,
    NewerPage,
}

/**
 * Keyboard model of a conversation (Q25: hardware QWERTY, no Ctrl or Fn, trackpad sends arrows).
 * Only KeyEvent constants are used, so this is plain JVM code under test.
 */
object ConversationKeys {
    fun command(
        keyCode: Int,
        shift: Boolean,
        atLatest: Boolean,
        draftBlank: Boolean,
    ): ConversationCommand =
        when (keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                // Shift+Enter is a newline; Enter on an empty draft does nothing.
                if (shift || draftBlank) ConversationCommand.None else ConversationCommand.Send
            }

            KeyEvent.KEYCODE_ESCAPE -> {
                if (atLatest) ConversationCommand.Back else ConversationCommand.ReturnToLatest
            }

            KeyEvent.KEYCODE_MOVE_END -> {
                // End inside a draft still moves the cursor once the view is at latest.
                if (atLatest) ConversationCommand.None else ConversationCommand.ReturnToLatest
            }

            KeyEvent.KEYCODE_PAGE_UP -> {
                ConversationCommand.OlderPage
            }

            KeyEvent.KEYCODE_PAGE_DOWN -> {
                ConversationCommand.NewerPage
            }

            else -> {
                ConversationCommand.None
            }
        }
}
