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
        val fixes = if (list != null && isMisspelt(list, word)) {
            list.corrections(word, limit.coerceIn(1, MAX_SUGGESTIONS))
        } else {
            null
        }
        return if (fixes == null) {
            SpellVerdictResult(IN_DICTIONARY, emptyList())
        } else {
            SpellVerdictResult(LOOKS_LIKE_TYPO, fixes)
        }
    }

    private fun isMisspelt(list: WordList, word: String): Boolean =
        checkable(word) && !isAcronym(word) && !list.contains(word)

    private const val MAX_SUGGESTIONS = 5

    /** Plain words only: not numbers, addresses, hashtags, mixed letters and digits, or one letter. */
    private fun checkable(word: String): Boolean =
        word.length >= 2 && word.all { it.isLetter() || it == '\'' || it == '’' }

    private fun isAcronym(word: String): Boolean = word.length > 1 && word.all { it.isUpperCase() }
}
