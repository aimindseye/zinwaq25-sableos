package org.vaachak.reader.core.data.local

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.vaachak.reader.core.domain.model.OpdsEntity

class OpdsDaoTest : BaseDaoTest() {

    private lateinit var dao: OpdsDao

    override fun onSetup() {
        dao = database.opdsDao()
    }

    private fun createDummyFeed(
        id: Long = 0L,
        title: String,
        url: String,
        isPredefined: Boolean = false
    ) = OpdsEntity(id = id, title = title, url = url, isPredefined = isPredefined)

    @Test
    fun `getAllFeeds sorts predefined feeds first, then alphabetically`() = runTest {
        dao.insertFeed(createDummyFeed(id = 1, title = "Zebra Feed", url = "url1", isPredefined = false))
        dao.insertFeed(createDummyFeed(id = 2, title = "Project Gutenberg", url = "url2", isPredefined = true))
        dao.insertFeed(createDummyFeed(id = 3, title = "Apple Feed", url = "url3", isPredefined = false))

        dao.getAllFeeds().test {
            val feeds = awaitItem()
            assertEquals(3, feeds.size)
            assertEquals("Project Gutenberg", feeds[0].title)
            assertEquals("Apple Feed", feeds[1].title)
            assertEquals("Zebra Feed", feeds[2].title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getFeedsCount returns accurate total`() = runTest {
        assertEquals(0, dao.getFeedsCount())
        dao.insertFeed(createDummyFeed(title = "Test", url = "url1"))
        assertEquals(1, dao.getFeedsCount())
    }
}