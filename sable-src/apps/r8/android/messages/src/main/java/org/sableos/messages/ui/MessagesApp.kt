@file:Suppress("FunctionNaming")

package org.sableos.messages.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.sableos.design.SableActionButton
import org.sableos.design.SablePageHeader
import org.sableos.design.SablePanel
import org.sableos.design.SableSpacing
import org.sableos.messages.MessagesController
import org.sableos.messages.core.MessagesText.t
import java.text.DateFormat

@Composable
fun MessagesApp(controller: MessagesController) {
    val state = controller.state
    val actions = controller.actions
    BackHandler(enabled = state.screen != Screen.Threads) { actions.back() }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
                    .padding(horizontal = SableSpacing.ScreenHorizontal, vertical = SableSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
        ) {
            state.notice?.let { Notice(it, actions.dismissNotice) }
            AppContent(controller)
        }
    }
}

@Composable
private fun ColumnScope.AppContent(controller: MessagesController) {
    val state = controller.state
    val access = state.access
    when {
        !access.checked -> {
            Unit
        }

        !access.hasTelephony && !access.canRead -> {
            NoTelephonyScreen()
        }

        !access.canUseSms -> {
            AccessScreen(access, controller.actions)
        }

        else -> {
            when (val screen = state.screen) {
                Screen.Threads -> ThreadsScreen(state, controller.visibleThreads, controller.actions)
                is Screen.Conversation -> ConversationScreen(screen, state, controller.actions)
                is Screen.Compose -> ComposeScreen(screen, state, controller.actions)
            }
        }
    }
}

@Composable
private fun NoTelephonyScreen() {
    SablePageHeader(title = t("sable_messages"), subtitle = t("no_mobile_network_for_texts"))
    Text(
        text = t("this_device_has_no_sms"),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AccessScreen(
    access: Access,
    actions: MessagesActions,
) {
    SablePageHeader(title = t("allow_sms"), subtitle = t("sable_messages_needs_to_read"))
    Text(
        text =
            t("texts_stay_on_this_phone"),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    if (access.roleAvailable && !access.isDefault) {
        SableActionButton(
            text = t("make_sable_messages_the_default"),
            modifier = Modifier.fillMaxWidth(),
            onClick = actions.requestDefaultRole,
        )
    }
    SableActionButton(
        text = t("allow_sms_access"),
        modifier = Modifier.fillMaxWidth(),
        primary = !access.roleAvailable || access.isDefault,
        onClick = actions.requestPermissions,
    )
    if (access.deniedForGood) {
        Text(
            text = t("android_won_t_ask_again"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        SableActionButton(
            text = t("open_app_settings"),
            modifier = Modifier.fillMaxWidth(),
            primary = false,
            onClick = actions.openAppSettings,
        )
    }
}

@Composable
internal fun Banner(
    text: String,
    action: String?,
    onAction: () -> Unit,
) {
    SablePanel {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        if (action != null) SableActionButton(text = action, primary = false, onClick = onAction)
    }
}

@Composable
private fun Notice(
    text: String,
    onDismiss: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        SableActionButton(text = "OK", primary = false, onClick = onDismiss)
    }
}

/** "19:10" today, a short date otherwise. */
internal fun shortTime(millis: Long): String =
    DateUtils
        .formatSameDayTime(millis, System.currentTimeMillis(), DateFormat.SHORT, DateFormat.SHORT)
        .toString()
