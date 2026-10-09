package org.sableos.reader.library

import org.sableos.reader.model.LibraryItem
import org.sableos.reader.model.PublicationKind
import org.sableos.reader.model.ReadingProgress

/** Deterministic synthetic libraries for view tests, including very large ones. */
internal object LibraryFixtures {
    /** A linked EPUB; [change] adjusts whatever a test cares about. */
    fun item(id: String, title: String = "Title $id", change: (LibraryItem) -> LibraryItem = { it }): LibraryItem =
        change(
            LibraryItem(
                id = id,
                profileId = "default",
                kind = PublicationKind.EPUB,
                title = title,
                authors = listOf("Author"),
                source = "content://$id",
                addedAt = 1L,
            ),
        )

    /** [count] items spread over every kind, with a realistic mix of state, series and missing sources. */
    fun synthetic(count: Int): List<LibraryItem> = List(count) { i ->
        val opened = i % 3 != 0
        val inSeries = i % 4 == 0
        item("item-%06d".format(i), "Book ${(i * 7919) % count} ${if (i % 5 == 0) "Éclair" else "Volume"}") {
            it.copy(
                kind = PublicationKind.entries[i % PublicationKind.entries.size],
                authors = listOf("Author ${i % 500}"),
                series = if (inSeries) "Series ${i % 300}" else null,
                seriesIndex = if (inSeries) (i / 300).toDouble() else null,
                progress = if (opened) ReadingProgress.page(i % 100, 100, 1L) else null,
                finished = i % 11 == 0,
                favorite = i % 13 == 0,
                addedAt = (i % 1000).toLong() + 1,
                lastOpenedAt = if (opened) (i % 997).toLong() + 1 else 0L,
                source = if (i % 17 == 0) null else "content://item/$i",
                collectionIds = if (i % 9 == 0) setOf("shelf-${i % 7}") else emptySet(),
            )
        }
    }
}
