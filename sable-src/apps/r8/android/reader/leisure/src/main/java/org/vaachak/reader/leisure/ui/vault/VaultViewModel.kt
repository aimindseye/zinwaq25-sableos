package org.vaachak.reader.leisure.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.vaachak.reader.core.data.repository.VaultRepository
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vaultRepository: VaultRepository
) : ViewModel() {

    // Exposes the current active profile (defaults to "default")
    val activeVaultId: StateFlow<String> = vaultRepository.activeVaultId
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VaultRepository.DEFAULT_VAULT_ID
        )

    // Tracks if the user has turned on the multi-profile feature
    val isMultiUserMode: StateFlow<Boolean> = vaultRepository.isMultiUserMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun switchVault(newVaultId: String) {
        viewModelScope.launch {
            vaultRepository.setActiveVaultId(newVaultId)
        }
    }

    fun toggleMultiUserMode(enabled: Boolean) {
        viewModelScope.launch {
            vaultRepository.setMultiUserMode(enabled)
        }
    }
}
