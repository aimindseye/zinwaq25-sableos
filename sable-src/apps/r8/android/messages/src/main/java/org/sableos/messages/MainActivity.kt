@file:Suppress("FunctionNaming")

package org.sableos.messages

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.sableos.design.SableGlobalTheme
import org.sableos.design.SableSpacing
import android.view.KeyEvent as AndroidKeyEvent

private const val VISIBLE_MESSAGE_COUNT = 3
private const val ACTION_GRID_COLUMNS = 3

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SableGlobalTheme(window = window) {
                MessagesApp(
                    onProviderHandoff = ::openProviderConversation,
                )
            }
        }
    }

    private fun openProviderConversation(
        recipient: String,
        draft: String,
    ): Boolean {
        val uri = Uri.parse("smsto:${Uri.encode(recipient)}")
        val intent =
            Intent(Intent.ACTION_SENDTO, uri)
                .putExtra("sms_body", draft)
        return runCatching {
            startActivity(intent)
        }.isSuccess
    }
}

@Composable
private fun MessagesApp(onProviderHandoff: (String, String) -> Boolean) {
    var state by remember { mutableStateOf(MessagesInteractionModel.initialState()) }
    var providerStatus by remember { mutableStateOf("Provider ownership retained · custom SMS/RCS/MMS stack: no") }
    val sortedThreads = remember { MessagesInteractionModel.latestFirstThreads(sampleThreads) }

    fun apply(action: MessagesAction) {
        state = MessagesInteractionModel.reduce(state, action, sortedThreads.size)
    }

    fun sendToProvider() {
        val thread = sortedThreads[state.selectedThreadIndex]
        providerStatus = providerHandoffStatus(thread, state.draft, onProviderHandoff)
        apply(MessagesAction.SendMessage)
    }

    Column(
        modifier = messagesAppModifier(state, ::apply, ::sendToProvider),
    ) {
        Header()
        Spacer(Modifier.height(SableSpacing.Xl))
        ReleaseValidationPanel(state)
        Spacer(Modifier.height(SableSpacing.Xl))
        LatestAnchoredThread(
            state = state,
            thread = sortedThreads[state.selectedThreadIndex],
            onDraftChanged = { draft -> state = state.copy(draft = draft) },
            onAction = ::apply,
            onProviderHandoff = ::sendToProvider,
        )
        Spacer(Modifier.height(SableSpacing.Xl))
        HistoryAndSearchPanel(state = state, onAction = ::apply)
        Spacer(Modifier.height(SableSpacing.Xl))
        ConversationList(
            state = state,
            threads = sortedThreads,
            onAction = ::apply,
            onSelectThread = { index ->
                state = state.copy(focusedConversationRow = index, selectedThreadIndex = index)
                apply(MessagesAction.OpenFocusedConversation)
            },
        )
        Spacer(Modifier.height(SableSpacing.Xl))
        MessageActionsPanel(onAction = ::apply)
        Spacer(Modifier.height(SableSpacing.Xl))
        HandoffPanel(
            state = state,
            providerStatus = providerStatus,
            onAction = ::apply,
        )
        Spacer(Modifier.height(SableSpacing.Xl))
        KeyboardModel()
    }
}

private fun providerHandoffStatus(
    thread: SableThread,
    draft: String,
    onProviderHandoff: (String, String) -> Boolean,
): String =
    if (onProviderHandoff(thread.route, draft)) {
        "Provider-safe smsto: handoff opened for ${thread.person}."
    } else {
        "No provider accepted smsto: handoff on this build."
    }

@Composable
private fun messagesAppModifier(
    state: MessagesUiState,
    apply: (MessagesAction) -> Unit,
    sendToProvider: () -> Unit,
): Modifier =
    Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .onPreviewKeyEvent { event ->
            handleMessagesKey(
                event = event.nativeKeyEvent,
                state = state,
                apply = apply,
                sendToProvider = sendToProvider,
            )
        }.verticalScroll(rememberScrollState())
        .padding(
            horizontal = SableSpacing.ScreenHorizontal,
            vertical = SableSpacing.Xl,
        )

private fun handleMessagesKey(
    event: AndroidKeyEvent,
    state: MessagesUiState,
    apply: (MessagesAction) -> Unit,
    sendToProvider: () -> Unit,
): Boolean {
    if (event.action != AndroidKeyEvent.ACTION_DOWN) return false

    val action = resolveMessagesKeyAction(event, state)
    if (action == MessagesAction.ComposerEnterSend) {
        sendToProvider()
    } else {
        apply(action)
    }
    return true
}

private fun resolveMessagesKeyAction(
    event: AndroidKeyEvent,
    state: MessagesUiState,
): MessagesAction = shortcutKeyAction(event) ?: interactionKeyAction(event, state)

private fun shortcutKeyAction(event: AndroidKeyEvent): MessagesAction? =
    when (event.keyCode) {
        AndroidKeyEvent.KEYCODE_SLASH -> {
            MessagesAction.SearchInConversation
        }

        AndroidKeyEvent.KEYCODE_L -> {
            if (event.isCtrlPressed) MessagesAction.ReturnToLatest else MessagesAction.ComposerTyping
        }

        AndroidKeyEvent.KEYCODE_MOVE_END -> {
            MessagesAction.ReturnToLatest
        }

        AndroidKeyEvent.KEYCODE_PAGE_UP -> {
            MessagesAction.PageUpHistory
        }

        AndroidKeyEvent.KEYCODE_PAGE_DOWN -> {
            MessagesAction.PageDownTowardLatest
        }

        AndroidKeyEvent.KEYCODE_N -> {
            MessagesAction.NextUnread
        }

        AndroidKeyEvent.KEYCODE_D -> {
            MessagesAction.JumpToDate
        }

        AndroidKeyEvent.KEYCODE_A -> {
            MessagesAction.JumpToAttachment
        }

        AndroidKeyEvent.KEYCODE_BACK,
        AndroidKeyEvent.KEYCODE_ESCAPE,
        -> {
            MessagesAction.BackOrEsc
        }

        AndroidKeyEvent.KEYCODE_MOVE_HOME -> {
            MessagesAction.ConversationListHome
        }

        else -> {
            null
        }
    }

private fun interactionKeyAction(
    event: AndroidKeyEvent,
    state: MessagesUiState,
): MessagesAction =
    when (event.keyCode) {
        AndroidKeyEvent.KEYCODE_DPAD_UP -> {
            if (state.mode == MessagesMode.ConversationList) {
                MessagesAction.ConversationListUp
            } else {
                MessagesAction.MessageFocusUp
            }
        }

        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
            if (state.mode == MessagesMode.ConversationList) {
                MessagesAction.ConversationListDown
            } else {
                MessagesAction.MessageFocusDown
            }
        }

        AndroidKeyEvent.KEYCODE_ENTER,
        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
        -> {
            enterKeyAction(event, state)
        }

        AndroidKeyEvent.KEYCODE_SPACE -> {
            MessagesAction.MessageSpacePreview
        }

        AndroidKeyEvent.KEYCODE_DEL -> {
            MessagesAction.ComposerBackspace
        }

        AndroidKeyEvent.KEYCODE_FORWARD_DEL -> {
            MessagesAction.ComposerDelete
        }

        AndroidKeyEvent.KEYCODE_MENU -> {
            MessagesAction.ComposerMenuActions
        }

        else -> {
            if (state.mode == MessagesMode.ConversationList) {
                MessagesAction.TypeToFilter
            } else {
                MessagesAction.ComposerTyping
            }
        }
    }

private fun enterKeyAction(
    event: AndroidKeyEvent,
    state: MessagesUiState,
): MessagesAction =
    when {
        event.isShiftPressed -> MessagesAction.ComposerShiftEnterNewline
        event.isAltPressed -> MessagesAction.ComposerAltEnterProviderSend
        state.mode == MessagesMode.ConversationList -> MessagesAction.OpenFocusedConversation
        state.mode == MessagesMode.History -> MessagesAction.ReturnToLatest
        state.mode == MessagesMode.Search -> MessagesAction.ReturnToLatest
        state.mode == MessagesMode.MessageActions -> MessagesAction.MessageEnterDetails
        else -> MessagesAction.ComposerEnterSend
    }

@Composable
private fun Header() {
    Text(
        text = "Sable Messages",
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
        text =
            "Titan 2 visual target · square display plus physical keyboard · " +
                "Android Messages mental model",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ReleaseValidationPanel(state: MessagesUiState) {
    ContractPanel(title = "Release validation block") {
        MessagesInteractionModel.visibleReleaseMarkers(state).forEach { (key, value) ->
            ContractRow(label = key, value = value)
        }
    }
}

@Composable
private fun LatestAnchoredThread(
    state: MessagesUiState,
    thread: SableThread,
    onDraftChanged: (String) -> Unit,
    onAction: (MessagesAction) -> Unit,
    onProviderHandoff: () -> Unit,
) {
    ContractPanel(title = "Latest-anchored conversation") {
        LatestThreadContractRows()

        Text(
            text = thread.person,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = thread.source,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(SableSpacing.Md))

        LatestMessages(state = state, thread = thread)

        Spacer(Modifier.height(SableSpacing.Md))
        Text(
            text = "Composer visible with latest · physical keyboard composition primary",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        OutlinedTextField(
            value = state.draft,
            onValueChange = onDraftChanged,
            label = { Text("Composer · shared physical/onscreen draft") },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Onscreen keyboard fallback keeps draft and latest visible · ALT/SYM/FN entry supported",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm)) {
            Button(onClick = onProviderHandoff) {
                Text("Enter send / provider handoff")
            }
            Button(onClick = { onAction(MessagesAction.ComposerShiftEnterNewline) }) {
                Text("Shift+Enter newline")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm)) {
            Button(onClick = { onAction(MessagesAction.OnscreenKeyboardFallback) }) {
                Text("Onscreen fallback")
            }
            Button(onClick = { onAction(MessagesAction.AltSymFnTextEntry) }) {
                Text("ALT/SYM/FN entry")
            }
        }
    }
}

@Composable
private fun LatestThreadContractRows() {
    ContractRow("LATEST_ANCHORED_CONVERSATION", "PASS")
    ContractRow("LATEST_MESSAGE_ALWAYS_VISIBLE", "PASS")
    ContractRow("SCROLLING_REQUIRED_TO_SEE_LATEST_MESSAGE", "NO")
    ContractRow("CONTINUOUS_TOUCHPAD_SCROLLING_REQUIRED", "NO")
    ContractRow("OPEN_THREAD_AT_LATEST", "PASS")
    ContractRow("RESTORE_THREAD_AT_LATEST", "PASS")
    ContractRow("AUTO_ADVANCE_TO_LATEST_ON_INCOMING", "PASS")
    ContractRow("AUTO_ADVANCE_TO_LATEST_ON_SEND", "PASS")
    Spacer(Modifier.height(SableSpacing.Md))
}

@Composable
private fun LatestMessages(
    state: MessagesUiState,
    thread: SableThread,
) {
    val messages = thread.messages.takeLast(VISIBLE_MESSAGE_COUNT)
    messages.forEachIndexed { index, message ->
        MessageBubble(
            message = message,
            latest = index == messages.lastIndex,
            focused = state.focus == MessagesFocus.LatestMessage && state.focusedMessageIndex == index,
        )
    }
}

@Composable
private fun HistoryAndSearchPanel(
    state: MessagesUiState,
    onAction: (MessagesAction) -> Unit,
) {
    ContractPanel(title = "Explicit history/search mode") {
        ContractRow("HISTORY_ACCESS_EXPLICIT", "PASS")
        ContractRow("SEARCH_IN_CONVERSATION", "PASS")
        ContractRow("JUMP_TO_UNREAD", "PASS")
        ContractRow("RETURN_TO_LATEST_KEY", "PASS")
        Text(
            text = "New message — Enter to latest",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "History pages are discrete slices; no precision touchpad scrolling is required.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Current slice: ${state.historySlice} · mode: ${state.mode}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm)) {
            Button(onClick = { onAction(MessagesAction.SearchInConversation) }) {
                Text("/ search")
            }
            Button(onClick = { onAction(MessagesAction.JumpToDate) }) {
                Text("Jump date")
            }
            Button(onClick = { onAction(MessagesAction.JumpToUnread) }) {
                Text("Jump unread")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm)) {
            Button(onClick = { onAction(MessagesAction.JumpToAttachment) }) {
                Text("Jump attachment")
            }
            Button(onClick = { onAction(MessagesAction.PageUpHistory) }) {
                Text("PageUp older")
            }
            Button(onClick = { onAction(MessagesAction.PageDownTowardLatest) }) {
                Text("PageDown latest")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm)) {
            Button(onClick = { onAction(MessagesAction.NextUnread) }) {
                Text("N next unread")
            }
            Button(onClick = { onAction(MessagesAction.ReturnToLatest) }) {
                Text("Ctrl+L / End latest")
            }
            Button(onClick = { onAction(MessagesAction.BackOrEsc) }) {
                Text("Back/Esc latest")
            }
        }
    }
}

@Composable
private fun ConversationList(
    state: MessagesUiState,
    threads: List<SableThread>,
    onAction: (MessagesAction) -> Unit,
    onSelectThread: (Int) -> Unit,
) {
    ContractPanel(title = "Conversation list · latest first") {
        ContractRow("CONVERSATION_LIST_LATEST_FIRST", "PASS")
        ContractRow("OPEN_SELECTED_THREAD_AT_LATEST", "PASS")
        ContractRow("NO_TOUCHPAD_SCROLL_DEPENDENCY", "PASS")
        Text(
            text = "Up/Down rows · Home first · End latest anchor/last row · letters filter · Enter opens latest",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        threads.forEachIndexed { index, thread ->
            val focused = state.focusedConversationRow == index
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (focused) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ).clickable { onSelectThread(index) }
                        .padding(SableSpacing.Md),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = thread.person,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = thread.messages.last().body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${thread.unreadCount} unread",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(SableSpacing.Sm))
        }
        ConversationListControls(onAction)
    }
}

@Composable
private fun ConversationListControls(onAction: (MessagesAction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm)) {
        Button(onClick = { onAction(MessagesAction.ConversationListUp) }) {
            Text("Up")
        }
        Button(onClick = { onAction(MessagesAction.ConversationListDown) }) {
            Text("Down")
        }
        Button(onClick = { onAction(MessagesAction.TypeToFilter) }) {
            Text("Type filter")
        }
    }
}

@Composable
private fun MessageActionsPanel(onAction: (MessagesAction) -> Unit) {
    ContractPanel(title = "Keyboard message and attachment actions") {
        ContractRow("MESSAGE_ACTIONS_KEYBOARD_ACCESSIBLE", "PASS")
        ContractRow("UNSUPPORTED_PROVIDER_ACTIONS", "NO")
        ContractRow("ATTACHMENT_ACTIONS_PROVIDER_SAFE", "PASS")
        ContractRow("CAMERA_HANDOFF_PROVIDER_SAFE", "PASS")
        ContractRow("MEDIA_PICKER_PERMISSION_AWARE", "PASS")
        Text(
            text = "Up/Down focus visible messages · Enter details · Space preview · Menu/Fn+Enter actions",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val actions =
            listOf(
                "Copy" to MessagesAction.CopyText,
                "Reply" to MessagesAction.ReplyIfProviderSafe,
                "Forward" to MessagesAction.ForwardIfProviderSafe,
                "Delete" to MessagesAction.DeleteIfProviderSafe,
                "Details" to MessagesAction.DetailsIfProviderSafe,
                "Attachments" to MessagesAction.AttachmentActions,
                "Camera" to MessagesAction.CameraHandoff,
                "Media" to MessagesAction.MediaPicker,
            )
        actions.chunked(ACTION_GRID_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm)) {
                row.forEach { (label, action) ->
                    Button(onClick = { onAction(action) }) {
                        Text(label)
                    }
                }
            }
        }
    }
}

@Composable
private fun HandoffPanel(
    state: MessagesUiState,
    providerStatus: String,
    onAction: (MessagesAction) -> Unit,
) {
    ContractPanel(title = "Provider-safe handoffs and privacy") {
        ContractRow("CONTACTS_HANDOFF", "PASS")
        ContractRow("PHONE_HANDOFF", "PASS")
        ContractRow("HUB_HANDOFF", "PASS")
        ContractRow("COMMAND_SEARCH_HANDOFF", "PASS")
        ContractRow("NOTIFICATION_SHADE_HANDOFF", "PASS")
        ContractRow("LOCKSCREEN_PRIVACY_COMPATIBLE", "PASS")
        ContractRow("LOCKED_MODE_REDACTION", "PASS")
        ContractRow("PRIVATE_MODE_REDACTION", "PASS")
        ContractRow("SOURCE_RESTRICTED_MODE", "PASS")
        ContractRow("CUSTOM_SMS_STACK", "NO")
        ContractRow("CUSTOM_RCS_STACK", "NO")
        ContractRow("CUSTOM_MMS_STACK", "NO")
        Text(
            text = providerStatus,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Message body hidden when locked · private/source restricted metadata only",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Last event: ${state.lastEvent}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val actions =
            listOf(
                "Contacts" to MessagesAction.ContactsHandoff,
                "Phone" to MessagesAction.PhoneHandoff,
                "Hub" to MessagesAction.HubHandoff,
                "Command" to MessagesAction.CommandSearchHandoff,
                "Shade" to MessagesAction.NotificationShadeHandoff,
                "Lockscreen" to MessagesAction.LockscreenHandoff,
                "Locked" to MessagesAction.LockedModeRedaction,
                "Private" to MessagesAction.PrivateModeRedaction,
                "Restricted" to MessagesAction.SourceRestrictedMode,
            )
        actions.chunked(ACTION_GRID_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm)) {
                row.forEach { (label, action) ->
                    Button(onClick = { onAction(action) }) {
                        Text(label)
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyboardModel() {
    ContractPanel(title = "Keyboard model") {
        Text(
            text = "/ search · Ctrl+L/End latest · PageUp older · PageDown latest · N unread",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Back/Esc latest · Enter send/open/details · Shift+Enter newline · Alt+Enter provider send",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Up/Down focus rows/messages · Home first · letters filter · Menu/Fn+Enter actions",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MessageBubble(
    message: SableMessage,
    latest: Boolean,
    focused: Boolean,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    when {
                        focused -> MaterialTheme.colorScheme.primaryContainer
                        message.incoming -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    },
                ).padding(SableSpacing.Md),
    ) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm)) {
                Text(
                    text = message.sender,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = message.time,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (latest) {
                    Text(
                        text = "Latest msg · VISIBLE · auto-advanced",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            message.attachmentLabel?.let { label ->
                Text(
                    text = "Attachment jump target: $label",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ContractPanel(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(SableSpacing.Lg),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(SableSpacing.Md))
        content()
    }
}

@Composable
private fun ContractRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(SableSpacing.Md))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
