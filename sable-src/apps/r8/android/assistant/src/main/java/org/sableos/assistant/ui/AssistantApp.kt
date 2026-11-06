@file:Suppress("FunctionNaming") // Compose functions are named like types.

package org.sableos.assistant.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import org.sableos.assistant.core.AssistantText.t
import org.sableos.assistant.core.ChatTurn
import org.sableos.design.SableActionButton
import org.sableos.design.SableSpacing
import org.sableos.design.sableScreenPadding

@Composable
fun AssistantApp(
    state: AssistantState,
    actions: AssistantActions,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        if (state.showSettings) {
            AssistantSettingsScreen(state, actions)
        } else {
            ChatScreen(state, actions)
        }
    }
}

@Composable
private fun ChatScreen(
    state: AssistantState,
    actions: AssistantActions,
) {
    val name = state.settings.name
    val list = rememberLazyListState()
    LaunchedEffect(state.turns.size, state.busy, state.error) {
        list.animateScrollToItem(list.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
    }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .sableScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
    ) {
        ChatHeader(name, onNew = state::newChat, onSettings = { state.showSettings = true })
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
        ) {
            item { Bubble(ChatTurn(fromOwner = false, text = t("greeting", name))) }
            if (state.settings.provider == null) {
                item { Note(t("setup_first"), onClick = { state.showSettings = true }) }
            }
            items(state.turns) { Bubble(it) }
            if (state.listening) item { Note(t("listening")) }
            if (state.busy) item { Note(t("thinking", name)) }
            state.error?.let { message -> item { Note(message, error = true) } }
        }
        MessageBox(state, name, actions)
    }
}

@Composable
private fun ChatHeader(
    name: String,
    onNew: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CosgroveAvatar(Modifier.size(AVATAR_LARGE))
        Spacer(Modifier.size(SableSpacing.Md))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleLarge)
            Text(
                t("keys_hint"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onNew) { Text(t("new_chat")) }
        TextButton(onClick = onSettings) { Text(t("settings")) }
    }
}

/** Enter sends; Shift+Enter is a new line; Space held in an empty box talks. */
@Composable
private fun MessageBox(
    state: AssistantState,
    name: String,
    actions: AssistantActions,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm)) {
        OutlinedTextField(
            value = state.draft,
            onValueChange = { state.draft = it.take(MAX_MESSAGE) },
            placeholder = { Text(t("message_hint", name)) },
            maxLines = 4,
            modifier =
                Modifier.weight(1f).onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val send = event.key == Key.Enter && !event.isShiftPressed
                    val talk = event.key == Key.Spacebar && (state.draft.isEmpty() || state.listening)
                    when {
                        send && event.type == KeyEventType.KeyDown -> actions.send(state.draft)
                        talk && event.type == KeyEventType.KeyDown && native.repeatCount == 0 -> actions.talkDown()
                        talk && event.type == KeyEventType.KeyUp -> actions.talkUp()
                    }
                    send || talk
                },
        )
        TalkButton(state.listening, actions)
        SableActionButton(text = t("send"), onClick = { actions.send(state.draft) })
    }
}

/** Listens while pressed, like the Space key; a tap alone hears nothing. */
@Composable
private fun TalkButton(
    listening: Boolean,
    actions: AssistantActions,
) {
    Surface(
        shape = RoundedCornerShape(BUBBLE_CORNER),
        color = if (listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor =
            if (listening) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier.pointerInput(actions) {
                detectTapGestures(
                    onPress = {
                        actions.talkDown()
                        tryAwaitRelease()
                        actions.talkUp()
                    },
                )
            },
    ) {
        Text(t("talk"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(SableSpacing.Md))
    }
}

@Composable
private fun Bubble(turn: ChatTurn) {
    val owner = turn.fromOwner
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (owner) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!owner) {
            CosgroveAvatar(Modifier.size(AVATAR_SMALL))
            Spacer(Modifier.size(SableSpacing.Sm))
        }
        Surface(
            shape = RoundedCornerShape(BUBBLE_CORNER),
            color = if (owner) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            contentColor =
                if (owner) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = BUBBLE_MAX),
        ) {
            Text(turn.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(SableSpacing.Md))
        }
    }
}

@Composable
private fun Note(
    text: String,
    error: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    if (onClick == null) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    } else {
        TextButton(onClick = onClick) { Text(text, color = MaterialTheme.colorScheme.primary) }
    }
}

private val AVATAR_LARGE = 48.dp
private val AVATAR_SMALL = 28.dp
private val BUBBLE_CORNER = 14.dp
private val BUBBLE_MAX = 300.dp
private const val MAX_MESSAGE = 4_000
