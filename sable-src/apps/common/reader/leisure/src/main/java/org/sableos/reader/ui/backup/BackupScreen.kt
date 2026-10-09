package org.sableos.reader.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SCREEN_PADDING = 16.dp
private val BACKUP_TYPES = arrayOf("application/json", "application/octet-stream", "text/plain")

/**
 * Library backup and restore. Both actions go through the system file picker: the person chooses where the backup is
 * written and which file is read, and nothing is ever sent anywhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
            uri?.let(viewModel::export)
        }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(viewModel::restore)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library backup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        BackupContent(
            state = state,
            onCreate = { exportLauncher.launch("SableReader-backup-${today()}.json") },
            onRestore = { restoreLauncher.launch(BACKUP_TYPES) },
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun BackupContent(
    state: BackupUiState,
    onCreate: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(SCREEN_PADDING),
    ) {
        Text(
            "A backup holds your library list, reading positions, bookmarks, highlights, collections and per-title " +
                "viewer settings. It never contains books, covers or where files are stored.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "After restoring on a new device, titles show \"Needs file\" until you choose the same file again. " +
                "Restoring never deletes anything and can safely be repeated.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onCreate, enabled = !state.isWorking, modifier = Modifier.fillMaxWidth()) {
            Text("Create backup…")
        }
        OutlinedButton(onClick = onRestore, enabled = !state.isWorking, modifier = Modifier.fillMaxWidth()) {
            Text("Restore from backup…")
        }
        if (state.isWorking) CircularProgressIndicator()
        state.message?.let {
            val color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            Text(it, style = MaterialTheme.typography.bodyLarge, color = color)
        }
    }
}

private fun today(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
