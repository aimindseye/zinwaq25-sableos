package org.sableos.notes.core

import org.sableos.notes.core.NotesText.t

/**
 * One note: plain text with lightweight structure ("# " headings and [Checklist] lines) and an
 * optional colour label. The first line that has text is the title.
 */
data class Note(
    val id: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    val label: NoteLabel = NoteLabel.NONE,
) {
    val title: String get() = NoteText.title(body)
}

/** Colour labels, as in Color Notes and BlackBerry Remember's categories. [NONE] draws a neutral bar. */
@Suppress("MagicNumber") // ARGB colours
enum class NoteLabel(
    val argb: Long,
    private val nameKey: String,
) {
    NONE(0xFF64748B, "no_colour"),
    YELLOW(0xFFF5C542, "yellow"),
    ORANGE(0xFFF28C45, "orange"),
    RED(0xFFE5534B, "red"),
    PINK(0xFFE8458F, "pink"),
    PURPLE(0xFF7A42E8, "purple"),
    BLUE(0xFF4D9CFF, "blue"),
    GREEN(0xFF35C66B, "green"),
    ;

    val displayName: String get() = t(nameKey)

    fun next(): NoteLabel = entries[(ordinal + 1) % entries.size]

    companion object {
        fun parse(name: String?): NoteLabel = entries.firstOrNull { it.name == name } ?: NONE
    }
}

/** Title and preview text for lists. */
object NoteText {
    val UNTITLED: String get() = t("empty_note")
    private const val PREVIEW_CHARS = 120
    private val HEADING = Regex("""^#+\s*""")

    fun title(body: String): String = lines(body).firstOrNull() ?: UNTITLED

    /** The lines after the title, joined on one line. */
    fun preview(body: String): String =
        lines(body).drop(1).joinToString(" · ").let {
            if (it.length > PREVIEW_CHARS) it.take(PREVIEW_CHARS).trimEnd() + "…" else it
        }

    /** Non-blank lines without heading or checklist markers. */
    private fun lines(body: String): List<String> =
        body
            .lineSequence()
            .map { Checklist.parse(it)?.text ?: it.replace(HEADING, "") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
}
