package org.vaachak.reader.core.data.local

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.vaachak.reader.core.domain.model.BookEntity
import org.vaachak.reader.core.domain.model.HighlightEntity

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
    fun `updateEpubProgressByUri stores fraction locator and typed progress`() = runTest {
        val uri = "content://book1"
        dao.insertBook(createDummyBook(hash = "hash1", profileId = profile1, uri = uri, progress = 0.1))

        dao.updateEpubProgressByUri(uri, profile1, 0.5, "/4/2", "{\"type\":\"epub\"}", false, 5000L)

        val updated = dao.getBookByUri(uri, profile1)!!
        assertEquals(0.5, updated.progress, 0.0)
        assertEquals("/4/2", updated.lastCfiLocation)
        assertEquals("{\"type\":\"epub\"}", updated.progressJson)
        assertEquals(5000L, updated.lastRead)
        assertFalse(updated.finished)
    }

    @Test
    fun `finished is sticky once reached`() = runTest {
        val uri = "content://book1"
        dao.insertBook(createDummyBook(hash = "hash1", profileId = profile1, uri = uri))

        dao.updateEpubProgressByUri(uri, profile1, 1.0, "end", null, true, 10L)
        dao.updateEpubProgressByUri(uri, profile1, 0.2, "back", null, false, 20L)

        assertTrue(dao.getBookByUri(uri, profile1)!!.finished)
    }

    @Test
    fun `new books default to epub kind and no favorite`() = runTest {
        dao.insertBook(createDummyBook(hash = "hash1", profileId = profile1))

        val book = dao.getBookByHash("hash1", profile1)!!
        assertEquals("EPUB", book.kind)
        assertFalse(book.favorite)
        assertFalse(book.finished)
    }

    @Test
    fun `getBooksByKind separates publication kinds`() = runTest {
        dao.insertBook(createDummyBook(hash = "e1", profileId = profile1))
        dao.insertBook(createDummyBook(hash = "c1", profileId = profile1).copy(kind = "COMIC"))
        dao.insertBook(createDummyBook(hash = "a1", profileId = profile1).copy(kind = "AUDIOBOOK"))

        dao.getBooksByKind(profile1, "COMIC").test {
            assertEquals(listOf("c1"), awaitItem().map { it.bookHash })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upsertItem updates in place without cascading child rows`() = runTest {
        dao.insertBook(createDummyBook(hash = "hash1", profileId = profile1, title = "Old"))
        val highlight = HighlightEntity(
            id = "h1", bookHashId = "hash1", profileId = profile1,
            locatorJson = "{}", text = "kept", color = 0
        )
        database.highlightDao().insertHighlight(highlight)

        dao.upsertItem(createDummyBook(hash = "hash1", profileId = profile1, title = "New"))

        assertEquals("New", dao.getBookByHash("hash1", profile1)!!.title)
        database.highlightDao().getHighlightsForBook("hash1", profile1).test {
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `favorite finished and metadata edits are local and targeted`() = runTest {
        dao.insertBook(createDummyBook(hash = "hash1", profileId = profile1))
        dao.insertBook(createDummyBook(hash = "hash1", profileId = profile2))

        dao.setFavorite("hash1", profile1, true)
        dao.setFinished("hash1", profile1, true)
        dao.updateMetadata("hash1", profile1, "Edited", "Sub", "A; B", "Saga", 2.0)

        val edited = dao.getBookByHash("hash1", profile1)!!
        assertTrue(edited.favorite)
        assertTrue(edited.finished)
        assertEquals("Edited", edited.title)
        assertEquals("Sub", edited.subtitle)
        assertEquals("A; B", edited.author)
        assertEquals("Saga", edited.seriesName)
        assertEquals(2.0, edited.seriesIndex!!, 0.0)
        assertFalse(dao.getBookByHash("hash1", profile2)!!.favorite)
    }

    @Test
    fun `relinkSource re-points the content uri only`() = runTest {
        dao.insertBook(createDummyBook(hash = "hash1", profileId = profile1, uri = "content://old"))

        dao.relinkSource("hash1", profile1, "content://new")

        val book = dao.getBookByHash("hash1", profile1)!!
        assertEquals("content://new", book.localUri)
        assertEquals("Test Book", book.title)
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