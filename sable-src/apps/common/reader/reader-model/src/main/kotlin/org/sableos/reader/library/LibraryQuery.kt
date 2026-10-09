package org.sableos.reader.library

import org.sableos.reader.model.PublicationKind

/** Which slice of the library to show. */
enum class LibraryScope {
    ALL,

    /** Opened, not finished, with some progress: the "continue" shelf. */
    IN_PROGRESS,
    FINISHED,
    FAVORITES,
}

enum class LibrarySort { RECENT, TITLE, AUTHOR, PROGRESS, ADDED }

/**
 * One request against the unified library. Empty [kinds] means every kind; a null [collectionId] means no collection
 * filter. [search] is matched token by token against title, subtitle, authors and series.
 */
data class LibraryQuery(
    val scope: LibraryScope = LibraryScope.ALL,
    val kinds: Set<PublicationKind> = emptySet(),
    val collectionId: String? = null,
    val search: String = "",
    val sort: LibrarySort = LibrarySort.RECENT,
)

/** Counts for the filter chips, computed in one pass. */
data class LibraryCounts(
    val total: Int,
    val byKind: Map<PublicationKind, Int>,
    val inProgress: Int,
    val finished: Int,
    val favorites: Int,
    /** Items whose file is not linked on this device (restored from a backup, or the source went away). */
    val unlinked: Int,
)
