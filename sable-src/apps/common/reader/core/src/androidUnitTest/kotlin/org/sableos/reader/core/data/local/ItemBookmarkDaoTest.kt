package org.sableos.reader.core.data.local

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.reader.model.LocatorCodec
import org.sableos.reader.model.PageLocator
import org.sableos.reader.model.TimeLocator
import org.sableos.reader.core.domain.model.ItemBookmarkEntity
import org.vaachak.reader.core.data.local.BaseDaoTest
import org.vaachak.reader.core.data.local.profile1
import org.vaachak.reader.core.domain.model.BookEntity

class ItemBookmarkDaoTest : BaseDaoTest() {

    private lateinit var dao: ItemBookmarkDao

    override fun onSetup() {
        dao = database.itemBookmarkDao()
    }

    private suspend fun book(hash: String, kind: String) =
        database.bookDao().insertBook(
            BookEntity(bookHash = hash, profileId = profile1, title = hash, author = "A", kind = kind)
        )

    @Test
    fun pageAndTimeBookmarksRoundTripThroughTypedLocators() = runTest {
        book("pdf", "PDF")
        book("m4b", "AUDIOBOOK")
        dao.upsertBookmark(
            ItemBookmarkEntity("p1", "pdf", profile1, LocatorCodec.encode(PageLocator(11, 200)), "Page 12", 1L)
        )
        val timeJson = LocatorCodec.encode(TimeLocator(90_000, 3_600_000, 2))
        dao.upsertBookmark(ItemBookmarkEntity("t1", "m4b", profile1, timeJson, "Nice", 2L))

        dao.getBookmarks("pdf", profile1).test {
            assertEquals(PageLocator(11, 200), LocatorCodec.decode(awaitItem().single().locatorJson))
            cancelAndIgnoreRemainingEvents()
        }
        dao.getBookmarks("m4b", profile1).test {
            assertEquals(TimeLocator(90_000, 3_600_000, 2), LocatorCodec.decode(awaitItem().single().locatorJson))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deletingTheItemRemovesItsBookmarks() = runTest {
        book("pdf", "PDF")
        dao.upsertBookmark(ItemBookmarkEntity("p1", "pdf", profile1, "{}", "x", 1L))

        database.bookDao().deleteBook("pdf", profile1)

        assertTrue(dao.getAllOnce(profile1).isEmpty())
    }

    @Test
    fun deleteBookmarkRemovesExactlyOne() = runTest {
        book("pdf", "PDF")
        dao.upsertBookmark(ItemBookmarkEntity("p1", "pdf", profile1, "{}", "x", 1L))
        dao.upsertBookmark(ItemBookmarkEntity("p2", "pdf", profile1, "{}", "y", 2L))

        dao.deleteBookmark("p1")

        assertEquals(listOf("p2"), dao.getAllOnce(profile1).map { it.bookmarkId })
    }
}
