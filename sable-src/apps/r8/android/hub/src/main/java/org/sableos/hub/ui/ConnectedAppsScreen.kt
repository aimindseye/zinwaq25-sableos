package org.sableos.hub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.sableos.design.SableActionButton
import org.sableos.design.SablePageHeader
import org.sableos.design.SablePanel
import org.sableos.design.SableRefreshableSurface
import org.sableos.design.SableScreen
import org.sableos.design.SableSpacing
import org.sableos.hub.ConnectedAppCandidate
import org.sableos.hub.ConnectedAppKey
import org.sableos.hub.ConnectedAppPolicy
import org.sableos.hub.policy.AttentionOutput
import org.sableos.hub.policy.ConnectedAppsParity
import java.util.Locale

/** Sable Attention state for the connected-apps screen: only profile-supported Sable outputs. */
internal data class AttentionUi(
    val selectableOutputs: List<AttentionOutput>,
    val selections: Map<ConnectedAppKey, Set<AttentionOutput>>,
)

private enum class ConnectedAppsPivot {
    Favorites,
    Enabled,
    Available,
}

@Composable
internal fun ConnectedAppsScreen(
    candidates: List<ConnectedAppCandidate>,
    policies: Map<ConnectedAppKey, ConnectedAppPolicy>,
    notificationAccessGranted: Boolean,
    loading: Boolean,
    onRefresh: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onPolicyChanged: (ConnectedAppPolicy) -> Unit,
    attention: AttentionUi = AttentionUi(emptyList(), emptyMap()),
    focusedKey: ConnectedAppKey? = null,
    privateMode: Boolean = false,
    onPrivateModeChanged: (Boolean) -> Unit = {},
    onAttentionChanged: (ConnectedAppKey, Set<AttentionOutput>) -> Unit = { _, _ -> },
    onOpenAndroidNotificationSettings: (ConnectedAppKey) -> Unit = {},
    onOpenAttentionSettings: () -> Unit = {},
) {
    var pivot by remember {
        mutableStateOf(
            if (focusedKey != null) ConnectedAppsPivot.Available else ConnectedAppsPivot.Favorites,
        )
    }
    var search by remember {
        mutableStateOf("")
    }

    SableRefreshableSurface(
        isRefreshing = loading,
        onRefresh = onRefresh,
    ) {
        SableScreen {
            SablePageHeader(
                title = "connected apps",
                subtitle =
                    "Choose installed apps that may contribute notification-derived conversations.",
            )

            SablePanel {
                Text(
                    text =
                        if (notificationAccessGranted) {
                            "Notification access is enabled."
                        } else {
                            "Notification access is required before connected apps can " +
                                "contribute messages."
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
                SableActionButton(
                    text =
                        if (notificationAccessGranted) {
                            "Review notification access"
                        } else {
                            "Enable notification access"
                        },
                    primary = !notificationAccessGranted,
                    onClick = onOpenNotificationAccess,
                )
                PolicySwitchRow(
                    label = "Private mode: hide senders and message text in Hub",
                    checked = privateMode,
                    enabled = true,
                    onCheckedChange = onPrivateModeChanged,
                )
                TextButton(onClick = onOpenAttentionSettings) {
                    Text("Sable Attention")
                }
            }

            Text(
                text =
                    "Sable does not sign into these services. The installed app keeps " +
                        "networking, " +
                        "encryption, account state and complete history.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (loading) {
                Text(
                    text = "Loading installed apps…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (focusedKey != null && candidates.any { it.key == focusedKey }) {
                // Opened from Android Settings for one app: show just that app's Hub/Attention.
                val candidate = candidates.first { it.key == focusedKey }
                ConnectedAppRow(
                    candidate = candidate,
                    policy = policies[candidate.key] ?: ConnectedAppPolicy(key = candidate.key),
                    notificationAccessGranted = notificationAccessGranted,
                    attention = attention,
                    onPolicyChanged = onPolicyChanged,
                    onAttentionChanged = onAttentionChanged,
                    onOpenAndroidNotificationSettings = onOpenAndroidNotificationSettings,
                )
            } else {
                ConnectedAppsPivotRow(
                    selected = pivot,
                    onSelect = { selected ->
                        pivot = selected
                        search = ""
                    },
                )

                if (pivot != ConnectedAppsPivot.Enabled) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search installed apps") },
                    )
                }

                val visibleCandidates =
                    visibleCandidates(
                        candidates = candidates,
                        policies = policies,
                        pivot = pivot,
                        search = search,
                    )

                if (visibleCandidates.isEmpty()) {
                    Text(
                        text =
                            when (pivot) {
                                ConnectedAppsPivot.Favorites -> {
                                    if (search.isBlank()) {
                                        "No favorite apps are set."
                                    } else {
                                        "No favorite apps match this search."
                                    }
                                }

                                ConnectedAppsPivot.Enabled -> {
                                    "No connected apps are enabled."
                                }

                                ConnectedAppsPivot.Available -> {
                                    if (search.isBlank()) {
                                        "No additional installed apps are available."
                                    } else {
                                        "No installed apps match this search."
                                    }
                                }
                            },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(SableSpacing.Xs),
                    ) {
                        items(
                            items = visibleCandidates,
                            key = { candidate ->
                                candidate.key.packageName + ":" + candidate.key.userSerial
                            },
                        ) { candidate ->
                            val policy =
                                policies[candidate.key]
                                    ?: ConnectedAppPolicy(key = candidate.key)
                            ConnectedAppRow(
                                candidate = candidate,
                                policy = policy,
                                notificationAccessGranted = notificationAccessGranted,
                                attention = attention,
                                onPolicyChanged = onPolicyChanged,
                                onAttentionChanged = onAttentionChanged,
                                onOpenAndroidNotificationSettings = onOpenAndroidNotificationSettings,
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

private fun visibleCandidates(
    candidates: List<ConnectedAppCandidate>,
    policies: Map<ConnectedAppKey, ConnectedAppPolicy>,
    pivot: ConnectedAppsPivot,
    search: String,
): List<ConnectedAppCandidate> {
    val locale = Locale.getDefault()
    return candidates
        .filter { candidate ->
            val policy = policies[candidate.key]?.normalized()
            val enabled = policy?.includeInMessages == true
            val favorite = policy?.favorite == true
            val belongsToPivot =
                when (pivot) {
                    ConnectedAppsPivot.Favorites -> favorite
                    ConnectedAppsPivot.Enabled -> enabled
                    ConnectedAppsPivot.Available -> !enabled
                }
            val matchesSearch =
                search.isBlank() ||
                    candidate.label.contains(search, ignoreCase = true) ||
                    candidate.key.packageName.contains(search, ignoreCase = true)

            belongsToPivot && matchesSearch
        }.sortedWith(
            compareByDescending<ConnectedAppCandidate> { candidate ->
                policies[candidate.key]?.normalized()?.favorite == true
            }.thenBy {
                it.label.lowercase(locale)
            }.thenBy {
                it.key.packageName
            }.thenBy {
                it.key.userSerial
            },
        )
}

@Composable
private fun ConnectedAppsPivotRow(
    selected: ConnectedAppsPivot,
    onSelect: (ConnectedAppsPivot) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SableSpacing.Lg),
    ) {
        ConnectedAppsPivot.entries.forEach { pivot ->
            TextButton(
                onClick = { onSelect(pivot) },
            ) {
                Text(
                    text = pivot.name.lowercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color =
                        if (pivot == selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}

@Composable
private fun ConnectedAppRow(
    candidate: ConnectedAppCandidate,
    policy: ConnectedAppPolicy,
    notificationAccessGranted: Boolean,
    attention: AttentionUi,
    onPolicyChanged: (ConnectedAppPolicy) -> Unit,
    onAttentionChanged: (ConnectedAppKey, Set<AttentionOutput>) -> Unit,
    onOpenAndroidNotificationSettings: (ConnectedAppKey) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = SableSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SableSpacing.Md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.label,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = candidate.key.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text =
                        candidate.profileLabel + " · " +
                            ConnectedAppsParity.statusLabel(
                                ConnectedAppsParity.status(
                                    policy = policy,
                                    notificationAccessGranted = notificationAccessGranted,
                                    profileLocked = candidate.profileLocked,
                                ),
                            ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = policy.includeInMessages,
                onCheckedChange = { enabled ->
                    onPolicyChanged(
                        policy.copy(includeInMessages = enabled).normalized(),
                    )
                },
            )
        }

        // Delivery (alerting, sound, vibration, pop-up, badge, lock screen) is Android's.
        TextButton(onClick = { onOpenAndroidNotificationSettings(candidate.key) }) {
            Text("Delivery: Android notification settings")
        }

        PolicySwitchRow(
            label = "Hub priority (ordering only, not a Do Not Disturb exception)",
            checked = policy.favorite,
            enabled = true,
            onCheckedChange = { enabled ->
                onPolicyChanged(policy.copy(favorite = enabled).normalized())
            },
        )

        if (policy.includeInMessages) {
            PolicySwitchRow(
                label = "Allow quick reply",
                checked = policy.allowQuickReply,
                enabled = true,
                onCheckedChange = { enabled ->
                    onPolicyChanged(policy.copy(allowQuickReply = enabled))
                },
            )
            PolicySwitchRow(
                label = "Hide from SableLauncher",
                checked = policy.hideFromLauncher,
                enabled = true,
                onCheckedChange = { enabled ->
                    onPolicyChanged(policy.copy(hideFromLauncher = enabled))
                },
            )
            TextButton(
                onClick = {
                    onPolicyChanged(
                        policy.copy(
                            retention = policy.retention.next(),
                        ),
                    )
                },
            ) {
                Text("History retention: ${policy.retention.label}")
            }
            TextButton(
                onClick = {
                    onPolicyChanged(policy.copy(previewPolicy = policy.previewPolicy.next()))
                },
            ) {
                Text("Hub preview: ${policy.previewPolicy.label}")
            }
        }

        // Attention: only outputs this device profile validated; unsupported ones are hidden.
        val selected = attention.selections[candidate.key].orEmpty()
        attention.selectableOutputs.forEach { output ->
            PolicySwitchRow(
                label = "Attention: ${attentionLabel(output)}",
                checked = output in selected,
                enabled = true,
                onCheckedChange = { enabled ->
                    onAttentionChanged(
                        candidate.key,
                        if (enabled) selected + output else selected - output,
                    )
                },
            )
        }
    }
}

internal fun attentionLabel(output: AttentionOutput): String =
    when (output) {
        AttentionOutput.Audio -> "Sound"
        AttentionOutput.Haptic -> "Vibration"
        AttentionOutput.StatusLed -> "Notification light"
        AttentionOutput.KeyboardBacklight -> "Keyboard backlight"
        AttentionOutput.SecondaryDisplay -> "Glance display"
        AttentionOutput.AlwaysOnDisplay -> "Always-on display"
    }

@Composable
private fun PolicySwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SableSpacing.Md),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}
