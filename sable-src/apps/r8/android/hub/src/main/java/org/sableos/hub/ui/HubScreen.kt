package org.sableos.hub.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.sableos.design.SableActionButton
import org.sableos.design.SableAdaptiveTopNav
import org.sableos.design.SableAlphabetIndex
import org.sableos.design.SableAlphabetRail
import org.sableos.design.SableDestination
import org.sableos.design.SableFocusMemory
import org.sableos.design.SableLatestContentPolicy
import org.sableos.design.SableListOrder
import org.sableos.design.SableListRow
import org.sableos.design.SablePageHeader
import org.sableos.design.SablePanel
import org.sableos.design.SableRefreshableSurface
import org.sableos.design.SableReturnToCurrent
import org.sableos.design.SableScreen
import org.sableos.design.SableScrollableScreen
import org.sableos.design.SableSpacing
import org.sableos.design.sableFocusRing
import org.sableos.design.sableLetterJump
import org.sableos.hub.HubCall
import org.sableos.hub.HubCallKind
import org.sableos.hub.HubCapabilities
import org.sableos.hub.HubConversation
import org.sableos.hub.HubConversationSource
import org.sableos.hub.HubLaunchQuery
import org.sableos.hub.HubMessage
import org.sableos.hub.HubSendResult
import org.sableos.hub.HubSnapshot
import org.sableos.hub.policy.ComposerEnterAction
import org.sableos.hub.policy.HubAddress
import org.sableos.hub.policy.HubComposerKeys
import org.sableos.hub.policy.HubDrafts
import org.sableos.hub.policy.HubInbox
import org.sableos.hub.policy.HubInboxFilter
import org.sableos.hub.policy.HubInboxItem
import org.sableos.hub.policy.HubThreadResolver
import java.text.DateFormat
import java.util.Date

private enum class HubPivot {
    Priority,
    Inbox,
    Email,
    People,
}

private sealed interface HubRoute {
    data class Root(
        val pivot: HubPivot,
    ) : HubRoute

    /**
     * [origin]: the pivot Back returns to (BACK_RETURNS_ONE_LOGICAL_LAYER). [address] opens the SMS
     * thread for a number when there is no thread id yet ([HubThreadResolver.NEW_THREAD_ID]).
     */
    data class Conversation(
        val threadId: Long,
        val origin: HubPivot = HubPivot.Inbox,
        val address: String? = null,
    ) : HubRoute

    /** [back]: the route Back returns to (the pivot or conversation it was opened from). */
    data class Compose(
        val recipient: String = "",
        val back: HubRoute = Root(HubPivot.Inbox),
    ) : HubRoute
}

/** An unsent new message kept while the user backs out of compose, restored on re-entry this session. */
private data class HubSmsDraft(
    val recipient: String,
    val body: String,
)

@Composable
fun HubScreen(
    snapshot: HubSnapshot?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onRequestAccess: () -> Unit,
    onSendSms: (String, String) -> HubSendResult,
    onOpenAndroidMessaging: (String?, String?) -> Boolean,
    onOpenSableMail: () -> Boolean,
    onOpenConnectedApps: () -> Unit,
    onReplyConnected: (String, String) -> HubSendResult,
    onOpenConnectedApp: (String, Long) -> Boolean,
    onOpenSourceNotificationSettings: (String, Long) -> Boolean = { _, _ -> false },
    onDial: (String) -> Boolean = { false },
    drafts: Map<String, String> = emptyMap(),
    onDraftChange: (String, String) -> Unit = { _, _ -> },
    launchQuery: HubLaunchQuery? = null,
    onLaunchQueryConsumed: () -> Unit = {},
    pendingThreadId: Long? = null,
    onPendingThreadConsumed: () -> Unit = {},
) {
    var route by remember {
        mutableStateOf<HubRoute>(HubRoute.Root(HubPivot.Priority))
    }

    // Shade "H" / Settings handoff: open the conversation Hub resolved for the notification.
    LaunchedEffect(pendingThreadId, snapshot != null) {
        if (pendingThreadId != null && snapshot != null) {
            route = HubRoute.Conversation(threadId = pendingThreadId)
            onPendingThreadConsumed()
        }
    }
    var search by remember {
        mutableStateOf("")
    }
    var inboxFilter by remember {
        mutableStateOf(HubInboxFilter.All)
    }
    var statusMessage by remember {
        mutableStateOf<String?>(null)
    }

    // Start's Command "Search Hub for …": open the inbox, all sources, with the search filled in.
    LaunchedEffect(launchQuery) {
        val query = launchQuery ?: return@LaunchedEffect
        route = HubRoute.Root(HubPivot.Inbox)
        inboxFilter = HubInboxFilter.All
        search = query.query
        statusMessage = null
        onLaunchQueryConsumed()
    }
    // Back from a conversation lands on the row it was opened from (FOCUS_RESTORATION).
    val focusMemory = remember { SableFocusMemory() }

    // New-message compose keeps its recipient and text while the user backs out this session;
    // thread drafts are persisted by the activity ([drafts], keyed by HubDrafts).
    val composeDrafts = remember { mutableStateMapOf<String, HubSmsDraft>() }

    // Hardware Back/Esc goes up one level (BACK_RETURNS_ONE_LOGICAL_LAYER); only the root leaves Hub.
    BackHandler(enabled = route !is HubRoute.Root) {
        when (val current = route) {
            is HubRoute.Root -> {
                Unit
            }

            is HubRoute.Conversation -> {
                statusMessage = null
                route = HubRoute.Root(current.origin)
            }

            is HubRoute.Compose -> {
                route = current.back
            }
        }
    }

    if (snapshot == null) {
        SableScreen {
            SablePageHeader(
                title = "hub",
                subtitle = "Priority, messages, calls, email and people, kept local to this device.",
            )
            SablePanel {
                Text(
                    text = "Loading conversations…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val openThread: (HubRoute.Conversation) -> Unit = { target ->
        statusMessage = null
        route = target
    }
    val dial: (String) -> Unit = { number ->
        statusMessage =
            when {
                number.isBlank() -> "This caller's number is not available."
                onDial(number) -> null
                else -> "The Phone app is unavailable."
            }
    }

    SableRefreshableSurface(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
    ) {
        when (val current = route) {
            is HubRoute.Root -> {
                SableScreen {
                    SablePageHeader(
                        title = "hub",
                        subtitle = "Local communication across your enabled sources.",
                    )

                    HubPivotRow(
                        selected = current.pivot,
                        onSelect = { pivot ->
                            route = HubRoute.Root(pivot)
                            search = ""
                            statusMessage = null
                        },
                    )

                    statusMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    when (current.pivot) {
                        HubPivot.Priority -> {
                            PriorityPivot(
                                modifier = Modifier.weight(1f),
                                snapshot = snapshot,
                                onConversation = { conversation ->
                                    openThread(
                                        HubRoute.Conversation(
                                            threadId = conversation.threadId,
                                            origin = HubPivot.Priority,
                                        ),
                                    )
                                },
                                onOpenSableMail = onOpenSableMail,
                            )
                        }

                        HubPivot.Inbox -> {
                            InboxPivot(
                                modifier = Modifier.weight(1f),
                                snapshot = snapshot,
                                focusMemory = focusMemory,
                                filter = inboxFilter,
                                onFilterChange = { filter ->
                                    inboxFilter = filter
                                    statusMessage = null
                                },
                                search = search,
                                onSearchChange = { search = it },
                                onConversation = { conversation ->
                                    openThread(HubRoute.Conversation(threadId = conversation.threadId))
                                },
                                onCall = { call -> dial(call.number) },
                                onTextCaller = { call ->
                                    openThread(
                                        HubRoute.Conversation(
                                            threadId = HubThreadResolver.NEW_THREAD_ID,
                                            address = call.number,
                                        ),
                                    )
                                },
                                onCompose = {
                                    route = HubRoute.Compose()
                                },
                                onOpenConnectedApps = onOpenConnectedApps,
                                onRequestAccess = onRequestAccess,
                            )
                        }

                        HubPivot.Email -> {
                            EmailPivot(
                                snapshot = snapshot,
                                onOpenSableMail = onOpenSableMail,
                            )
                        }

                        HubPivot.People -> {
                            PeoplePivot(
                                modifier = Modifier.weight(1f),
                                snapshot = snapshot,
                                search = search,
                                onSearchChange = { search = it },
                                onCompose = { number ->
                                    openThread(
                                        HubRoute.Conversation(
                                            threadId = HubThreadResolver.NEW_THREAD_ID,
                                            origin = HubPivot.People,
                                            address = number,
                                        ),
                                    )
                                },
                                onRequestAccess = onRequestAccess,
                            )
                        }
                    }
                }
            }

            is HubRoute.Conversation -> {
                val conversation =
                    HubThreadResolver.resolve(snapshot.conversations, current.threadId, current.address)
                        ?: current.address?.takeIf(String::isNotBlank)?.let { address ->
                            newSmsConversation(address, snapshot)
                        }

                if (conversation == null) {
                    MissingConversationView(
                        onBack = {
                            statusMessage = null
                            route = HubRoute.Root(current.origin)
                        },
                    )
                } else {
                    ConversationRoute(
                        snapshot = snapshot,
                        conversation = conversation,
                        statusMessage = statusMessage,
                        onStatus = { statusMessage = it },
                        drafts = drafts,
                        onDraftChange = onDraftChange,
                        onBack = {
                            statusMessage = null
                            route = HubRoute.Root(current.origin)
                        },
                        onSendSms = onSendSms,
                        onDial = dial,
                        onRequestAccess = onRequestAccess,
                        onOpenAndroidMessaging = onOpenAndroidMessaging,
                        onReplyConnected = onReplyConnected,
                        onOpenConnectedApp = onOpenConnectedApp,
                        onOpenSourceNotificationSettings = onOpenSourceNotificationSettings,
                    )
                }
            }

            is HubRoute.Compose -> {
                SableScrollableScreen {
                    ComposeView(
                        draft = composeDrafts[current.recipient] ?: HubSmsDraft(current.recipient, ""),
                        onDraftChange = { draft -> composeDrafts[current.recipient] = draft },
                        canSendSms = snapshot.capabilities.canSendSms,
                        onBack = {
                            route = current.back
                        },
                        onRequestAccess = onRequestAccess,
                        onSendSms = { recipient, body ->
                            val result = onSendSms(recipient, body)
                            statusMessage = result.message
                            if (result.success) {
                                composeDrafts.remove(current.recipient)
                                // Sent messages are shown in place: land in the thread, composer ready.
                                route =
                                    HubRoute.Conversation(
                                        threadId = HubThreadResolver.NEW_THREAD_ID,
                                        address = recipient,
                                    )
                            }
                            result
                        },
                        onOpenMmsComposer = { recipient, body ->
                            val opened =
                                onOpenAndroidMessaging(
                                    recipient.takeIf { it.isNotBlank() },
                                    body.takeIf { it.isNotBlank() },
                                )
                            statusMessage =
                                if (opened) {
                                    "Opened the system MMS composer."
                                } else {
                                    "MMS composer is unavailable."
                                }
                        },
                    )
                }
            }
        }
    }
}

/** An SMS thread that does not exist yet: the first message creates it in the provider. */
private fun newSmsConversation(
    address: String,
    snapshot: HubSnapshot,
): HubConversation =
    HubConversation(
        threadId = HubThreadResolver.NEW_THREAD_ID,
        address = address,
        displayName =
            snapshot.people
                .firstOrNull { person -> HubAddress.sameAddress(person.phoneNumber, address) }
                ?.displayName
                ?: address,
        lastBody = "",
        lastDateMillis = 0L,
        unreadCount = 0,
    )

@Composable
private fun ConversationRoute(
    snapshot: HubSnapshot,
    conversation: HubConversation,
    statusMessage: String?,
    onStatus: (String?) -> Unit,
    drafts: Map<String, String>,
    onDraftChange: (String, String) -> Unit,
    onBack: () -> Unit,
    onSendSms: (String, String) -> HubSendResult,
    onDial: (String) -> Unit,
    onRequestAccess: () -> Unit,
    onOpenAndroidMessaging: (String?, String?) -> Boolean,
    onReplyConnected: (String, String) -> HubSendResult,
    onOpenConnectedApp: (String, Long) -> Boolean,
    onOpenSourceNotificationSettings: (String, Long) -> Boolean,
) {
    val threadMessages =
        if (conversation.threadId == HubThreadResolver.NEW_THREAD_ID) {
            emptyList()
        } else {
            snapshot.messages
                .filter { message ->
                    message.threadId == conversation.threadId
                }.sortedBy { message ->
                    message.dateMillis
                }.takeLast(MAX_VISIBLE_THREAD_MESSAGES)
        }
    val sourcePackage = conversation.sourcePackage
    val sourceUserSerial = conversation.sourceUserSerial
    val isSms = conversation.source == HubConversationSource.Sms
    val connectedSource =
        if (!isSms && sourcePackage != null && sourceUserSerial != null) {
            sourcePackage to sourceUserSerial
        } else {
            null
        }
    val draftKey =
        if (isSms) {
            HubDrafts.smsKey(conversation.address)
        } else {
            HubDrafts.connectedKey(conversation.threadId)
        }
    val composer =
        when {
            isSms && conversation.address.isNotBlank() -> {
                HubComposerSpec(
                    label = "Text message",
                    canSend = snapshot.capabilities.canSendSms,
                    send = { body -> onSendSms(conversation.address, body) },
                )
            }

            !isSms && conversation.canQuickReply && conversation.sourceNotificationKey != null -> {
                HubComposerSpec(
                    label = "Reply",
                    canSend = true,
                    send = { body -> onReplyConnected(conversation.sourceNotificationKey, body) },
                )
            }

            else -> {
                null
            }
        }

    SableScreen {
        ConversationView(
            modifier = Modifier.weight(1f),
            conversation = conversation,
            messages = threadMessages,
            statusMessage = statusMessage,
            draft = drafts[draftKey].orEmpty(),
            onDraftChange = { body -> onDraftChange(draftKey, body) },
            composer = composer,
            onRequestAccess = onRequestAccess,
            onBack = onBack,
            onCall =
                if (isSms && conversation.address.isNotBlank()) {
                    { onDial(conversation.address) }
                } else {
                    null
                },
            onAttach =
                if (isSms && conversation.address.isNotBlank()) {
                    {
                        val opened =
                            onOpenAndroidMessaging(
                                conversation.address,
                                drafts[draftKey]?.takeIf(String::isNotBlank),
                            )
                        onStatus(if (opened) "Opened the system MMS composer." else "MMS composer is unavailable.")
                    }
                } else {
                    null
                },
            onOpenSource =
                connectedSource?.let { (packageName, serial) ->
                    {
                        val opened = onOpenConnectedApp(packageName, serial)
                        onStatus(
                            if (opened) {
                                "Opened ${conversation.sourceLabel ?: packageName}."
                            } else {
                                "The source app is unavailable."
                            },
                        )
                    }
                },
            onOpenNotificationSettings =
                connectedSource?.let { (packageName, serial) ->
                    {
                        // Delivery policy is Android's: hand off, never mirror it.
                        if (!onOpenSourceNotificationSettings(packageName, serial)) {
                            onStatus("Android notification settings are unavailable.")
                        }
                    }
                },
        )
    }
}

/** The inline composer of an open thread: SMS through Android telephony, or a source RemoteInput reply. */
private class HubComposerSpec(
    val label: String,
    val canSend: Boolean,
    val send: (String) -> HubSendResult,
)

@Composable
private fun MissingConversationView(onBack: () -> Unit) {
    SableScreen {
        SablePageHeader(
            title = "conversation unavailable",
            subtitle = "The source conversation is no longer available in local Sable history.",
        )
        SableActionButton(
            text = "Back",
            primary = false,
            onClick = onBack,
        )
    }
}

@Composable
private fun HubPivotRow(
    selected: HubPivot,
    onSelect: (HubPivot) -> Unit,
) {
    // Four equal-width labels clip mid-word on a square screen or at large font
    // scale; the adaptive nav keeps whole labels and puts the rest under "more".
    SableAdaptiveTopNav(
        destinations = HUB_DESTINATIONS,
        selectedId = selected.name,
        onSelect = { id -> onSelect(HubPivot.valueOf(id)) },
    )
}

private val HUB_DESTINATIONS =
    HubPivot.entries.map { pivot -> SableDestination(pivot.name, pivot.name.lowercase()) }

@Composable
private fun ColumnScope.PriorityPivot(
    modifier: Modifier,
    snapshot: HubSnapshot,
    onConversation: (HubConversation) -> Unit,
    onOpenSableMail: () -> Boolean,
) {
    // Hub priority is ordering/aggregation only; it is not a Do Not Disturb exception.
    val conversations =
        snapshot.conversations
            .filter { conversation ->
                conversation.unreadCount > 0 || conversation.canQuickReply || conversation.hubPriority
            }.sortedWith(
                compareByDescending<HubConversation> { it.hubPriority }
                    .thenByDescending { it.lastDateMillis },
            )
    val hasMailAlerts =
        snapshot.mail.available &&
            snapshot.mail.detail != "no mail alerts"

    if (conversations.isEmpty() && !hasMailAlerts) {
        Text(
            text = "Nothing needs your attention.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    if (hasMailAlerts) {
        SablePanel {
            Text(
                text = "Email",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = snapshot.mail.detail,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SableActionButton(
                text = "Open Sable Mail",
                primary = false,
                onClick = {
                    onOpenSableMail()
                },
            )
        }
    }

    if (conversations.isNotEmpty()) {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SableSpacing.Xs),
        ) {
            items(
                items = conversations.take(MAX_VISIBLE_CONVERSATIONS),
                key = { conversation -> conversationKey(conversation) },
            ) { conversation ->
                ConversationRow(
                    conversation = conversation,
                    onClick = { onConversation(conversation) },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.EmailPivot(
    snapshot: HubSnapshot,
    onOpenSableMail: () -> Boolean,
) {
    SablePanel {
        Text(
            text = "Sable Mail",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = snapshot.mail.detail,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text =
                "Hub reads only the bounded local mail snapshot. " +
                    "Accounts, sync and mailbox storage remain in Sable Mail.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SableActionButton(
            text = "Open Sable Mail",
            onClick = {
                onOpenSableMail()
            },
        )
    }
}

@Composable
private fun ColumnScope.InboxPivot(
    modifier: Modifier,
    snapshot: HubSnapshot,
    focusMemory: SableFocusMemory,
    filter: HubInboxFilter,
    onFilterChange: (HubInboxFilter) -> Unit,
    search: String,
    onSearchChange: (String) -> Unit,
    onConversation: (HubConversation) -> Unit,
    onCall: (HubCall) -> Unit,
    onTextCaller: (HubCall) -> Unit,
    onCompose: () -> Unit,
    onOpenConnectedApps: () -> Unit,
    onRequestAccess: () -> Unit,
) {
    val capabilities = snapshot.capabilities
    val needsAccess =
        when (filter) {
            HubInboxFilter.All -> !capabilities.canReadSms || !capabilities.canSendSms || !capabilities.canReadCallLog
            HubInboxFilter.Messages -> !capabilities.canReadSms || !capabilities.canSendSms
            HubInboxFilter.Calls -> !capabilities.canReadCallLog
            HubInboxFilter.Notifications -> false
        }
    if (needsAccess) {
        InboxAccessPrompt(
            snapshot = snapshot,
            onRequestAccess = onRequestAccess,
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
    ) {
        SableActionButton(
            text = "new message",
            modifier = Modifier.weight(1f),
            onClick = onCompose,
        )
        SableActionButton(
            text = "connected apps",
            modifier = Modifier.weight(1f),
            primary = false,
            onClick = onOpenConnectedApps,
        )
    }

    OutlinedTextField(
        value = search,
        onValueChange = onSearchChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Search messages, calls, notifications") },
    )

    val query = search.trim()
    val items =
        remember(snapshot, filter, query) {
            HubInbox.items(snapshot.conversations, snapshot.calls, filter, query)
        }
    val listState = rememberLazyListState()
    val newestItem = items.firstOrNull()
    val visibleItems = items.take(MAX_VISIBLE_CONVERSATIONS)
    val rowKeys = visibleItems.map(HubInboxItem::key)
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val chipRequesters = remember { HubInboxFilter.entries.associateWith { FocusRequester() } }
    var restoreDone by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // A/M/C/N pick a filter while focus is on a chip or a row; in the search field letters stay text.
    val selectFilter: (HubInboxFilter) -> Unit = { selected ->
        onFilterChange(selected)
        scope.launch {
            withFrameNanos { }
            runCatching { chipRequesters.getValue(selected).requestFocus() }
        }
    }

    LaunchedEffect(
        query,
        filter,
        newestItem?.key,
        newestItem?.dateMillis,
    ) {
        // First visit after Back: restore the row the user opened.
        if (!restoreDone) {
            restoreDone = true
            val target = if (query.isBlank()) focusMemory.restore(FOCUS_SURFACE_INBOX, rowKeys) else null
            // One-shot: a later visit (pivot switch, handoff Back) opens on the newest again.
            focusMemory.forget(FOCUS_SURFACE_INBOX)
            if (target != null) {
                listState.scrollToItem(target)
                withFrameNanos { }
                runCatching { requesters[rowKeys[target]]?.requestFocus() }
                return@LaunchedEffect
            }
        }
        // Otherwise the newest row is what is visible on open, and after a new arrival.
        if (newestItem != null && listState.firstVisibleItemIndex <= 1) {
            listState.scrollToItem(0)
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .onKeyEvent { event ->
                    val plain = !event.isAltPressed && !event.isCtrlPressed && !event.isMetaPressed
                    val shortcut =
                        if (event.type == KeyEventType.KeyDown && plain && event.utf16CodePoint > 0) {
                            HubInboxFilter.forShortcut(event.utf16CodePoint.toChar())
                        } else {
                            null
                        }
                    shortcut?.let(selectFilter) != null
                },
        verticalArrangement = Arrangement.spacedBy(SableSpacing.Xs),
    ) {
        InboxFilterChips(
            snapshot = snapshot,
            selected = filter,
            requesters = chipRequesters,
            onSelect = selectFilter,
        )

        if (items.isEmpty()) {
            Text(
                text = inboxEmptyText(capabilities, filter, query),
                modifier = Modifier.padding(vertical = SableSpacing.Lg),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(SableSpacing.Xs),
            ) {
                itemsIndexed(
                    items = visibleItems,
                    key = { _, item -> item.key },
                ) { index, item ->
                    val rowModifier = Modifier.focusRequester(requesters.getOrPut(item.key) { FocusRequester() })
                    when (item) {
                        is HubInboxItem.Conversation -> {
                            ConversationRow(
                                conversation = item.conversation,
                                onClick = {
                                    focusMemory.remember(FOCUS_SURFACE_INBOX, item.key, index)
                                    onConversation(item.conversation)
                                },
                                modifier = rowModifier,
                            )
                        }

                        is HubInboxItem.Call -> {
                            CallRow(
                                call = item.call,
                                onCall = {
                                    focusMemory.remember(FOCUS_SURFACE_INBOX, item.key, index)
                                    onCall(item.call)
                                },
                                onText = {
                                    focusMemory.remember(FOCUS_SURFACE_INBOX, item.key, index)
                                    onTextCaller(item.call)
                                },
                                modifier = rowModifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun inboxEmptyText(
    capabilities: HubCapabilities,
    filter: HubInboxFilter,
    query: String,
): String =
    when {
        query.isNotBlank() -> "Nothing matches “$query”."
        filter == HubInboxFilter.Messages && !capabilities.canReadSms -> "Grant SMS access to show conversations."
        filter == HubInboxFilter.Calls && !capabilities.canReadCallLog -> "Grant call log access to show recent calls."
        filter == HubInboxFilter.Calls -> "No recent calls."
        filter == HubInboxFilter.Notifications -> "No connected-app conversations. Choose apps under connected apps."
        else -> "No conversations yet."
    }

@Composable
private fun InboxFilterChips(
    snapshot: HubSnapshot,
    selected: HubInboxFilter,
    requesters: Map<HubInboxFilter, FocusRequester>,
    onSelect: (HubInboxFilter) -> Unit,
) {
    // Scrolls sideways instead of clipping "Notifications" on the square screen or at large font.
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
    ) {
        HubInboxFilter.entries.forEach { filter ->
            val count = HubInbox.attentionCount(snapshot.conversations, snapshot.calls, filter)
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = {
                    Text(
                        text = if (count > 0) "${filter.label} · $count" else filter.label,
                        maxLines = 1,
                    )
                },
                modifier = Modifier.focusRequester(requesters.getValue(filter)),
            )
        }
    }
}

@Composable
private fun InboxAccessPrompt(
    snapshot: HubSnapshot,
    onRequestAccess: () -> Unit,
) {
    val capabilities = snapshot.capabilities
    val detail =
        when {
            !capabilities.canReadSms -> "Allow SMS access to read conversations and send messages."
            !capabilities.canSendSms -> "Allow sending access to send SMS from Hub."
            else -> "Allow call log access to show recent and missed calls. Calls stay in the Phone app."
        }

    SablePanel {
        Text(
            text = "Access needed",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SableActionButton(
            text = "Review access",
            primary = false,
            onClick = onRequestAccess,
        )
    }
}

@Composable
private fun CallRow(
    call: HubCall,
    onCall: () -> Unit,
    onText: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val attention = call.kind.needsAttention
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Enter on the row calls back through the Phone app's dialer (never a direct call).
        Row(
            modifier =
                modifier
                    .weight(1f)
                    .sableFocusRing()
                    .clickable(onClick = onCall)
                    .padding(vertical = HubRowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(HubAvatarSize)
                        .background(
                            if (attention) HubUnreadBackground else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = callGlyph(call.kind),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (attention) HubUnreadAccent else MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(HubAvatarTextGap))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = call.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Call · " + callKindLabel(call.kind) + " · " + clockTime(call.dateMillis),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (attention) HubUnreadAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (call.number.isNotBlank()) {
            Spacer(Modifier.width(HubTrailingGap))
            SableActionButton(
                text = "Text",
                primary = false,
                onClick = onText,
            )
        }
    }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

private fun callGlyph(kind: HubCallKind): String =
    when (kind) {
        HubCallKind.Incoming -> "↙"
        HubCallKind.Outgoing -> "↗"
        HubCallKind.Missed, HubCallKind.Rejected -> "✕"
        HubCallKind.Voicemail -> "◎"
        HubCallKind.Blocked -> "⊘"
        HubCallKind.Other -> "•"
    }

private fun callKindLabel(kind: HubCallKind): String =
    when (kind) {
        HubCallKind.Incoming -> "incoming"
        HubCallKind.Outgoing -> "outgoing"
        HubCallKind.Missed -> "missed"
        HubCallKind.Voicemail -> "voicemail"
        HubCallKind.Rejected -> "declined"
        HubCallKind.Blocked -> "blocked"
        HubCallKind.Other -> "call"
    }

@Composable
private fun ConversationRow(
    conversation: HubConversation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unread = conversation.unreadCount > 0
    val accent =
        if (unread) {
            HubUnreadAccent
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    val sourceLine =
        listOfNotNull(
            conversation.sourceLabel ?: "connected app",
            conversation.profileBadge,
        ).joinToString(" · ")
    val initial =
        conversation.displayName
            .trim()
            .firstOrNull()
            ?.uppercaseChar()
            ?.toString()
            ?: "?"

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .sableFocusRing()
                .clickable(onClick = onClick)
                .padding(vertical = HubRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(HubAvatarSize)
                    .background(
                        if (unread) {
                            HubUnreadBackground
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.width(HubAvatarTextGap))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    when {
                        // Source label on every row: the inbox mixes SMS, calls and connected apps.
                        conversation.source != HubConversationSource.ConnectedApp &&
                            conversation.lastBody.isBlank() -> {
                            "SMS"
                        }

                        conversation.source != HubConversationSource.ConnectedApp -> {
                            "SMS · " + conversation.lastBody.take(SMS_PREVIEW_LENGTH)
                        }

                        conversation.lastBody.isBlank() -> {
                            "$sourceLine · content hidden"
                        }

                        else -> {
                            "$sourceLine · " +
                                conversation.lastBody.take(CONNECTED_PREVIEW_LENGTH)
                        }
                    },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(HubTrailingGap))
        Column(
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = clockTime(conversation.lastDateMillis),
                style = MaterialTheme.typography.bodyMedium,
                color = accent,
            )
            if (unread) {
                Text(
                    text = conversation.unreadCount.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = HubUnreadAccent,
                )
            }
        }
    }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

private fun conversationKey(conversation: HubConversation): String =
    conversation.threadId.toString() + ":" + conversation.address

private const val FOCUS_SURFACE_INBOX = "hub-inbox"

private fun clockTime(epochMillis: Long): String =
    DateFormat
        .getTimeInstance(DateFormat.SHORT)
        .format(Date(epochMillis))

@Composable
private fun ColumnScope.PeoplePivot(
    modifier: Modifier,
    snapshot: HubSnapshot,
    search: String,
    onSearchChange: (String) -> Unit,
    onCompose: (String) -> Unit,
    onRequestAccess: () -> Unit,
) {
    if (!snapshot.capabilities.canReadContacts) {
        SablePanel {
            Text(
                text = "Contacts access is optional and only improves people matching.",
                style = MaterialTheme.typography.bodyMedium,
            )
            SableActionButton(
                text = "Review access",
                primary = false,
                onClick = onRequestAccess,
            )
        }
    }

    OutlinedTextField(
        value = search,
        onValueChange = onSearchChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Search people") },
    )

    val query = search.trim()
    val people =
        if (query.isBlank()) {
            snapshot.people
        } else {
            snapshot.people.filter { person ->
                person.displayName.contains(query, ignoreCase = true) ||
                    person.phoneNumber.contains(query, ignoreCase = true)
            }
        }

    if (people.isEmpty()) {
        Text(
            text = "No matching people are available.",
            modifier = Modifier.padding(vertical = SableSpacing.Lg),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        val visiblePeople = people.take(MAX_VISIBLE_PEOPLE)
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        // One functional index: starred people form one section, then A-Z; the
        // rail shows only letters that exist and the keyboard jumps by letter.
        val alphabet =
            remember(visiblePeople) {
                SableAlphabetIndex.build(
                    labels = visiblePeople.map { it.displayName },
                    pinnedCount = visiblePeople.takeWhile { it.favorite }.size,
                )
            }
        val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }
        val jumpTo: (Int) -> Unit = { position ->
            scope.launch { listState.scrollToItem(position) }
        }

        Row(
            modifier = modifier.fillMaxWidth(),
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .weight(1f)
                        .sableLetterJump(alphabet, enabled = query.isBlank(), onJump = jumpTo),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(SableSpacing.Xs),
            ) {
                items(
                    items = visiblePeople,
                    key = { person ->
                        person.id.toString() + ":" + person.phoneNumber
                    },
                ) { person ->
                    SableListRow(
                        title = person.displayName,
                        subtitle = person.phoneNumber,
                        onClick = { onCompose(person.phoneNumber) },
                    )
                }
            }

            if (query.isBlank()) {
                SableAlphabetRail(
                    index = alphabet,
                    activeKey = alphabet.sectionKeyAt(firstVisible),
                    onJump = jumpTo,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.ConversationView(
    modifier: Modifier,
    conversation: HubConversation,
    messages: List<HubMessage>,
    statusMessage: String?,
    draft: String,
    onDraftChange: (String) -> Unit,
    composer: HubComposerSpec?,
    onRequestAccess: () -> Unit,
    onBack: () -> Unit,
    onCall: (() -> Unit)?,
    onAttach: (() -> Unit)?,
    onOpenSource: (() -> Unit)?,
    onOpenNotificationSettings: (() -> Unit)? = null,
) {
    var composerStatus by remember(conversation.threadId, conversation.address) {
        mutableStateOf<String?>(null)
    }

    SablePageHeader(
        title = conversation.displayName,
        subtitle =
            conversation.sourceLabel
                ?.takeIf { conversation.source == HubConversationSource.ConnectedApp }
                ?.let { label -> listOfNotNull(label, conversation.profileBadge).joinToString(" · ") }
                ?: conversation.address,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
    ) {
        SableActionButton(
            text = "Back",
            modifier = Modifier.weight(1f),
            primary = false,
            onClick = onBack,
        )
        onCall?.let { action ->
            SableActionButton(
                text = "Call",
                modifier = Modifier.weight(1f),
                primary = false,
                onClick = action,
            )
        }
        onAttach?.let { action ->
            SableActionButton(
                text = "Attach",
                modifier = Modifier.weight(1f),
                primary = false,
                onClick = action,
            )
        }
        onOpenSource?.let { action ->
            SableActionButton(
                text = "Open app",
                modifier = Modifier.weight(1f),
                primary = false,
                onClick = action,
            )
        }
    }
    // Its own full-width row: a third equal-width "Notifications" button wraps mid-word
    // on the square screen (KF-D: labels never clip).
    onOpenNotificationSettings?.let { action ->
        SableActionButton(
            text = "Notifications",
            modifier = Modifier.fillMaxWidth(),
            primary = false,
            onClick = action,
        )
    }

    statusMessage?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // Newest message at the bottom and visible on open (reverse layout, index 0 =
    // newest); a "latest" action returns there whenever history is scrolled into.
    val conversationState = rememberLazyListState()
    val conversationScope = rememberCoroutineScope()
    val showLatest by remember(messages.size) {
        derivedStateOf {
            val visible = conversationState.layoutInfo.visibleItemsInfo
            SableLatestContentPolicy.showReturnToCurrent(
                firstVisibleIndex = visible.firstOrNull()?.index ?: 0,
                lastVisibleIndex = visible.lastOrNull()?.index ?: 0,
                count = messages.size,
                order = SableListOrder.NewestFirst,
            )
        }
    }
    // AUTO_ADVANCE: a message that arrives (or is sent) while the latest is in view is shown;
    // reading history is never yanked away except by the user's own sent message.
    val newest = messages.lastOrNull()
    LaunchedEffect(newest?.id) {
        if (newest != null && (conversationState.firstVisibleItemIndex <= 1 || !newest.incoming)) {
            conversationState.animateScrollToItem(0)
        }
    }
    Box(modifier = modifier.fillMaxWidth()) {
        if (messages.isEmpty()) {
            Text(
                text = if (composer != null) "No messages yet. Write the first one below." else "No messages yet.",
                modifier = Modifier.align(Alignment.BottomStart).padding(vertical = SableSpacing.Md),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = conversationState,
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
        ) {
            items(
                items = messages.asReversed(),
                key = HubMessage::id,
            ) { message ->
                MessageBubble(message)
            }
        }
        SableReturnToCurrent(
            visible = showLatest,
            onClick = { conversationScope.launch { conversationState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(SableSpacing.Sm),
        )
    }

    when {
        composer == null -> {
            Unit
        }

        !composer.canSend -> {
            SablePanel {
                Text(
                    text = "SMS sending needs permission.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                SableActionButton(
                    text = "Review access",
                    primary = false,
                    onClick = onRequestAccess,
                )
            }
        }

        else -> {
            HubInlineComposer(
                label = composer.label,
                draft = draft,
                onDraftChange = onDraftChange,
                status = composerStatus,
                onSend = { body ->
                    val result = composer.send(body)
                    composerStatus = result.message.takeUnless { result.success }
                    if (result.success) {
                        onDraftChange("")
                        conversationScope.launch { conversationState.animateScrollToItem(0) }
                    }
                },
            )
        }
    }
}

/** Hardware Enter sends and Shift+Enter inserts a line break ([HubComposerKeys]). */
private fun Modifier.hubEnterToSend(
    body: String,
    canSend: Boolean,
    onSend: () -> Unit,
): Modifier =
    onPreviewKeyEvent { event ->
        if (event.key != Key.Enter && event.key != Key.NumPadEnter) {
            return@onPreviewKeyEvent false
        }
        when (HubComposerKeys.enterAction(event.isShiftPressed, event.isAltPressed, body, canSend)) {
            ComposerEnterAction.Send -> {
                if (event.type == KeyEventType.KeyDown) onSend()
                true
            }

            ComposerEnterAction.Swallow -> {
                true
            }

            ComposerEnterAction.Newline,
            ComposerEnterAction.PassThrough,
            -> {
                false
            }
        }
    }

/**
 * The composer stays under the latest message (COMPOSER_VISIBLE_WITH_LATEST). Enter sends,
 * Shift+Enter starts a new line; the on-screen keyboard's action key sends too.
 */
@Composable
private fun HubInlineComposer(
    label: String,
    draft: String,
    onDraftChange: (String) -> Unit,
    status: String?,
    onSend: (String) -> Unit,
) {
    val send = {
        if (draft.isNotBlank()) {
            onSend(draft)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier =
                Modifier
                    .weight(1f)
                    .hubEnterToSend(body = draft, canSend = true, onSend = send),
            maxLines = HUB_INLINE_COMPOSER_MAX_LINES,
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
        )
        SableActionButton(
            text = "Send",
            onClick = send,
        )
    }
    status?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun MessageBubble(message: HubMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            if (message.incoming) {
                Arrangement.Start
            } else {
                Arrangement.End
            },
    ) {
        Surface(
            modifier = Modifier.widthIn(max = HubBubbleMaxWidth),
            shape = RoundedCornerShape(HubBubbleCornerRadius),
            color =
                if (message.incoming) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
        ) {
            Column(
                modifier = Modifier.padding(SableSpacing.Md),
                verticalArrangement = Arrangement.spacedBy(SableSpacing.Xs),
            ) {
                if (
                    message.source == HubConversationSource.ConnectedApp &&
                    message.incoming &&
                    !message.senderLabel.isNullOrBlank()
                ) {
                    Text(
                        text = message.senderLabel.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text =
                        when {
                            message.body.isNotBlank() -> {
                                message.body
                            }

                            message.source == HubConversationSource.ConnectedApp -> {
                                "Content hidden by source app"
                            }

                            else -> {
                                "Empty SMS body"
                            }
                        },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                        if (message.handoffOnly) {
                            "Handed off · " + relativeTime(message.dateMillis)
                        } else {
                            relativeTime(message.dateMillis)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ComposeView(
    draft: HubSmsDraft,
    onDraftChange: (HubSmsDraft) -> Unit,
    canSendSms: Boolean,
    onBack: () -> Unit,
    onRequestAccess: () -> Unit,
    onSendSms: (String, String) -> HubSendResult,
    onOpenMmsComposer: (String, String) -> Unit,
) {
    val recipient = draft.recipient
    val body = draft.body
    var localStatus by remember {
        mutableStateOf<String?>(null)
    }

    SablePageHeader(
        title = "new message",
        subtitle = "Send SMS, or hand off to the system MMS composer for attachments.",
    )

    OutlinedTextField(
        value = recipient,
        onValueChange = { onDraftChange(draft.copy(recipient = it)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Phone number") },
    )

    OutlinedTextField(
        value = body,
        onValueChange = { onDraftChange(draft.copy(body = it)) },
        modifier =
            Modifier
                .fillMaxWidth()
                .hubEnterToSend(body = body, canSend = canSendSms && recipient.isNotBlank()) {
                    localStatus = onSendSms(recipient.trim(), body).message
                },
        minLines = SMS_COMPOSE_MIN_LINES,
        label = { Text("Message") },
    )

    localStatus?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (canSendSms) {
        SableActionButton(
            text = "Send SMS",
            onClick = {
                val result = onSendSms(recipient.trim(), body)
                localStatus = result.message
            },
        )
    } else {
        SablePanel {
            Text(
                text = "SMS sending needs permission.",
                style = MaterialTheme.typography.bodyMedium,
            )
            SableActionButton(
                text = "Review access",
                primary = false,
                onClick = onRequestAccess,
            )
        }
    }

    SableActionButton(
        text = "MMS / attachment",
        primary = false,
        onClick = {
            onOpenMmsComposer(recipient.trim(), body)
        },
    )

    SableActionButton(
        text = "Back",
        primary = false,
        onClick = onBack,
    )
}

private fun relativeTime(timestamp: Long): String =
    DateUtils
        .getRelativeTimeSpanString(
            timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()

private val HubAvatarSize = 48.dp
private val HubRowVerticalPadding = 10.dp
private val HubAvatarTextGap = 14.dp
private val HubTrailingGap = 10.dp
private val HubBubbleMaxWidth = 320.dp
private val HubBubbleCornerRadius = 18.dp
private val HubUnreadAccent = Color(0xFF35C66B)
private val HubUnreadBackground = Color(0xFF0B3524)

private const val SMS_PREVIEW_LENGTH = 120
private const val CONNECTED_PREVIEW_LENGTH = 100
private const val HUB_INLINE_COMPOSER_MAX_LINES = 4
private const val SMS_COMPOSE_MIN_LINES = 4
private const val MAX_VISIBLE_CONVERSATIONS = 120
private const val MAX_VISIBLE_THREAD_MESSAGES = 250
private const val MAX_VISIBLE_PEOPLE = 300
