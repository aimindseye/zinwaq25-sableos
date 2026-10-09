package org.vaachak.reader.leisure.ui.settings

// --- MAESTRO FIX 1: Import semantics ---
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import org.vaachak.reader.leisure.navigation.Screen
import org.vaachak.reader.leisure.ui.testability.Tid
import org.vaachak.reader.leisure.ui.testability.TidScreen
import org.vaachak.reader.leisure.ui.testability.tid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // NEW: Collect Vault State
    val activeVaultId by viewModel.activeVaultId.collectAsState()
    val isMultiUserMode by viewModel.isMultiUserMode.collectAsState()

    val scrollState = rememberScrollState()

    val settingsTabs = listOf("Global", "Book", "Content", "Intelligence")
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    TidScreen(Tid.Screen.settings) {
        Scaffold { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    settingsTabs.forEachIndexed { index, title ->
                        val tabTid = when (index) {
                            0 -> Tid.Settings.tabGlobal
                            1 -> Tid.Settings.tabBook
                            2 -> Tid.Settings.tabContent
                            3 -> Tid.Settings.tabIntelligence
                            else -> return@forEachIndexed
                        }
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            modifier = Modifier
                                .tid(tabTid)
                                // --- MAESTRO FIX 2: Explicit semantics for Tabs ---
                                .semantics(mergeDescendants = true) {
                                    contentDescription = "Settings Tab: $title"
                                },
                            text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (selectedTab) {
                        0 -> GlobalSettingsTab(state, activeVaultId, isMultiUserMode, viewModel, navController)
                        1 -> BookSettingsTab(navController)
                        2 -> ContentSettingsTab(navController)
                        3 -> IntelligenceSettingsTab(state, navController)
                    }
                }
            }
        }
    }
}

@Composable
fun GlobalSettingsTab(
    state: SettingsUiState,
    activeVaultId: String,
    isMultiUserMode: Boolean,
    viewModel: SettingsViewModel,
    navController: NavController
) {
    var showVaultDialog by remember { mutableStateOf(false) }

    SettingsGroup(title = "Account & Profiles") {
        SettingsTile(
            icon = Icons.Default.Person, title = "Reader Profiles", subtitle = "Manage local profiles and credentials",
            onClick = { navController.navigate("reader_profiles") }
        )

        SettingsTile(
            icon = Icons.Default.Group, title = "Local Multi-User Mode", subtitle = "Isolate libraries for different offline readers",
            onClick = { viewModel.setMultiUserMode(!isMultiUserMode) },
            trailing = { Switch(checked = isMultiUserMode, onCheckedChange = { viewModel.setMultiUserMode(it) }) }
        )

        if (isMultiUserMode) {
            SettingsTile(
                icon = Icons.Default.SwitchAccount, title = "Active Profile", subtitle = activeVaultId.replaceFirstChar { it.uppercase() },
                onClick = { showVaultDialog = true }
            )
        }

        SettingsTile(
            icon = Icons.Default.Sync, title = "Sync Configuration", subtitle = "Manage end-to-end encrypted vaults",
            onClick = { navController.navigate(Screen.SyncSettings.route) }
        )
    }

    SettingsGroup(title = "Device & Network") {
        SettingsTile(
            icon = if (state.isOfflineMode) Icons.Outlined.CloudOff else Icons.Default.CloudQueue,
            title = "Offline Mode", subtitle = "Default: On. Cloud features disabled.",
            onClick = { viewModel.toggleOfflineMode(!state.isOfflineMode) },
            trailing = { Switch(checked = state.isOfflineMode, onCheckedChange = { viewModel.toggleOfflineMode(it) }) }
        )
        SettingsTile(
            icon = Icons.Default.Contrast, title = "App-wide Theme", subtitle = "E-ink vs LCD, Sharpness",
            onClick = { navController.navigate("settings/app_appearance") }
        )
    }

    if (showVaultDialog) {
        if (state.userProfile?.isAuthenticated == true) {
            AlertDialog(
                onDismissRequest = { showVaultDialog = false },
                title = { Text("Cloud Account Active") },
                text = { Text("To switch local profiles safely, please log out of your current cloud account first to prevent mixing sync data.") },
                confirmButton = { TextButton(onClick = { showVaultDialog = false }) { Text("OK") } } // Maestro sees "OK"
            )
        } else {
            var newVaultName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showVaultDialog = false },
                title = { Text("Switch Local Profile") },
                text = {
                    Column {
                        Text("Enter the name of the offline profile you want to switch to or create.")
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = newVaultName,
                            onValueChange = { newVaultName = it },
                            label = { Text("Profile Name") }, // Maestro reads this label
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newVaultName.isNotBlank()) {
                            viewModel.switchVault(newVaultName)
                        }
                        showVaultDialog = false
                    }) { Text("Switch") } // Maestro sees "Switch"
                },
                dismissButton = {
                    TextButton(onClick = { showVaultDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun BookSettingsTab(navController: NavController) {
    SettingsGroup(title = "Library & Visuals") {
        SettingsTile(
            icon = Icons.AutoMirrored.Filled.LibraryBooks, title = "Library Settings (Visuals)", subtitle = "E-ink optimization, Cover Styles, Smart Stacks",
            onClick = { navController.navigate(Screen.Bookshelf.route) }
        )
    }

    SettingsGroup(title = "Reading Experience") {
        SettingsTile(
            icon = Icons.Default.FormatSize, title = "Default Book Appearance", subtitle = "Fonts, margins, publisher styles, page color",
            onClick = { navController.navigate("settings/appearance") }
        )
        SettingsTile(
            icon = Icons.Default.RecordVoiceOver, title = "Speech & Narration", subtitle = "Language, Speed, Pitch, Sleep-Timer",
            onClick = { navController.navigate("settings/tts") }
        )
        SettingsTile(
            icon = Icons.Default.Book, title = "Dictionary Settings", subtitle = "Manage local and custom dictionaries",
            onClick = { navController.navigate("settings/dictionary") }
        )
    }
}

@Composable
fun ContentSettingsTab(navController: NavController) {
    SettingsGroup(title = "Sources") {
        SettingsTile(
            icon = Icons.AutoMirrored.Filled.List, title = "Manage Catalogs", subtitle = "Add or remove OPDS feeds",
            onClick = { navController.navigate("catalog_manage") }
        )
    }
}

@Composable
fun IntelligenceSettingsTab(state: SettingsUiState, navController: NavController) {
    if (state.isOfflineMode) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                // --- MAESTRO FIX 3: Semantic merging for error cards ---
                .semantics(mergeDescendants = true) {
                    contentDescription = "Intelligence features are disabled while Offline Mode is active."
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Text("Intelligence features are disabled while Offline Mode is active.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
        }
    } else {
        SettingsGroup(title = "AI Configuration") {
            SettingsTile(
                icon = Icons.Default.AutoAwesome, title = "Gemini Integration", subtitle = "Manage API keys for Global Recall and Smart Summaries",
                onClick = { navController.navigate(Screen.AiConfig.route) }
            )
            SettingsTile(
                icon = Icons.Default.Image, title = "Cloudflare Works", subtitle = "Image generation settings",
                onClick = { /* Navigate to Cloudflare Config */ }
            )
        }
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // --- MAESTRO FIX 4: Add clear semantics for Groups ---
            .semantics(mergeDescendants = false) {
                contentDescription = "Settings Group: $title"
            }
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(8.dp)) { content() }
        }
    }
}

@Composable
fun SettingsTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            // --- MAESTRO FIX 5: Merge the Settings Tile ---
            .semantics(mergeDescendants = true) {
                contentDescription = "$title setting. $subtitle"
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // --- MAESTRO FIX 6: Provide content descriptions for trailing icons ---
        if (trailing != null) trailing() else Icon(Icons.Default.ChevronRight, contentDescription = "Open $title", tint = Color.Gray)
    }
}
