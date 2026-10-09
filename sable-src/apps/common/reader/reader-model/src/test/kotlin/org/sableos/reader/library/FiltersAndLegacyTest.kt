package org.sableos.reader.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.reader.backup.IdScope
import org.sableos.reader.model.EpubLocator
import org.sableos.reader.model.LocatorCodec
import org.sableos.reader.model.PageLocator
import org.sableos.reader.model.PublicationKind

class FiltersAndLegacyTest {
    private fun counts(
        byKind: Map<PublicationKind, Int> = emptyMap(),
        inProgress: Int = 0,
        finished: Int = 0,
        favorites: Int = 0,
        unlinked: Int = 0,
    ) = LibraryCounts(byKind.values.sum(), byKind, inProgress, finished, favorites, unlinked)

    @Test
    fun chipsAppearOnlyWhenTheyLeadSomewhere() {
        assertEquals(listOf("All"), LibraryFilters.labels(counts()))
        val full = counts(
            byKind = mapOf(PublicationKind.AUDIOBOOK to 1, PublicationKind.EPUB to 4, PublicationKind.COMIC to 2),
            inProgress = 2,
            finished = 1,
            favorites = 3,
            unlinked = 1,
        )
        assertEquals(
            listOf("All", "Continue", "Favorites", "Finished", "Books", "Comics", "Audiobooks", "Needs file"),
            LibraryFilters.labels(full),
        )
    }

    @Test
    fun kindLabelsRoundTrip() {
        PublicationKind.entries.forEach { assertEquals(it, LibraryFilters.kindOfLabel(LibraryFilters.kindLabel(it))) }
        assertNull(LibraryFilters.kindOfLabel("All"))
        assertNull(LibraryFilters.kindOfLabel("en"))
    }

    @Test
    fun inProgressNeedsOpenedUnfinishedAndSomeProgress() {
        assertTrue(LibraryFilters.isInProgress(false, 5L, 0.1))
        assertFalse(LibraryFilters.isInProgress(true, 5L, 0.1))
        assertFalse(LibraryFilters.isInProgress(false, 0L, 0.1))
        assertFalse(LibraryFilters.isInProgress(false, 5L, 0.0))
    }

    @Test
    fun typedProgressWinsAndLegacyEpubRowsAreWrapped() {
        val typed = LocatorCodec.encode(PageLocator(3, 10))
        assertEquals(PageLocator(3, 10), LegacyProgress.of(typed, 0.9, "x", 7L)?.locator)
        val legacy = LegacyProgress.of(null, 0.4, "{\"href\":\"a\"}", 7L)
        assertEquals(EpubLocator("{\"href\":\"a\"}", 0.4), legacy?.locator)
        assertEquals(7L, legacy?.updatedAt)
        assertNull(LegacyProgress.of(null, 0.0, "x", 7L))
        assertNull(LegacyProgress.of("garbage", 0.0, null, 7L))
        assertEquals(1.0, LegacyProgress.of(null, 5.0, null, 1L)?.fraction ?: 0.0, 0.0)
    }

    @Test
    fun idsAreScopedPerProfileAndStableThroughARoundTrip() {
        assertEquals("abc", IdScope.scoped("default", "abc"))
        assertEquals("p2:abc", IdScope.scoped("p2", "abc"))
        assertEquals("abc", IdScope.canonical("p2", "p2:abc"))
        assertEquals("abc", IdScope.canonical("p2", "abc"))
        assertEquals("abc", IdScope.canonical("default", "abc"))
        val stored = IdScope.scoped("p2", IdScope.canonical("p2", "p2:abc"))
        assertEquals("p2:abc", stored)
        assertEquals("p2:abc", IdScope.scoped("p2", "abc"))
        assertFalse(IdScope.scoped("p2", "abc") == IdScope.scoped("p3", "abc"))
    }
}
