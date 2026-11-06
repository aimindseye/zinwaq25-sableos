package org.sableos.phone.core

/** Numbers and durations as Sable Phone shows and compares them. */
object CallFormat {
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3600

    /** A call's length: `0:07`, `12:45`, `1:02:03`. */
    fun duration(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / SECONDS_PER_HOUR
        val m = s % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val sec = s % SECONDS_PER_MINUTE
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    /** Only the characters that are dialled: digits, `+` at the start, `*`, `#` and pauses (`,` `;`). */
    fun dialable(raw: String): String {
        val sb = StringBuilder()
        raw.forEach { c ->
            when {
                c.isDigit() || c == '*' || c == '#' || c == ',' || c == ';' -> sb.append(c)
                c == '+' && sb.isEmpty() -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * The key two numbers share when they are the same line: the last [MATCH_DIGITS] digits, so `+1 555 0101 222`
     * and `(555) 010-1222` match. Shorter numbers (service codes) must match in full.
     */
    fun matchKey(raw: String): String = raw.filter { it.isDigit() }.takeLast(MATCH_DIGITS)

    fun sameNumber(a: String, b: String): Boolean {
        val ka = matchKey(a)
        return ka.isNotEmpty() && ka == matchKey(b)
    }

    const val MATCH_DIGITS = 9
}
