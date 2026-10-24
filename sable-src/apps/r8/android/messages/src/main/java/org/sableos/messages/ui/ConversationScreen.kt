@file:Suppress("FunctionNaming")

package org.sableos.messages.ui

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sableos.design.SableActionButton
import org.sableos.design.SableReturnToCurrent
import org.sableos.design.SableSpacing
import org.sableos.design.sableFocusRing
import org.sableos.messages.core.Attachment
import org.sableos.messages.core.ConversationCommand
import org.sableos.messages.core.ConversationKeys
import org.sableos.messages.core.ConversationMessage
import org.sableos.messages.core.DeliveryState
import org.sableos.messages.core.Direction
import org.sableos.messages.core.LatestEvent
import org.sableos.messages.core.LatestPolicy
import org.sableos.messages.core.LatestState
import org.sableos.messages.core.MessageStatus

private val BubbleMaxWidth = 300.dp
private val ImageMaxHeight = 220.dp
private const val IMAGE_MAX_PIXELS = 720

/**
 * One conversation, latest-anchored (MESSAGES_CONVERSATION_UX): it opens at the newest message,
 * follows sends and arrivals, and End or Esc come back from history.
 */
@Composable
internal fun ColumnScope.ConversationScreen(
    screen: Screen.Conversation,
    state: MessagesUiState,
    actions: MessagesActions,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val latestState = remember(screen.threadId) { mutableStateOf(LatestState()) }
    var latest by latestState
    val messages = state.conversation
    TrackLatest(screen.threadId, state, listState, latestState)

    val onKey: (KeyEvent) -> Boolean = { event ->
        val command = ConversationKeys.command(event.keyCode, event.isShiftPressed, latest.atLatest, draftBlank = true)
        when (command) {
            ConversationCommand.ReturnToLatest -> latest = LatestPolicy.reduce(latest, LatestEvent.ReturnToLatest)
            ConversationCommand.Back -> actions.back()
            ConversationCommand.OlderPage -> page(scope, listState, older = true)
            ConversationCommand.NewerPage -> page(scope, listState, older = false)
            ConversationCommand.Send, ConversationCommand.None -> Unit
        }
        command != ConversationCommand.None && command != ConversationCommand.Send
    }

    Column(
        modifier =
            Modifier.weight(1f).onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown && onKey(event.nativeKeyEvent)
            },
        verticalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
    ) {
        ConversationHeader(screen, actions)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            MessageList(messages, screen, state.senderNames, listState, actions)
            LatestIndicator(latest, Modifier.align(Alignment.BottomCenter)) {
                latest = LatestPolicy.reduce(latest, LatestEvent.ReturnToLatest)
            }
        }
        Composer(screen, state, actions, onSent = { latest = LatestPolicy.reduce(latest, LatestEvent.Sent) })
    }
}

/** Feeds loads, arrivals and scrolling into [LatestPolicy] and performs its scrolls. */
@Composable
private fun TrackLatest(
    threadId: Long,
    state: MessagesUiState,
    listState: LazyListState,
    latest: MutableState<LatestState>,
) {
    fun reduce(event: LatestEvent) {
        latest.value = LatestPolicy.reduce(latest.value, event)
    }
    val messages = state.conversation
    var previousKeys by remember(threadId) { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(threadId, messages, state.conversationLoaded) {
        if (!state.conversationLoaded) return@LaunchedEffect
        val previous = previousKeys
        reduce(previous?.let { LatestPolicy.arrivals(it, messages) } ?: LatestEvent.Opened)
        previousKeys = messages.map { it.key }.toSet()
    }
    val scrollToLatest = latest.value.scrollToLatest
    LaunchedEffect(scrollToLatest, messages.size) {
        if (scrollToLatest && messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
            reduce(LatestEvent.Scrolled)
        }
    }
    LaunchedEffect(listState, messages.size, scrollToLatest) {
        if (scrollToLatest) return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index ?: -1
        }.collect { last -> reduce(LatestEvent.Viewport(last, messages.size)) }
    }
}

private fun page(
    scope: CoroutineScope,
    listState: LazyListState,
    older: Boolean,
) {
    val pageSize =
        listState.layoutInfo.visibleItemsInfo.size
            .coerceAtLeast(1)
    val target =
        if (older) {
            listState.firstVisibleItemIndex - pageSize
        } else {
            listState.firstVisibleItemIndex + pageSize
        }
    scope.launch {
        listState.animateScrollToItem(target.coerceIn(0, (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
    }
}

@Composable
private fun ConversationHeader(
    screen: Screen.Conversation,
    actions: MessagesActions,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = screen.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = (screen.addresses.joinToString(", ").ifEmpty { "Unknown" }) + " · SMS",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SableActionButton(text = "Back", primary = false, onClick = actions.back)
    }
}

@Composable
private fun MessageList(
    messages: List<ConversationMessage>,
    screen: Screen.Conversation,
    senderNames: Map<String, String>,
    listState: LazyListState,
    actions: MessagesActions,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
    ) {
        items(messages, key = { it.key }) { message ->
            val sender =
                if (screen.addresses.size > 1 &&
                    message.direction == Direction.Incoming
                ) {
                    senderNames[message.address] ?: message.address
                } else {
                    null
                }
            Bubble(message, sender, actions)
        }
    }
}

@Composable
private fun LatestIndicator(
    latest: LatestState,
    modifier: Modifier,
    onReturn: () -> Unit,
) {
    if (latest.showNewMessageIndicator) {
        Surface(
            onClick = onReturn,
            modifier = modifier.padding(SableSpacing.Sm).sableFocusRing(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Text(
                text = "New message — End to latest",
                modifier = Modifier.padding(horizontal = SableSpacing.Lg, vertical = SableSpacing.Sm),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    } else {
        SableReturnToCurrent(
            visible = latest.showReturnToLatest,
            onClick = onReturn,
            modifier = modifier.padding(SableSpacing.Sm),
        )
    }
}

@Composable
private fun Bubble(
    message: ConversationMessage,
    sender: String?,
    actions: MessagesActions,
) {
    val outgoing = message.direction == Direction.Outgoing
    val failed = message.state == DeliveryState.Failed || message.state == DeliveryState.DownloadFailed
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier =
                Modifier
                    .widthIn(max = BubbleMaxWidth)
                    .then(if (failed) Modifier.sableFocusRing().clickable { actions.retry(message) } else Modifier),
            shape = MaterialTheme.shapes.medium,
            color = if (outgoing) colors.primaryContainer else colors.surfaceVariant,
            contentColor = if (outgoing) colors.onPrimaryContainer else colors.onSurface,
            border =
                when {
                    failed -> BorderStroke(1.dp, colors.error)
                    outgoing -> BorderStroke(1.dp, colors.primary)
                    else -> null
                },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = SableSpacing.Md, vertical = SableSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(SableSpacing.Xs),
            ) {
                BubbleContent(message, sender, actions)
                Text(
                    text = MessageStatus.label(message.state, message.errorCode, shortTime(message.dateMillis)),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (failed) colors.error else colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BubbleContent(
    message: ConversationMessage,
    sender: String?,
    actions: MessagesActions,
) {
    sender?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    message.subject?.let { Text(text = it, style = MaterialTheme.typography.titleSmall) }
    if (message.body.isNotBlank()) Text(text = message.body, style = MaterialTheme.typography.bodyLarge)
    message.attachments.forEach { attachment ->
        if (attachment.isImage) {
            AttachmentImage(attachment, actions.loadImage)
        } else {
            Text(
                text = "Attachment · ${attachment.name ?: attachment.contentType}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AttachmentImage(
    attachment: Attachment,
    load: (Long, Int) -> Bitmap?,
) {
    val bitmap by produceState<Bitmap?>(null, attachment.partId) {
        value = withContext(Dispatchers.IO) { load(attachment.partId, IMAGE_MAX_PIXELS) }
    }
    val image = bitmap
    if (image == null) {
        Text(
            text = "Picture · ${attachment.name ?: attachment.contentType}",
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = attachment.name ?: "Picture",
            modifier = Modifier.fillMaxWidth().heightIn(max = ImageMaxHeight).clip(MaterialTheme.shapes.small),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun Composer(
    screen: Screen.Conversation,
    state: MessagesUiState,
    actions: MessagesActions,
    onSent: () -> Unit,
) {
    if (!screen.canReply) {
        Text(
            text = "Group conversation: replies need MMS, which Sable Messages doesn't send yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(screen.threadId) { runCatching { focus.requestFocus() } }
    val send = {
        if (state.draft.isNotBlank()) {
            actions.send()
            onSent()
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.draft,
            onValueChange = actions.setDraft,
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focus)
                    .onPreviewKeyEvent { event ->
                        val native = event.nativeKeyEvent
                        val enter =
                            native.keyCode == KeyEvent.KEYCODE_ENTER || native.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                        // Enter sends (nothing on an empty draft); Shift+Enter is a newline.
                        if (!enter || native.isShiftPressed) return@onPreviewKeyEvent false
                        if (event.type == KeyEventType.KeyDown) send()
                        true
                    },
            placeholder = { Text("Text message · Enter sends") },
            maxLines = COMPOSER_MAX_LINES,
        )
        Spacer(Modifier.width(SableSpacing.Sm))
        Button(onClick = send, enabled = state.draft.isNotBlank(), shape = MaterialTheme.shapes.medium) { Text("Send") }
    }
}

private const val COMPOSER_MAX_LINES = 4
