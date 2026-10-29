package org.sableos.notes.core

/**
 * The on-disk form of a note: a short header, a blank line, then the text verbatim.
 *
 * ```
 * sable-note 1
 * label YELLOW
 * created 1730000000000
 * updated 1730000000000
 *
 * Shopping
 * - [ ] milk
 * ```
 */
object NoteCodec {
    const val MAGIC = "sable-note 1"

    fun encode(note: Note): String =
        buildString {
            append(MAGIC).append('\n')
            append("label ").append(note.label.name).append('\n')
            append("created ").append(note.createdAt).append('\n')
            append("updated ").append(note.updatedAt).append('\n')
            append('\n')
            append(note.body)
        }

    /** Null when [text] is not a note this version writes. */
    fun decode(
        id: String,
        text: String,
    ): Note? {
        val split = text.indexOf("\n\n")
        val header = if (split < 0) emptyList() else text.substring(0, split).split('\n')
        if (header.firstOrNull() != MAGIC) return null
        val fields =
            header
                .drop(1)
                .mapNotNull { line ->
                    line.indexOf(' ').takeIf { it > 0 }?.let { line.substring(0, it) to line.substring(it + 1) }
                }.toMap()
        return fields["created"]?.toLongOrNull()?.let { created ->
            val updated = fields["updated"]?.toLongOrNull() ?: created
            Note(id, text.substring(split + 2), created, updated, NoteLabel.parse(fields["label"]))
        }
    }
}
