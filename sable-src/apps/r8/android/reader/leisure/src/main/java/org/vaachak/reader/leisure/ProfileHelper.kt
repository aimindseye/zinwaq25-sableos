package org.vaachak.reader.leisure

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.vaachak.reader.core.domain.model.ProfileEntity
import org.vaachak.reader.leisure.ui.settings.ProfileViewModel
import org.vaachak.reader.leisure.ui.testability.Tid
import org.vaachak.reader.leisure.ui.testability.TidScreen
import org.vaachak.reader.leisure.ui.testability.tid

@Composable
fun ProfilePickerScreen(
    isEink: Boolean,
    viewModel: ProfileViewModel,
    onProfileSelected: (String) -> Unit
) {
    val contentColor = if (isEink) Color.Black else MaterialTheme.colorScheme.onBackground
    val bgColor = if (isEink) Color.White else MaterialTheme.colorScheme.background

    // Reactively observe the database and multi-user state!
    val profiles by viewModel.profiles.collectAsState()
    val pinError by viewModel.pinError.collectAsState()
    val isMultiUserMode by viewModel.isMultiUserMode.collectAsState()

    // Navigation States (Replaces the Dialogs!)
    var showAddScreen by remember { mutableStateOf(false) }
    var profileRequiringPin by remember { mutableStateOf<ProfileEntity?>(null) }
    var enteredPin by remember { mutableStateOf("") }

    TidScreen(Tid.Screen.vault) {
        // --- FIX: Allow adding if Multi-User is ON, OR if there are zero profiles ---
        if (showAddScreen && (isMultiUserMode || profiles.isEmpty())) {
            // --- FULL-SCREEN: ADD PROFILE ---
            var newName by remember { mutableStateOf("") }
            var newPin by remember { mutableStateOf("") }

            val generateRandomName = {
                val adjectives = listOf("Quiet", "Curious", "Sleepy", "Brave", "Clever", "Cosmic", "Wandering", "Lunar", "Mystic", "Nimble")
                val nouns = listOf("Reader", "Owl", "Fox", "Panda", "Badger", "Bear", "Cat", "Dragon", "Raven", "Wolf")
                newName = "${adjectives.random()} ${nouns.random()}"
            }

            Column(
                modifier = Modifier.fillMaxSize().background(bgColor).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Add Reader Profile", style = MaterialTheme.typography.headlineMedium, color = contentColor)
                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(0.8f).tid("profile_picker_name"),
                    trailingIcon = {
                        IconButton(onClick = generateRandomName, modifier = Modifier.tid("profile_picker_random_name")) {
                            Icon(Icons.Default.Refresh, contentDescription = "Generate Random Name", tint = contentColor)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it },
                    label = { Text("PIN (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(0.8f).tid("profile_picker_pin"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text("Leave PIN blank to login instantly.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)

                Spacer(modifier = Modifier.height(32.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextButton(onClick = { showAddScreen = false }, modifier = Modifier.tid("profile_picker_cancel")) {
                        Text("Cancel", color = contentColor)
                    }
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                viewModel.createProfile(name = newName.trim(), pin = newPin.ifBlank { null })
                                showAddScreen = false
                            }
                        },
                        modifier = Modifier.tid("profile_picker_create")
                    ) {
                        Text("Create")
                    }
                }
            }

        } else if (profileRequiringPin != null) {
            // --- FULL-SCREEN: PIN ENTRY ---
            Column(
                modifier = Modifier.fillMaxSize().background(bgColor).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Welcome back, ${profileRequiringPin?.name}", style = MaterialTheme.typography.headlineMedium, color = contentColor)
                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = enteredPin,
                    onValueChange = { enteredPin = it; viewModel.clearPinError() },
                    label = { Text("Enter PIN") },
                    singleLine = true,
                    isError = pinError != null,
                    modifier = Modifier.fillMaxWidth(0.8f).tid("profile_picker_enter_pin"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )

                if (pinError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(pinError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextButton(
                        onClick = {
                            profileRequiringPin = null
                            viewModel.clearPinError()
                        },
                        modifier = Modifier.tid("profile_picker_cancel_pin")
                    ) { Text("Cancel", color = contentColor) }

                    Button(
                        onClick = {
                            val profile = profileRequiringPin ?: return@Button
                            viewModel.selectProfile(profile, enteredPin) {
                                profileRequiringPin = null
                                onProfileSelected(it)
                            }
                        },
                        modifier = Modifier.tid("profile_picker_unlock")
                    ) { Text("Unlock") }
                }
            }

        } else {
            // --- ORIGINAL: PROFILE PICKER ---
            Column(
                modifier = Modifier.fillMaxSize().background(bgColor),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Who's reading?",
                    style = MaterialTheme.typography.headlineLarge,
                    color = contentColor,
                    modifier = Modifier.tid(Tid.Vault.title)
                )
                Spacer(modifier = Modifier.height(48.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    profiles.forEach { profile ->
                        ProfileAvatar(
                            profileId = profile.profileId,
                            name = profile.name,
                            isEink = isEink,
                            onClick = {
                                if (profile.pinHash == null) {
                                    viewModel.selectProfile(profile, null, onProfileSelected)
                                } else {
                                    profileRequiringPin = profile
                                    enteredPin = ""
                                    viewModel.clearPinError()
                                }
                            }
                        )
                    }

                    // --- FIX: Conditionally show "Add Profile" button if Multi-User OR Empty ---
                    if (isMultiUserMode || profiles.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .tid(Tid.Vault.addProfile)
                                .semantics(mergeDescendants = true) {
                                    contentDescription = "Add New Reader Profile"
                                }
                                .clickable { showAddScreen = true }
                        ) {
                            Box(
                                modifier = Modifier.size(100.dp).background(if (isEink) Color.LightGray else Color.DarkGray, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(48.dp), tint = contentColor)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Add Profile", style = MaterialTheme.typography.titleMedium, color = contentColor)
                        }
                    }
                    // ----------------------------------------------------
                }
            }
        }
    }
}

@Composable
fun ProfileAvatar(profileId: String, name: String, isEink: Boolean, onClick: () -> Unit) {
    val contentColor = if (isEink) Color.Black else MaterialTheme.colorScheme.onBackground
    val boxColor = if (isEink) Color.Black else MaterialTheme.colorScheme.primary
    val letterColor = if (isEink) Color.White else MaterialTheme.colorScheme.onPrimary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .tid(Tid.Vault.profile(profileId))
            .semantics(mergeDescendants = true) {
                contentDescription = "Login as Profile: $name"
            }
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.size(100.dp).background(boxColor, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(name.take(1).uppercase(), style = MaterialTheme.typography.displayMedium, color = letterColor)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(name, style = MaterialTheme.typography.titleMedium, color = contentColor)
    }
}