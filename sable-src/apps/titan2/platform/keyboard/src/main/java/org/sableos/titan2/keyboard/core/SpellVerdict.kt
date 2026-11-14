package org.sableos.titan2.keyboard.core

/**
 * Whether the system spell checker should underline a word, kept pure so it is JVM-tested. [attributes] use the
 * values of android.view.textservice.SuggestionsInfo (stable public ABI).
 */
data class SpellVerdictResult(val attributes: Int, val suggestions: List<String>)

object SpellVerdict {
    const val IN_DICTIONARY = 0x0001
    const val LOOKS_LIKE_TYPO = 0x0002

    fun of(list: WordList?, word: String, limit: Int): SpellVerdictResult {
        if (list == null || !checkable(word) || list.contains(word) || isAcronym(word)) {
            return SpellVerdictResult(IN_DICTIONARY, emptyList())
        }
        return SpellVerdictResult(LOOKS_LIKE_TYPO, list.corrections(word, limit.coerceIn(1, MAX_SUGGESTIONS)))
    }

    private const val MAX_SUGGESTIONS = 5

    /** Plain words only: not numbers, addresses, hashtags, mixed letters and digits, or one letter. */
    private fun checkable(word: String): Boolean =
        word.length >= 2 && word.all { it.isLetter() || it == '\'' || it == '’' }

    private fun isAcronym(word: String): Boolean = word.length > 1 && word.all { it.isUpperCase() }
}
