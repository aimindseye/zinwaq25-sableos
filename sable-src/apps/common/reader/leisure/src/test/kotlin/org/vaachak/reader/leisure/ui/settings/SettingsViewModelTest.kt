package org.vaachak.reader.leisure.ui.settings

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
import org.vaachak.reader.core.data.local.ProfileDao
import org.vaachak.reader.core.data.repository.SettingsRepository
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
    private lateinit var vaultRepository: VaultRepository
    private lateinit var profileDao: ProfileDao
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        AppCoroutineConfig.mainOverride = testDispatcher
        AppCoroutineConfig.ioOverride = testDispatcher
        AppCoroutineConfig.defaultOverride = testDispatcher
        AppCoroutineConfig.sharingStartedOverride = kotlinx.coroutines.flow.SharingStarted.Eagerly

        settingsRepo = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        profileDao = mockk(relaxed = true)

        every { vaultRepository.hasCompletedOnboarding } returns MutableStateFlow(true)
        every { vaultRepository.activeVaultId } returns MutableStateFlow("profile_1")
        every { vaultRepository.isMultiUserMode } returns MutableStateFlow(true)

        every { profileDao.getAllProfiles() } returns flowOf(emptyList())

        every { settingsRepo.themeMode } returns flowOf(ThemeMode.LIGHT)
        every { settingsRepo.einkContrast } returns flowOf(1f)
        every { settingsRepo.bookshelfPreferences } returns flowOf(BookshelfPreferences())
        every { settingsRepo.readerPreferences } returns flowOf(ReaderPreferences())
        every { settingsRepo.ttsSettings } returns flowOf(mockk(relaxed = true))

        every { settingsRepo.getUseEmbeddedDictionary() } returns flowOf(false)
        every { settingsRepo.getDictionaryFolder() } returns flowOf("")

        viewModel = SettingsViewModel(
            settingsRepo = settingsRepo,
            vaultRepository = vaultRepository,
            profileDao = profileDao
        )
    }

    @After
    fun tearDown() {
        AppCoroutineConfig.reset()
    }

    @Test
    fun `completeOnboarding records only the multi-user choice`() = runTest(testDispatcher) {
        viewModel.completeOnboarding(isMultiUser = true)
        advanceUntilIdle()

        coVerify(exactly = 1) { vaultRepository.completeOnboarding(true) }
    }

    @Test
    fun `switchVault sanitizes the profile id`() = runTest(testDispatcher) {
        viewModel.switchVault("  Piyush D!  ")
        advanceUntilIdle()

        coVerify(exactly = 1) { vaultRepository.setActiveVaultId("piyush_d_") }
    }

    @Test
    fun `tts speed and pitch are clamped`() = runTest(testDispatcher) {
        viewModel.updateTtsSpeed(9f)
        viewModel.updateTtsPitch(0f)
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepo.setTtsDefaultSpeed(2.5f) }
        coVerify(exactly = 1) { settingsRepo.setTtsPitch(0.1f) }
    }

    @Test
    fun `valid dictionary folder is persisted and reported`() = runTest(testDispatcher) {
        every { settingsRepo.validateStarDictFolder("content://dict") } returns true

        viewModel.setDictionaryFolder("content://dict")
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepo.setDictionaryFolder("content://dict") }
        assertEquals("Dictionary folder set successfully.", viewModel.statusMessage.value)
    }

    @Test
    fun `invalid dictionary folder surfaces an error and is not persisted`() = runTest(testDispatcher) {
        every { settingsRepo.validateStarDictFolder("content://bad") } returns false

        viewModel.setDictionaryFolder("content://bad")
        advanceUntilIdle()

        coVerify(exactly = 0) { settingsRepo.setDictionaryFolder(any()) }
        assertTrue(viewModel.uiState.value.errorMessage?.startsWith("Invalid Folder") == true)
    }

    @Test
    fun `cover element toggles keep the other elements`() = runTest(testDispatcher) {
        val prefs = BookshelfPreferences(showFormatBadge = true, showFavoriteIcon = true, showProgressBadge = true)

        viewModel.toggleCoverElement(prefs, favorite = false)
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepo.setCoverStyleElements(true, false, true) }
    }
}
