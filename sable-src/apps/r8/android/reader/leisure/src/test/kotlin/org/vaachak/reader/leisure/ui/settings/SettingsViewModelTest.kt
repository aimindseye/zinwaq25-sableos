package org.vaachak.reader.leisure.ui.settings

import android.content.Context
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.vaachak.reader.core.common.AppCoroutineConfig
import org.vaachak.reader.core.data.local.ProfileDao
import org.vaachak.reader.core.data.repository.OpdsRepository
import org.vaachak.reader.core.data.repository.SettingsRepository
import org.vaachak.reader.core.data.repository.SyncRepository
import org.vaachak.reader.core.data.repository.VaultRepository
import org.vaachak.reader.core.domain.model.BookshelfPreferences
import org.vaachak.reader.core.domain.model.ReaderPreferences
import org.vaachak.reader.core.domain.model.ThemeMode
import org.vaachak.reader.leisure.testutil.MainDispatcherRule

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDispatcher get() = mainDispatcherRule.dispatcher

    private lateinit var settingsRepo: SettingsRepository
    private lateinit var syncRepo: SyncRepository
    private lateinit var opdsRepo: OpdsRepository
    private lateinit var vaultRepository: VaultRepository
    private lateinit var profileDao: ProfileDao
    private lateinit var context: Context
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        AppCoroutineConfig.mainOverride = testDispatcher
        AppCoroutineConfig.ioOverride = testDispatcher
        AppCoroutineConfig.defaultOverride = testDispatcher
        AppCoroutineConfig.sharingStartedOverride = kotlinx.coroutines.flow.SharingStarted.Eagerly

        settingsRepo = mockk(relaxed = true)
        syncRepo = mockk(relaxed = true)
        opdsRepo = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        profileDao = mockk(relaxed = true)
        context = mockk(relaxed = true)

        every { vaultRepository.hasCompletedOnboarding } returns MutableStateFlow(true)
        every { vaultRepository.isOfflineMode } returns MutableStateFlow(false)
        every { vaultRepository.activeVaultId } returns MutableStateFlow("profile_1")
        every { vaultRepository.isMultiUserMode } returns MutableStateFlow(true)

        every { profileDao.getAllProfiles() } returns flowOf(emptyList())

        every { settingsRepo.syncUsername } returns flowOf("Piyush")
        every { settingsRepo.deviceName } returns flowOf("Boox")
        every { settingsRepo.lastSyncTimestamp } returns flowOf(0L)
        every { settingsRepo.isOfflineModeEnabled } returns flowOf(false)

        every { settingsRepo.geminiKey } returns flowOf("")
        every { settingsRepo.cfUrl } returns flowOf("")
        every { settingsRepo.cfToken } returns flowOf("")
        every { settingsRepo.isAutoSaveRecapsEnabled } returns flowOf(false)

        every { settingsRepo.themeMode } returns flowOf(ThemeMode.LIGHT)
        every { settingsRepo.einkContrast } returns flowOf(1f)
        every { settingsRepo.bookshelfPreferences } returns flowOf(BookshelfPreferences())
        every { settingsRepo.readerPreferences } returns flowOf(ReaderPreferences())

        every { settingsRepo.ttsSettings } returns flowOf(mockk(relaxed = true))
        every { opdsRepo.catalogs } returns flowOf(emptyList())

        every { settingsRepo.isSyncEnabled } returns flowOf(false)
        every { settingsRepo.syncCloudUrl } returns flowOf("")
        every { settingsRepo.localServerUrl } returns flowOf("")
        every { settingsRepo.useLocalServer } returns flowOf(false)

        every { settingsRepo.getUseEmbeddedDictionary() } returns flowOf(false)
        every { settingsRepo.getDictionaryFolder() } returns flowOf("")

        viewModel = SettingsViewModel(
            settingsRepo = settingsRepo,
            syncRepo = syncRepo,
            opdsRepo = opdsRepo,
            vaultRepository = vaultRepository,
            profileDao = profileDao,
            context = context
        )
    }

    @After
    fun tearDown() {
        AppCoroutineConfig.reset()
    }

    @Test
    fun `logout clears sync profile and resets vault to default`() = runTest(testDispatcher) {
        viewModel.logout()
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepo.updateSyncProfile("", "", "") }
        coVerify(exactly = 1) { settingsRepo.setLastSyncTimestamp(0L) }
        coVerify(exactly = 1) { vaultRepository.setActiveVaultId(VaultRepository.DEFAULT_VAULT_ID) }
    }
}