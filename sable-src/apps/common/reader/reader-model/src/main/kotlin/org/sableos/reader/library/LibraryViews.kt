package org.sableos.reader.library

import java.text.Normalizer
import org.sableos.reader.model.LibraryItem
import org.sableos.reader.model.NaturalOrder
import org.sableos.reader.model.PublicationKind

/** Case- and accent-insensitive search text, shared by every view so matching is identical everywhere. */
object SearchText {
    private val combiningMarks = Regex("\\p{Mn}+")
    private val whitespace = Regex("\\s+")

    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return decomposed.replace(combiningMarks, "").lowercase().replace(whitespace, " ").trim()
    }

    fun tokens(query: String): List<String> = normalize(query).split(' ').filter { it.isNotEmpty() }
}

/**
 * Pure views over the unified library: scope, kind, collection and search filters plus sorting. One pass to filter
 * and one stable sort, so a 50 000-item library stays interactive. Items are never mutated.
 */
object LibraryViews {
    fun apply(items: List<LibraryItem>, query: LibraryQuery): List<LibraryItem> {
        val tokens = SearchText.tokens(query.search)
        val filtered = items.filter { matches(it, query, tokens) }
        return filtered.sortedWith(comparator(query.sort))
    }

    /** The "continue" shelf: opened, unfinished, most recently opened first. */
    fun continueReading(items: List<LibraryItem>, limit: Int): List<LibraryItem> =
        items.filter(::isInProgress).sortedByDescending { it.lastOpenedAt }.take(limit.coerceAtLeast(0))

    fun counts(items: List<LibraryItem>): LibraryCounts {
        val byKind = HashMap<PublicationKind, Int>()
        var inProgress = 0
        var finished = 0
        var favorites = 0
        var unlinked = 0
        for (item in items) {
            byKind.merge(item.kind, 1, Int::plus)
            if (isInProgress(item)) inProgress++
            if (item.finished) finished++
            if (item.favorite) favorites++
            if (item.source == null) unlinked++
        }
        return LibraryCounts(items.size, byKind, inProgress, finished, favorites, unlinked)
    }

    fun isInProgress(item: LibraryItem): Boolean =
        LibraryFilters.isInProgress(item.finished, item.lastOpenedAt, item.progressFraction)

    private fun matches(item: LibraryItem, query: LibraryQuery, tokens: List<String>): Boolean =
        inScope(item, query.scope) &&
            (query.kinds.isEmpty() || item.kind in query.kinds) &&
            (query.collectionId == null || query.collectionId in item.collectionIds) &&
            (tokens.isEmpty() || matchesAll(item, tokens))

    private fun inScope(item: LibraryItem, scope: LibraryScope): Boolean = when (scope) {
        LibraryScope.ALL -> true
        LibraryScope.IN_PROGRESS -> isInProgress(item)
        LibraryScope.FINISHED -> item.finished
        LibraryScope.FAVORITES -> item.favorite
    }

    private fun matchesAll(item: LibraryItem, tokens: List<String>): Boolean {
        val haystack = SearchText.normalize(
            listOfNotNull(item.title, item.subtitle, item.series, item.authorLine).joinToString(" "),
        )
        return tokens.all { haystack.contains(it) }
    }

    private fun comparator(sort: LibrarySort): Comparator<LibraryItem> {
        val byTitle = Comparator<LibraryItem> { a, b -> NaturalOrder.compare(a.title, b.title) }
        val primary: Comparator<LibraryItem> = when (sort) {
            LibrarySort.RECENT -> compareByDescending<LibraryItem> { maxOf(it.lastOpenedAt, it.addedAt) }
            LibrarySort.TITLE -> byTitle
            LibrarySort.AUTHOR -> Comparator { a, b -> NaturalOrder.compare(a.authorLine, b.authorLine) }
            LibrarySort.PROGRESS -> compareByDescending { it.progressFraction }
            LibrarySort.ADDED -> compareByDescending { it.addedAt }
        }
        // Ties fall back to title then id so the order is total and stable across runs.
        return primary.then(byTitle).thenBy { it.id }
    }
}
