package io.github.aimindseye.tenseven.ui

/**
 * SableOS (BBOS 7 profile): typing a number of three or more digits on the home offers Dial. The
 * characters of a dialable number are digits, plus, star and hash; any letter makes it a search.
 */
internal object SableDial {
    private const val MIN_DIGITS = 3

    fun isNumber(query: String): Boolean =
        query.count { it.isDigit() } >= MIN_DIGITS && query.all { it.isDigit() || it in "+*#" }
}
