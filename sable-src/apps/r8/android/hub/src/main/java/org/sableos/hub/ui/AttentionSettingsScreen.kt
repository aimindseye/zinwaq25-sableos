package org.sableos.hub.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.sableos.design.SableActionButton
import org.sableos.design.SablePageHeader
import org.sableos.design.SablePanel
import org.sableos.design.SableScrollableScreen
import org.sableos.hub.HubText.t
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
            title = t("sable_attention_2"),
            subtitle =
                t("android_decides_whether_a_notification"),
        )

        SablePanel {
            Text(text = t("android_notification_settings"), style = MaterialTheme.typography.titleMedium)
            AndroidLink(
                label = t("notifications_sound_vibration_pop_up"),
                concept = NotificationConcept.Channels,
                onOpenAndroid = onOpenAndroid,
            )
            AndroidLink(t("conversations"), NotificationConcept.ConversationPriority, onOpenAndroid)
            AndroidLink(t("do_not_disturb"), NotificationConcept.DoNotDisturb, onOpenAndroid)
            AndroidLink(t("notification_history"), NotificationConcept.NotificationHistory, onOpenAndroid)
        }

        SablePanel {
            Text(text = t("this_device"), style = MaterialTheme.typography.titleMedium)
            profile.visibleOutputs().forEach { output ->
                OutputRow(output, onOpenAndroid, onOpenConnectedApps)
            }
            if (profile.selectableOutputs().isEmpty()) {
                Text(
                    text =
                        t("this_device_profile_declares_no"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SableActionButton(text = t("back"), primary = false, onClick = onBack)
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
                t("off_unless_you_turn_it")
            } else {
                t("on_for_apps_you_have")
            }
        Text(
            text = t("choose_apps_in_connected_apps", default),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onOpenConnectedApps) { Text(t("connected_apps_2")) }
    } else {
        Text(
            text = t("follows_android_notification_settings"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { onOpenAndroid(output.concept) }) { Text(t("open_android_settings")) }
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
