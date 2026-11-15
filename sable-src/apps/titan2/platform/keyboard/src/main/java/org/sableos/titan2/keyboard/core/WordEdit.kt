package org.sableos.titan2.keyboard.core

/** Pure text rules behind Alt+Backspace, the on-screen swipe-to-delete and double-space. */
object WordEdit {
    const val KEYCODE_DEL = 67
    const val KEYCODE_I = 37

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '’'

    /**
     * How many characters to delete to remove the last word: the spaces before the cursor and the word (or the run
     * of punctuation) in front of them.
     */
    fun lastWordLength(before: String): Int {
        var i = before.length
        while (i > 0 && before[i - 1].isWhitespace()) i--
        if (i > 0) {
            val word = isWordChar(before[i - 1])
            while (i > 0 && !before[i - 1].isWhitespace() && isWordChar(before[i - 1]) == word) i--
        }
        return before.length - i
    }

    /** A letter or digit followed by one space: a second space turns that into ". ". */
    fun endsWordSpace(before: String): Boolean =
        before.length >= 2 && before.last() == ' ' && before[before.length - 2].isLetterOrDigit()

    /** Alt+Backspace (Ctrl+Backspace where there is a Ctrl) deletes the last word. */
    fun deletesWord(keyCode: Int, alt: Boolean, shift: Boolean, ctrl: Boolean): Boolean =
        keyCode == KEYCODE_DEL && (alt || ctrl) && !shift

    /** Alt+Shift+I turns incognito mode (no learning, no clipboard history) on or off. */
    fun togglesIncognito(keyCode: Int, alt: Boolean, shift: Boolean, ctrlOrMeta: Boolean): Boolean =
        keyCode == KEYCODE_I && alt && shift && !ctrlOrMeta
}
