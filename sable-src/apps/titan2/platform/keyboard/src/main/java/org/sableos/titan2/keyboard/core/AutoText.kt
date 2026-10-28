package org.sableos.titan2.keyboard.core

/**
 * BlackBerry-style AutoText: a short word followed by Space or punctuation is replaced with its long form
 * ("dont " becomes "don't ", "ld " today's date). The built-in English list fixes contractions and common typos;
 * the user's own entries work in every language and win over the built-ins. Pure, so it is unit-tested on a JVM.
 *
 * A replacement may hold variables the keyboard fills in when it expands: %D long date, %T time, %V SableOS
 * build (the phone's build number), %% a percent sign. An entry whose variable is unknown is left as typed.
 */
class AutoText(user: Map<String, String> = emptyMap()) {
    private val user: Map<String, String> = user.mapKeys { it.key.lowercase() }

    data class Hit(val word: String, val replacement: String)

    /** The replacement for the word that ends [before], or null when nothing changes. */
    fun match(before: String, english: Boolean, vars: (Char) -> String? = { null }): Hit? {
        val word = trailingWord(before) ?: return null
        val key = word.lowercase()
        val raw = user[key] ?: BUILT_IN[key]?.takeIf { english }
        val out = raw?.let { expand(it, vars) }?.let { matchCase(word, it) }
        return if (out == null || out == word) null else Hit(word, out)
    }

    companion object {
        const val MAX_SHORTCUT = 32

        /** Characters that end a word and trigger AutoText when typed. */
        const val BOUNDARIES = " .,!?;:"

        // a word preceded by one of these is part of an address, path, tag or number, not prose
        private const val GLUED = "@#/\\_-.:&=+%$"

        val BUILT_IN: Map<String, String> = mapOf(
            "i" to "I",
            "im" to "I'm",
            "ive" to "I've",
            "youre" to "you're",
            "theyre" to "they're",
            "dont" to "don't",
            "cant" to "can't",
            "wont" to "won't",
            "didnt" to "didn't",
            "doesnt" to "doesn't",
            "isnt" to "isn't",
            "wasnt" to "wasn't",
            "arent" to "aren't",
            "werent" to "weren't",
            "havent" to "haven't",
            "hasnt" to "hasn't",
            "couldnt" to "couldn't",
            "shouldnt" to "shouldn't",
            "wouldnt" to "wouldn't",
            "thats" to "that's",
            "whats" to "what's",
            "teh" to "the",
            "hte" to "the",
            "adn" to "and",
            "taht" to "that",
            "waht" to "what",
            "becuase" to "because",
            "recieve" to "receive",
            "definately" to "definitely",
            "seperate" to "separate",
            "tommorow" to "tomorrow",
            "untill" to "until",
            "wierd" to "weird",
            "alot" to "a lot",
            "ld" to "%D",
            "lt" to "%T",
            "myver" to "%V"
        )

        /** True for a shortcut the user may save: 1 to 32 letters, digits or apostrophes, no spaces. */
        fun validShortcut(s: String): Boolean = s.length in 1..MAX_SHORTCUT && s.all(::wordChar)

        /** Reads the stored user list, one `shortcut=replacement` per line; bad lines are skipped. */
        fun parse(text: String): Map<String, String> = text.lineSequence()
            .mapNotNull { line ->
                val eq = line.indexOf('=')
                val short = if (eq > 0) line.substring(0, eq).trim().lowercase() else ""
                val long = if (eq > 0) line.substring(eq + 1).trim() else ""
                if (validShortcut(short) && long.isNotEmpty()) short to long else null
            }
            .toMap()

        /** Writes the user list in [parse]'s format, sorted by shortcut. */
        fun format(entries: Map<String, String>): String =
            entries.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" }

        private fun wordChar(c: Char) = c.isLetterOrDigit() || c == '\''

        private fun trailingWord(before: String): String? {
            var start = before.length
            while (start > 0 && wordChar(before[start - 1])) start--
            val word = before.substring(start)
            val glued = start > 0 && before[start - 1] in GLUED
            return word.takeIf { it.length in 1..MAX_SHORTCUT && it.any(Char::isLetter) && !glued }
        }

        private fun expand(raw: String, vars: (Char) -> String?): String? {
            val sb = StringBuilder()
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                if (c == '%' && i + 1 < raw.length) {
                    val name = raw[i + 1]
                    sb.append(if (name == '%') "%" else vars(name) ?: return null)
                    i += 2
                } else {
                    sb.append(c)
                    i++
                }
            }
            return sb.toString()
        }

        /** Typed in capitals or Capitalised: the replacement follows, unless it carries its own capitals. */
        private fun matchCase(typed: String, repl: String): String = when {
            repl.any(Char::isUpperCase) -> repl

            typed.length > 1 && typed.filter(
                Char::isLetter
            ).all(Char::isUpperCase) -> repl.uppercase()

            typed.first().isUpperCase() -> repl.replaceFirstChar(Char::uppercaseChar)

            else -> repl
        }
    }
}
