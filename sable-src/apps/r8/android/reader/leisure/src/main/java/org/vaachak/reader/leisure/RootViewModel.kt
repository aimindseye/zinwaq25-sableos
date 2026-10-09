package org.vaachak.reader.leisure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.vaachak.reader.core.data.repository.SettingsRepository
import org.vaachak.reader.core.data.repository.VaultRepository
import org.vaachak.reader.core.domain.model.ThemeMode
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    vaultRepository: VaultRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    // INSTANT-PATH: Fetches immediately on cold start
    val appThemeMode = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.LIGHT)

    val einkContrastVal = settingsRepository.einkContrast
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1f)

    // INSTANT-PATH: Routing logic
    val hasCompletedOnboarding = vaultRepository.hasCompletedOnboarding
    val isMultiUserMode = vaultRepository.isMultiUserMode
    val activeVaultId = vaultRepository.activeVaultId
    val isOfflineMode = vaultRepository.isOfflineMode
}
