package org.sableos.assistant.core

/**
 * The little JSON Cosgrove needs for OpenAI-style chat: writing a request and reading a reply or a
 * model list. Values read as String, Double, Boolean, null, List<Any?> and Map<String, Any?>.
 */
object Json {
    fun quote(text: String): String =
        buildString {
            append('"')
            for (c in text) {
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
                }
            }
            append('"')
        }

    /** Writes maps, lists, strings, numbers, booleans and null. */
    fun write(value: Any?): String =
        when (value) {
            null -> "null"
            is String -> quote(value)
            is Boolean, is Int, is Long -> value.toString()
            is Number -> value.toDouble().toString()
            is Map<*, *> -> value.entries.joinToString(",", "{", "}") { (k, v) -> quote(k.toString()) + ":" + write(v) }
            is List<*> -> value.joinToString(",", "[", "]") { write(it) }
            else -> quote(value.toString())
        }

    /** Reads [text], or null when it is not JSON. */
    fun parse(text: String): Any? = runCatching { Reader(text).document() }.getOrNull()

    /** The value at [path] (map keys, or list indexes as Int), or null when it is missing. */
    fun at(
        value: Any?,
        vararg path: Any,
    ): Any? =
        path.fold(value) { node, step ->
            when {
                node is Map<*, *> && step is String -> node[step]
                node is List<*> && step is Int -> node.getOrNull(step)
                else -> null
            }
        }

    private class Reader(
        private val s: String,
    ) {
        private var i = 0

        fun document(): Any? {
            val v = value()
            space()
            require(i == s.length) { "trailing text" }
            return v
        }

        private fun space() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun value(): Any? {
            space()
            require(i < s.length) { "end of text" }
            return when (s[i]) {
                '{' -> obj()
                '[' -> list()
                '"' -> string()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> number()
            }
        }

        private fun word(
            w: String,
            v: Any?,
        ): Any? {
            require(s.startsWith(w, i)) { "bad word" }
            i += w.length
            return v
        }

        private fun number(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDouble()
        }

        private fun obj(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            i++
            space()
            if (s[i] == '}') return map.also { i++ }
            while (true) {
                space()
                val key = string()
                space()
                require(s[i] == ':') { "colon" }
                i++
                map[key] = value()
                space()
                if (s[i] == ',') {
                    i++
                } else {
                    require(s[i] == '}') { "brace" }
                    i++
                    return map
                }
            }
        }

        private fun list(): List<Any?> {
            val out = ArrayList<Any?>()
            i++
            space()
            if (s[i] == ']') return out.also { i++ }
            while (true) {
                out += value()
                space()
                if (s[i] == ',') {
                    i++
                } else {
                    require(s[i] == ']') { "bracket" }
                    i++
                    return out
                }
            }
        }

        private fun string(): String {
            require(s[i] == '"') { "quote" }
            i++
            val out = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> out.append(escape())
                    else -> out.append(c)
                }
            }
        }

        private fun escape(): Char =
            when (val e = s[i++]) {
                'n' -> {
                    '\n'
                }

                'r' -> {
                    '\r'
                }

                't' -> {
                    '\t'
                }

                'b' -> {
                    '\b'
                }

                'f' -> {
                    '\u000C'
                }

                'u' -> {
                    s
                        .substring(i, i + UNICODE_DIGITS)
                        .toInt(HEX)
                        .toChar()
                        .also { i += UNICODE_DIGITS }
                }

                else -> {
                    e
                }
            }
    }

    /** A \\uXXXX escape: four hex digits. */
    private const val UNICODE_DIGITS = 4
    private const val HEX = 16
}
