/*
 *  Copyright (c) 2026 Piyush Daiya
 *  *
 *  * Permission is hereby granted, free of charge, to any person obtaining a copy
 *  * of this software and associated documentation files (the "Software"), to deal
 *  * in the Software without restriction, including without limitation the rights
 *  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  * copies of the Software, and to permit persons to whom the Software is
 *  * furnished to do so, subject to the following conditions:
 *  *
 *  * The above copyright notice and this permission notice shall be included in all
 *  * copies or substantial portions of the Software.
 *  *
 *  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  * SOFTWARE.
 */

package org.vaachak.reader.leisure.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.vaachak.reader.leisure.ui.testability.Tid
import org.vaachak.reader.leisure.ui.testability.TidScreen
import org.vaachak.reader.leisure.ui.testability.tid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionarySettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // --- 1. COLLECT STATE ---

    // Explicitly collecting flows.
    // If inference fails, 'initialValue' helps, though StateFlows usually don't need it.
    val useLocalDict by viewModel.useEmbeddedDict.collectAsStateWithLifecycle()
    val dictFolder by viewModel.dictionaryFolder.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // FIX: syncMessage is a separate flow in ViewModel, not part of uiState
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()

    // --- 2. FILE LAUNCHER ---

    // FIX: added explicit <Uri?, Uri?> to help compiler inference
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) {
                // Ignore if already granted or not persistable
            }
            viewModel.setDictionaryFolder(it.toString())
        }
    }

    // --- 3. SIDE EFFECTS ---

    // Error Handling
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
    }

    // FIX: Observing the separate syncMessage flow
    LaunchedEffect(syncMessage) {
        syncMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            // We don't have a clearSyncMessage() in VM, so it might persist
            // until VM clears it (which your VM logic does after 3000ms).
        }
    }

    // --- 4. UI ---

    TidScreen(Tid.Screen.dictionary) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dictionary Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.tid("dictionary_settings_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // Section 1: Source Selection
            Text("DICTIONARY SOURCE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)
                )
            ) {
                Column {
                    // Option A: Built-in JSON
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setUseEmbeddedDictionary(false) }
                            .padding(16.dp)
                            .semantics(mergeDescendants = true) {
                                contentDescription = "Built-in English Dictionary, ${if (!useLocalDict) "Selected" else "Not Selected"}"
                            }
                            .tid("dictionary_source_builtin"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !useLocalDict,
                            onClick = { viewModel.setUseEmbeddedDictionary(false) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Built-in English Dictionary", style = MaterialTheme.typography.bodyLarge)
                            Text("Simple offline definitions (JSON)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider()

                    // Option B: StarDict Folder
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setUseEmbeddedDictionary(true) }
                            .padding(16.dp)
                            .semantics(mergeDescendants = true) {
                                contentDescription = "Custom Dictionary (StarDict), ${if (useLocalDict) "Selected" else "Not Selected"}"
                            }
                            .tid("dictionary_source_custom"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = useLocalDict,
                            onClick = { viewModel.setUseEmbeddedDictionary(true) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Custom Dictionary (StarDict)", style = MaterialTheme.typography.bodyLarge)
                            Text("Use .ifo/.idx/.dict files from folder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Section 2: Folder Configuration
            if (useLocalDict) {
                Text("CONFIGURATION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)

                OutlinedCard(
                    onClick = { folderLauncher.launch(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .tid("dictionary_folder_select")
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Select Dictionary Folder. Current: ${if (dictFolder.isNotBlank()) dictFolder else "None selected"}"
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dictionary Folder", style = MaterialTheme.typography.bodyMedium)
                            if (dictFolder.isNotBlank()) {
                                // Safe parsing of path
                                val readablePath = try {
                                    Uri.parse(dictFolder).path?.split(":")?.lastOrNull() ?: dictFolder
                                } catch (e: Exception) { dictFolder }

                                Text(
                                    readablePath,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text("Select folder containing dictionary files...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                Text(
                    text = "Tip: Download StarDict dictionaries (tar.bz2), extract them, and place the .ifo, .idx, and .dict files in a folder on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
    }
}
