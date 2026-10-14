package org.vaachak.reader.leisure.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import org.vaachak.reader.core.domain.model.ProfileEntity
import org.vaachak.reader.leisure.ui.testability.Tid
import org.vaachak.reader.leisure.ui.testability.TidScreen
import org.vaachak.reader.leisure.ui.testability.tid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderProfilesScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    // Reactively load profiles from Room!
    val profiles by viewModel.profiles.collectAsState()
    val pinError by viewModel.pinError.collectAsState()

    // State for the full-screen PIN change overlay
    var profileToEdit by remember { mutableStateOf<ProfileEntity?>(null) }
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }

    TidScreen(Tid.Screen.readerProfiles) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Manage Profiles") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.tid("reader_profiles_back")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->

            val editing = profileToEdit
            if (editing != null) {
                ChangePinForm(
                    profile = editing,
                    pinError = pinError,
                    oldPin = oldPin,
                    newPin = newPin,
                    onOldPinChange = { oldPin = it; viewModel.clearPinError() },
                    onNewPinChange = { newPin = it; viewModel.clearPinError() },
                    onCancel = {
                        profileToEdit = null
                        viewModel.clearPinError()
                    },
                    onSave = {
                        if (viewModel.changePin(editing, oldPin, newPin)) profileToEdit = null
                    }
                )
            } else if (profiles.isEmpty()) {
                // --- MAIN LIST: SHOW PROFILES ---
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No profiles found", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(profiles) { profile ->
                        ProfileCard(
                            profile = profile,
                            onEditPin = {
                                oldPin = ""
                                newPin = ""
                                viewModel.clearPinError()
                                profileToEdit = profile
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Full-screen form for setting, changing or removing a profile PIN. */
@Composable
private fun ChangePinForm(
    profile: ProfileEntity,
    pinError: String?,
    oldPin: String,
    newPin: String,
    onOldPinChange: (String) -> Unit,
    onNewPinChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Change PIN for ${profile.name}", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        // Only ask for Old PIN if they actually have one set
        if (profile.pinHash != null) {
            OutlinedTextField(
                value = oldPin,
                onValueChange = onOldPinChange,
                label = { Text("Current PIN") },
                singleLine = true,
                isError = pinError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.tid("reader_profiles_old_pin")
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = newPin,
            onValueChange = onNewPinChange,
            label = { Text("New PIN (Optional)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.tid("reader_profiles_new_pin")
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text("Leave New PIN blank to remove password.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)

        if (pinError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(pinError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.tid("reader_profiles_cancel_pin")
            ) {
                Text("Cancel")
            }
            Button(
                onClick = onSave,
                modifier = Modifier.tid("reader_profiles_save_pin")
            ) {
                Text("Save Changes")
            }
        }
    }
}

@Composable
private fun ProfileCard(profile: ProfileEntity, onEditPin: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .tid(Tid.Vault.profile(profile.profileId))
            .semantics(mergeDescendants = true) {
                contentDescription = "Profile: ${profile.name}, ${if (profile.pinHash != null) "Locked" else "Unlocked"}"
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar Box
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profile.name.take(1).uppercase(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Name
            Text(
                text = profile.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )

            // Change PIN Action
            IconButton(
                onClick = onEditPin,
                modifier = Modifier.tid("reader_profiles_edit_pin_${profile.profileId}")
            ) {
                val icon = if (profile.pinHash != null) Icons.Default.Lock else Icons.Default.LockOpen
                Icon(
                    imageVector = icon,
                    contentDescription = if (profile.pinHash != null) "Change PIN" else "Set PIN"
                )
            }
        }
    }
}
