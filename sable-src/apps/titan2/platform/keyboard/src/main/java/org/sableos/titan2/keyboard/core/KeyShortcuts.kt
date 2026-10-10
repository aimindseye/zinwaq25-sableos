package org.sableos.titan2.keyboard.core

/**
 * Hardware-keyboard shortcuts that act on the IME itself rather than on text. Pure, so they are unit-tested on a
 * JVM; the constants mirror android.view.KeyEvent and android.view.inputmethod.EditorInfo (stable public ABI).
 */
object KeyShortcuts {
    const val KEYCODE_1 = 8
    const val KEYCODE_2 = 9
    const val KEYCODE_3 = 10
    const val KEYCODE_E = 33
    const val KEYCODE_R = 46
    const val KEYCODE_W = 51
    const val IME_MASK_ACTION = 0x000000ff
    const val IME_ACTION_SEND = 4

    private val CANDIDATE_KEYS = mapOf(
        KEYCODE_1 to 0,
        KEYCODE_2 to 1,
        KEYCODE_3 to 2,
        // W E R carry 1 2 3 on the Alt layer of phone-pad keyboards (Q25, Titan 2), so Ctrl needs no Alt.
        KEYCODE_W to 0,
        KEYCODE_E to 1,
        KEYCODE_R to 2
    )

    /**
     * Ctrl+1/2/3 (or Ctrl+W/E/R) picks candidate 1-3. Only while that candidate is showing: otherwise the key stays
     * an ordinary app shortcut (Ctrl+W, Ctrl+R in a browser).
     */
    fun candidateIndex(
        keyCode: Int,
        ctrl: Boolean,
        otherModifier: Boolean,
        candidateCount: Int
    ): Int? {
        if (!ctrl || otherModifier) return null
        return CANDIDATE_KEYS[keyCode]?.takeIf { it < candidateCount }
    }

    /**
     * Enter performs the field's Send action when the user turned this on and the app declares Send for the field.
     * Shift+Enter or Alt+Enter keep the platform's Enter (a new line in a multi-line message box); Ctrl and Meta
     * combinations stay app shortcuts.
     */
    fun enterSends(
        enabled: Boolean,
        imeOptions: Int?,
        shift: Boolean,
        alt: Boolean,
        ctrlOrMeta: Boolean
    ): Boolean = enabled && imeOptions != null && !shift && !alt && !ctrlOrMeta &&
        imeOptions and IME_MASK_ACTION == IME_ACTION_SEND
}
