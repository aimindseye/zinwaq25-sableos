package org.vaachak.reader.leisure.ui.reader

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.robolectric.RobolectricTestRunner
import org.vaachak.reader.core.common.AppCoroutineConfig
import org.vaachak.reader.core.data.local.BookDao
import org.vaachak.reader.core.data.local.HighlightDao
import org.vaachak.reader.core.data.repository.AiRepository
import org.vaachak.reader.core.data.repository.DictionaryRepository
import org.vaachak.reader.core.data.repository.ReadiumManager
import org.vaachak.reader.core.data.repository.SettingsRepository
import org.vaachak.reader.core.data.repository.SyncRepository
import org.vaachak.reader.core.data.repository.VaultRepository
import org.vaachak.reader.core.domain.model.ReaderPreferences
import org.vaachak.reader.core.domain.model.TtsSettings
import org.vaachak.reader.leisure.testutil.MainDispatcherRule

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDispatcher get() = mainDispatcherRule.dispatcher

    private lateinit var application: Application
    private lateinit var aiRepository: AiRepository
    private lateinit var readiumManager: ReadiumManager
    private lateinit var highlightDao: HighlightDao
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var dictionaryRepository: DictionaryRepository
    private lateinit var syncRepository: SyncRepository
    private lateinit var bookDao: BookDao
    private lateinit var vaultRepository: VaultRepository
    private lateinit var viewModel: ReaderViewModel

    @Before
    fun setup() {
        AppCoroutineConfig.mainOverride = testDispatcher
        AppCoroutineConfig.ioOverride = testDispatcher
        AppCoroutineConfig.defaultOverride = testDispatcher
        AppCoroutineConfig.sharingStartedOverride = kotlinx.coroutines.flow.SharingStarted.Eagerly

        application = mockk(relaxed = true)
        aiRepository = mockk(relaxed = true)
        readiumManager = mockk(relaxed = true)
        highlightDao = mockk(relaxed = true)
        settingsRepo = mockk(relaxed = true)
        dictionaryRepository = mockk(relaxed = true)
        syncRepository = mockk(relaxed = true)
        bookDao = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)

        every { vaultRepository.activeVaultId } returns MutableStateFlow("profile_piyush")

        every { settingsRepo.isEinkEnabled } returns flowOf(false)
        every { settingsRepo.isOfflineModeEnabled } returns flowOf(false)
        every { settingsRepo.readerPreferences } returns flowOf(ReaderPreferences())
        every { settingsRepo.ttsSettings } returns flowOf(
            TtsSettings(1.0f, true, "underline", "default", false, 0, 0.5f, "default")
        )

        viewModel = ReaderViewModel(
            application = application,
            aiRepository = aiRepository,
            readiumManager = readiumManager,
            highlightDao = highlightDao,
            settingsRepo = settingsRepo,
            dictionaryRepository = dictionaryRepository,
            syncRepository = syncRepository,
            bookDao = bookDao,
            vaultRepository = vaultRepository
        )
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        AppCoroutineConfig.reset()
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `closeBook securely saves progress using active vault profile`() = runTest(testDispatcher) {
        val mockUri = "content://dummy/book.epub"
        val mockLocator = mockk<Locator>(relaxed = true)

        every { mockLocator.locations.totalProgression } returns 0.75
        every { mockLocator.toJSON().toString() } returns "{\"mock\":true}"

        val uriField = ReaderViewModel::class.java.getDeclaredField("_currentLocalUri")
        uriField.isAccessible = true
        (uriField.get(viewModel) as MutableStateFlow<String?>).value = mockUri

        val locatorField = ReaderViewModel::class.java.getDeclaredField("_currentLocator")
        locatorField.isAccessible = true
        (locatorField.get(viewModel) as MutableStateFlow<Locator?>).value = mockLocator

        viewModel.closeBook()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            bookDao.updateBookProgressByUri(
                eq(mockUri),
                eq("profile_piyush"),
                eq(0.75),
                any(),
                any()
            )
        }
    }
}