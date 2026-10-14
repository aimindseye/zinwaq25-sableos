package org.sableos.hub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import org.sableos.design.SableAdaptiveTopNav
import org.sableos.design.SableDestination
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
    val rowContext =
        ConnectedAppRowContext(
            notificationAccessGranted = notificationAccessGranted,
            attention = attention,
            onPolicyChanged = onPolicyChanged,
            onAttentionChanged = onAttentionChanged,
            onOpenAndroidNotificationSettings = onOpenAndroidNotificationSettings,
        )

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

            NotificationAccessPanel(
                notificationAccessGranted = notificationAccessGranted,
                onOpenNotificationAccess = onOpenNotificationAccess,
                privateMode = privateMode,
                onPrivateModeChanged = onPrivateModeChanged,
                onOpenAttentionSettings = onOpenAttentionSettings,
            )

            Text(
                text =
                    "Sable does not sign into these services. The installed app keeps " +
                        "networking, " +
                        "encryption, account state and complete history.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Opened from Android Settings for one app: show just that app's Hub/Attention.
            val focusedCandidate = focusedKey?.let { key -> candidates.firstOrNull { it.key == key } }
            when {
                loading -> {
                    Text(
                        text = "Loading installed apps…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                focusedCandidate != null -> {
                    ConnectedAppRow(
                        candidate = focusedCandidate,
                        policy = policyFor(policies, focusedCandidate),
                        context = rowContext,
                    )
                }

                else -> {
                    ConnectedAppsBrowser(
                        candidates = candidates,
                        policies = policies,
                        pivot = pivot,
                        search = search,
                        onPivotChange = { selected ->
                            pivot = selected
                            search = ""
                        },
                        onSearchChange = { search = it },
                        rowContext = rowContext,
                    )
                }
            }
        }
    }
}

/** What every connected-app row needs besides its own candidate and policy. */
private class ConnectedAppRowContext(
    val notificationAccessGranted: Boolean,
    val attention: AttentionUi,
    val onPolicyChanged: (ConnectedAppPolicy) -> Unit,
    val onAttentionChanged: (ConnectedAppKey, Set<AttentionOutput>) -> Unit,
    val onOpenAndroidNotificationSettings: (ConnectedAppKey) -> Unit,
)

private fun policyFor(
    policies: Map<ConnectedAppKey, ConnectedAppPolicy>,
    candidate: ConnectedAppCandidate,
): ConnectedAppPolicy = policies[candidate.key] ?: ConnectedAppPolicy(key = candidate.key)

@Composable
private fun NotificationAccessPanel(
    notificationAccessGranted: Boolean,
    onOpenNotificationAccess: () -> Unit,
    privateMode: Boolean,
    onPrivateModeChanged: (Boolean) -> Unit,
    onOpenAttentionSettings: () -> Unit,
) {
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
}

@Composable
private fun ColumnScope.ConnectedAppsBrowser(
    candidates: List<ConnectedAppCandidate>,
    policies: Map<ConnectedAppKey, ConnectedAppPolicy>,
    pivot: ConnectedAppsPivot,
    search: String,
    onPivotChange: (ConnectedAppsPivot) -> Unit,
    onSearchChange: (String) -> Unit,
    rowContext: ConnectedAppRowContext,
) {
    ConnectedAppsPivotRow(
        selected = pivot,
        onSelect = onPivotChange,
    )

    if (pivot != ConnectedAppsPivot.Enabled) {
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
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
            text = emptyCandidatesText(pivot, search),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

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
            ConnectedAppRow(
                candidate = candidate,
                policy = policyFor(policies, candidate),
                context = rowContext,
            )
            HorizontalDivider()
        }
    }
}

private fun emptyCandidatesText(
    pivot: ConnectedAppsPivot,
    search: String,
): String =
    when {
        pivot == ConnectedAppsPivot.Enabled -> "No connected apps are enabled."
        pivot == ConnectedAppsPivot.Favorites && search.isBlank() -> "No favorite apps are set."
        pivot == ConnectedAppsPivot.Favorites -> "No favorite apps match this search."
        search.isBlank() -> "No additional installed apps are available."
        else -> "No installed apps match this search."
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
    // Three titleLarge labels overflow a fixed row on square screens; the adaptive
    // nav keeps whole labels (and uses "more" if they still do not fit).
    SableAdaptiveTopNav(
        destinations = CONNECTED_APPS_DESTINATIONS,
        selectedId = selected.name,
        onSelect = { id -> onSelect(ConnectedAppsPivot.valueOf(id)) },
    )
}

private val CONNECTED_APPS_DESTINATIONS =
    ConnectedAppsPivot.entries.map { pivot -> SableDestination(pivot.name, pivot.name.lowercase()) }

@Composable
private fun ConnectedAppRow(
    candidate: ConnectedAppCandidate,
    policy: ConnectedAppPolicy,
    context: ConnectedAppRowContext,
) {
    val onPolicyChanged = context.onPolicyChanged
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = SableSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
    ) {
        ConnectedAppSummary(
            candidate = candidate,
            policy = policy,
            notificationAccessGranted = context.notificationAccessGranted,
            onPolicyChanged = onPolicyChanged,
        )

        // Delivery (alerting, sound, vibration, pop-up, badge, lock screen) is Android's.
        TextButton(onClick = { context.onOpenAndroidNotificationSettings(candidate.key) }) {
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
            ConnectedAppMessageOptions(
                policy = policy,
                onPolicyChanged = onPolicyChanged,
            )
        }

        ConnectedAppAttentionSwitches(
            key = candidate.key,
            attention = context.attention,
            onAttentionChanged = context.onAttentionChanged,
        )
    }
}

@Composable
private fun ConnectedAppSummary(
    candidate: ConnectedAppCandidate,
    policy: ConnectedAppPolicy,
    notificationAccessGranted: Boolean,
    onPolicyChanged: (ConnectedAppPolicy) -> Unit,
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
}

@Composable
private fun ConnectedAppMessageOptions(
    policy: ConnectedAppPolicy,
    onPolicyChanged: (ConnectedAppPolicy) -> Unit,
) {
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

/** Attention: only outputs this device profile validated; unsupported ones are hidden. */
@Composable
private fun ConnectedAppAttentionSwitches(
    key: ConnectedAppKey,
    attention: AttentionUi,
    onAttentionChanged: (ConnectedAppKey, Set<AttentionOutput>) -> Unit,
) {
    val selected = attention.selections[key].orEmpty()
    attention.selectableOutputs.forEach { output ->
        PolicySwitchRow(
            label = "Attention: ${attentionLabel(output)}",
            checked = output in selected,
            enabled = true,
            onCheckedChange = { enabled ->
                onAttentionChanged(key, toggled(selected, output, enabled))
            },
        )
    }
}

private fun toggled(
    selected: Set<AttentionOutput>,
    output: AttentionOutput,
    enabled: Boolean,
): Set<AttentionOutput> = if (enabled) selected + output else selected - output

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
