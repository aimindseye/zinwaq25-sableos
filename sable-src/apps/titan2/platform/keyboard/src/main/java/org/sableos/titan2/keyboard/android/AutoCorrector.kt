package org.sableos.titan2.keyboard.android

import android.content.Context
import android.view.inputmethod.InputConnection
import org.sableos.titan2.keyboard.core.AutoCorrect
import org.sableos.titan2.keyboard.core.Correction

/**
 * Applies [AutoCorrect] when Space is about to be typed and remembers the last change so Backspace right after it can
 * put back exactly what the user typed. Undoing teaches the keyboard the word is the user's own, so it is not
 * corrected again. Only for languages whose list is a full dictionary (not the small Indic lists).
 */
class AutoCorrector(private val context: Context) {
    private var last: Correction? = null

    /** Forget the last change: any other key ends the chance to undo it. */
    fun clear() {
        last = null
    }

    /**
     * Corrects the word before the cursor ahead of the Space that follows. [skip] is true when something else
     * (AutoText) is about to change the same word. Returns whether it changed anything.
     */
    fun onSpace(ic: InputConnection, field: SuggestionField, skip: (String) -> Boolean): Boolean {
        last = null
        val fix = correction(ic, field, skip)
        fix?.let { replace(ic, it) }
        return fix != null
    }

    private fun correction(
        ic: InputConnection,
        field: SuggestionField,
        skip: (String) -> Boolean
    ): Correction? {
        val lang = Dictionaries.languageOf(field.languageTag)
            ?.takeIf { it in Dictionaries.FULL_LANGUAGES }
        val words = lang?.let { Dictionaries.words(context, it) }
        val before = ic.getTextBeforeCursor(CONTEXT_CHARS, 0)?.toString().orEmpty()
        val midWord = ic.getTextAfterCursor(1, 0)?.firstOrNull()?.isLetterOrDigit() == true
        val ready = field.allowed && !midWord && !skip(before)
        return if (lang != null && words != null && ready) {
            AutoCorrect.fix(before, words, UserWordsStore.forLanguage(context, lang))
        } else {
            null
        }
    }

    private fun replace(ic: InputConnection, fix: Correction) {
        ic.beginBatchEdit()
        ic.deleteSurroundingText(fix.typed.length, 0)
        ic.commitText(fix.replacement, 1)
        ic.endBatchEdit()
        last = fix
    }

    /** Backspace straight after a correction and its Space: restores the typed word. True when it did. */
    fun undo(ic: InputConnection, field: SuggestionField): Boolean {
        val fix = last
        last = null
        val before = ic.getTextBeforeCursor(CONTEXT_CHARS, 0)?.toString().orEmpty()
        val undoable = fix != null && before.endsWith(fix.replacement + " ")
        if (undoable && fix != null) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(fix.replacement.length + 1, 0)
            ic.commitText(fix.typed, 1)
            ic.endBatchEdit()
            Dictionaries.languageOf(field.languageTag)?.let { lang ->
                UserWordsStore.forLanguage(context, lang).apply {
                    learnWord(fix.typed)
                    learnWord(fix.typed)
                }
            }
        }
        return undoable
    }

    private companion object {
        const val CONTEXT_CHARS = 40
    }
}
