package org.vaachak.reader.leisure.ui.catalog

import android.app.Application
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.vaachak.reader.core.data.repository.GutendexRepository
import org.vaachak.reader.core.data.repository.LibraryRepository
import org.vaachak.reader.core.data.repository.OpdsRepository
import org.vaachak.reader.core.data.repository.SettingsRepository
import org.vaachak.reader.core.domain.model.OpdsEntity
import org.vaachak.reader.leisure.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelUrlHardeningTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val opdsRepository: OpdsRepository = mockk()
    private val gutendexRepository: GutendexRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk()
    private val libraryRepository: LibraryRepository = mockk(relaxed = true)
    private val application: Application = mockk(relaxed = true)

    private fun buildViewModel(): CatalogViewModel {
        every { opdsRepository.catalogs } returns flowOf(emptyList())
        every { opdsRepository.allFeeds } returns flowOf(emptyList())

        every { settingsRepository.isEinkEnabled } returns flowOf(false)
        every { settingsRepository.isOfflineModeEnabled } returns flowOf(false)

        return CatalogViewModel(
            opdsRepository = opdsRepository,
            gutendexRepository = gutendexRepository,
            settingsRepo = settingsRepository,
            libraryRepository = libraryRepository,
            application = application
        )
    }

    @Test
    fun addCatalog_defaultsBlankTitleAndForcesHttpsOpdsUrl() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val inserted = slot<OpdsEntity>()
            coEvery { opdsRepository.insertFeed(capture(inserted)) } returns Result.success("Catalog has been saved")

            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.addCatalog(
                title = "",
                url = "mac-mini.tail1f3687.ts.net/calibre",
                user = "",
                pass = ""
            )

            advanceUntilIdle()

            assertEquals("Library", inserted.captured.title)
            assertEquals(
                "https://mac-mini.tail1f3687.ts.net/calibre/opds",
                inserted.captured.url
            )
            assertNull(inserted.captured.username)
            assertNull(inserted.captured.password)
        }

    @Test
    fun addCatalog_keepsGutendexBooksUrlWithoutAppendingOpds() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val inserted = slot<OpdsEntity>()
            coEvery { opdsRepository.insertFeed(capture(inserted)) } returns Result.success("Catalog has been saved")

            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.addCatalog(
                title = "Gutendex",
                url = "https://gutendex.com/books",
                user = null,
                pass = null
            )

            advanceUntilIdle()

            assertEquals("https://gutendex.com/books", inserted.captured.url)
        }

    @Test
    fun updateCatalog_upgradesHttpToHttpsAndPreservesEntityIdentity() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val updated = slot<OpdsEntity>()
            coEvery { opdsRepository.updateFeed(capture(updated)) } returns Result.success("Catalog has been updated")

            val existing = OpdsEntity(
                id = 7L,
                title = "Old Title",
                url = "https://old.example/opds",
                username = "oldUser",
                password = "oldPass",
                isPredefined = true
            )

            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.updateCatalog(
                feed = existing,
                title = "My Calibre",
                url = "http://mac-mini.tail1f3687.ts.net/calibre/opds",
                user = "",
                pass = ""
            )

            advanceUntilIdle()

            assertEquals(7L, updated.captured.id)
            assertEquals("My Calibre", updated.captured.title)
            assertEquals(
                "https://mac-mini.tail1f3687.ts.net/calibre/opds",
                updated.captured.url
            )
            assertEquals(true, updated.captured.isPredefined)
            assertNull(updated.captured.username)
            assertNull(updated.captured.password)
        }

    @Test
    fun addCatalog_keepsXmlFeedUrlAndUpgradesToHttps() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val inserted = slot<OpdsEntity>()
            coEvery { opdsRepository.insertFeed(capture(inserted)) } returns Result.success("Catalog has been saved")

            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.addCatalog(
                title = "Custom Feed",
                url = "http://example.com/catalog.xml",
                user = null,
                pass = null
            )

            advanceUntilIdle()

            assertEquals("Custom Feed", inserted.captured.title)
            assertEquals(
                "https://example.com/catalog.xml",
                inserted.captured.url
            )
            assertNull(inserted.captured.username)
            assertNull(inserted.captured.password)
        }

    @Test
    fun updateCatalog_keepsNonBlankCredentials() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val updated = slot<OpdsEntity>()
            coEvery { opdsRepository.updateFeed(capture(updated)) } returns Result.success("Catalog has been updated")

            val existing = OpdsEntity(
                id = 99L,
                title = "Existing",
                url = "https://existing.example/opds"
            )

            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.updateCatalog(
                feed = existing,
                title = "Secure Feed",
                url = "https://secure.example/opds",
                user = "reader",
                pass = "secret"
            )

            advanceUntilIdle()

            assertEquals("reader", updated.captured.username)
            assertEquals("secret", updated.captured.password)
        }

    @Test
    fun updateCatalog_keepsNullCredentialsAsNull() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val updated = slot<OpdsEntity>()
            coEvery { opdsRepository.updateFeed(capture(updated)) } returns Result.success("Catalog has been updated")

            val existing = OpdsEntity(
                id = 100L,
                title = "Existing",
                url = "https://existing.example/opds",
                username = "old-user",
                password = "old-pass"
            )

            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.updateCatalog(
                feed = existing,
                title = "Secure Feed",
                url = "https://secure.example/opds",
                user = null,
                pass = null
            )

            advanceUntilIdle()

            assertNull(updated.captured.username)
            assertNull(updated.captured.password)
        }
}
