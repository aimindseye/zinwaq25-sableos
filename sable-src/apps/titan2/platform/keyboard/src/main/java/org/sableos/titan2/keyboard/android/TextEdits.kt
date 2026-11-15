package org.sableos.titan2.keyboard.android

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import org.sableos.titan2.keyboard.core.WordEdit

/** Text edits shared by the physical chords and the on-screen gestures. [ic] is read at call time. */
class TextEdits(private val ic: () -> InputConnection?) {
    /** Deletes the last word (and the spaces after it) before the cursor. */
    fun deleteWord() {
        val c = ic() ?: return
        val before = c.getTextBeforeCursor(WINDOW, 0)?.toString().orEmpty()
        val n = WordEdit.lastWordLength(before)
        if (n > 0) c.deleteSurroundingText(n, 0)
    }

    /** Moves the cursor by [steps] characters; negative is left. */
    fun moveCursor(steps: Int) {
        val c = ic() ?: return
        val key = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(steps)) {
            c.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
            c.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
        }
    }

    /** A space typed after "word ": replaces that space with ". ". True when it did. */
    fun doubleSpacePeriod(): Boolean {
        val c = ic() ?: return false
        val before = c.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        val hit = WordEdit.endsWordSpace(before)
        if (hit) {
            c.deleteSurroundingText(1, 0)
            c.commitText(". ", 1)
        }
        return hit
    }

    private companion object {
        const val WINDOW = 64
    }
}
