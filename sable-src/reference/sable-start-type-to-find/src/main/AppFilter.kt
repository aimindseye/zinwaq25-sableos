package org.sableos.reference.typetofind

data class AppEntry(val label: String, val pkg: String, val cls: String)

/** Type-to-find ranking for All Apps: label prefix, then word prefix, then substring. Case-insensitive, stable by label. */
object AppFilter {
    fun filter(apps: List<AppEntry>, query: String): List<AppEntry> =
        filterBy(apps, query) { it.label }

    /** Same ranking for any labelled item (apps, commands), so every surface ranks identically. */
    fun <T> filterBy(items: List<T>, query: String, label: (T) -> String): List<T> {
        val q = query.trim().lowercase()
        val sorted = items.sortedBy { label(it).lowercase() }
        if (q.isEmpty()) return sorted
        fun rank(a: T): Int {
            val l = label(a).lowercase()
            return when {
                l.startsWith(q) -> 0
                l.split(' ', '-', '_', '.').any { it.startsWith(q) } -> 1
                l.contains(q) -> 2
                else -> -1
            }
        }
        return sorted.map {
            it to rank(it)
        }.filter { it.second >= 0 }.sortedBy { it.second }.map { it.first }
    }

    /** The app the Enter key launches: the first match, or null when nothing matches. */
    fun firstMatch(apps: List<AppEntry>, query: String): AppEntry? =
        if (query.isBlank()) null else filter(apps, query).firstOrNull()
}
