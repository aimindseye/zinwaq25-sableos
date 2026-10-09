package org.vaachak.reader.core.data.local

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.vaachak.reader.core.domain.model.BookEntity

class BookDaoTest : BaseDaoTest() {

    private lateinit var dao: BookDao

    override fun onSetup() {
        dao = database.bookDao()
    }

    private fun createDummyBook(
        hash: String,
        profileId: String,
        title: String = "Test Book",
        uri: String = "content://dummy/$hash",
        progress: Double = 0.0,
        updatedAt: Long = 1000L,
        isDirty: Boolean = false
    ) = BookEntity(
        bookHash = hash,
        profileId = profileId,
        title = title,
        localUri = uri,
        progress = progress,
        lastCfiLocation = "",
        lastRead = updatedAt,
        updatedAt = updatedAt,
        isDirty = isDirty,
        coverPath = null,
        author = "Author"
    )

    @Test
    fun `insertBook and getAllBooks validates multi-tenant isolation`() = runTest {
        val book1 = createDummyBook(hash = "hash1", profileId = profile1)
        val book2 = createDummyBook(hash = "hash2", profileId = profile2)

        dao.insertBook(book1)
        dao.insertBook(book2)

        dao.getAllBooks(profile1).test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("hash1", items.first().bookHash)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isBookExists returns true only for exact title and profile match`() = runTest {
        val book = createDummyBook(hash = "hash1", profileId = profile1, title = "The Martian")
        dao.insertBook(book)

        assertTrue(dao.isBookExists("The Martian", profile1))
        assertFalse(dao.isBookExists("The Martian", profile2))
        assertFalse(dao.isBookExists("Dune", profile1))
    }

    @Test
    fun `updateBookProgressByUri updates progress and flags as dirty`() = runTest {
        val uri = "content://book1"
        val book = createDummyBook(hash = "hash1", profileId = profile1, uri = uri, progress = 0.1, isDirty = false)
        dao.insertBook(book)

        val newTimestamp = 5000L
        dao.updateBookProgressByUri(uri, profile1, 0.5, "/4/2", newTimestamp)

        val updatedBook = dao.getBookByUri(uri, profile1)
        assertNotNull(updatedBook)
        assertEquals(0.5, updatedBook!!.progress, 0.0)
        assertTrue(updatedBook.isDirty)
    }

    @Test
    fun `updateBookMetadataFromSync applies incoming cloud data and clears dirty flag`() = runTest {
        val book = createDummyBook(hash = "hash1", profileId = profile1, updatedAt = 1000L, isDirty = true)
        dao.insertBook(book)

        dao.updateBookMetadataFromSync("hash1", profile1, "/new/cfi", 0.99, 2000L)

        val syncedBook = dao.getBookByHash("hash1", profile1)
        assertEquals(0.99, syncedBook!!.progress, 0.0)
        assertFalse(syncedBook.isDirty)
    }

    @Test
    fun `updateBookMetadataFromSync ignores stale cloud data`() = runTest {
        val book = createDummyBook(hash = "hash1", profileId = profile1, progress = 0.5, updatedAt = 5000L)
        dao.insertBook(book)

        dao.updateBookMetadataFromSync("hash1", profile1, "/old/cfi", 0.1, 2000L)

        val unchangedBook = dao.getBookByHash("hash1", profile1)
        assertEquals(0.5, unchangedBook!!.progress, 0.0)
    }

    @Test
    fun `deleteBook strictly removes only the targeted profile's book`() = runTest {
        dao.insertBook(createDummyBook(hash = "hash1", profileId = profile1))
        dao.insertBook(createDummyBook(hash = "hash1", profileId = profile2))

        dao.deleteBook("hash1", profile1)

        assertNull(dao.getBookByHash("hash1", profile1))
        assertNotNull(dao.getBookByHash("hash1", profile2))
    }
}