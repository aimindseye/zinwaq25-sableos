package org.sableos.reader.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.reader.library.LibraryFixtures.item
import org.sableos.reader.model.PublicationKind
import org.sableos.reader.model.ReadingProgress

class LibraryViewsTest {
    private val epub = item("e1", "Dune") {
        it.copy(
            authors = listOf("Frank Herbert"),
            addedAt = 10,
            lastOpenedAt = 50,
            progress = ReadingProgress.epub("{}", 0.4, 1L),
        )
    }
    private val pdf = item("p1", "Manual") { it.copy(kind = PublicationKind.PDF, addedAt = 30) }
    private val comic = item("c1", "Naruto 2") {
        it.copy(
            kind = PublicationKind.COMIC,
            series = "Naruto",
            seriesIndex = 2.0,
            favorite = true,
            addedAt = 20,
            lastOpenedAt = 60,
            progress = ReadingProgress.page(3, 10, 1L),
        )
    }
    private val audio = item("a1", "Émile") {
        it.copy(
            kind = PublicationKind.AUDIOBOOK,
            authors = listOf("Jean-Jacques Rousseau"),
            finished = true,
            addedAt = 40,
            lastOpenedAt = 70,
            progress = ReadingProgress.time(100, 100, 0, 1L),
            source = null,
        )
    }
    private val all = listOf(epub, pdf, comic, audio)

    private fun ids(query: LibraryQuery) = LibraryViews.apply(all, query).map { it.id }

    @Test
    fun scopesSelectTheRightSlice() {
        assertEquals(setOf("e1", "c1"), ids(LibraryQuery(LibraryScope.IN_PROGRESS)).toSet())
        assertEquals(listOf("a1"), ids(LibraryQuery(LibraryScope.FINISHED)))
        assertEquals(listOf("c1"), ids(LibraryQuery(LibraryScope.FAVORITES)))
        assertEquals(4, ids(LibraryQuery()).size)
    }

    @Test
    fun kindAndCollectionFiltersCombine() {
        assertEquals(listOf("p1"), ids(LibraryQuery(kinds = setOf(PublicationKind.PDF))))
        val two = ids(LibraryQuery(kinds = setOf(PublicationKind.PDF, PublicationKind.EPUB)))
        assertEquals(setOf("e1", "p1"), two.toSet())
        val shelved = all.map { if (it.id == "p1") it.copy(collectionIds = setOf("s")) else it }
        assertEquals(listOf("p1"), LibraryViews.apply(shelved, LibraryQuery(collectionId = "s")).map { it.id })
        assertTrue(LibraryViews.apply(shelved, LibraryQuery(collectionId = "nope")).isEmpty())
    }

    @Test
    fun searchIsTokenwiseCaseAndAccentInsensitive() {
        assertEquals(listOf("a1"), ids(LibraryQuery(search = "emile")))
        assertEquals(listOf("a1"), ids(LibraryQuery(search = "  ROUSSEAU  jean ")))
        assertEquals(listOf("c1"), ids(LibraryQuery(search = "naruto")))
        assertEquals(listOf("e1"), ids(LibraryQuery(search = "herbert dune")))
        assertTrue(ids(LibraryQuery(search = "herbert naruto")).isEmpty())
        assertEquals(4, ids(LibraryQuery(search = "   ")).size)
    }

    @Test
    fun sortsFollowTheirKey() {
        assertEquals(listOf("a1", "c1", "e1", "p1"), ids(LibraryQuery(sort = LibrarySort.RECENT)))
        assertEquals(listOf("a1", "p1", "c1", "e1"), ids(LibraryQuery(sort = LibrarySort.ADDED)))
        // Titles Dune, Manual, Naruto 2, Émile: the accented title sorts after plain ASCII letters.
        assertEquals(listOf("e1", "p1", "c1", "a1"), ids(LibraryQuery(sort = LibrarySort.TITLE)))
        assertEquals("a1", ids(LibraryQuery(sort = LibrarySort.PROGRESS)).first())
    }

    @Test
    fun equalKeysFallBackToTitleThenId() {
        val twins = listOf(item("b", "Same"), item("a", "Same"), item("c", "Alpha"))
        val sorted = LibraryViews.apply(twins, LibraryQuery(sort = LibrarySort.TITLE))
        assertEquals(listOf("c", "a", "b"), sorted.map { it.id })
    }

    @Test
    fun continueReadingIsOpenedUnfinishedAndMostRecentFirst() {
        assertEquals(listOf("c1", "e1"), LibraryViews.continueReading(all, 10).map { it.id })
        assertEquals(listOf("c1"), LibraryViews.continueReading(all, 1).map { it.id })
        assertTrue(LibraryViews.continueReading(all, 0).isEmpty())
        assertTrue(LibraryViews.continueReading(all, -3).isEmpty())
    }

    @Test
    fun countsAreOneConsistentPass() {
        val counts = LibraryViews.counts(all)
        assertEquals(4, counts.total)
        assertEquals(1, counts.byKind[PublicationKind.PDF])
        assertEquals(2, counts.inProgress)
        assertEquals(1, counts.finished)
        assertEquals(1, counts.favorites)
        assertEquals(1, counts.unlinked)
    }
}
