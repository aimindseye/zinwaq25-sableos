package org.sableos.notes.core

/** Checklist items gathered from every note: the Tasks screen. */
object Tasks {
    data class Task(
        val noteId: String,
        val noteTitle: String,
        /** The item's line in its note (0-based), for [Checklist.toggleLine]. */
        val line: Int,
        val text: String,
        val done: Boolean,
    )

    /** Open items, latest-edited note first; done items too only when [withDone] (explicit history). */
    fun collect(
        notes: List<Note>,
        withDone: Boolean,
    ): List<Task> =
        notes.sortedByDescending { it.updatedAt }.flatMap { note ->
            note.body.split('\n').mapIndexedNotNull { line, text ->
                Checklist
                    .parse(text)
                    ?.takeIf { (withDone || !it.done) && it.text.isNotBlank() }
                    ?.let { Task(note.id, note.title, line, it.text, it.done) }
            }
        }
}
