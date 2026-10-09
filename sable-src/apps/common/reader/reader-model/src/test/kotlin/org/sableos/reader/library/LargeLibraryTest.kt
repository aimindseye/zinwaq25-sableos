package org.sableos.reader.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.reader.model.NaturalOrder
import org.sableos.reader.model.PublicationKind

/**
 * Synthetic 50 000-item libraries. The time bounds are deliberately generous (a view over fifty thousand items takes
 * milliseconds); they exist to catch an accidental quadratic algorithm, not to benchmark.
 */
class LargeLibraryTest {
    private val big = LibraryFixtures.synthetic(50_000)

    private fun <T> timed(limitMs: Long, block: () -> T): T {
        val start = System.nanoTime()
        val result = block()
        val elapsed = (System.nanoTime() - start) / 1_000_000
        assertTrue("took ${elapsed}ms, limit ${limitMs}ms", elapsed < limitMs)
        return result
    }

    @Test
    fun everyViewOverFiftyThousandItemsStaysFast() {
        LibrarySort.entries.forEach { sort ->
            val view = timed(5_000) { LibraryViews.apply(big, LibraryQuery(sort = sort)) }
            assertEquals(big.size, view.size)
            assertEquals(big.size, view.map { it.id }.toSet().size)
        }
    }

    @Test
    fun filtersAndSearchAgreeWithABruteForceCount() {
        val comics = timed(5_000) { LibraryViews.apply(big, LibraryQuery(kinds = setOf(PublicationKind.COMIC))) }
        assertEquals(big.count { it.kind == PublicationKind.COMIC }, comics.size)
        val eclairs = timed(5_000) { LibraryViews.apply(big, LibraryQuery(search = "eclair")) }
        assertEquals(big.count { it.title.contains("Éclair") }, eclairs.size)
        val inProgress = LibraryViews.apply(big, LibraryQuery(LibraryScope.IN_PROGRESS))
        assertEquals(big.count(LibraryViews::isInProgress), inProgress.size)
    }

    @Test
    fun countsSeriesAndContinueShelfScale() {
        val counts = timed(2_000) { LibraryViews.counts(big) }
        assertEquals(big.size, counts.byKind.values.sum())
        assertEquals(big.count { it.source == null }, counts.unlinked)
        val groups = timed(5_000) { SeriesGroups.group(big) }
        assertTrue(groups.isNotEmpty())
        val names = groups.map { it.name }
        assertEquals(names, names.sortedWith { a, b -> NaturalOrder.compare(a, b) })
        val shelf = timed(2_000) { LibraryViews.continueReading(big, 20) }
        assertEquals(20, shelf.size)
        assertEquals(shelf.sortedByDescending { it.lastOpenedAt }.map { it.id }, shelf.map { it.id })
    }
}
