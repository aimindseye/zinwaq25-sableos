package org.sableos.notes.core

/**
 * Checklist lines in a note's text: "- [ ] milk" is open, "- [x] milk" is done. The text stays
 * plain, so an exported note reads the same in any editor.
 */
object Checklist {
    const val OPEN = "- [ ] "
    const val DONE = "- [x] "
    private val ITEM = Regex("""^(\s*)- \[([ xX])] ?(.*)$""")

    data class Item(
        val indent: String,
        val text: String,
        val done: Boolean,
    )

    data class Progress(
        val done: Int,
        val total: Int,
    )

    /** An edit to the text: the new text and where the cursor goes. */
    data class Edit(
        val text: String,
        val cursor: Int,
    )

    fun parse(line: String): Item? =
        ITEM.matchEntire(line)?.let { match ->
            val (indent, mark, text) = match.destructured
            Item(indent, text, mark != " ")
        }

    fun progress(body: String): Progress {
        val items = body.lineSequence().mapNotNull(::parse).toList()
        return Progress(items.count { it.done }, items.size)
    }

    /** Ticks or unticks the item on line [line] (0-based); null when that line is not an item. */
    fun toggleLine(
        body: String,
        line: Int,
    ): String? {
        val lines = body.split('\n').toMutableList()
        val item = lines.getOrNull(line)?.let(::parse) ?: return null
        lines[line] = item.indent + (if (item.done) OPEN else DONE) + item.text
        return lines.joinToString("\n")
    }

    /** Ctrl+Enter: ticks or unticks the item under the cursor, keeping the cursor on its line. */
    fun toggleAt(
        body: String,
        cursor: Int,
    ): Edit? {
        val line = lineIndex(body, cursor)
        val text = toggleLine(body, line) ?: return null
        return Edit(text, cursor.coerceIn(0, text.length))
    }

    /** Ctrl+T: turns the line under the cursor into an open item, or an item back into plain text. */
    fun convertAt(
        body: String,
        cursor: Int,
    ): Edit {
        val start = lineStart(body, cursor)
        val end = lineEnd(body, cursor)
        val line = body.substring(start, end)
        val item = parse(line)
        val replaced =
            if (item != null) {
                item.indent + item.text
            } else {
                val indent = line.takeWhile { it == ' ' || it == '\t' }
                indent + OPEN + line.substring(indent.length)
            }
        val delta = replaced.length - line.length
        val text = body.substring(0, start) + replaced + body.substring(end)
        return Edit(text, (cursor + delta).coerceIn(start, start + replaced.length))
    }

    /**
     * Enter at the end of an item starts the next open item, as list apps do; Enter on an empty item
     * ends the list. Null when the cursor's line is not an item, so Enter types a plain newline.
     */
    fun newline(
        body: String,
        cursor: Int,
    ): Edit? {
        val start = lineStart(body, cursor)
        val end = lineEnd(body, cursor)
        return parse(body.substring(start, end))?.let { item ->
            if (item.text.isBlank()) {
                Edit(body.substring(0, start) + body.substring(end), start)
            } else {
                val insert = "\n" + item.indent + OPEN
                Edit(body.substring(0, cursor) + insert + body.substring(cursor), cursor + insert.length)
            }
        }
    }

    fun lineIndex(
        body: String,
        cursor: Int,
    ): Int = body.substring(0, cursor.coerceIn(0, body.length)).count { it == '\n' }

    private fun lineStart(
        body: String,
        cursor: Int,
    ): Int = body.lastIndexOf('\n', cursor.coerceIn(0, body.length) - 1) + 1

    private fun lineEnd(
        body: String,
        cursor: Int,
    ): Int = body.indexOf('\n', cursor.coerceIn(0, body.length)).let { if (it < 0) body.length else it }
}
