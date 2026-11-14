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

    fun suggest(before: String): List<Suggestion> {
        val token = tokenBefore(before)
        return when {
            token == null -> emptyList()
            token.startsWith(':') -> shortcodeEmoji(token)
            else -> forWord(token)
        }
    }

    private fun forWord(token: String): List<Suggestion> {
        val word = token.replace('’', '\'')
        if (!isPlainWord(word)) return emptyList()
        val typed = token.length
        val completions = words?.complete(word, MAX_WORDS + 1).orEmpty()
            .map { matchCase(word, it) }
            .filter { !it.equals(word, true) }
            .map { Suggestion(it, typed, SuggestionKind.Completion) }
        val fixes = misspeltFixes(word).map {
            Suggestion(matchCase(word, it), typed, SuggestionKind.Correction)
        }
        val emojis = emojiFor(word).map { Suggestion(it, typed, SuggestionKind.Emoji) }
        val ordered = (fixes + completions).distinctBy { it.text }.take(MAX_WORDS)
        return ordered.take(EMOJI_SLOT) + emojis.take(1) + ordered.drop(EMOJI_SLOT) + emojis.drop(1)
    }

    private fun misspeltFixes(word: String): List<String> {
        val list = words
        return if (list != null &&
            !list.contains(word)
        ) {
            list.corrections(word, MAX_WORDS)
        } else {
            emptyList()
        }
    }

    private fun emojiFor(word: String): List<String> = if (word.length >=
        MIN_EMOJI_WORD
    ) {
        emoji?.forWord(word, MAX_EMOJI).orEmpty()
    } else {
        emptyList()
    }

    private fun shortcodeEmoji(token: String): List<Suggestion> {
        val name = token.drop(1)
        val index = emoji
        if (index == null || !isShortcodeName(name)) return emptyList()
        return index.search(name, MAX_EMOJI * 2).map {
            Suggestion(it, token.length, SuggestionKind.Emoji)
        }
    }

    /** Gives [candidate] the capitalisation the user is typing in: Word, WORD or word. */
    private fun matchCase(typed: String, candidate: String): String = when {
        isShouting(typed) -> candidate.uppercase()
        typed.first().isUpperCase() -> candidate.replaceFirstChar { it.uppercase() }
        else -> candidate
    }

    private fun isShouting(typed: String): Boolean =
        typed.length > 1 && typed.all { it.isUpperCase() || it == '\'' }

    companion object {
        const val MAX_WORDS = 4
        const val MAX_EMOJI = 3
        const val MIN_EMOJI_WORD = 3
        const val MIN_SHORTCODE = 2
        const val EMOJI_SLOT = 2
        private const val MAX_TOKEN = 32
        private const val TOKEN_PUNCTUATION = "'’_+-"
        private const val SHORTCODE_PUNCTUATION = "_+-"

        private fun isPlainWord(word: String): Boolean =
            word.isNotEmpty() && word.all { it.isLetter() || it == '\'' }

        private fun isShortcodeName(name: String): Boolean = name.length >= MIN_SHORTCODE &&
            name.all { it.isLetterOrDigit() || it in SHORTCODE_PUNCTUATION }

        private fun isTokenChar(c: Char): Boolean = c.isLetterOrDigit() || c in TOKEN_PUNCTUATION

        // a ':' that opens a shortcode follows a space or the start, not a word (so "a:b" is not one)
        private fun opensShortcode(before: String, i: Int): Boolean =
            i > 0 && before[i - 1] == ':' && (i == 1 || !before[i - 2].isLetterOrDigit())

        private fun tokenStart(before: String): Int {
            var i = before.length
            while (i > 0 && isTokenChar(before[i - 1])) i--
            return if (opensShortcode(before, i)) i - 1 else i
        }

        /** The trailing run of letters and inner apostrophes (or a `:` shortcode) of [before]; quotes dropped. */
        private fun tokenBefore(before: String): String? {
            val word = before.substring(tokenStart(before)).trimStart('\'', '’')
            return word.takeIf { it.isNotEmpty() && it.length <= MAX_TOKEN }
        }

        /** The partial word ending at the cursor, or null when the cursor is not at the end of a word. */
        fun currentWord(before: String): String? =
            tokenBefore(before)?.takeUnless { it.startsWith(':') }

        /**
         * The text to commit for [s]: words get a trailing space so typing carries on at once, emoji do not. The
         * caller first deletes [Suggestion.replaces] characters before the cursor.
         */
        fun accept(s: Suggestion): String = if (s.kind ==
            SuggestionKind.Emoji
        ) {
            s.text
        } else {
            s.text + " "
        }
    }
}
