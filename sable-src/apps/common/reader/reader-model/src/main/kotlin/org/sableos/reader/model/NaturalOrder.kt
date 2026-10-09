package org.sableos.reader.model

/**
 * Natural, case-insensitive ordering for page names: `page2` sorts before `page10`, `001` equals `1` numerically.
 * Digit runs of any length compare correctly without ever being parsed into a number, so a hostile 100 000-digit
 * name cannot overflow or stall the comparison.
 */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val result = if (a[i].isDigit() && b[j].isDigit()) {
                val endA = digitRunEnd(a, i)
                val endB = digitRunEnd(b, j)
                val numeric = compareDigitRuns(a.substring(i, endA), b.substring(j, endB))
                i = endA
                j = endB
                numeric
            } else {
                val textual = a[i].lowercaseChar().compareTo(b[j].lowercaseChar())
                i++
                j++
                textual
            }
            if (result != 0) return result
        }
        return (a.length - i).compareTo(b.length - j).takeIf { it != 0 } ?: a.compareTo(b)
    }

    private fun digitRunEnd(text: String, start: Int): Int {
        var end = start
        while (end < text.length && text[end].isDigit()) end++
        return end
    }

    private fun compareDigitRuns(a: String, b: String): Int {
        val x = a.trimStart('0')
        val y = b.trimStart('0')
        return if (x.length != y.length) x.length.compareTo(y.length) else x.compareTo(y)
    }
}
