package io.github.aimindseye.tenseven.conversations

/**
 * SableOS (2.0 plan, BB10 "Hub over apps"): the rows of the Hub peek, a panel that opens over the app in
 * front (hold the Recents key, or Hub in the Alt+BlackBerry key menu) and closes back to it with Back.
 * Pure rules, unit-tested: snoozed conversations are left out, unread ones come first, then the newest.
 */
object SableHubPeek {
    const val MAX_ROWS = 8
    private const val PREVIEW_CHARS = 60

    data class Entry(
        val id: String,
        val name: String,
        val preview: String,
        val at: Long,
        val unread: Int,
        val snoozed: Boolean,
    )

    fun rows(entries: List<Entry>): List<Entry> =
        entries
            .filterNot(Entry::snoozed)
            .sortedWith(compareByDescending<Entry> { it.unread > 0 }.thenByDescending(Entry::at))
            .take(MAX_ROWS)

    /** "Anna (2): See you at six" — the unread count only when there is one, the preview kept short. */
    fun label(entry: Entry): String {
        val count = if (entry.unread > 0) " (${entry.unread})" else ""
        val text = entry.preview.replace('\n', ' ').trim()
        val short = if (text.length > PREVIEW_CHARS) text.take(PREVIEW_CHARS - 1).trimEnd() + "…" else text
        return if (short.isEmpty()) "${entry.name}$count" else "${entry.name}$count: $short"
    }

    fun title(entries: List<Entry>): String {
        val unread = entries.filterNot(Entry::snoozed).sumOf(Entry::unread)
        return if (unread > 0) "Hub · $unread new" else "Hub"
    }
}
