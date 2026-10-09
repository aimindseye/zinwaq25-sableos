package org.sableos.reader.input

import android.view.KeyEvent

/**
 * Activity-level key routing for the active reading surface. The activity offers every key event here *before*
 * views see it, so a WebView or a focused button cannot swallow Reader shortcuts; whether a key is ours is decided
 * by [ReaderKeyMap], which already lets an editable field keep its typing.
 *
 * Exactly one surface is registered at a time. A consumed key-down also consumes its matching key-up, and an
 * auto-repeat of a key whose initial press was consumed stays consumed even when the policy ignores the repeat (the
 * one-shot repeat guard), so it never leaks to a focused view as a second activation.
 */
class ReaderKeyDispatcher {
    private var handler: ((KeyInput) -> Boolean)? = null
    private val consumedKeyCodes = mutableSetOf<Int>()

    /** Registers [newHandler] and returns the function that unregisters it (a no-op if it was already replaced). */
    fun register(newHandler: (KeyInput) -> Boolean): () -> Unit {
        handler = newHandler
        return {
            if (handler === newHandler) {
                handler = null
                consumedKeyCodes.clear()
            }
        }
    }

    fun dispatch(event: KeyEvent): Boolean {
        val active = handler ?: return false
        return when (event.action) {
            KeyEvent.ACTION_UP -> consumedKeyCodes.remove(event.keyCode)
            KeyEvent.ACTION_DOWN -> {
                val input = KeyEventMapper.toKeyInput(event)
                val handled = input != null && active(input)
                if (handled) consumedKeyCodes += event.keyCode
                handled || (event.repeatCount > 0 && event.keyCode in consumedKeyCodes)
            }
            else -> false
        }
    }
}
