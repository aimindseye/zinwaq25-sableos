package org.sableos.titan2.keyboard.core

/**
 * What counts as part of a word when the keyboard reads the text before the cursor. Letters in any script, and the
 * combining marks that Indic scripts put vowel signs in (they are not letters), plus the joiners.
 */
object Tokens {
    private const val MAX_TOKEN = 32
    private const val TOKEN_PUNCTUATION = "'’_+-"
    private const val JOINERS = "‌‍"

    fun isMark(c: Char): Boolean = when (Character.getType(c).toByte()) {
        Character.NON_SPACING_MARK,
        Character.COMBINING_SPACING_MARK,
        Character.ENCLOSING_MARK -> true

        else -> false
    }

    /** A letter, mark or joiner: what a word is made of. */
    fun isWordChar(c: Char): Boolean = c.isLetter() || isMark(c) || c in JOINERS

    fun isTokenChar(c: Char): Boolean =
        c.isLetterOrDigit() || isMark(c) || c in JOINERS || c in TOKEN_PUNCTUATION

    // a ':' that opens a shortcode follows a space or the start, not a word (so "a:b" is not one)
    private fun opensShortcode(before: String, i: Int): Boolean =
        i > 0 && before[i - 1] == ':' && (i == 1 || !before[i - 2].isLetterOrDigit())

    private fun tokenStart(before: String): Int {
        var i = before.length
        while (i > 0 && isTokenChar(before[i - 1])) i--
        return if (opensShortcode(before, i)) i - 1 else i
    }

    /** The trailing run of word characters and inner apostrophes (or a `:` shortcode) of [before]; quotes dropped. */
    fun before(before: String): String? {
        val word = before.substring(tokenStart(before)).trimStart('\'', '’')
        return word.takeIf { it.isNotEmpty() && it.length <= MAX_TOKEN }
    }

    /** The partial word ending at [before], or null when it does not end in one. */
    fun currentWord(before: String): String? = before(before)?.takeUnless { it.startsWith(':') }

    /** A whole word: letters, marks, joiners and apostrophes only. */
    fun isPlainWord(word: String): Boolean =
        word.isNotEmpty() && word.all { isWordChar(it) || it == '\'' }
}
