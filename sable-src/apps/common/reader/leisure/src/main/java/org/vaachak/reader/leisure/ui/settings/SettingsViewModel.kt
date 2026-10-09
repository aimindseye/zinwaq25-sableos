package org.vaachak.reader.leisure.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vaachak.reader.core.common.AppCoroutineConfig
import org.vaachak.reader.core.data.local.ProfileDao
import org.vaachak.reader.core.data.repository.SettingsRepository
import org.vaachak.reader.core.data.repository.VaultRepository
import org.vaachak.reader.core.domain.model.BookshelfPreferences
import org.vaachak.reader.core.domain.model.CoverAspectRatio
import org.vaachak.reader.core.domain.model.DitheringMode
import org.vaachak.reader.core.domain.model.ReaderPreferences
import org.vaachak.reader.core.domain.model.ThemeMode
import org.vaachak.reader.core.domain.model.TtsSettings
import javax.inject.Inject

private const val MIN_TTS_SPEED = 0.5f
private const val MAX_TTS_SPEED = 2.5f
private const val MIN_TTS_PITCH = 0.1f
private const val MAX_TTS_PITCH = 2.0f
private const val PITCH_STEPS_PER_UNIT = 10f

private data class ThemeState(
    val theme: ThemeMode,
    val contrast: Float,
    val bookshelfPrefs: BookshelfPreferences
)

private data class ContentState(
    val tts: TtsSettings,
    val error: String?
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val vaultRepository: VaultRepository,
    private val profileDao: ProfileDao
) : ViewModel() {

    val hasCompletedOnboarding: StateFlow<Boolean> = vaultRepository.hasCompletedOnboarding
        .stateIn(viewModelScope, AppCoroutineConfig.eagerly, false)

    fun completeOnboarding(isMultiUser: Boolean) {
        viewModelScope.launch {
            vaultRepository.completeOnboarding(isMultiUser)
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

    private val _errorMessage = MutableStateFlow<String?>(null)

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    val readerPreferences: StateFlow<ReaderPreferences> = settingsRepo.readerPreferences
        .stateIn(viewModelScope, AppCoroutineConfig.whileSubscribed, ReaderPreferences())

    val useEmbeddedDict: StateFlow<Boolean> = settingsRepo.getUseEmbeddedDictionary()
        .stateIn(viewModelScope, AppCoroutineConfig.whileSubscribed, false)

    val dictionaryFolder: StateFlow<String> = settingsRepo.getDictionaryFolder()
        .stateIn(viewModelScope, AppCoroutineConfig.whileSubscribed, "")

    private val themeFlow = combine(
        settingsRepo.themeMode,
        settingsRepo.einkContrast,
        settingsRepo.bookshelfPreferences
    ) { theme: ThemeMode, contrast: Float, prefs: BookshelfPreferences ->
        ThemeState(theme, contrast, prefs)
    }.distinctUntilChanged()

    private val contentFlow = combine(
        settingsRepo.ttsSettings,
        _errorMessage
    ) { tts, error ->
        ContentState(tts, error)
    }.distinctUntilChanged()

    val uiState: StateFlow<SettingsUiState> = combine(
        themeFlow,
        contentFlow
    ) { theme, content ->
        SettingsUiState(
            errorMessage = content.error,
            themeMode = theme.theme,
            einkContrast = theme.contrast,
            bookshelfPreferences = theme.bookshelfPrefs,
            ttsSettings = content.tts
        )
    }.stateIn(viewModelScope, AppCoroutineConfig.whileSubscribed, SettingsUiState())

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

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun updateTtsSpeed(newSpeed: Float) = viewModelScope.launch {
        settingsRepo.setTtsDefaultSpeed(newSpeed.coerceIn(MIN_TTS_SPEED, MAX_TTS_SPEED))
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
        settingsRepo.setTtsPitch(
            (pitch.coerceIn(MIN_TTS_PITCH, MAX_TTS_PITCH) * PITCH_STEPS_PER_UNIT).toInt() / PITCH_STEPS_PER_UNIT
        )
    }

    fun setSleepTimer(minutes: Int) = viewModelScope.launch {
        settingsRepo.setTtsSleepTimer(minutes)
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
                _statusMessage.value = "Dictionary folder set successfully."
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
    }

    fun setEinkContrast(value: Float) = viewModelScope.launch {
        settingsRepo.setContrast(value)
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
        progress: Boolean? = null
    ) {
        viewModelScope.launch {
            settingsRepo.setCoverStyleElements(
                format = format ?: currentPrefs.showFormatBadge,
                favorite = favorite ?: currentPrefs.showFavoriteIcon,
                progress = progress ?: currentPrefs.showProgressBadge
            )
        }
    }
}
