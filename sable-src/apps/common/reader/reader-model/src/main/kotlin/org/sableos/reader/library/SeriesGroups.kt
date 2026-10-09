package org.sableos.reader.library

import org.sableos.reader.model.LibraryItem
import org.sableos.reader.model.NaturalOrder

/** A series with its volumes in reading order. */
data class SeriesGroup(val name: String, val items: List<LibraryItem>) {
    /** The first volume that is not finished, i.e. what to read next; null when the series is complete. */
    val next: LibraryItem? get() = items.firstOrNull { !it.finished }
}

object SeriesGroups {
    /**
     * Groups items that share a series name (case- and accent-insensitive) into series with at least [minSize] volumes.
     * Volumes sort by series index (items without one last, then by title); series sort by name.
     */
    fun group(items: List<LibraryItem>, minSize: Int = 2): List<SeriesGroup> {
        val groups = LinkedHashMap<String, MutableList<LibraryItem>>()
        for (item in items) {
            val key = item.series?.let(SearchText::normalize)?.takeIf { it.isNotEmpty() } ?: continue
            groups.getOrPut(key) { mutableListOf() } += item
        }
        return groups.values.filter { it.size >= minSize }.map { volumes ->
            val ordered = volumes.sortedWith(
                compareBy<LibraryItem> { it.seriesIndex ?: Double.MAX_VALUE }
                    .thenComparator { a, b -> NaturalOrder.compare(a.title, b.title) }
                    .thenBy { it.id },
            )
            SeriesGroup(ordered.first().series.orEmpty(), ordered)
        }.sortedWith { a, b -> NaturalOrder.compare(a.name, b.name) }
    }
}
