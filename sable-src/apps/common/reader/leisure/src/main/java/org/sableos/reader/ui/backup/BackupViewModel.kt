package org.sableos.reader.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sableos.reader.core.backup.BackupRepository
import org.sableos.reader.core.backup.BackupSummary
import org.sableos.reader.core.backup.RestoreReport

data class BackupUiState(
    val isWorking: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

/** Runs a backup or restore the person asked for and reports the outcome in plain words. */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val repository: BackupRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = mutableState.asStateFlow()

    fun export(target: Uri) {
        launch { repository.export(target).map(::describe) }
    }

    fun restore(source: Uri) {
        launch { repository.restore(source).map(::describe) }
    }

    private fun launch(work: suspend () -> Result<String>) {
        if (mutableState.value.isWorking) return
        mutableState.value = BackupUiState(isWorking = true)
        viewModelScope.launch {
            val result = work()
            mutableState.update {
                BackupUiState(
                    message = result.getOrNull() ?: result.exceptionOrNull()?.message ?: "Something went wrong.",
                    isError = result.isFailure,
                )
            }
        }
    }

    private companion object {
        const val NOTHING_NEW = "Nothing new to restore: your library already has everything in this backup."
    }

    private fun describe(summary: BackupSummary): String =
        "Backed up ${summary.items} titles, ${summary.bookmarks} bookmarks, ${summary.highlights} highlights " +
            "and ${summary.collections} collections."

    private fun describe(report: RestoreReport): String {
        val parts = mutableListOf<String>()
        if (report.addedItems > 0) parts += "${report.addedItems} titles added (they need their files)"
        if (report.progressUpdated > 0) parts += "${report.progressUpdated} reading positions moved forward"
        val notes = report.bookmarks + report.highlights
        if (notes > 0) parts += "$notes bookmarks and highlights"
        if (report.collections > 0) parts += "${report.collections} collections"
        if (report.viewSettings > 0) parts += "${report.viewSettings} viewer settings"
        val body = if (parts.isEmpty()) NOTHING_NEW else parts.joinToString(", ") + "."
        return if (report.dropped > 0) "$body ${report.dropped} damaged records were skipped." else "Restored. $body"
    }
}
