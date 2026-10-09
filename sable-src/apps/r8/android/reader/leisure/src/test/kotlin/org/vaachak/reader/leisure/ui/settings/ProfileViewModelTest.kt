package org.vaachak.reader.leisure.ui.settings

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.vaachak.reader.core.data.local.ProfileDao
import org.vaachak.reader.core.data.repository.SyncRepository
import org.vaachak.reader.core.data.repository.VaultRepository
import org.vaachak.reader.core.domain.model.ProfileEntity
import org.vaachak.reader.core.utils.SecurityUtils

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private lateinit var profileDao: ProfileDao
    private lateinit var vaultRepository: VaultRepository
    private lateinit var syncRepository: SyncRepository
    private lateinit var viewModel: ProfileViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val fakeProfilesFlow = MutableStateFlow<List<ProfileEntity>>(emptyList())
    private val fakeVaultIdFlow = MutableStateFlow("guest_vault")

    private val dummyProfile = ProfileEntity(
        profileId = "profile_piyush",
        name = "Piyush",
        isGuest = false,
        pinHash = SecurityUtils.hashPin("1234"),
        avatarColorHex = "#FFFFFF",
        lastActiveTimestamp = 1000L
    )

    private val unlockedProfile = ProfileEntity(
        profileId = "profile_guest",
        name = "Guest",
        isGuest = true,
        pinHash = null,
        avatarColorHex = "#000000",
        lastActiveTimestamp = 2000L
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        profileDao = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        syncRepository = mockk(relaxed = true)

        every { profileDao.getAllProfiles() } returns fakeProfilesFlow
        every { vaultRepository.activeVaultId } returns fakeVaultIdFlow

        viewModel = ProfileViewModel(profileDao, vaultRepository, syncRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `profiles and activeProfile state flows initialize and combine correctly`() = runTest {
        // FIX: Active collector to trigger WhileSubscribed logic
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.activeProfile.collect {} }

        fakeProfilesFlow.value = listOf(dummyProfile, unlockedProfile)
        fakeVaultIdFlow.value = "profile_piyush"

        advanceUntilIdle()

        val active = viewModel.activeProfile.value
        assertEquals("Piyush", active?.name)
        assertEquals("profile_piyush", active?.profileId)

        collectJob.cancel()
    }

    @Test
    fun `createProfile hashes PIN and inserts into database`() = runTest {
        viewModel.createProfile(name = "NewUser", pin = "9999", isGuest = false)
        advanceUntilIdle()

        coVerify {
            profileDao.insertProfile(match {
                it.name == "NewUser" &&
                        it.pinHash == SecurityUtils.hashPin("9999") &&
                        it.pinHash != "9999"
            })
        }
    }

    @Test
    fun `unlockProfile with correct PIN succeeds and updates vault`() = runTest {
        val isSuccess = viewModel.unlockProfile(dummyProfile, "1234")
        advanceUntilIdle()

        assertTrue(isSuccess)
        assertNull(viewModel.pinError.value)
        coVerify { vaultRepository.setActiveVaultId("profile_piyush") }
        coVerify { profileDao.updateLastActive(eq("profile_piyush"), any()) }
    }

    @Test
    fun `unlockProfile with incorrect PIN fails and emits pinError state`() = runTest {
        val isSuccess = viewModel.unlockProfile(dummyProfile, "0000")

        assertFalse(isSuccess)
        assertEquals("Incorrect PIN", viewModel.pinError.value)
        coVerify(exactly = 0) { vaultRepository.setActiveVaultId(any()) }
    }

    @Test
    fun `selectProfile activates profile instantly if no PIN is required`() = runTest {
        var activatedId: String? = null
        val onActivated = { id: String -> activatedId = id }

        viewModel.selectProfile(unlockedProfile, enteredPin = null, onActivated = onActivated)
        advanceUntilIdle()

        assertEquals("profile_guest", activatedId)
        coVerify { vaultRepository.setActiveVaultId("profile_guest") }
    }

    @Test
    fun `changePin fails if current PIN is missing or incorrect`() = runTest {
        val successNoOld = viewModel.changePin(dummyProfile, oldPin = "", newPin = "5678")
        assertFalse(successNoOld)
        assertEquals("Current PIN required", viewModel.pinError.value)

        val successWrongOld = viewModel.changePin(dummyProfile, oldPin = "1111", newPin = "5678")
        assertFalse(successWrongOld)
        assertEquals("Incorrect current PIN", viewModel.pinError.value)
        coVerify(exactly = 0) { profileDao.updatePin(any(), any()) }
    }

    @Test
    fun `changePin succeeds with correct old PIN`() = runTest {
        val success = viewModel.changePin(dummyProfile, oldPin = "1234", newPin = "5678")
        advanceUntilIdle()

        assertTrue(success)
        assertNull(viewModel.pinError.value)
        coVerify { profileDao.updatePin("profile_piyush", SecurityUtils.hashPin("5678")) }
    }
}