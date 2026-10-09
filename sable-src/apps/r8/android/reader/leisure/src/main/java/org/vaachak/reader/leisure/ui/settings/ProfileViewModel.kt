package org.vaachak.reader.leisure.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.vaachak.reader.core.data.local.ProfileDao
import org.vaachak.reader.core.data.repository.SyncRepository
import org.vaachak.reader.core.data.repository.VaultRepository
import org.vaachak.reader.core.domain.model.ProfileEntity
import org.vaachak.reader.core.utils.SecurityUtils
import org.vaachak.reader.core.utils.getCurrentTimeMillis
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileDao: ProfileDao,
    private val vaultRepository: VaultRepository,
    private val syncRepository: SyncRepository // Injected
) : ViewModel() {

    // --- NEW: Expose Multi-User State ---
    val isMultiUserMode: StateFlow<Boolean> = vaultRepository.isMultiUserMode

    // Automatically reacts to database changes (e.g., when a new profile is added)
    val profiles: StateFlow<List<ProfileEntity>> = profileDao.getAllProfiles()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Reactively fetches the active profile based on the DataStore Vault ID
    val activeProfile: StateFlow<ProfileEntity?> = combine(
        profiles,
        vaultRepository.activeVaultId // FIXED: Removed "Flow" suffix to match VaultRepository
    ) { allProfiles, vaultId ->
        allProfiles.find { it.profileId == vaultId }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // UI State for handling PIN entry errors
    private val _pinError = MutableStateFlow<String?>(null)
    val pinError = _pinError.asStateFlow()

    /**
     * Creates a new profile. If no PIN is provided, it's an unlocked profile.
     */
    fun createProfile(name: String, pin: String?, isGuest: Boolean = false, colorHex: String = "#333333") {
        viewModelScope.launch {
            val hashedPin = if (pin.isNullOrBlank()) null else SecurityUtils.hashPin(pin)

            val newProfile = ProfileEntity(
                name = name,
                isGuest = isGuest,
                pinHash = hashedPin,
                avatarColorHex = colorHex,
                lastActiveTimestamp = getCurrentTimeMillis()
            )
            profileDao.insertProfile(newProfile)
        }
    }

    /**
     * Attempts to unlock a profile.
     * Returns TRUE if successful (or no PIN required). Returns FALSE if PIN is wrong.
     */
    fun unlockProfile(profile: ProfileEntity, enteredPin: String?): Boolean {
        // Case 1: Profile has no password. Let them right in!
        if (profile.pinHash == null) {
            activateProfile(profile.profileId)
            return true
        }

        // Case 2: Profile has a password, but user didn't enter one
        if (enteredPin.isNullOrBlank()) {
            _pinError.value = "PIN required"
            return false
        }

        // Case 3: Verify the hash
        val enteredHash = SecurityUtils.hashPin(enteredPin)
        return if (profile.pinHash == enteredHash) {
            _pinError.value = null
            activateProfile(profile.profileId)
            true
        } else {
            _pinError.value = "Incorrect PIN"
            false
        }
    }

    fun selectProfile(
        profile: ProfileEntity,
        enteredPin: String?,
        onActivated: (String) -> Unit
    ) {
        if (profile.pinHash == null) {
            activateProfile(profile.profileId, onActivated)
            return
        }

        if (enteredPin.isNullOrBlank()) {
            _pinError.value = "PIN required"
            return
        }

        val enteredHash = SecurityUtils.hashPin(enteredPin)
        if (profile.pinHash != enteredHash) {
            _pinError.value = "Incorrect PIN"
            return
        }

        _pinError.value = null
        activateProfile(profile.profileId, onActivated)
    }

    /**
     * Tells the app which vault to look at, and updates the "last active" sorting.
     */
    private fun activateProfile(profileId: String, onActivated: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            // Tell the database this profile was just used (pushes it to the left of the picker)
            profileDao.updateLastActive(profileId, getCurrentTimeMillis())

            // Tell the DataStore to unlock this specific user's books/settings
            vaultRepository.setActiveVaultId(profileId)

            // NEW: Trigger a background sync immediately using the new profile's credentials
            syncRepository.sync()

            onActivated?.invoke(profileId)
        }
    }

    fun clearPinError() {
        _pinError.value = null
    }

    /**
     * Attempts to change a profile's PIN.
     * Requires the old PIN if the profile is currently locked.
     */
    fun changePin(profile: ProfileEntity, oldPin: String, newPin: String): Boolean {
        // 1. Verify the old PIN if the profile currently has one
        if (profile.pinHash != null) {
            if (oldPin.isBlank()) {
                _pinError.value = "Current PIN required"
                return false
            }
            val oldHash = SecurityUtils.hashPin(oldPin)
            if (oldHash != profile.pinHash) {
                _pinError.value = "Incorrect current PIN"
                return false
            }
        }

        // 2. Save the new PIN (or null if they left it blank to remove the PIN)
        viewModelScope.launch {
            val newHash = if (newPin.isBlank()) null else SecurityUtils.hashPin(newPin)
            profileDao.updatePin(profile.profileId, newHash)
            _pinError.value = null
        }
        return true
    }
}