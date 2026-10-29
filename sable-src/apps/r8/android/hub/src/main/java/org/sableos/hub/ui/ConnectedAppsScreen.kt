package org.sableos.hub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import org.sableos.design.SableSpacing
import org.sableos.hub.ConnectedAppCandidate
import org.sableos.hub.ConnectedAppKey
import org.sableos.hub.ConnectedAppPolicy
import org.sableos.hub.HubText.t
import org.sableos.hub.policy.AttentionOutput
import org.sableos.hub.policy.ConnectedAppsParity
import org.sableos.hub.policy.HubMailSources
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
    combineConversations: Boolean = true,
    onCombineConversationsChanged: (Boolean) -> Unit = {},
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
        // One lazy list for the header, the settings panel and the apps: on the Q25's square
        // screen the panel alone fills the window, so a fixed header above a weighted list left
        // no room for the apps.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
                contentPadding =
                    PaddingValues(
                        horizontal = SableSpacing.ScreenHorizontal,
                        vertical = SableSpacing.ScreenVertical,
                    ),
                verticalArrangement = Arrangement.spacedBy(SableSpacing.Lg),
            ) {
                item {
                    SablePageHeader(
                        title = t("connected_apps"),
                        subtitle =
                            t("choose_installed_apps_that_may"),
                    )
                }
                item {
                    NotificationAccessPanel(
                        notificationAccessGranted = notificationAccessGranted,
                        onOpenNotificationAccess = onOpenNotificationAccess,
                        privateMode = privateMode,
                        onPrivateModeChanged = onPrivateModeChanged,
                        combineConversations = combineConversations,
                        onCombineConversationsChanged = onCombineConversationsChanged,
                        onOpenAttentionSettings = onOpenAttentionSettings,
                    )
                }
                item {
                    Text(
                        text =
                            t("sable_does_not_sign_into"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Opened from Android Settings for one app: show just that app's Hub/Attention.
                val focusedCandidate = focusedKey?.let { key -> candidates.firstOrNull { it.key == key } }
                when {
                    loading -> {
                        item {
                            Text(
                                text = t("loading_installed_apps"),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    focusedCandidate != null -> {
                        item {
                            ConnectedAppRow(
                                candidate = focusedCandidate,
                                policy = policyFor(policies, focusedCandidate),
                                context = rowContext,
                            )
                        }
                    }

                    else -> {
                        connectedAppsBrowser(
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
    combineConversations: Boolean,
    onCombineConversationsChanged: (Boolean) -> Unit,
    onOpenAttentionSettings: () -> Unit,
) {
    SablePanel {
        Text(
            text =
                if (notificationAccessGranted) {
                    t("notification_access_is_enabled")
                } else {
                    t("notification_access_is_required_before")
                },
            style = MaterialTheme.typography.bodyMedium,
        )
        SableActionButton(
            text =
                if (notificationAccessGranted) {
                    t("review_notification_access")
                } else {
                    t("enable_notification_access")
                },
            primary = !notificationAccessGranted,
            onClick = onOpenNotificationAccess,
        )
        PolicySwitchRow(
            label = t("private_mode_hub_shows_counts"),
            checked = privateMode,
            enabled = true,
            onCheckedChange = onPrivateModeChanged,
        )
        PolicySwitchRow(
            label = t("combine_conversations"),
            detail =
                t("group_adjacent_messages_from_the"),
            checked = combineConversations,
            enabled = true,
            onCheckedChange = onCombineConversationsChanged,
        )
        TextButton(onClick = onOpenAttentionSettings) {
            Text(t("sable_attention"))
        }
    }
}

private fun LazyListScope.connectedAppsBrowser(
    candidates: List<ConnectedAppCandidate>,
    policies: Map<ConnectedAppKey, ConnectedAppPolicy>,
    pivot: ConnectedAppsPivot,
    search: String,
    onPivotChange: (ConnectedAppsPivot) -> Unit,
    onSearchChange: (String) -> Unit,
    rowContext: ConnectedAppRowContext,
) {
    item {
        ConnectedAppsPivotRow(
            selected = pivot,
            onSelect = onPivotChange,
        )
    }

    if (pivot != ConnectedAppsPivot.Enabled) {
        item {
            OutlinedTextField(
                value = search,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(t("search_installed_apps")) },
            )
        }
    }

    val visibleCandidates =
        visibleCandidates(
            candidates = candidates,
            policies = policies,
            pivot = pivot,
            search = search,
        )

    item {
        MailAppsNote(
            candidates = candidates,
            policies = policies,
        )
    }

    if (visibleCandidates.isEmpty()) {
        item {
            Text(
                text = emptyCandidatesText(pivot, search),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

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

/** Mail apps are opt-in sources like any other; one line of guidance, plus a duplicate warning. */
@Composable
private fun MailAppsNote(
    candidates: List<ConnectedAppCandidate>,
    policies: Map<ConnectedAppKey, ConnectedAppPolicy>,
) {
    if (candidates.none { candidate -> HubMailSources.isKnownMailPackage(candidate.key.packageName) }) return
    val includedPackages =
        policies.values
            .filter { policy -> policy.normalized().includeInMessages }
            .map { policy -> policy.key.packageName }
    Column(verticalArrangement = Arrangement.spacedBy(SableSpacing.Xs)) {
        Text(
            text =
                t("use_one_app_per_email"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (HubMailSources.duplicateAccountWarning(includedPackages)) {
            Text(
                text = t("more_than_one_mail_app"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun emptyCandidatesText(
    pivot: ConnectedAppsPivot,
    search: String,
): String =
    when {
        pivot == ConnectedAppsPivot.Enabled -> t("no_connected_apps_are_enabled")
        pivot == ConnectedAppsPivot.Favorites && search.isBlank() -> t("no_favorite_apps_are_set")
        pivot == ConnectedAppsPivot.Favorites -> t("no_favorite_apps_match_this")
        search.isBlank() -> t("no_additional_installed_apps_are")
        else -> t("no_installed_apps_match_this")
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
            }.thenByDescending { candidate ->
                // Installed mail apps group together near the top so they are easy to turn on.
                HubMailSources.isKnownMailPackage(candidate.key.packageName)
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
            Text(t("delivery_android_notification_settings"))
        }

        PolicySwitchRow(
            label = t("hub_priority_ordering_only_not"),
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
                    mailPrefix(candidate) + candidate.profileLabel + " · " +
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

private fun mailPrefix(candidate: ConnectedAppCandidate): String =
    if (HubMailSources.isKnownMailPackage(candidate.key.packageName)) t("mail") else ""

@Composable
private fun ConnectedAppMessageOptions(
    policy: ConnectedAppPolicy,
    onPolicyChanged: (ConnectedAppPolicy) -> Unit,
) {
    PolicySwitchRow(
        label = t("allow_quick_reply"),
        checked = policy.allowQuickReply,
        enabled = true,
        onCheckedChange = { enabled ->
            onPolicyChanged(policy.copy(allowQuickReply = enabled))
        },
    )
    PolicySwitchRow(
        label = t("hide_from_sablelauncher"),
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
        Text(t("history_retention", policy.retention.label))
    }
    TextButton(
        onClick = {
            onPolicyChanged(policy.copy(previewPolicy = policy.previewPolicy.next()))
        },
    ) {
        Text(t("hub_preview", policy.previewPolicy.label))
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
            label = t("attention", attentionLabel(output)),
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
        AttentionOutput.Audio -> t("sound")
        AttentionOutput.Haptic -> t("vibration")
        AttentionOutput.StatusLed -> t("notification_light")
        AttentionOutput.KeyboardBacklight -> t("keyboard_backlight")
        AttentionOutput.SecondaryDisplay -> t("glance_display")
        AttentionOutput.AlwaysOnDisplay -> t("always_on_display")
    }

@Composable
private fun PolicySwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    detail: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SableSpacing.Md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            detail?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}
