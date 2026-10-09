package org.sableos.hub.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.sableos.design.SableActionButton
import org.sableos.design.SablePageHeader
import org.sableos.design.SablePanel
import org.sableos.design.SableScrollableScreen
import org.sableos.hub.policy.AttentionDefault
import org.sableos.hub.policy.AttentionDefaults
import org.sableos.hub.policy.AttentionDeviceProfile
import org.sableos.hub.policy.AttentionOutput
import org.sableos.hub.policy.NotificationConcept
import org.sableos.hub.policy.OutputOwner

@Composable
internal fun AttentionSettingsScreen(
    profile: AttentionDeviceProfile,
    onOpenAndroid: (NotificationConcept) -> Unit,
    onOpenConnectedApps: () -> Unit,
    onBack: () -> Unit,
) {
    SableScrollableScreen {
        SablePageHeader(
            title = "sable attention",
            subtitle =
                "Android decides whether a notification alerts. Sable Attention only maps that " +
                    "decision to outputs this device supports, and never bypasses Do Not Disturb.",
        )

        SablePanel {
            Text(text = "Android notification settings", style = MaterialTheme.typography.titleMedium)
            AndroidLink(
                label = "Notifications: sound, vibration, pop-up, lock screen",
                concept = NotificationConcept.Channels,
                onOpenAndroid = onOpenAndroid,
            )
            AndroidLink("Conversations", NotificationConcept.ConversationPriority, onOpenAndroid)
            AndroidLink("Do Not Disturb", NotificationConcept.DoNotDisturb, onOpenAndroid)
            AndroidLink("Notification history", NotificationConcept.NotificationHistory, onOpenAndroid)
        }

        SablePanel {
            Text(text = "This device", style = MaterialTheme.typography.titleMedium)
            profile.visibleOutputs().forEach { output ->
                OutputRow(output, onOpenAndroid, onOpenConnectedApps)
            }
            if (profile.selectableOutputs().isEmpty()) {
                Text(
                    text =
                        "This device profile declares no Sable-controlled attention outputs, so " +
                            "notifications use only what Android provides.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SableActionButton(text = "Back", primary = false, onClick = onBack)
    }
}

@Composable
private fun OutputRow(
    output: AttentionOutput,
    onOpenAndroid: (NotificationConcept) -> Unit,
    onOpenConnectedApps: () -> Unit,
) {
    Text(text = attentionLabel(output), style = MaterialTheme.typography.bodyLarge)
    if (output.owner == OutputOwner.Sable) {
        val default =
            if (AttentionDefaults.defaultFor(output) == AttentionDefault.Off) {
                "Off unless you turn it on for an app."
            } else {
                "On for apps you have not configured; locked and private content is redacted."
            }
        Text(
            text = "$default Choose apps in Connected apps.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onOpenConnectedApps) { Text("Connected apps") }
    } else {
        Text(
            text = "Follows Android notification settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { onOpenAndroid(output.concept) }) { Text("Open Android settings") }
    }
}

@Composable
private fun AndroidLink(
    label: String,
    concept: NotificationConcept,
    onOpenAndroid: (NotificationConcept) -> Unit,
) {
    TextButton(onClick = { onOpenAndroid(concept) }) { Text(label) }
}
