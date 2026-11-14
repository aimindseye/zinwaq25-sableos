package org.sableos.titan2.keyboard.core

/** What a suggestion is, for ordering and for how accepting it edits the text. */
enum class SuggestionKind { Completion, Correction, Emoji, NextWord }

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
class WordSuggester(
    private val words: WordList?,
    private val emoji: EmojiIndex?,
    private val user: UserWords? = null
) {

    fun suggest(before: String): List<Suggestion> {
        val token = Tokens.before(before)
        return when {
            token == null -> nextWords(before)
            token.startsWith(':') -> shortcodeEmoji(token)
            else -> forWord(token)
        }
    }

    private fun forWord(token: String): List<Suggestion> {
        val word = token.replace('’', '\'')
        if (!Tokens.isPlainWord(word)) return emptyList()
        val typed = token.length
        val own = user?.complete(word, MAX_OWN).orEmpty()
        val completions = (own + words?.complete(word, MAX_WORDS + 1).orEmpty())
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
        val known = user?.isKnown(word) == true
        return if (list != null && !known && !list.contains(word)) {
            list.corrections(word, MAX_WORDS)
        } else {
            emptyList()
        }
    }

    /** After a word and one space: what the user usually types next, from their own phrases. */
    private fun nextWords(before: String): List<Suggestion> {
        val previous = if (before.endsWith(" ")) Tokens.currentWord(before.dropLast(1)) else null
        return previous?.let { user?.next(it, MAX_WORDS) }.orEmpty()
            .map { Suggestion(it, 0, SuggestionKind.NextWord) }
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
        const val MAX_OWN = 2
        const val MAX_EMOJI = 3
        const val MIN_EMOJI_WORD = 3
        const val MIN_SHORTCODE = 2
        const val EMOJI_SLOT = 2
        private const val SHORTCODE_PUNCTUATION = "_+-"

        private fun isShortcodeName(name: String): Boolean = name.length >= MIN_SHORTCODE &&
            name.all { it.isLetterOrDigit() || it in SHORTCODE_PUNCTUATION }

        /** The partial word ending at the cursor, or null when the cursor is not at the end of a word. */
        fun currentWord(before: String): String? = Tokens.currentWord(before)

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
