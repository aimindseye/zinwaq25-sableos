package org.sableos.reader.core.data.local

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.reader.core.domain.model.CollectionEntity
import org.sableos.reader.core.domain.model.CollectionItemEntity
import org.vaachak.reader.core.data.local.BaseDaoTest
import org.vaachak.reader.core.data.local.profile1
import org.vaachak.reader.core.data.local.profile2
import org.vaachak.reader.core.domain.model.BookEntity

class CollectionDaoTest : BaseDaoTest() {

    private lateinit var dao: CollectionDao

    override fun onSetup() {
        dao = database.collectionDao()
    }

    private suspend fun book(hash: String, profile: String = profile1, kind: String = "EPUB") =
        database.bookDao().insertBook(
            BookEntity(bookHash = hash, profileId = profile, title = hash, author = "A", kind = kind)
        )

    @Test
    fun collectionsAreIsolatedPerProfileAndOrderedByName() = runTest {
        dao.upsertCollection(CollectionEntity("c1", profile1, "beta"))
        dao.upsertCollection(CollectionEntity("c2", profile1, "Alpha"))
        dao.upsertCollection(CollectionEntity("c3", profile2, "Other"))

        dao.getCollections(profile1).test {
            assertEquals(listOf("Alpha", "beta"), awaitItem().map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aCollectionCanMixEpubComicAndAudiobookItems() = runTest {
        book("epub", kind = "EPUB")
        book("cbz", kind = "COMIC")
        book("m4b", kind = "AUDIOBOOK")
        dao.upsertCollection(CollectionEntity("c1", profile1, "Mixed"))
        listOf("epub", "cbz", "m4b").forEach { dao.addItem(CollectionItemEntity("c1", it, profile1)) }

        assertEquals(3, dao.getMembershipsOnce(profile1).size)
    }

    @Test
    fun addingTheSameItemTwiceIsIdempotent() = runTest {
        book("epub")
        dao.upsertCollection(CollectionEntity("c1", profile1, "Shelf"))

        dao.addItem(CollectionItemEntity("c1", "epub", profile1))
        dao.addItem(CollectionItemEntity("c1", "epub", profile1))

        assertEquals(1, dao.getMembershipsOnce(profile1).size)
    }

    @Test
    fun deletingABookRemovesItsMembershipsButNotTheCollection() = runTest {
        book("epub")
        book("cbz", kind = "COMIC")
        dao.upsertCollection(CollectionEntity("c1", profile1, "Shelf"))
        dao.addItem(CollectionItemEntity("c1", "epub", profile1))
        dao.addItem(CollectionItemEntity("c1", "cbz", profile1))

        database.bookDao().deleteBook("epub", profile1)

        assertEquals(listOf("cbz"), dao.getMembershipsOnce(profile1).map { it.bookHash })
        assertEquals(1, dao.getCollectionsOnce(profile1).size)
    }

    @Test
    fun deletingACollectionRemovesMembershipsButNeverTheBooks() = runTest {
        book("epub")
        dao.upsertCollection(CollectionEntity("c1", profile1, "Shelf"))
        dao.addItem(CollectionItemEntity("c1", "epub", profile1))

        dao.deleteCollection("c1", profile1)

        assertTrue(dao.getMembershipsOnce(profile1).isEmpty())
        assertEquals("epub", database.bookDao().getBookByHash("epub", profile1)!!.bookHash)
    }

    @Test
    fun renamingOrReUpsertingACollectionKeepsItsMemberships() = runTest {
        book("epub")
        dao.upsertCollection(CollectionEntity("c1", profile1, "Shelf"))
        dao.addItem(CollectionItemEntity("c1", "epub", profile1))

        dao.renameCollection("c1", profile1, "Renamed")
        dao.upsertCollection(CollectionEntity("c1", profile1, "Renamed again"))

        assertEquals(1, dao.getMembershipsOnce(profile1).size)
        assertEquals("Renamed again", dao.getCollectionsOnce(profile1).single().name)
    }

    @Test
    fun removeItemOnlyRemovesTheTargetedMembership() = runTest {
        book("a")
        book("b")
        dao.upsertCollection(CollectionEntity("c1", profile1, "Shelf"))
        dao.addItem(CollectionItemEntity("c1", "a", profile1))
        dao.addItem(CollectionItemEntity("c1", "b", profile1))

        dao.removeItem("c1", "a", profile1)

        assertEquals(listOf("b"), dao.getMembershipsOnce(profile1).map { it.bookHash })
    }
}
