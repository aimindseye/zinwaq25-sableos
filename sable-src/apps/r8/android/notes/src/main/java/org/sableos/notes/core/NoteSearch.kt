package org.sableos.notes.core

/** Search across notes (every word must appear) and find-in-note. Case is ignored. */
object NoteSearch {
    private const val CONTEXT_CHARS = 24

    data class Hit(
        val note: Note,
        /** The text around the first match, or null when the match is in the title. */
        val context: String?,
    )

    fun search(
        notes: List<Note>,
        query: String,
    ): List<Hit> {
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return notes.map { Hit(it, null) }
        return notes
            .filter { note -> words.all { note.body.contains(it, ignoreCase = true) } }
            .map { Hit(it, context(it, words.first())) }
    }

    /** The next match of [query] at or after [from], wrapping to the start; null when there is none. */
    fun nextMatch(
        body: String,
        query: String,
        from: Int,
    ): IntRange? {
        val start = from.coerceIn(0, body.length)
        val at =
            body.indexOf(query, start, ignoreCase = true).takeIf { it >= 0 }
                ?: body.indexOf(query, 0, ignoreCase = true)
        return if (query.isEmpty() || at < 0) null else at until at + query.length
    }

    private fun context(
        note: Note,
        word: String,
    ): String? {
        if (note.title.contains(word, ignoreCase = true)) return null
        val at = note.body.indexOf(word, ignoreCase = true)
        val start = (at - CONTEXT_CHARS).coerceAtLeast(0)
        val end = (at + word.length + CONTEXT_CHARS).coerceAtMost(note.body.length)
        val text =
            note.body
                .substring(start, end)
                .replace('\n', ' ')
                .trim()
        return (if (start > 0) "…" else "") + text + (if (end < note.body.length) "…" else "")
    }
}
