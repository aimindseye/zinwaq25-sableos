package org.vaachak.reader.core.data.local

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.vaachak.reader.core.domain.model.BookEntity
import org.vaachak.reader.core.domain.model.HighlightEntity
import java.util.UUID

class HighlightDaoTest : BaseDaoTest() {
    private lateinit var dao: HighlightDao
    private lateinit var bookDao: BookDao

    override fun onSetup() {
        dao = database.highlightDao()
        bookDao = database.bookDao()
    }

    private fun createDummyBook(
        hash: String,
        profileId: String,
        title: String = "Parent Book",
        uri: String = "content://dummy/$hash"
    ) = BookEntity(
        bookHash = hash,
        profileId = profileId,
        title = title,
        localUri = uri,
        progress = 0.0,
        lastCfiLocation = "",
        lastRead = 1000L,
        updatedAt = 1000L,
        isDirty = false,
        coverPath = null,
        author = "Author"
    )

    private suspend fun insertParentBook(profileId: String, bookHashId: String = "book_1") {
        bookDao.insertBook(createDummyBook(hash = bookHashId, profileId = profileId))
    }

    private fun createDummyHighlight(
        profileId: String,
        bookHashId: String = "book_1",
        tag: String = "note",
        id: String = UUID.randomUUID().toString()
    ) = HighlightEntity(
        id = id,
        bookHashId = bookHashId,
        profileId = profileId,
        locatorJson = "{}",
        text = "Sample text",
        color = 0,
        tag = tag,
        created = 1000L
    )

    @Test
    fun `getHighlightsForBook strictly isolates by profileId`() = runTest {
        insertParentBook(profile1, "book_1")
        insertParentBook(profile2, "book_1")

        dao.insertHighlight(createDummyHighlight(profileId = profile1, bookHashId = "book_1"))
        dao.insertHighlight(createDummyHighlight(profileId = profile2, bookHashId = "book_1"))

        dao.getHighlightsForBook("book_1", profile1).test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals(profile1, items.first().profileId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getBooksWithBookmarks retrieves only books marked with BOOKMARK tag for specific profile`() = runTest {
        insertParentBook(profile1, "book_1")
        insertParentBook(profile1, "book_2")

        dao.insertHighlight(createDummyHighlight(profileId = profile1, bookHashId = "book_1", tag = "BOOKMARK"))
        dao.insertHighlight(createDummyHighlight(profileId = profile1, bookHashId = "book_2", tag = "note"))

        dao.getBooksWithBookmarks(profile1).test {
            val bookHashes = awaitItem()
            assertEquals(1, bookHashes.size)
            assertEquals("book_1", bookHashes.first())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteHighlightById removes specific record`() = runTest {
        val id = UUID.randomUUID().toString()
        insertParentBook(profile1, "book_1")

        dao.insertHighlight(createDummyHighlight(profileId = profile1, bookHashId = "book_1", id = id))

        dao.deleteHighlightById(id)

        dao.getHighlightsForBook("book_1", profile1).test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}