package org.vaachak.reader.leisure.ui.bookshelf

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.vaachak.reader.core.common.AppCoroutineConfig
import org.vaachak.reader.core.data.local.BookDao
import org.vaachak.reader.core.data.local.HighlightDao
import org.vaachak.reader.core.data.repository.LibraryRepository
import org.vaachak.reader.core.data.repository.SettingsRepository
import org.vaachak.reader.core.data.repository.VaultRepository
import org.vaachak.reader.core.domain.model.BookEntity
import org.vaachak.reader.core.domain.model.BookshelfPreferences
import org.vaachak.reader.leisure.testutil.MainDispatcherRule

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BookshelfViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDispatcher get() = mainDispatcherRule.dispatcher

    private lateinit var bookDao: BookDao
    private lateinit var highlightDao: HighlightDao
    private lateinit var libraryRepository: LibraryRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var vaultRepository: VaultRepository
    private lateinit var viewModel: BookshelfViewModel

    private val fakeVaultId = MutableStateFlow("profile_piyush")

    private val dummyBook1 = BookEntity(
        bookHash = "1",
        profileId = "profile_piyush",
        title = "Dune",
        author = "Frank Herbert",
        progress = 0.5,
        language = "en"
    )

    @Before
    fun setup() {
        AppCoroutineConfig.mainOverride = testDispatcher
        AppCoroutineConfig.ioOverride = testDispatcher
        AppCoroutineConfig.defaultOverride = testDispatcher
        AppCoroutineConfig.sharingStartedOverride = kotlinx.coroutines.flow.SharingStarted.Eagerly

        bookDao = mockk(relaxed = true)
        highlightDao = mockk(relaxed = true)
        libraryRepository = mockk(relaxed = true)
        settingsRepo = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)

        every { settingsRepo.isEinkEnabled } returns flowOf(false)
        every { settingsRepo.bookshelfPreferences } returns flowOf(BookshelfPreferences())

        every { vaultRepository.activeVaultId } returns fakeVaultId

        every { bookDao.getAllBooksSortedByRecent(any()) } returns flowOf(listOf(dummyBook1))
        every { highlightDao.getBooksWithBookmarks(any()) } returns flowOf(emptyList())
        every { highlightDao.getHighlightsForBook(any(), any()) } returns flowOf(emptyList())

        viewModel = BookshelfViewModel(
            bookDao = bookDao,
            libraryRepository = libraryRepository,
            highlightDao = highlightDao,
            settingsRepo = settingsRepo,
            vaultRepository = vaultRepository
        )
    }

    @After
    fun tearDown() {
        AppCoroutineConfig.reset()
    }

    @Test
    fun `updateSearchQuery filters the library list correctly`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.updateSearchQuery("Dune")
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertEquals("Dune", state.searchQuery)
        assertTrue(state.groupedLibrary.isNotEmpty())
        assertEquals("Dune", state.groupedLibrary.values.flatten().first().title)
    }

    @Test
    fun `library always reports the physical book count`() = runTest(testDispatcher) {
        val second = dummyBook1.copy(bookHash = "2", title = "Dune Messiah")
        every { bookDao.getAllBooksSortedByRecent(any()) } returns flowOf(listOf(dummyBook1, second))
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertEquals(2, state.groupedLibrary.values.flatten().map { it.bookHash }.distinct().size)
    }

    @Test
    fun `deleteBookByUri deletes within the active profile only`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.deleteBookByUri("content://dune")
        advanceUntilIdle()

        coVerify(exactly = 1) { bookDao.deleteBookByUri("content://dune", "profile_piyush") }
    }
}
