@file:Suppress("FunctionNaming")

package org.sableos.messages.ui

import android.view.KeyEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import org.sableos.design.SableActionButton
import org.sableos.design.SableSize
import org.sableos.design.SableSpacing
import org.sableos.design.sableFocusRing
import org.sableos.messages.core.Addresses
import org.sableos.messages.core.MessagesText.t
import org.sableos.messages.data.ContactNumber

/** New message: type a number or a name (contacts are suggested), then the text; Enter sends. */
@Composable
internal fun ColumnScope.ComposeScreen(
    screen: Screen.Compose,
    state: MessagesUiState,
    actions: MessagesActions,
) {
    var to by remember(screen) { mutableStateOf(screen.recipients) }
    var body by remember(screen) { mutableStateOf(screen.body) }
    val toFocus = remember { FocusRequester() }
    val bodyFocus = remember { FocusRequester() }
    LaunchedEffect(screen) {
        runCatching { if (screen.recipients.isBlank()) toFocus.requestFocus() else bodyFocus.requestFocus() }
    }
    val pick: (ContactNumber) -> Unit = { contact ->
        to = contact.number
        actions.searchContacts("")
        runCatching { bodyFocus.requestFocus() }
    }

    ComposeHeader(actions)
    OutlinedTextField(
        value = to,
        onValueChange = {
            to = it
            actions.searchContacts(it)
        },
        modifier =
            Modifier.fillMaxWidth().focusRequester(toFocus).onPreviewKeyEvent { event ->
                if (!isEnter(event.nativeKeyEvent)) return@onPreviewKeyEvent false
                // Enter on a name picks the first contact; on a number it moves to the text.
                val first = state.suggestions.firstOrNull()
                when {
                    event.type != KeyEventType.KeyDown -> Unit
                    first != null && !Addresses.isSendable(to) -> pick(first)
                    else -> runCatching { bodyFocus.requestFocus() }
                }
                true
            },
        label = { Text(t("to_name_or_number")) },
        singleLine = true,
    )
    LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SableSpacing.Xs),
    ) {
        items(state.suggestions, key = { it.name + it.number }) { contact -> Suggestion(contact) { pick(contact) } }
    }
    MessageField(body, { body = it }, Addresses.isSendable(to.substringBefore(',')), bodyFocus) {
        actions.sendNew(to, body)
    }
}

/** The text and Send; Enter sends, Shift+Enter is a newline. */
@Composable
private fun MessageField(
    body: String,
    onBody: (String) -> Unit,
    recipientReady: Boolean,
    focus: FocusRequester,
    send: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = body,
            onValueChange = onBody,
            modifier =
                Modifier.weight(1f).focusRequester(focus).onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (!isEnter(native) || native.isShiftPressed) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown && body.isNotBlank()) send()
                    true
                },
            placeholder = { Text(t("text_message_enter_sends")) },
            maxLines = 4,
        )
        Button(
            onClick = send,
            enabled = body.isNotBlank() && recipientReady,
            modifier = Modifier.padding(start = SableSpacing.Sm),
            shape = MaterialTheme.shapes.medium,
        ) { Text(t("send")) }
    }
}

@Composable
private fun ComposeHeader(actions: MessagesActions) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = t("new_message"),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        SableActionButton(text = t("back"), primary = false, onClick = actions.back)
    }
}

private fun isEnter(event: KeyEvent): Boolean =
    event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

@Composable
private fun Suggestion(
    contact: ContactNumber,
    onPick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .sableFocusRing()
                .clickable(onClick = onPick)
                .heightIn(min = SableSize.TouchTarget)
                .padding(horizontal = SableSpacing.Md, vertical = SableSpacing.Xs),
    ) {
        Text(
            text = contact.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${contact.label} · ${contact.number}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
