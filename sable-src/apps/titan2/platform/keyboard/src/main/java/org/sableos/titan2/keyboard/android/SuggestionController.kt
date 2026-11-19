package org.sableos.titan2.keyboard.android

import android.content.Context
import android.os.Handler
import android.os.SystemClock
import android.view.inputmethod.InputConnection
import org.sableos.titan2.keyboard.core.Suggestion
import org.sableos.titan2.keyboard.core.SuggestionKind
import org.sableos.titan2.keyboard.core.UserWords
import org.sableos.titan2.keyboard.core.WordLearner
import org.sableos.titan2.keyboard.core.WordList
import org.sableos.titan2.keyboard.core.WordSuggester

/** What the service knows about the field the suggestions are for. */
data class SuggestionField(
    /** Suggestions are wanted here: a prose field, no selection, a typing language that types letters directly. */
    val allowed: Boolean,
    /** The typing language's tag, for choosing its word list. */
    val languageTag: String,
    /** Words and phrases may be learned from this field. */
    val learn: Boolean
)

/**
 * Autocomplete, spelling, emoji and next-word suggestions for the word before the cursor, and learning of the user's
 * own words and phrases. It reads the editor's text rather than key events, so the physical keys, the on-screen
 * keyboard and a moved cursor all go through [update]. Holds the current [suggestions]; [onChange] runs when they or
 * the strip hold change.
 */
class SuggestionController(
    private val context: Context,
    private val ui: Handler,
    private val onChange: () -> Unit
) {
    var suggestions: List<Suggestion> = emptyList()
        private set

    private var lastBefore = ""
    private var holdUntil = 0L
    private val holdEnd = Runnable { onChange() }
    private val saveLater = Runnable { UserWordsStore.save(context) }

    /** The strip stays up a moment after the last suggestion so the app does not resize between words. */
    val holdOpen: Boolean get() = suggestions.isEmpty() && SystemClock.uptimeMillis() < holdUntil

    fun update(ic: InputConnection?, field: SuggestionField) {
        // Diagnostic (SableSuggest): the gates the keyboard passed in. No typed text.
        android.util.Log.d(
            "SableSuggest",
            "update allowed=${field.allowed} lang=${field.languageTag} " +
                "learn=${field.learn} ic=${ic != null}"
        )
        val old = suggestions
        suggestions = if (ic != null && field.allowed) compute(ic, field) else forget()
        if (suggestions.isNotEmpty()) {
            holdUntil = SystemClock.uptimeMillis() + HOLD_MS
            ui.removeCallbacks(holdEnd)
            ui.postDelayed(holdEnd, HOLD_MS + HOLD_SLACK_MS)
        }
        if (suggestions != old) onChange()
    }

    private fun forget(): List<Suggestion> {
        lastBefore = ""
        return emptyList()
    }

    /** Field changed or closed: drop everything and write what was learned. */
    fun reset() {
        suggestions = emptyList()
        lastBefore = ""
        holdUntil = 0L
        ui.removeCallbacks(holdEnd)
        ui.removeCallbacks(saveLater)
        UserWordsStore.save(context)
    }

    private fun compute(ic: InputConnection, field: SuggestionField): List<Suggestion> {
        val lang = Dictionaries.languageOf(field.languageTag)
        val onReady = {
            ui.post { update(ic, field) }
            Unit
        }
        val words = lang?.let { Dictionaries.words(context, it, onReady) }
        val emoji = Dictionaries.emoji(context, onReady)
        val user = lang?.let { UserWordsStore.forLanguage(context, it) }
        val before = ic.getTextBeforeCursor(CONTEXT_CHARS, 0)?.toString().orEmpty()
        if (field.learn && user != null) learn(user, words, before)
        lastBefore = before
        val midWord = ic.getTextAfterCursor(1, 0)?.firstOrNull()?.isLetterOrDigit() == true
        val result = if ((words == null && emoji == null) || midWord) {
            emptyList()
        } else {
            runCatching {
                WordSuggester(words, emoji, user).suggest(before)
            }.getOrDefault(emptyList())
        }
        // Diagnostic (SableSuggest): why a list is empty. Lengths and flags only, never text.
        android.util.Log.d(
            "SableSuggest",
            "compute lang=$lang words=${words != null} emoji=${emoji != null} " +
                "user=${user != null} before=${before.length} midWord=$midWord " +
                "result=${result.size}"
        )
        return result
    }

    private fun learn(user: UserWords, words: WordList?, before: String) {
        val done = WordLearner.finished(lastBefore, before)
        if (done != null && words != null) {
            if (!words.contains(done.word)) user.learnWord(done.word)
            done.previous?.let { user.learnPair(it, done.word) }
            ui.removeCallbacks(saveLater)
            ui.postDelayed(saveLater, SAVE_DELAY_MS)
        }
    }

    /** Takes suggestion [i], replacing what was typed of the word; false when there is none. */
    fun accept(ic: InputConnection?, i: Int, field: SuggestionField): Boolean {
        val s = suggestions.getOrNull(i)
        if (s == null || ic == null) return false
        val before = ic.getTextBeforeCursor(CONTEXT_CHARS, 0)?.toString().orEmpty()
        ic.beginBatchEdit()
        ic.deleteSurroundingText(s.replaces, 0)
        ic.commitText(WordSuggester.accept(s), 1)
        ic.endBatchEdit()
        suggestions = emptyList()
        // the text grew by more than one character, so it is not read as a finished word; the phrase is counted here
        lastBefore = ic.getTextBeforeCursor(CONTEXT_CHARS, 0)?.toString().orEmpty()
        if (field.learn &&
            s.kind != SuggestionKind.Emoji
        ) {
            learnAccepted(field.languageTag, before, s)
        }
        onChange()
        return true
    }

    private fun learnAccepted(tag: String, before: String, s: Suggestion) {
        val lang = Dictionaries.languageOf(tag)
        val previous = WordLearner.wordBefore(before.dropLast(s.replaces))
        if (lang != null && previous != null) {
            UserWordsStore.forLanguage(context, lang).learnPair(previous, s.text)
            ui.removeCallbacks(saveLater)
            ui.postDelayed(saveLater, SAVE_DELAY_MS)
        }
    }

    private companion object {
        const val HOLD_MS = 2000L
        const val HOLD_SLACK_MS = 50L
        const val CONTEXT_CHARS = 40
        const val SAVE_DELAY_MS = 10_000L
    }
}
