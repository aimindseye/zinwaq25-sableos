package org.sableos.reader.library

import org.sableos.reader.model.PublicationKind

/**
 * The labels of the library filter row and what they mean. The row is built from what the library actually holds, so a
 * chip never leads to an empty list.
 */
object LibraryFilters {
    const val ALL: String = "All"
    const val CONTINUE: String = "Continue"
    const val FAVORITES: String = "Favorites"
    const val FINISHED: String = "Finished"
    const val NEEDS_FILE: String = "Needs file"

    fun kindLabel(kind: PublicationKind): String = when (kind) {
        PublicationKind.EPUB -> "Books"
        PublicationKind.PDF -> "PDF"
        PublicationKind.COMIC -> "Comics"
        PublicationKind.AUDIOBOOK -> "Audiobooks"
    }

    fun kindOfLabel(label: String): PublicationKind? = PublicationKind.entries.firstOrNull { kindLabel(it) == label }

    /** Opened, unfinished and with some progress. Single definition shared by the shelf and the views. */
    fun isInProgress(finished: Boolean, lastOpenedAt: Long, progress: Double): Boolean =
        !finished && lastOpenedAt > 0L && progress > 0.0

    /** The ordered chip labels for [counts]: state chips first, then one per kind present. */
    fun labels(counts: LibraryCounts): List<String> = buildList {
        add(ALL)
        if (counts.inProgress > 0) add(CONTINUE)
        if (counts.favorites > 0) add(FAVORITES)
        if (counts.finished > 0) add(FINISHED)
        PublicationKind.entries.filter { (counts.byKind[it] ?: 0) > 0 }.forEach { add(kindLabel(it)) }
        if (counts.unlinked > 0) add(NEEDS_FILE)
    }
}
