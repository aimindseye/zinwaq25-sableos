package org.vaachak.reader.leisure.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vaachak.reader.core.common.AppCoroutineConfig
import org.vaachak.reader.core.data.local.ProfileDao
import org.vaachak.reader.core.data.repository.OpdsRepository
import org.vaachak.reader.core.data.repository.SettingsRepository
import org.vaachak.reader.core.data.repository.SyncRepository
import org.vaachak.reader.core.data.repository.VaultRepository
import org.vaachak.reader.core.domain.model.AiConfig
import org.vaachak.reader.core.domain.model.BookshelfPreferences
import org.vaachak.reader.core.domain.model.CoverAspectRatio
import org.vaachak.reader.core.domain.model.DitheringMode
import org.vaachak.reader.core.domain.model.OpdsEntity
import org.vaachak.reader.core.domain.model.ReaderPreferences
import org.vaachak.reader.core.domain.model.SettingsSection
import org.vaachak.reader.core.domain.model.ThemeMode
import org.vaachak.reader.core.domain.model.TtsSettings
import org.vaachak.reader.core.domain.model.UserProfile
import org.vaachak.reader.leisure.utils.EinkHelper
import javax.inject.Inject

private data class AccountState(
    val username: String,
    val device: String,
    val lastSync: Long,
    val offline: Boolean
)

private data class AiState(
    val gemini: String,
    val cfUrl: String,
    val token: String,
    val autoSave: Boolean
)

private data class ThemeState(
    val theme: ThemeMode,
    val contrast: Float,
    val bookshelfPrefs: BookshelfPreferences
)

private data class ViewState(
    val editSec: SettingsSection,
    val masked: Boolean,
    val error: String?
)

private data class ContentState(
    val tts: TtsSettings,
    val feeds: List<OpdsEntity>,
    val syncLoading: Boolean
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val syncRepo: SyncRepository,
    private val opdsRepo: OpdsRepository,
    private val vaultRepository: VaultRepository,
    private val profileDao: ProfileDao,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val hasCompletedOnboarding: StateFlow<Boolean> = vaultRepository.hasCompletedOnboarding
        .stateIn(viewModelScope, AppCoroutineConfig.eagerly, false)

    val isOfflineMode: StateFlow<Boolean> = vaultRepository.isOfflineMode
        .stateIn(viewModelScope, AppCoroutineConfig.eagerly, false)

    fun completeOnboarding(isMultiUser: Boolean, isOffline: Boolean) {
        viewModelScope.launch {
            vaultRepository.completeOnboarding(isMultiUser, isOffline)
        }
    }

    val activeVaultId: StateFlow<String> = vaultRepository.activeVaultId
        .stateIn(
            viewModelScope,
            AppCoroutineConfig.whileSubscribed,
            VaultRepository.DEFAULT_VAULT_ID
        )

    val localProfileName: StateFlow<String> = combine(
        vaultRepository.activeVaultId,
        profileDao.getAllProfiles()
    ) { vaultId, profiles ->
        if (vaultId == VaultRepository.DEFAULT_VAULT_ID) {
            ""
        } else {
            profiles.find { it.profileId == vaultId }?.name ?: ""
        }
    }.stateIn(viewModelScope, AppCoroutineConfig.whileSubscribed, "")

    val isMultiUserMode: StateFlow<Boolean> = vaultRepository.isMultiUserMode
        .stateIn(viewModelScope, AppCoroutineConfig.whileSubscribed, false)

    val appThemeMode: StateFlow<ThemeMode> = settingsRepo.themeMode
        .stateIn(viewModelScope, AppCoroutineConfig.eagerly, ThemeMode.LIGHT)

    val einkContrastVal: StateFlow<Float> = settingsRepo.einkContrast
        .stateIn(viewModelScope, AppCoroutineConfig.eagerly, 1f)

    val isSyncEnabled: StateFlow<Boolean> = settingsRepo.isSyncEnabled
        .stateIn(viewModelScope, AppCoroutineConfig.whileSubscribed, false)

    val syncCloudUrl: StateFlow<String> = settingsRepo.syncCloudUrl
        .stateIn(viewModelScope, AppCoroutineConfig.whileSubscribed, "")

    val localServerUrl: StateFlow<String> = settingsRepo.localServerUrl
        .stateIn(viewModelScope, AppCoroutineConfig.whileSubscribed, "")

    val useLocalServer: StateFlow<Boolean> = settingsRepo.useLocalServer
        .stateIn(viewModelScope, AppCoroutineConfig.whileSubscribed, false)

    private val _activeEditSection = MutableStateFlow(SettingsSection.NONE)
    private val _isAiMasked = MutableStateFlow(true)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isSyncLoading = MutableStateFlow(false)

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage = _syncMessage.asStateFlow()

    val readerPreferences: StateFlow<ReaderPreferences> = settingsRepo.readerPreferences
        .stateIn(viewModelScope, AppCoroutineConfig.whileSubscribed, ReaderPreferences())

    val useEmbeddedDict: StateFlow<Boolean> = settingsRepo.getUseEmbeddedDictionary()
        .stateIn(viewModelScope, AppCoroutineConfig.whileSubscribed, false)

    val dictionaryFolder: StateFlow<String> = settingsRepo.getDictionaryFolder()
        .stateIn(viewModelScope, AppCoroutineConfig.whileSubscribed, "")

    private val accountFlow = combine(
        settingsRepo.syncUsername,
        settingsRepo.deviceName,
        settingsRepo.lastSyncTimestamp,
        settingsRepo.isOfflineModeEnabled
    ) { username, device, lastSync, offline ->
        AccountState(username, device, lastSync, offline)
    }.distinctUntilChanged()

    private val aiFlow = combine(
        settingsRepo.geminiKey,
        settingsRepo.cfUrl,
        settingsRepo.cfToken,
        settingsRepo.isAutoSaveRecapsEnabled
    ) { gemini, cfUrl, token, autoSave ->
        AiState(gemini, cfUrl, token, autoSave)
    }.distinctUntilChanged()

    private val themeFlow = combine(
        settingsRepo.themeMode,
        settingsRepo.einkContrast,
        settingsRepo.bookshelfPreferences
    ) { theme: ThemeMode, contrast: Float, prefs: BookshelfPreferences ->
        ThemeState(theme, contrast, prefs)
    }.distinctUntilChanged()

    private val viewFlow = combine(
        _activeEditSection,
        _isAiMasked,
        _errorMessage
    ) { editSec, masked, error ->
        ViewState(editSec, masked, error)
    }.distinctUntilChanged()

    private val contentFlow = combine(
        settingsRepo.ttsSettings,
        opdsRepo.catalogs,
        _isSyncLoading
    ) { tts, feeds, syncLoading ->
        ContentState(tts, feeds, syncLoading)
    }.distinctUntilChanged()

    val uiState: StateFlow<SettingsUiState> = combine(
        accountFlow,
        aiFlow,
        themeFlow,
        viewFlow,
        contentFlow
    ) { acc, ai, theme, view, content ->
        val isAuthenticated = acc.username.isNotBlank()
        SettingsUiState(
            userProfile = UserProfile(
                username = if (isAuthenticated) acc.username else null,
                deviceName = acc.device,
                lastSyncTime = acc.lastSync,
                isAuthenticated = isAuthenticated
            ),
            isSyncLoading = content.syncLoading,
            isOfflineMode = acc.offline,
            catalogs = content.feeds,
            aiConfig = AiConfig(
                isEnabled = ai.gemini.isNotBlank(),
                geminiKey = ai.gemini,
                cloudflareUrl = ai.cfUrl,
                authToken = ai.token,
                autoSaveRecaps = ai.autoSave
            ),
            activeEditSection = view.editSec,
            isAiMasked = view.masked,
            errorMessage = view.error,
            themeMode = theme.theme,
            einkContrast = theme.contrast,
            bookshelfPreferences = theme.bookshelfPrefs,
            ttsSettings = content.tts
        )
    }.stateIn(viewModelScope, AppCoroutineConfig.whileSubscribed, SettingsUiState())

    fun setSyncEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setSyncEnabled(enabled)
    }

    fun setSyncCloudUrl(url: String) = viewModelScope.launch {
        settingsRepo.setSyncCloudUrl(url)
    }

    fun setLocalServerUrl(url: String) = viewModelScope.launch {
        settingsRepo.setLocalServerUrl(url)
    }

    fun setUseLocalServer(useLocal: Boolean) = viewModelScope.launch {
        settingsRepo.setUseLocalServer(useLocal)
    }

    fun setMultiUserMode(enabled: Boolean) {
        viewModelScope.launch {
            vaultRepository.setMultiUserMode(enabled)
        }
    }

    fun switchVault(vaultId: String) {
        viewModelScope.launch {
            val cleanId = vaultId
                .trim()
                .lowercase()
                .replace(Regex("[^a-z0-9]"), "_")

            if (cleanId.isNotBlank()) {
                vaultRepository.setActiveVaultId(cleanId)
            }
        }
    }

    fun toggleEditSection(section: SettingsSection) {
        if (_activeEditSection.value == section) {
            _activeEditSection.value = SettingsSection.NONE
            _isAiMasked.value = true
        } else {
            _activeEditSection.value = section
        }
    }

    fun setAiMasked(masked: Boolean) {
        _isAiMasked.value = masked
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun saveAiConfig(
        gemini: String,
        cfUrl: String,
        token: String,
        autoSave: Boolean
    ) {
        viewModelScope.launch {
            try {
                val currentTheme = settingsRepo.themeMode.first()
                val isEink = currentTheme == ThemeMode.E_INK
                settingsRepo.saveSettings(
                    gemini = gemini,
                    cfUrl = cfUrl,
                    cfToken = token,
                    isEnk = isEink
                )
                settingsRepo.setAutoSaveRecaps(autoSave)
                _activeEditSection.value = SettingsSection.NONE
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save settings: ${e.message}"
            }
        }
    }

    fun toggleOfflineMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setOfflineMode(enabled)
        }
    }

    fun logout() {
        viewModelScope.launch {
            settingsRepo.updateSyncProfile("", "", "")
            settingsRepo.setLastSyncTimestamp(0L)
            vaultRepository.setActiveVaultId(VaultRepository.DEFAULT_VAULT_ID)
            _activeEditSection.value = SettingsSection.NONE
        }
    }

    fun updateTtsSpeed(newSpeed: Float) = viewModelScope.launch {
        settingsRepo.setTtsDefaultSpeed(newSpeed.coerceIn(0.5f, 2.5f))
    }

    fun setTtsAutoPageTurn(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setTtsAutoPageTurn(enabled)
    }

    fun setTtsVisualStyle(style: String) = viewModelScope.launch {
        settingsRepo.setTtsVisualStyle(style)
    }

    fun setTtsLanguage(language: String) = viewModelScope.launch {
        settingsRepo.setTtsLanguage(language)
    }

    fun updateTtsPitch(pitch: Float) = viewModelScope.launch {
        settingsRepo.setTtsPitch((pitch.coerceIn(0.1f, 2.0f) * 10f).toInt() / 10f)
    }

    fun setSleepTimer(minutes: Int) = viewModelScope.launch {
        settingsRepo.setTtsSleepTimer(minutes)
    }

    fun loginToSyncServer(user: String, pass: String) {
        viewModelScope.launch {
            _isSyncLoading.value = true
            _syncMessage.value = "Authenticating..."
            val result = syncRepo.login(user.trim(), pass)
            _syncMessage.value = if (result.isSuccess) {
                "Login successful!"
            } else {
                "Login failed: ${result.exceptionOrNull()?.message}"
            }
            _isSyncLoading.value = false
            delay(3000)
            if (_syncMessage.value?.startsWith("Login") == true) {
                _syncMessage.value = null
            }
        }
    }

    fun registerToSyncServer(user: String, pass: String) {
        viewModelScope.launch {
            _isSyncLoading.value = true
            _syncMessage.value = "Registering account..."
            val result = syncRepo.register(user.trim(), pass)
            _syncMessage.value = if (result.isSuccess) {
                "Registration successful! You can now log in."
            } else {
                "Registration failed: ${result.exceptionOrNull()?.message}"
            }
            _isSyncLoading.value = false
            delay(4000)
            if (_syncMessage.value?.startsWith("Registration") == true) {
                _syncMessage.value = null
            }
        }
    }

    fun triggerManualSync() {
        viewModelScope.launch {
            _syncMessage.value = "Syncing..."
            _isSyncLoading.value = true
            val result = syncRepo.sync()
            _syncMessage.value = if (result.isSuccess) {
                "Sync Complete!"
            } else {
                "Sync Failed: ${result.exceptionOrNull()?.message}"
            }
            _isSyncLoading.value = false
            delay(3000)
            _syncMessage.value = null
        }
    }

    fun testServerConnection() {
        viewModelScope.launch {
            _syncMessage.value = "Testing Server..."
            _isSyncLoading.value = true
            try {
                val useLocal = settingsRepo.useLocalServer.first()
                val url = if (useLocal) {
                    settingsRepo.localServerUrl.first()
                } else {
                    settingsRepo.syncCloudUrl.first()
                }

                val result = syncRepo.testConnection(url)
                _syncMessage.value = if (result.isSuccess) {
                    "Connected Successfully!"
                } else {
                    "Connection Failed: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _syncMessage.value = "Error: ${e.message}"
            }
            _isSyncLoading.value = false
            delay(3000)
            _syncMessage.value = null
        }
    }

    fun updateReaderPreferences(prefs: ReaderPreferences) = viewModelScope.launch {
        settingsRepo.saveReaderPreferences(prefs)
    }

    fun setUseEmbeddedDictionary(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setUseEmbeddedDictionary(enabled)
    }

    fun setDictionaryFolder(uri: String) {
        viewModelScope.launch {
            val isValid = withContext(AppCoroutineConfig.io) {
                settingsRepo.validateStarDictFolder(uri)
            }

            if (isValid) {
                settingsRepo.setDictionaryFolder(uri)
                _errorMessage.value = null
                _syncMessage.value = "Dictionary folder set successfully."
            } else {
                _errorMessage.value = "Invalid Folder: No StarDict (.idx) files found."
            }
        }
    }

    fun toggleEinkMode(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setThemeMode(if (enabled) ThemeMode.E_INK else ThemeMode.LIGHT)
    }

    fun setAppTheme(mode: ThemeMode) = viewModelScope.launch {
        settingsRepo.setThemeMode(mode)
        delay(100)
        EinkHelper.requestFullRefresh(context)
    }

    fun setEinkContrast(value: Float) = viewModelScope.launch {
        settingsRepo.setContrast(value)
    }

    fun onContrastChangedFinished() = viewModelScope.launch {
        delay(200)
        EinkHelper.requestFullRefresh(context)
    }

    fun triggerEinkRefresh(context: Context) {
        EinkHelper.requestFullRefresh(context)
    }

    fun setDitheringMode(mode: DitheringMode) = viewModelScope.launch {
        settingsRepo.setDitheringMode(mode)
    }

    fun setGroupBySeries(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setGroupBySeries(enabled)
    }

    fun setCoverAspectRatio(ratio: CoverAspectRatio) = viewModelScope.launch {
        settingsRepo.setCoverAspectRatio(ratio)
    }

    fun toggleCoverElement(
        currentPrefs: BookshelfPreferences,
        format: Boolean? = null,
        favorite: Boolean? = null,
        progress: Boolean? = null,
        sync: Boolean? = null
    ) {
        viewModelScope.launch {
            settingsRepo.setCoverStyleElements(
                format = format ?: currentPrefs.showFormatBadge,
                favorite = favorite ?: currentPrefs.showFavoriteIcon,
                progress = progress ?: currentPrefs.showProgressBadge,
                sync = sync ?: currentPrefs.showSyncStatus
            )
        }
    }
}