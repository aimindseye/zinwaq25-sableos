package org.sableos.titan2.keyboard.core

/** What a suggestion is, for ordering and for how accepting it edits the text. */
enum class SuggestionKind { Completion, Correction, Emoji }

/**
 * A suggestion for the word being typed. Accepting it replaces the [replaces] characters before the cursor with
 * [text] (and a following space for a word, see [WordSuggester.accept]).
 */
data class Suggestion(val text: String, val replaces: Int, val kind: SuggestionKind)

/**
 * Autocomplete, spelling suggestions and emoji for the word before the cursor. Stateless: it works from the text
 * the editor reports, so it behaves the same for the physical keys, the on-screen keyboard and a cursor moved by
 * hand, and it stores nothing the user types.
 */
class WordSuggester(private val words: WordList?, private val emoji: EmojiIndex?) {

    /** The partial word ending at the cursor, or null when the cursor is not at the end of a word. */
    fun currentWord(before: String): String? = Companion.currentWord(before)

    fun suggest(before: String): List<Suggestion> {
        val token = tokenBefore(before) ?: return emptyList()
        if (token.startsWith(':')) return shortcodeEmoji(token)
        val word = token.replace('’', '\'')
        if (word.isEmpty() || word.any { !it.isLetter() && it != '\'' }) return emptyList()
        val typed = token.length
        val wordSugg = wordSuggestions(word).map { Suggestion(it, typed, SuggestionKind.Completion) }
        val fixes = if (words != null && !words.contains(word)) {
            words.corrections(word, MAX_WORDS).map { Suggestion(matchCase(word, it), typed, SuggestionKind.Correction) }
        } else {
            emptyList()
        }
        val emojis = if (word.length >= MIN_EMOJI_WORD) {
            emoji?.forWord(word, MAX_EMOJI).orEmpty().map { Suggestion(it, typed, SuggestionKind.Emoji) }
        } else {
            emptyList()
        }
        val ordered = (fixes + wordSugg).distinctBy { it.text }.take(MAX_WORDS)
        return ordered.take(EMOJI_SLOT) + emojis.take(1) + ordered.drop(EMOJI_SLOT) + emojis.drop(1)
    }

    private fun wordSuggestions(word: String): List<String> {
        val list = words ?: return emptyList()
        return list.complete(word, MAX_WORDS + 1).map { matchCase(word, it) }.filter { !it.equals(word, true) }
    }

    private fun shortcodeEmoji(token: String): List<Suggestion> {
        val name = token.drop(1)
        if (name.length < MIN_SHORTCODE || name.any { !(it.isLetterOrDigit() || it == '_' || it == '+' || it == '-') }) {
            return emptyList()
        }
        return emoji?.search(name.replace('_', ' ').trim().replace(' ', '_'), MAX_EMOJI * 2).orEmpty()
            .map { Suggestion(it, token.length, SuggestionKind.Emoji) }
    }

    /** Gives [candidate] the capitalisation the user is typing in: Word, WORD or word. */
    private fun matchCase(typed: String, candidate: String): String = when {
        typed.length > 1 && typed.all { it.isUpperCase() || it == '\'' } -> candidate.uppercase()
        typed.first().isUpperCase() -> candidate.replaceFirstChar { it.uppercase() }
        else -> candidate
    }

    companion object {
        const val MAX_WORDS = 4
        const val MAX_EMOJI = 3
        const val MIN_EMOJI_WORD = 3
        const val MIN_SHORTCODE = 2
        const val EMOJI_SLOT = 2
        private const val MAX_TOKEN = 32

        /** The trailing run of letters and inner apostrophes (and a leading `:` shortcode) of [before]. */
        private fun tokenBefore(before: String): String? {
            var i = before.length
            while (i > 0) {
                val c = before[i - 1]
                if (c.isLetterOrDigit() || c == '\'' || c == '’' || c == '_' || c == '+' || c == '-') i-- else break
            }
            if (i > 0 && before[i - 1] == ':' && (i == 1 || !before[i - 2].isLetterOrDigit())) i--
            val t = before.substring(i)
            if (t.isEmpty() || t.length > MAX_TOKEN) return null
            // an apostrophe that opens the token is a quote, not part of the word
            return if (t.startsWith('\'') || t.startsWith('’')) t.drop(1).ifEmpty { null } else t
        }

        fun currentWord(before: String): String? = tokenBefore(before)?.takeUnless { it.startsWith(':') }

        /**
         * The text to commit for [s]: words get a trailing space so typing carries on at once, emoji do not. The
         * caller first deletes [Suggestion.replaces] characters before the cursor.
         */
        fun accept(s: Suggestion): String = if (s.kind == SuggestionKind.Emoji) s.text else s.text + " "
    }
}
