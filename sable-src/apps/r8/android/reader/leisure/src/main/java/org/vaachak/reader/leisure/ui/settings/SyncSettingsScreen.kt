package org.vaachak.reader.leisure.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.vaachak.reader.core.domain.model.ThemeMode
import org.vaachak.reader.leisure.ui.reader.components.VaachakHeader
import org.vaachak.reader.leisure.ui.testability.Tid
import org.vaachak.reader.leisure.ui.testability.TidScreen
import org.vaachak.reader.leisure.ui.testability.tid

@Composable
fun SyncSettingsScreen(
    navController: androidx.navigation.NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val isEink = state.themeMode == ThemeMode.E_INK

    val isSyncEnabled by viewModel.isSyncEnabled.collectAsState()
    val syncCloudUrlFlow by viewModel.syncCloudUrl.collectAsState()
    val localServerUrlFlow by viewModel.localServerUrl.collectAsState()
    val useLocalServer by viewModel.useLocalServer.collectAsState()
    val localProfileName by viewModel.localProfileName.collectAsState()

    var localSyncCloudUrl by remember { mutableStateOf(syncCloudUrlFlow) }
    var localSelfHostedUrl by remember { mutableStateOf(localServerUrlFlow) }
    var showAuthDialog by remember { mutableStateOf(false) }

    LaunchedEffect(syncCloudUrlFlow) {
        if (localSyncCloudUrl.isEmpty() && syncCloudUrlFlow.isNotEmpty()) localSyncCloudUrl = syncCloudUrlFlow
    }
    LaunchedEffect(localServerUrlFlow) {
        if (localSelfHostedUrl.isEmpty() && localServerUrlFlow.isNotEmpty()) localSelfHostedUrl = localServerUrlFlow
    }

    val isAuthenticated = state.userProfile?.isAuthenticated == true
    val currentUsername = state.userProfile?.username ?: ""

    TidScreen(Tid.Screen.syncSettings) {
        Scaffold(
            topBar = { VaachakHeader(title = "Sync Configuration", onBack = { navController.popBackStack() }, isEink = isEink) }
        ) { padding ->
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                if (syncMessage != null) {
                    Text(
                        text = syncMessage!!,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (syncMessage!!.contains("Failed") || syncMessage!!.contains("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                if (state.isOfflineMode) {
                    Card(colors = CardDefaults.cardColors(containerColor = if (isEink) Color.White else MaterialTheme.colorScheme.errorContainer), border = if (isEink) BorderStroke(1.dp, Color.Black) else null, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CloudOff, contentDescription = null, tint = if (isEink) Color.Black else MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Sync features are disabled while Offline Mode is active.", color = if (isEink) Color.Black else MaterialTheme.colorScheme.onErrorContainer, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Sync Across Devices", fontWeight = FontWeight.Bold)
                            Text("Securely backup progress to the cloud.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(checked = isSyncEnabled, onCheckedChange = { viewModel.setSyncEnabled(it) }, modifier = Modifier.tid("sync_settings_enable_toggle"))
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp), color = if (isEink) Color.Black else MaterialTheme.colorScheme.outlineVariant)

                    if (isSyncEnabled) {
                        // SECTION 1: SERVER URL & CONNECTION TEST
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CloudSync, contentDescription = null, tint = if (isEink) Color.Black else MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("1. Server Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = !useLocalServer, onClick = { viewModel.setUseLocalServer(false) })
                                Text("Use Production Cloudflare Server")
                            }
                            OutlinedTextField(
                                value = localSyncCloudUrl,
                                onValueChange = { localSyncCloudUrl = it; viewModel.setSyncCloudUrl(it) },
                                label = { Text("Cloudflare URL") },
                                enabled = !useLocalServer, singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(start = 32.dp, bottom = 8.dp).tid("sync_settings_tier1_url")
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = useLocalServer, onClick = { viewModel.setUseLocalServer(true) })
                                Text("Use Local/Self-Hosted Server")
                            }
                            OutlinedTextField(
                                value = localSelfHostedUrl,
                                onValueChange = { localSelfHostedUrl = it; viewModel.setLocalServerUrl(it) },
                                label = { Text("Local URL (e.g., http://192.168.1.X:8787)") },
                                enabled = useLocalServer, singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(start = 32.dp, bottom = 16.dp).tid("sync_settings_tier2_url")
                            )

                            OutlinedButton(
                                onClick = { viewModel.testServerConnection() },
                                modifier = Modifier.fillMaxWidth().padding(start = 32.dp).tid("sync_settings_test_connection"),
                                colors = if (isEink) ButtonDefaults.outlinedButtonColors(contentColor = Color.Black) else ButtonDefaults.outlinedButtonColors()
                            ) {
                                Text("Test Connection")
                            }
                        }

                        // SECTION 2: AUTHENTICATION
                        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("2. Account Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                if (isAuthenticated) {
                                    Text("Authenticated as: $currentUsername", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(onClick = { viewModel.logout() }) { Text("Log Out") }
                                } else {
                                    Text("You must log in to sync your vault.", color = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = { showAuthDialog = true }) { Text("Log In or Register") }
                                }
                            }
                        }

                        // SECTION 3: SYNC NOW
                        Button(
                            onClick = { viewModel.triggerManualSync() },
                            enabled = isAuthenticated,
                            modifier = Modifier.fillMaxWidth().height(50.dp).tid("sync_settings_sync_now"),
                            colors = if (isEink) ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White) else ButtonDefaults.buttonColors()
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("3. Sync Now")
                        }
                    }
                }
            }

            if (showAuthDialog) {
                var authUser by remember { mutableStateOf(if (localProfileName.isBlank()) "" else localProfileName.replaceFirstChar { it.uppercase() }) }
                var authPass by remember { mutableStateOf("") }
                var passwordVisible by remember { mutableStateOf(false) } // <-- ADD THIS

                AlertDialog(
                    onDismissRequest = { showAuthDialog = false },
                    title = { Text("Sync Account") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Make sure your server URL is entered correctly before authenticating.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Surface(color = if (isEink) Color.LightGray else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(text = "Note: Your local profile PIN cannot be used here. Please create a strong password to secure your cloud backup.", style = MaterialTheme.typography.labelMedium, color = if (isEink) Color.Black else MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(12.dp))
                            }
                            Spacer(Modifier.height(4.dp))

                            OutlinedTextField(value = authUser, onValueChange = { authUser = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth().tid("sync_auth_user"))

                            // 2. Replace the old password field with this one
                            OutlinedTextField(
                                value = authPass,
                                onValueChange = { authPass = it },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    autoCorrectEnabled = false // Extra safety to prevent dictionary caching
                                ),
                                trailingIcon = {
                                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(imageVector = image, contentDescription = if (passwordVisible) "Hide password" else "Show password")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().tid("sync_auth_pass")
                            )
                        }
                    },
                    confirmButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { viewModel.registerToSyncServer(authUser, authPass); showAuthDialog = false },
                                modifier = Modifier.tid("sync_auth_register_btn")
                            ) { Text("Register", color = if (isEink) Color.Black else MaterialTheme.colorScheme.primary) }

                            Button(
                                onClick = { viewModel.loginToSyncServer(authUser, authPass); showAuthDialog = false },
                                colors = if (isEink) ButtonDefaults.buttonColors(containerColor = Color.Black) else ButtonDefaults.buttonColors(),
                                modifier = Modifier.tid("sync_auth_login_btn")
                            ) { Text("Log In") }
                        }
                    },
                    dismissButton = { TextButton(onClick = { showAuthDialog = false }) { Text("Cancel", color = Color.Gray) } }
                )
            }
        }
    }
}