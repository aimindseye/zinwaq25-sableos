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
    SablePageHeader(title = "Sable Messages", subtitle = "No mobile network for texts")
    Text(
        text = "This device has no SMS service (no SIM or no mobile radio), so it can't send or receive texts.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AccessScreen(
    access: Access,
    actions: MessagesActions,
) {
    SablePageHeader(title = "Allow SMS", subtitle = "Sable Messages needs to read and send texts")
    Text(
        text =
            "Texts stay on this phone. Sable Messages reads them from Android's message store and " +
                "sends only what you write.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    if (access.roleAvailable && !access.isDefault) {
        SableActionButton(
            text = "Make Sable Messages the default SMS app",
            modifier = Modifier.fillMaxWidth(),
            onClick = actions.requestDefaultRole,
        )
    }
    SableActionButton(
        text = "Allow SMS access",
        modifier = Modifier.fillMaxWidth(),
        primary = !access.roleAvailable || access.isDefault,
        onClick = actions.requestPermissions,
    )
    if (access.deniedForGood) {
        Text(
            text = "Android won't ask again. In Settings > Apps > Sable Messages > Permissions, allow SMS.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        SableActionButton(
            text = "Open app settings",
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
