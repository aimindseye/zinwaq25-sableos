package org.vaachak.reader.leisure.ui.bookshelf

import io.mockk.coEvery
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
import org.vaachak.reader.core.data.repository.AiRepository
import org.vaachak.reader.core.data.repository.LibraryRepository
import org.vaachak.reader.core.data.repository.SettingsRepository
import org.vaachak.reader.core.data.repository.SyncRepository
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
    private lateinit var aiRepository: AiRepository
    private lateinit var syncRepository: SyncRepository
    private lateinit var viewModel: BookshelfViewModel

    private val fakeVaultId = MutableStateFlow("profile_piyush")
    private val fakeOfflineMode = MutableStateFlow(false)

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
        AppCoroutineConfig.bookshelfStartupSyncDelayMsOverride = 0L

        bookDao = mockk(relaxed = true)
        highlightDao = mockk(relaxed = true)
        libraryRepository = mockk(relaxed = true)
        settingsRepo = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        aiRepository = mockk(relaxed = true)
        syncRepository = mockk(relaxed = true)

        every { settingsRepo.isOfflineModeEnabled } returns fakeOfflineMode
        every { settingsRepo.isEinkEnabled } returns flowOf(false)
        every { settingsRepo.lastSyncTimestamp } returns flowOf(0L)
        every { settingsRepo.syncUsername } returns flowOf("")
        every { settingsRepo.bookshelfPreferences } returns flowOf(BookshelfPreferences())

        every { vaultRepository.activeVaultId } returns fakeVaultId

        every { bookDao.getAllBooksSortedByRecent(any()) } returns flowOf(listOf(dummyBook1))
        every { highlightDao.getBooksWithBookmarks(any()) } returns flowOf(emptyList())
        every { highlightDao.getHighlightsForBook(any(), any()) } returns flowOf(emptyList())

        coEvery { syncRepository.sync() } returns Result.success(Unit)

        viewModel = BookshelfViewModel(
            bookDao = bookDao,
            libraryRepository = libraryRepository,
            highlightDao = highlightDao,
            aiRepository = aiRepository,
            settingsRepo = settingsRepo,
            syncRepository = syncRepository,
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
    fun `getQuickRecap blocks AI request when Offline Mode is enabled`() = runTest(testDispatcher) {
        advanceUntilIdle()

        fakeOfflineMode.value = true
        advanceUntilIdle()

        viewModel.getQuickRecap(dummyBook1)
        advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assertEquals(
            "Offline Mode enabled. Connect to use Recall.",
            finalState.snackbarMessage
        )
        coVerify(exactly = 0) { aiRepository.getRecallSummary(any(), any()) }
    }
}