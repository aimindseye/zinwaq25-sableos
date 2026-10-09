package org.vaachak.reader.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class VaultRepository(
    private val globalDataStore: DataStore<Preferences>
) {
    companion object {
        val ACTIVE_VAULT_ID = stringPreferencesKey("active_vault_id")
        val IS_MULTI_USER_MODE = booleanPreferencesKey("is_multi_user_mode")
        const val DEFAULT_VAULT_ID = "default"
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val IS_OFFLINE_MODE = booleanPreferencesKey("is_offline_mode")
    }

    // FAST-PATH: Instant RAM load for onboarding status
    val hasCompletedOnboarding: StateFlow<Boolean> = globalDataStore.data.map { prefs ->
        prefs[HAS_COMPLETED_ONBOARDING] ?: false
    }.stateIn(CoroutineScope(Dispatchers.IO), SharingStarted.Eagerly, false)

    // FAST-PATH: Instant RAM load for offline mode
    val isOfflineMode: StateFlow<Boolean> = globalDataStore.data.map { prefs ->
        prefs[IS_OFFLINE_MODE] ?: true // Defaulting to offline-first is usually safer
    }.stateIn(CoroutineScope(Dispatchers.IO), SharingStarted.Eagerly, true)

    // ... write functions to save these values ...
    suspend fun completeOnboarding(isMultiUser: Boolean, isOffline: Boolean) {
        globalDataStore.edit { prefs ->
            prefs[IS_MULTI_USER_MODE] = isMultiUser
            prefs[IS_OFFLINE_MODE] = isOffline
            prefs[HAS_COMPLETED_ONBOARDING] = true
        }
    }

    // OPTIMIZATION: Converted to an eager StateFlow on the IO dispatcher.
    // The UI will no longer freeze waiting for the first disk read.
    val activeVaultId: StateFlow<String> = globalDataStore.data.map { prefs ->
        prefs[ACTIVE_VAULT_ID] ?: DEFAULT_VAULT_ID
    }.stateIn(
        scope = CoroutineScope(Dispatchers.IO),
        started = SharingStarted.Eagerly,
        initialValue = DEFAULT_VAULT_ID
    )

      // FAST-PATH: Loads into memory on app launch instantly.
    val isMultiUserMode: StateFlow<Boolean> = globalDataStore.data.map { prefs ->
        prefs[IS_MULTI_USER_MODE] ?: false
    }.stateIn(
        scope = CoroutineScope(Dispatchers.IO),
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    @Deprecated("Use setActiveVaultId for clarity", ReplaceWith("setActiveVaultId(vaultId)"))
    suspend fun setActiveVault(vaultId: String) {
        setActiveVaultId(vaultId)
    }

    suspend fun setMultiUserMode(enabled: Boolean) {
        globalDataStore.edit { it[IS_MULTI_USER_MODE] = enabled }
    }
    /**
     * Updates the DataStore to point to the newly selected user's profile.
     * The rest of the app (Bookshelf, Settings) will instantly react to this change.
     */
    suspend fun setActiveVaultId(profileId: String) {
        globalDataStore.edit { prefs ->
            prefs[ACTIVE_VAULT_ID] = profileId
        }
    }
}
