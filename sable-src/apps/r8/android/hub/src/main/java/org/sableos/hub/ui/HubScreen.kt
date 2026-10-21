package org.sableos.hub.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
import org.sableos.hub.policy.HubConversationRollup
import org.sableos.hub.policy.HubDockEntry
import org.sableos.hub.policy.HubDockSource
import org.sableos.hub.policy.HubDrafts
import org.sableos.hub.policy.HubInbox
import org.sableos.hub.policy.HubInboxCluster
import org.sableos.hub.policy.HubInboxDivider
import org.sableos.hub.policy.HubInboxFilter
import org.sableos.hub.policy.HubInboxItem
import org.sableos.hub.policy.HubInboxRow
import org.sableos.hub.policy.HubInboxRowRole
import org.sableos.hub.policy.HubNewMessagePill
import org.sableos.hub.policy.HubPrivacyBadge
import org.sableos.hub.policy.HubPrivacyState
import org.sableos.hub.policy.HubPrivateSummary
import org.sableos.hub.policy.HubRedaction
import org.sableos.hub.policy.HubRowAction
import org.sableos.hub.policy.HubRowActions
import org.sableos.hub.policy.HubSearch
import org.sableos.hub.policy.HubSourceDock
import org.sableos.hub.policy.HubThreadCommand
import org.sableos.hub.policy.HubThreadKey
import org.sableos.hub.policy.HubThreadKeyContext
import org.sableos.hub.policy.HubThreadKeys
import org.sableos.hub.policy.HubThreadResolver
import org.sableos.hub.policy.HubThreadSearch
import java.text.DateFormat
import java.util.Calendar
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

/** Hub's navigation and the transient UI state its routes share. */
@Stable
private class HubUiState {
    var route by mutableStateOf<HubRoute>(HubRoute.Root(HubPivot.Priority))
    var search by mutableStateOf("")
    var inboxFilter by mutableStateOf(HubInboxFilter.All)
    var statusMessage by mutableStateOf<String?>(null)

    /** Source dock: the source the inbox is narrowed to (null: all) and whether the dock is a grid. */
    var dockSource by mutableStateOf<HubDockSource?>(null)
    var dockExpanded by mutableStateOf(false)

    /** Combined conversations the user expanded, by cluster id, kept while Hub is open. */
    val expandedClusters = mutableStateMapOf<String, Boolean>()

    /** "/" bumps this; the inbox search field takes focus on each change. */
    var searchFocusRequest by mutableIntStateOf(0)

    /** The row whose actions sheet is open (Menu, Alt+Enter or long press), or null. */
    var actionsItem by mutableStateOf<HubInboxItem?>(null)

    /** Counts-only view: the user asked for the (redacted) list this session. */
    var privateListShown by mutableStateOf(false)

    /** Shade "H" / Settings handoff: open the conversation Hub resolved for the notification. */
    fun openPendingThread(
        threadId: Long?,
        hasSnapshot: Boolean,
    ): Boolean {
        if (threadId == null || !hasSnapshot) return false
        route = HubRoute.Conversation(threadId = threadId)
        return true
    }

    /**
     * Start's Command "Search Hub for …" and Start's Hub tabs: open the inbox on the requested
     * filter (else All), all sources, with the search filled in.
     */
    fun applyLaunchQuery(query: HubLaunchQuery) {
        route = HubRoute.Root(HubPivot.Inbox)
        inboxFilter = query.filter ?: HubInboxFilter.All
        dockSource = null
        search = query.query
        statusMessage = null
    }

    /** Contacts' Hub route chip: the person's SMS thread, or a new one for the number. */
    fun openAddress(address: String) {
        statusMessage = null
        route =
            HubRoute.Conversation(
                threadId = HubThreadResolver.NEW_THREAD_ID,
                address = address,
            )
    }

    /** "/" from anywhere in the root view: the inbox search, focused. */
    fun focusSearch() {
        if ((route as? HubRoute.Root)?.pivot != HubPivot.Inbox) {
            route = HubRoute.Root(HubPivot.Inbox)
        }
        searchFocusRequest += 1
    }

    /** Hardware Back/Esc goes up one level (BACK_RETURNS_ONE_LOGICAL_LAYER); only the root leaves Hub. */
    fun back() {
        when (val current = route) {
            is HubRoute.Root -> Unit
            is HubRoute.Conversation -> closeConversation(current.origin)
            is HubRoute.Compose -> route = current.back
        }
    }

    fun selectPivot(pivot: HubPivot) {
        route = HubRoute.Root(pivot)
        search = ""
        statusMessage = null
    }

    fun selectInboxFilter(filter: HubInboxFilter) {
        inboxFilter = filter
        dockSource = null
        statusMessage = null
    }

    fun openThread(target: HubRoute.Conversation) {
        statusMessage = null
        route = target
    }

    fun closeConversation(origin: HubPivot) {
        statusMessage = null
        route = HubRoute.Root(origin)
    }

    fun dial(
        number: String,
        onDial: (String) -> Boolean,
    ) {
        statusMessage =
            when {
                number.isBlank() -> "This caller's number is not available."
                onDial(number) -> null
                else -> "The Phone app is unavailable."
            }
    }

    /** Sent messages are shown in place: land in the thread, composer ready. */
    fun composeSent(
        result: HubSendResult,
        recipient: String,
    ) {
        statusMessage = result.message
        if (result.success) {
            route =
                HubRoute.Conversation(
                    threadId = HubThreadResolver.NEW_THREAD_ID,
                    address = recipient,
                )
        }
    }

    fun mmsHandoff(opened: Boolean) {
        statusMessage = mmsHandoffStatus(opened)
    }
}

/** A dock source narrows the whole inbox (filter "All"); choosing it again clears it. */
private fun HubUiState.selectDockSource(source: HubDockSource) {
    inboxFilter = HubInboxFilter.All
    dockSource = HubSourceDock.toggle(dockSource, source)
    statusMessage = null
}

private fun mmsHandoffStatus(opened: Boolean): String =
    if (opened) "Opened the system MMS composer." else "MMS composer is unavailable."

/** The activity's callbacks and persisted thread drafts, handed down to every route. */
private class HubActions(
    val onRequestAccess: () -> Unit,
    val onSendSms: (String, String) -> HubSendResult,
    val onOpenAndroidMessaging: (String?, String?) -> Boolean,
    val onOpenSableMail: () -> Boolean,
    val onOpenConnectedApps: () -> Unit,
    val onReplyConnected: (String, String) -> HubSendResult,
    val onOpenConnectedApp: (String, Long) -> Boolean,
    val onOpenSourceNotificationSettings: (String, Long) -> Boolean,
    val onDial: (String) -> Boolean,
    val drafts: Map<String, String>,
    val onDraftChange: (String, String) -> Unit,
    val combineConversations: Boolean,
    val loadAppIcon: suspend (String, Long) -> ImageBitmap?,
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
    pendingAddress: String? = null,
    onPendingAddressConsumed: () -> Unit = {},
    combineConversations: Boolean = true,
    loadAppIcon: suspend (String, Long) -> ImageBitmap? = { _, _ -> null },
) {
    val state = remember { HubUiState() }

    LaunchedEffect(pendingThreadId, snapshot != null) {
        if (state.openPendingThread(pendingThreadId, snapshot != null)) {
            onPendingThreadConsumed()
        }
    }

    LaunchedEffect(pendingAddress) {
        val address = pendingAddress ?: return@LaunchedEffect
        state.openAddress(address)
        onPendingAddressConsumed()
    }

    LaunchedEffect(launchQuery) {
        val query = launchQuery ?: return@LaunchedEffect
        state.applyLaunchQuery(query)
        onLaunchQueryConsumed()
    }
    // Back from a conversation lands on the row it was opened from (FOCUS_RESTORATION).
    val focusMemory = remember { SableFocusMemory() }

    // New-message compose keeps its recipient and text while the user backs out this session;
    // thread drafts are persisted by the activity ([drafts], keyed by HubDrafts).
    val composeDrafts = remember { mutableStateMapOf<String, HubSmsDraft>() }

    BackHandler(enabled = state.route !is HubRoute.Root) {
        state.back()
    }

    if (snapshot == null) {
        HubLoadingView()
        return
    }

    val actions =
        HubActions(
            onRequestAccess = onRequestAccess,
            onSendSms = onSendSms,
            onOpenAndroidMessaging = onOpenAndroidMessaging,
            onOpenSableMail = onOpenSableMail,
            onOpenConnectedApps = onOpenConnectedApps,
            onReplyConnected = onReplyConnected,
            onOpenConnectedApp = onOpenConnectedApp,
            onOpenSourceNotificationSettings = onOpenSourceNotificationSettings,
            onDial = onDial,
            drafts = drafts,
            onDraftChange = onDraftChange,
            combineConversations = combineConversations,
            loadAppIcon = loadAppIcon,
        )

    SableRefreshableSurface(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
    ) {
        when (val current = state.route) {
            is HubRoute.Root -> {
                HubRootView(
                    state = state,
                    pivot = current.pivot,
                    snapshot = snapshot,
                    actions = actions,
                    focusMemory = focusMemory,
                )
            }

            is HubRoute.Conversation -> {
                HubConversationHost(
                    state = state,
                    route = current,
                    snapshot = snapshot,
                    actions = actions,
                )
            }

            is HubRoute.Compose -> {
                HubComposeHost(
                    state = state,
                    route = current,
                    snapshot = snapshot,
                    actions = actions,
                    composeDrafts = composeDrafts,
                )
            }
        }
    }
}

@Composable
private fun HubLoadingView() {
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
}

@Composable
private fun HubRootView(
    state: HubUiState,
    pivot: HubPivot,
    snapshot: HubSnapshot,
    actions: HubActions,
    focusMemory: SableFocusMemory,
) {
    val privacy = snapshot.privacy
    if (privacy.countsOnly && !(state.privateListShown && privacy.canShowList)) {
        HubPrivateCountsView(
            snapshot = snapshot,
            onShowList = { filter ->
                state.privateListShown = true
                state.selectPivot(HubPivot.Inbox)
                state.selectInboxFilter(filter)
            },
            onPrivacyOptions = actions.onOpenConnectedApps,
        )
        return
    }
    SableScreen(modifier = Modifier.slashOpensSearch(state::focusSearch)) {
        SablePageHeader(
            title = "hub",
            subtitle = "Local communication across your enabled sources.",
        )

        HubPivotRow(
            selected = pivot,
            onSelect = state::selectPivot,
        )

        state.statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when (pivot) {
            HubPivot.Priority -> {
                PriorityPivot(
                    modifier = Modifier.weight(1f),
                    snapshot = snapshot,
                    onConversation = { conversation ->
                        state.openThread(
                            HubRoute.Conversation(
                                threadId = conversation.threadId,
                                origin = HubPivot.Priority,
                            ),
                        )
                    },
                    onOpenSableMail = actions.onOpenSableMail,
                )
            }

            HubPivot.Inbox -> {
                HubInboxView(
                    state = state,
                    snapshot = snapshot,
                    actions = actions,
                    focusMemory = focusMemory,
                )
            }

            HubPivot.Email -> {
                EmailPivot(
                    snapshot = snapshot,
                    onOpenSableMail = actions.onOpenSableMail,
                )
            }

            HubPivot.People -> {
                PeoplePivot(
                    modifier = Modifier.weight(1f),
                    snapshot = snapshot,
                    search = state.search,
                    onSearchChange = { state.search = it },
                    onCompose = { number ->
                        state.openThread(
                            HubRoute.Conversation(
                                threadId = HubThreadResolver.NEW_THREAD_ID,
                                origin = HubPivot.People,
                                address = number,
                            ),
                        )
                    },
                    onRequestAccess = actions.onRequestAccess,
                )
            }
        }
    }
}

/**
 * Counts-only view (SABLE_HUB_KEYBOARD_FIRST_UX "Privacy model", design panel 4): while the
 * device is locked or Hub private mode is on, Hub opens on categories and counts alone. Unlocked,
 * a category opens its (redacted) list; locked, nothing opens.
 */
@Composable
private fun HubPrivateCountsView(
    snapshot: HubSnapshot,
    onShowList: (HubInboxFilter) -> Unit,
    onPrivacyOptions: () -> Unit,
) {
    val privacy = snapshot.privacy
    val counts = remember(snapshot) { HubPrivateSummary.counts(snapshot.conversations, snapshot.calls) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    SableScreen {
        SablePageHeader(
            title = "hub",
            subtitle = HubPrivateSummary.oneLine(counts),
        )
        SablePanel {
            Text(
                text = privacy.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = privacy.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SableSpacing.Xs),
        ) {
            itemsIndexed(items = counts, key = { _, count -> count.filter.name }) { index, count ->
                SableListRow(
                    title = count.label,
                    subtitle = count.detail,
                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    onClick = { if (privacy.canShowList) onShowList(count.filter) },
                )
            }
        }
        if (privacy.canShowList) {
            SableActionButton(
                text = if (counts.isEmpty()) "Show list" else "Show list without previews",
                modifier = if (counts.isEmpty()) Modifier.fillMaxWidth().focusRequester(first) else Modifier.fillMaxWidth(),
                primary = false,
                onClick = { onShowList(HubInboxFilter.All) },
            )
        } else {
            Text(
                text = "Unlock to view details",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (privacy.privateMode) {
            SableActionButton(
                text = "Private mode settings",
                modifier = Modifier.fillMaxWidth(),
                primary = false,
                onClick = onPrivacyOptions,
            )
        }
    }
}

/** "/" (any layer: Alt or Sym on the Q25) opens Hub search unless a text field typed it. */
private fun Modifier.slashOpensSearch(onSearch: () -> Unit): Modifier =
    onKeyEvent { event ->
        val slash =
            event.type == KeyEventType.KeyDown &&
                !event.isCtrlPressed &&
                !event.isMetaPressed &&
                event.utf16CodePoint == '/'.code
        if (slash) onSearch()
        slash
    }

@Composable
private fun ColumnScope.HubInboxView(
    state: HubUiState,
    snapshot: HubSnapshot,
    actions: HubActions,
    focusMemory: SableFocusMemory,
) {
    InboxPivot(
        modifier = Modifier.weight(1f),
        snapshot = snapshot,
        state = state,
        actions = actions,
        focusMemory = focusMemory,
        filter = state.inboxFilter,
        onFilterChange = state::selectInboxFilter,
        search = state.search,
        onSearchChange = { state.search = it },
        onConversation = { conversation ->
            state.openThread(HubRoute.Conversation(threadId = conversation.threadId))
        },
        onCall = { call -> state.dial(call.number, actions.onDial) },
        onTextCaller = { call ->
            state.openThread(
                HubRoute.Conversation(
                    threadId = HubThreadResolver.NEW_THREAD_ID,
                    address = call.number,
                ),
            )
        },
        onCompose = {
            state.route = HubRoute.Compose()
        },
        onOpenConnectedApps = actions.onOpenConnectedApps,
        onRequestAccess = actions.onRequestAccess,
    )
}

@Composable
private fun HubConversationHost(
    state: HubUiState,
    route: HubRoute.Conversation,
    snapshot: HubSnapshot,
    actions: HubActions,
) {
    val conversation: HubConversation? = conversationFor(route, snapshot)
    val onBack = { state.closeConversation(route.origin) }

    if (conversation == null) {
        MissingConversationView(onBack = onBack)
    } else {
        ConversationRoute(
            snapshot = snapshot,
            conversation = conversation,
            statusMessage = state.statusMessage,
            onStatus = { state.statusMessage = it },
            onBack = onBack,
            onDial = { number -> state.dial(number, actions.onDial) },
            actions = actions,
        )
    }
}

@Composable
private fun HubComposeHost(
    state: HubUiState,
    route: HubRoute.Compose,
    snapshot: HubSnapshot,
    actions: HubActions,
    composeDrafts: MutableMap<String, HubSmsDraft>,
) {
    SableScrollableScreen {
        ComposeView(
            draft = composeDrafts[route.recipient] ?: HubSmsDraft(route.recipient, ""),
            onDraftChange = { draft -> composeDrafts[route.recipient] = draft },
            canSendSms = snapshot.capabilities.canSendSms,
            onBack = {
                state.route = route.back
            },
            onRequestAccess = actions.onRequestAccess,
            onSendSms = { recipient, body ->
                val result = actions.onSendSms(recipient, body)
                if (result.success) {
                    composeDrafts.remove(route.recipient)
                }
                state.composeSent(result, recipient)
                result
            },
            onOpenMmsComposer = { recipient, body ->
                state.mmsHandoff(
                    actions.onOpenAndroidMessaging(
                        recipient.takeIf { it.isNotBlank() },
                        body.takeIf { it.isNotBlank() },
                    ),
                )
            },
        )
    }
}

/**
 * The conversation [route] points at: an existing thread when one matches, otherwise a fresh SMS
 * thread for a non-blank address, otherwise null (the route is stale).
 */
private fun conversationFor(
    route: HubRoute.Conversation,
    snapshot: HubSnapshot,
): HubConversation? {
    val existing: HubConversation? =
        HubThreadResolver.resolve(snapshot.conversations, route.threadId, route.address)
    if (existing != null) return existing
    val address: String? = route.address
    return if (address.isNullOrBlank()) null else newSmsConversation(address, snapshot)
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
    onBack: () -> Unit,
    onDial: (String) -> Unit,
    actions: HubActions,
) {
    val draftKey = draftKeyFor(conversation)
    val drafts = actions.drafts
    val smsAddress =
        conversation.address.takeIf { address ->
            conversation.source == HubConversationSource.Sms && address.isNotBlank()
        }
    val connectedSource = connectedSourceOf(conversation)

    SableScreen {
        ConversationView(
            modifier = Modifier.weight(1f),
            conversation = conversation,
            messages = threadMessagesFor(snapshot, conversation),
            statusMessage = statusMessage,
            draft = drafts[draftKey].orEmpty(),
            onDraftChange = { body -> actions.onDraftChange(draftKey, body) },
            composer = composerFor(conversation, snapshot, actions),
            onRequestAccess = actions.onRequestAccess,
            onBack = onBack,
            onCall = smsAddress?.let { address -> { onDial(address) } },
            onAttach =
                smsAddress?.let { address ->
                    {
                        val opened =
                            actions.onOpenAndroidMessaging(
                                address,
                                drafts[draftKey]?.takeIf(String::isNotBlank),
                            )
                        onStatus(mmsHandoffStatus(opened))
                    }
                },
            onOpenSource =
                connectedSource?.let { (packageName, serial) ->
                    {
                        val opened = actions.onOpenConnectedApp(packageName, serial)
                        onStatus(openSourceStatus(opened, conversation.sourceLabel ?: packageName))
                    }
                },
            onOpenNotificationSettings =
                connectedSource?.let { (packageName, serial) ->
                    {
                        // Delivery policy is Android's: hand off, never mirror it.
                        val opened = actions.onOpenSourceNotificationSettings(packageName, serial)
                        if (!opened) onStatus("Android notification settings are unavailable.")
                    }
                },
            privacy = snapshot.privacy,
        )
    }
}

private fun openSourceStatus(
    opened: Boolean,
    label: String,
): String = if (opened) "Opened $label." else "The source app is unavailable."

private fun threadMessagesFor(
    snapshot: HubSnapshot,
    conversation: HubConversation,
): List<HubMessage> =
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

/** The source app (package, user serial) of a connected-app thread; null for SMS. */
private fun connectedSourceOf(conversation: HubConversation): Pair<String, Long>? {
    val sourcePackage = conversation.sourcePackage
    val sourceUserSerial = conversation.sourceUserSerial
    return if (conversation.source != HubConversationSource.Sms && sourcePackage != null && sourceUserSerial != null) {
        sourcePackage to sourceUserSerial
    } else {
        null
    }
}

private fun draftKeyFor(conversation: HubConversation): String =
    if (conversation.source == HubConversationSource.Sms) {
        HubDrafts.smsKey(conversation.address)
    } else {
        HubDrafts.connectedKey(conversation.threadId)
    }

/** SMS through Android telephony, or a source RemoteInput reply; null when the thread cannot be answered here. */
private fun composerFor(
    conversation: HubConversation,
    snapshot: HubSnapshot,
    actions: HubActions,
): HubComposerSpec? =
    if (conversation.source == HubConversationSource.Sms) {
        conversation.address.takeIf(String::isNotBlank)?.let { address ->
            HubComposerSpec(
                label = "Text message",
                canSend = snapshot.capabilities.canSendSms,
                send = { body -> actions.onSendSms(address, body) },
            )
        }
    } else {
        conversation.sourceNotificationKey?.takeIf { conversation.canQuickReply }?.let { notificationKey ->
            HubComposerSpec(
                label = "Reply",
                canSend = true,
                send = { body -> actions.onReplyConnected(notificationKey, body) },
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
    state: HubUiState,
    actions: HubActions,
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
    if (inboxNeedsAccess(capabilities, filter)) {
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

    val chipRequesters = remember { HubInboxFilter.entries.associateWith { FocusRequester() } }
    val searchRequester = remember { FocusRequester() }
    LaunchedEffect(state.searchFocusRequest) {
        if (state.searchFocusRequest > 0) {
            withFrameNanos { }
            runCatching { searchRequester.requestFocus() }
        }
    }
    OutlinedTextField(
        value = search,
        onValueChange = onSearchChange,
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(searchRequester)
                .escapeLeavesSearch(
                    hasText = search.isNotEmpty(),
                    onClear = { onSearchChange("") },
                    onLeave = { runCatching { chipRequesters.getValue(filter).requestFocus() } },
                ),
        singleLine = true,
        label = { Text("/ Search messages, calls, mail") },
        placeholder = { Text(HubSearch.HINT, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )

    val query = search.trim()
    val dockSource = state.dockSource
    val todayStart = remember(snapshot) { startOfToday() }
    val items =
        remember(snapshot, filter, query, dockSource) {
            HubSourceDock.narrow(
                HubInbox.items(snapshot.conversations, snapshot.calls, filter, query, snapshot.people, todayStart),
                dockSource,
            )
        }
    val clusters =
        remember(items, actions.combineConversations) {
            HubConversationRollup.clusters(items.take(MAX_VISIBLE_CONVERSATIONS), actions.combineConversations)
        }
    val dockEntries =
        remember(snapshot) {
            HubSourceDock.entries(snapshot.conversations, snapshot.calls)
        }
    val listState = rememberLazyListState()
    val newestItem = items.firstOrNull()
    val visibleRows = HubConversationRollup.rows(clusters, state.expandedClusters.keys)
    val rowKeys = visibleRows.map { row -> row.item.key }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val dockFocus = remember { HubDockFocus() }
    var restoreDone by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val rowActions =
        HubInboxRowActions(
            onExpand = { cluster -> state.expandedClusters[cluster.id] = true },
            onCollapse = { cluster ->
                state.expandedClusters.remove(cluster.id)
                scope.launch {
                    withFrameNanos { }
                    runCatching { requesters[cluster.id]?.requestFocus() }
                }
            },
        )

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
        val firstVisit = !restoreDone
        restoreDone = true
        val restored =
            firstVisit &&
                restoreInboxRow(
                    restore = query.isBlank(),
                    focusMemory = focusMemory,
                    rowKeys = rowKeys,
                    listState = listState,
                    requesters = requesters,
                )
        // Otherwise the newest row is what is visible on open, and after a new arrival.
        if (!restored && newestItem != null && listState.firstVisibleItemIndex <= 1) {
            listState.scrollToItem(0)
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .inboxFilterShortcuts(selectFilter) {
                    scope.launch { dockFocus.focus() }
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
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(SableSpacing.Xs),
            ) {
                itemsIndexed(
                    items = visibleRows,
                    key = { _, row -> row.item.key },
                ) { index, row ->
                    InboxItemRow(
                        row = row,
                        modifier =
                            Modifier
                                .focusRequester(requesters.getOrPut(row.item.key) { FocusRequester() })
                                .clusterKeys(row, rowActions)
                                .rowActionsKey { state.actionsItem = row.item },
                        onOpen = { focusMemory.remember(FOCUS_SURFACE_INBOX, row.item.key, index) },
                        onLongPress = { state.actionsItem = row.item },
                        onConversation = onConversation,
                        onCall = onCall,
                        onTextCaller = onTextCaller,
                    )
                }
            }
        }

        Text(
            text = INBOX_KEY_HINT,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (dockEntries.isNotEmpty()) {
            InboxSourceDock(
                entries = dockEntries,
                state = state,
                focus = dockFocus,
                loadAppIcon = actions.loadAppIcon,
                onLongPress = { source ->
                    if (source is HubDockSource.App) {
                        val opened = actions.onOpenConnectedApp(source.packageName, source.userSerial)
                        if (!opened) state.statusMessage = "The source app is unavailable."
                    }
                },
            )
        }
    }

    state.actionsItem?.let { item ->
        HubRowActionsSheet(
            item = item,
            privacy = snapshot.privacy,
            onAction = { action -> runRowAction(action, item, state, actions, onConversation, onTextCaller) },
            onDismiss = { state.actionsItem = null },
        )
    }
}

/** Runs one row action through the activity's provider-safe handoffs. */
private fun runRowAction(
    action: HubRowAction,
    item: HubInboxItem,
    state: HubUiState,
    actions: HubActions,
    onConversation: (HubConversation) -> Unit,
    onTextCaller: (HubCall) -> Unit,
) {
    state.actionsItem = null
    when (item) {
        is HubInboxItem.Call -> {
            when (action) {
                HubRowAction.CallBack -> state.dial(item.call.number, actions.onDial)
                HubRowAction.Text -> onTextCaller(item.call)
                HubRowAction.PrivacyOptions -> actions.onOpenConnectedApps()
                else -> Unit
            }
        }

        is HubInboxItem.Conversation -> {
            val conversation = item.conversation
            val source = connectedSourceOf(conversation)
            when (action) {
                HubRowAction.Open, HubRowAction.Reply -> {
                    onConversation(conversation)
                }

                HubRowAction.CallBack -> {
                    state.dial(conversation.address, actions.onDial)
                }

                HubRowAction.OpenInMessages -> {
                    state.mmsHandoff(actions.onOpenAndroidMessaging(conversation.address, null))
                }

                HubRowAction.OpenSourceApp -> {
                    source?.let { (packageName, serial) ->
                        val opened = actions.onOpenConnectedApp(packageName, serial)
                        state.statusMessage = openSourceStatus(opened, conversation.sourceLabel ?: packageName)
                    }
                }

                HubRowAction.SourceNotifications -> {
                    source?.let { (packageName, serial) ->
                        if (!actions.onOpenSourceNotificationSettings(packageName, serial)) {
                            state.statusMessage = "Android notification settings are unavailable."
                        }
                    }
                }

                HubRowAction.PrivacyOptions -> {
                    actions.onOpenConnectedApps()
                }

                HubRowAction.Text -> {
                    Unit
                }
            }
        }
    }
}

/**
 * The row actions sheet: the row's provider-safe actions as a short focusable list, first one
 * focused. Back or Esc closes it and focus returns to the row.
 */
@Composable
private fun HubRowActionsSheet(
    item: HubInboxItem,
    privacy: HubPrivacyState,
    onAction: (HubRowAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val rowActions = remember(item, privacy) { HubRowActions.forItem(item, privacy) }
    val first = remember { FocusRequester() }
    LaunchedEffect(item) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Dialog(onDismissRequest = onDismiss) {
        SablePanel {
            Text(
                text = HubRowActions.title(item),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            rowActions.forEachIndexed { index, action ->
                SableActionButton(
                    text = action.label,
                    modifier = if (index == 0) Modifier.fillMaxWidth().focusRequester(first) else Modifier.fillMaxWidth(),
                    primary = index == 0,
                    onClick = { onAction(action) },
                )
            }
        }
    }
}

/** Menu, or Alt+Enter (the Q25's Fn+Enter), opens the focused row's actions. */
private fun Modifier.rowActionsKey(onActions: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
        val menu = event.key == Key.Menu || (enter && event.isAltPressed)
        when {
            !menu -> {
                false
            }

            event.type == KeyEventType.KeyDown -> {
                onActions()
                true
            }

            else -> {
                true
            }
        }
    }

/**
 * Esc in the search field clears the query, then leaves the field for the filter chips; it never
 * leaves Hub from here (SABLE_HUB_KEYBOARD_FIRST_UX "Back / Esc close … search").
 */
private fun Modifier.escapeLeavesSearch(
    hasText: Boolean,
    onClear: () -> Unit,
    onLeave: () -> Unit,
): Modifier =
    onPreviewKeyEvent { event ->
        if (event.key != Key.Escape) {
            return@onPreviewKeyEvent false
        }
        if (event.type == KeyEventType.KeyDown) {
            if (hasText) onClear() else onLeave()
        }
        true
    }

/** Local midnight today, for the `today` search word. */
private fun startOfToday(): Long =
    Calendar
        .getInstance()
        .apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

/** What a combined row does on Right/Space (expand) and Left (collapse, back to its parent). */
private class HubInboxRowActions(
    val onExpand: (HubInboxCluster) -> Unit,
    val onCollapse: (HubInboxCluster) -> Unit,
)

/**
 * Keyboard for combined conversations: on a parent row Right or Space expands and Left collapses
 * (Space toggles); on a child row Left collapses and returns to the parent. Enter still opens.
 */
private fun Modifier.clusterKeys(
    row: HubInboxRow,
    actions: HubInboxRowActions,
): Modifier =
    if (row.role == HubInboxRowRole.Single) {
        this
    } else {
        onPreviewKeyEvent { event ->
            val space = event.key == Key.Spacebar
            val parent = row.role == HubInboxRowRole.Parent
            when {
                // Swallow Space's key-up too, so it never also activates the row.
                space && parent && event.type == KeyEventType.KeyUp -> {
                    true
                }

                event.type != KeyEventType.KeyDown -> {
                    false
                }

                parent && !row.expanded && (event.key == Key.DirectionRight || space) -> {
                    actions.onExpand(row.cluster)
                    true
                }

                // Space toggles a parent; Left collapses from the parent or any of its children.
                row.expanded && ((parent && space) || event.key == Key.DirectionLeft) -> {
                    actions.onCollapse(row.cluster)
                    true
                }

                else -> {
                    false
                }
            }
        }
    }

/** Focus targets of the dock: its first entry, else its expand button. */
private class HubDockFocus {
    val firstEntry = FocusRequester()
    val chevron = FocusRequester()

    suspend fun focus() {
        withFrameNanos { }
        if (runCatching { firstEntry.requestFocus() }.isFailure) {
            runCatching { chevron.requestFocus() }
        }
    }
}

/**
 * The source dock: one round entry per source with its count, most attention first. Enter or a
 * tap narrows the inbox to that source (again clears it); a long press opens a connected app.
 * The chevron switches between one scrolling row and a wrapped grid (the 720x720 square screen).
 */
@Composable
private fun InboxSourceDock(
    entries: List<HubDockEntry>,
    state: HubUiState,
    focus: HubDockFocus,
    loadAppIcon: suspend (String, Long) -> ImageBitmap?,
    onLongPress: (HubDockSource) -> Unit,
) {
    val expanded = state.dockExpanded
    val visible = HubSourceDock.visible(entries, expanded, state.dockSource)
    val entryButtons: @Composable () -> Unit = {
        visible.forEachIndexed { index, entry ->
            DockEntryButton(
                entry = entry,
                selected = entry.source == state.dockSource,
                modifier = if (index == 0) Modifier.focusRequester(focus.firstEntry) else Modifier,
                loadAppIcon = loadAppIcon,
                onSelect = { state.selectDockSource(entry.source) },
                onLongPress = { onLongPress(entry.source) },
            )
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HubDockCornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SableSpacing.Sm, vertical = SableSpacing.Xs),
            verticalAlignment = if (expanded) Alignment.Top else Alignment.CenterVertically,
        ) {
            when {
                visible.isEmpty() -> {
                    Text(
                        text = "Nothing new",
                        modifier = Modifier.weight(1f).padding(horizontal = SableSpacing.Sm),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                expanded -> {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(SableSpacing.Xs),
                        verticalArrangement = Arrangement.spacedBy(SableSpacing.Xs),
                    ) {
                        entryButtons()
                    }
                }

                else -> {
                    Row(
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(SableSpacing.Xs),
                    ) {
                        entryButtons()
                    }
                }
            }
            Spacer(Modifier.width(SableSpacing.Xs))
            DockChevron(
                expanded = expanded,
                modifier = Modifier.focusRequester(focus.chevron),
                onToggle = { state.dockExpanded = !expanded },
            )
        }
    }
}

@Composable
private fun DockChevron(
    expanded: Boolean,
    modifier: Modifier,
    onToggle: () -> Unit,
) {
    val description = if (expanded) "Show fewer sources" else "Show all sources"
    Box(
        modifier =
            modifier
                .size(HubDockSlotSize)
                .sableFocusRing()
                .clickable(onClick = onToggle)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(HubDockIconSize)
                    .background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (expanded) "⌄" else "⌃",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun DockEntryButton(
    entry: HubDockEntry,
    selected: Boolean,
    modifier: Modifier,
    loadAppIcon: suspend (String, Long) -> ImageBitmap?,
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
) {
    val source = entry.source
    val icon by produceState<ImageBitmap?>(null, source) {
        value = (source as? HubDockSource.App)?.let { app -> loadAppIcon(app.packageName, app.userSerial) }
    }
    val description = if (entry.count > 0) "${entry.label}, ${entry.count}" else entry.label
    Box(
        modifier =
            modifier
                .size(HubDockSlotSize)
                .sableFocusRing()
                .combinedClickable(onClick = onSelect, onLongClick = onLongPress)
                .semantics {
                    contentDescription = description
                    this.selected = selected
                },
    ) {
        val ring = if (selected) HubUnreadAccent else Color.Transparent
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(HubDockIconSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(2.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = icon
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.size(HubDockIconSize).padding(SableSpacing.Xs),
                )
            } else {
                Text(
                    text = dockGlyph(entry),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
        if (entry.count > 0) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .background(HubUnreadAccent, CircleShape)
                        .padding(horizontal = SableSpacing.Xs),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = HubUnreadBackground,
                )
            }
        }
    }
}

/** SMS and calls use Sable glyphs; apps without an icon (or hidden behind a profile) their initial. */
private fun dockGlyph(entry: HubDockEntry): String =
    when (entry.source) {
        HubDockSource.Sms -> {
            HubRedaction.SMS_LABEL
        }

        HubDockSource.Calls -> {
            callGlyph(HubCallKind.Missed)
        }

        else -> {
            entry.label
                .trim()
                .firstOrNull()
                ?.uppercaseChar()
                ?.toString() ?: "?"
        }
    }

private fun inboxNeedsAccess(
    capabilities: HubCapabilities,
    filter: HubInboxFilter,
): Boolean {
    val smsMissing = !capabilities.canReadSms || !capabilities.canSendSms
    val callLogMissing = !capabilities.canReadCallLog
    return when (filter) {
        HubInboxFilter.All, HubInboxFilter.Priority, HubInboxFilter.People -> smsMissing || callLogMissing
        HubInboxFilter.Messages -> smsMissing
        HubInboxFilter.Calls, HubInboxFilter.Missed -> callLogMissing
        HubInboxFilter.Notifications, HubInboxFilter.Mail -> false
    }
}

/**
 * Brings back the inbox row remembered before the last open, once; returns whether one was restored.
 * [restore] is false while a search is active, where the remembered row may not be listed.
 */
private suspend fun restoreInboxRow(
    restore: Boolean,
    focusMemory: SableFocusMemory,
    rowKeys: List<String>,
    listState: LazyListState,
    requesters: Map<String, FocusRequester>,
): Boolean {
    val target = if (restore) focusMemory.restore(FOCUS_SURFACE_INBOX, rowKeys) else null
    // One-shot: a later visit (pivot switch, handoff Back) opens on the newest again.
    focusMemory.forget(FOCUS_SURFACE_INBOX)
    if (target == null) return false
    listState.scrollToItem(target)
    withFrameNanos { }
    runCatching { requesters[rowKeys[target]]?.requestFocus() }
    return true
}

/**
 * A/M/C/N/E pick an inbox filter and D jumps to the source dock; Alt, Ctrl and Meta chords are
 * left to the system.
 */
private fun Modifier.inboxFilterShortcuts(
    onSelect: (HubInboxFilter) -> Unit,
    onDock: () -> Unit,
): Modifier =
    onKeyEvent { event ->
        val plain = !event.isAltPressed && !event.isCtrlPressed && !event.isMetaPressed
        val typed =
            if (event.type == KeyEventType.KeyDown && plain && event.utf16CodePoint > 0) {
                event.utf16CodePoint.toChar().lowercaseChar()
            } else {
                null
            }
        val shortcut = typed?.let(HubInboxFilter::forShortcut)
        when {
            shortcut != null -> {
                onSelect(shortcut)
                true
            }

            typed == HubSourceDock.SHORTCUT -> {
                onDock()
                true
            }

            else -> {
                false
            }
        }
    }

/** [onOpen] records the row for focus restoration before it opens a thread or hands off a call. */
@Composable
private fun InboxItemRow(
    row: HubInboxRow,
    modifier: Modifier,
    onOpen: () -> Unit,
    onConversation: (HubConversation) -> Unit,
    onCall: (HubCall) -> Unit,
    onTextCaller: (HubCall) -> Unit,
    onLongPress: () -> Unit = {},
) {
    when (val item = row.item) {
        is HubInboxItem.Conversation -> {
            ConversationRow(
                conversation = item.conversation,
                onClick = {
                    onOpen()
                    onConversation(item.conversation)
                },
                modifier = modifier,
                rollup = row,
                onLongClick = onLongPress,
            )
        }

        is HubInboxItem.Call -> {
            CallRow(
                call = item.call,
                onCall = {
                    onOpen()
                    onCall(item.call)
                },
                onText = {
                    onOpen()
                    onTextCaller(item.call)
                },
                modifier = modifier,
                divider = row.divider,
                onLongClick = onLongPress,
            )
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
        filter == HubInboxFilter.Missed && !capabilities.canReadCallLog -> "Grant call log access to show missed calls."
        filter == HubInboxFilter.Missed -> "No missed calls."
        filter == HubInboxFilter.Priority -> "Nothing needs your attention."
        filter == HubInboxFilter.People && !capabilities.canReadContacts -> "Grant contacts access to match people."
        filter == HubInboxFilter.People -> "No conversations or calls with your contacts."
        filter == HubInboxFilter.Notifications -> "No connected-app conversations. Choose apps under connected apps."
        filter == HubInboxFilter.Mail -> "No mail notifications. Turn on a mail app under connected apps."
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
            val count = HubInbox.attentionCount(snapshot.conversations, snapshot.calls, filter, snapshot.people)
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
            text = REVIEW_ACCESS_LABEL,
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
    divider: HubInboxDivider = HubInboxDivider.Single,
    onLongClick: (() -> Unit)? = null,
) {
    val attention = call.kind.needsAttention
    val avatarBackground = if (attention) HubUnreadBackground else MaterialTheme.colorScheme.surfaceVariant
    val glyphColor = if (attention) HubUnreadAccent else MaterialTheme.colorScheme.onSurface
    val detailColor = if (attention) HubUnreadAccent else MaterialTheme.colorScheme.onSurfaceVariant
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
                    .combinedClickable(onClick = onCall, onLongClick = onLongClick)
                    .padding(vertical = HubRowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(HubAvatarSize)
                        .background(avatarBackground, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = callGlyph(call.kind),
                    style = MaterialTheme.typography.titleLarge,
                    color = glyphColor,
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
                    color = detailColor,
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
    HubRowDivider(divider)
}

/** One thin line under a row; two lines with a 2 dp gap close a combined conversation. */
@Composable
private fun HubRowDivider(divider: HubInboxDivider) {
    val line =
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    Box(modifier = line)
    if (divider == HubInboxDivider.Double) {
        Spacer(Modifier.height(HubDoubleDividerGap))
        Box(modifier = line)
    }
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
    rollup: HubInboxRow? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val child = rollup?.role == HubInboxRowRole.Child
    val parent = rollup?.takeIf { it.role == HubInboxRowRole.Parent }
    val unread = conversation.unreadCount > 0
    val accent =
        if (unread) {
            HubUnreadAccent
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    val avatarBackground = if (unread) HubUnreadBackground else MaterialTheme.colorScheme.surfaceVariant
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
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(vertical = HubRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (child) {
            // A combined child repeats neither the person's name nor the avatar.
            Spacer(Modifier.width(HubAvatarSize))
        } else {
            Box(
                modifier =
                    Modifier
                        .size(HubAvatarSize)
                        .background(avatarBackground, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.width(HubAvatarTextGap))
        Column(modifier = Modifier.weight(1f)) {
            if (!child) {
                Text(
                    text = conversation.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = conversationPreview(conversation),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            parent?.let { row -> RollupLine(row) }
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
    HubRowDivider(rollup?.divider ?: HubInboxDivider.Single)
}

/** Under a combined parent: the "+N" pill (with its expand state) and the children's sources. */
@Composable
private fun RollupLine(row: HubInboxRow) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
    ) {
        Box(
            modifier =
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(HubPillCornerRadius))
                    .padding(horizontal = SableSpacing.Sm),
        ) {
            Text(
                text = "+" + row.cluster.children.size + if (row.expanded) " ▾" else " ▸",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = row.cluster.childSourceLabels.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Source label on every row: the inbox mixes SMS, calls and connected apps. */
private fun conversationPreview(conversation: HubConversation): String {
    if (conversation.source != HubConversationSource.ConnectedApp) {
        return if (conversation.lastBody.isBlank()) "SMS" else "SMS · " + conversation.lastBody.take(SMS_PREVIEW_LENGTH)
    }
    val sourceLine =
        listOfNotNull(
            conversation.sourceLabel ?: "connected app",
            conversation.profileBadge,
        ).joinToString(" · ")
    return if (conversation.lastBody.isBlank()) {
        "$sourceLine · content hidden"
    } else {
        "$sourceLine · " + conversation.lastBody.take(CONNECTED_PREVIEW_LENGTH)
    }
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
                text = REVIEW_ACCESS_LABEL,
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
    privacy: HubPrivacyState = HubPrivacyState(),
) {
    // Newest message at the bottom and visible on open (reverse layout, index 0 = newest).
    val conversationState = rememberLazyListState()
    val conversationScope = rememberCoroutineScope()
    val history = remember(conversation.threadId, conversation.address) { HubThreadHistory() }
    val newestFirst = remember(messages) { messages.asReversed() }
    val matches = remember(newestFirst, history.query) { HubThreadSearch.matches(newestFirst, history.query) }
    val searchRequester = remember { FocusRequester() }

    val toLatest: () -> Unit = {
        history.closeSearch()
        conversationScope.launch { conversationState.animateScrollToItem(0) }
    }
    val showMatch: (Int) -> Unit = { position ->
        history.match = position
        matches.getOrNull(position)?.let { index ->
            conversationScope.launch { conversationState.animateScrollToItem(index) }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .threadKeys(
                    history = history,
                    draftEmpty = draft.isEmpty(),
                    atLatest = { conversationState.firstVisibleItemIndex == 0 },
                    onCommand = { command ->
                        when (command) {
                            HubThreadCommand.Latest -> {
                                toLatest()
                            }

                            HubThreadCommand.OlderPage, HubThreadCommand.NewerPage -> {
                                val target =
                                    HubThreadKeys.pageTarget(
                                        firstVisible = conversationState.firstVisibleItemIndex,
                                        visibleCount = conversationState.layoutInfo.visibleItemsInfo.size,
                                        count = newestFirst.size,
                                        older = command == HubThreadCommand.OlderPage,
                                    )
                                conversationScope.launch { conversationState.scrollToItem(target) }
                            }

                            HubThreadCommand.Search -> {
                                history.searchOpen = true
                                conversationScope.launch {
                                    withFrameNanos { }
                                    runCatching { searchRequester.requestFocus() }
                                }
                            }

                            HubThreadCommand.None -> {
                                Unit
                            }
                        }
                    },
                ),
        verticalArrangement = Arrangement.spacedBy(SableSpacing.Md),
    ) {
        SablePageHeader(
            title = conversation.displayName,
            subtitle =
                conversation.sourceLabel
                    ?.takeIf { conversation.source == HubConversationSource.ConnectedApp }
                    ?.let { label -> listOfNotNull(label, conversation.profileBadge).joinToString(" · ") }
                    ?: conversation.address,
        )
        // Privacy badge (MESSAGES_CONVERSATION_UX "contact/source/privacy header").
        Text(
            text = HubPrivacyBadge.text(conversation, privacy),
            modifier = Modifier.semantics { contentDescription = "Privacy: " + HubPrivacyBadge.text(conversation, privacy) },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            ConversationActionButton("Call", onCall)
            ConversationActionButton("Attach", onAttach)
            ConversationActionButton("Open app", onOpenSource)
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

        if (history.searchOpen) {
            ThreadSearchField(
                history = history,
                matchCount = matches.size,
                requester = searchRequester,
                onStep = { older -> showMatch(HubThreadSearch.step(history.match, matches.size, older)) },
            )
        }

        ConversationMessages(
            modifier = Modifier.weight(1f),
            messages = messages,
            listState = conversationState,
            emptyText = if (composer != null) "No messages yet. Write the first one below." else "No messages yet.",
            historyMode = history.searchOpen,
            highlighted = matches.getOrNull(history.match)?.let { index -> newestFirst.getOrNull(index)?.id },
            onLatest = toLatest,
        )

        composer?.let { spec ->
            ConversationComposer(
                conversation = conversation,
                composer = spec,
                draft = draft,
                onDraftChange = onDraftChange,
                onRequestAccess = onRequestAccess,
                onSent = toLatest,
                fieldModifier = Modifier.onFocusChanged { focus -> history.composerFocused = focus.hasFocus },
            )
        }

        Text(
            text = THREAD_KEY_HINT,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** History and search state of one open conversation (MESSAGES_CONVERSATION_UX "History mode"). */
@Stable
private class HubThreadHistory {
    var searchOpen by mutableStateOf(false)
    var query by mutableStateOf("")
    var match by mutableIntStateOf(-1)
    var composerFocused by mutableStateOf(false)
    var searchFocused by mutableStateOf(false)

    fun closeSearch() {
        searchOpen = false
        query = ""
        match = -1
        searchFocused = false
    }
}

/**
 * Conversation keys ([HubThreadKeys]): End / Ctrl+L latest, Esc out of history or search,
 * PageUp / PageDown (Sym+R / Sym+E) by page, "/" search. Seen before the composer, so a key
 * the policy leaves alone still types; a handled key's release is swallowed too.
 */
private fun Modifier.threadKeys(
    history: HubThreadHistory,
    draftEmpty: Boolean,
    atLatest: () -> Boolean,
    onCommand: (HubThreadCommand) -> Unit,
): Modifier =
    composed {
        val handled = remember { mutableSetOf<Key>() }
        onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp) {
                return@onPreviewKeyEvent handled.remove(event.key)
            }
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val key =
                when {
                    event.key == Key.MoveEnd -> HubThreadKey.End
                    event.key == Key.Escape -> HubThreadKey.Escape
                    event.key == Key.PageUp -> HubThreadKey.PageUp
                    event.key == Key.PageDown -> HubThreadKey.PageDown
                    event.key == Key.L -> HubThreadKey.LetterL
                    event.utf16CodePoint == '/'.code -> HubThreadKey.Slash
                    else -> HubThreadKey.Other
                }
            val typing = history.composerFocused || history.searchFocused
            val text = if (history.searchFocused) history.query else ""
            val command =
                HubThreadKeys.decide(
                    key,
                    HubThreadKeyContext(
                        composerFocused = typing,
                        draftEmpty = if (history.searchFocused) text.isEmpty() else draftEmpty,
                        atLatest = atLatest(),
                        searchOpen = history.searchOpen,
                        ctrl = event.isCtrlPressed,
                        // "/" is on the Alt or Sym layer of the Q25, so Alt never blocks it.
                        alt = event.isAltPressed && key != HubThreadKey.Slash,
                        shift = event.isShiftPressed && key != HubThreadKey.Slash,
                        meta = event.isMetaPressed,
                    ),
                )
            if (command == HubThreadCommand.None) {
                false
            } else {
                handled += event.key
                onCommand(command)
                true
            }
        }
    }

/** Search in this conversation: Enter older match, Shift+Enter newer, Esc back to latest. */
@Composable
private fun ThreadSearchField(
    history: HubThreadHistory,
    matchCount: Int,
    requester: FocusRequester,
    onStep: (Boolean) -> Unit,
) {
    OutlinedTextField(
        value = history.query,
        onValueChange = { text ->
            history.query = text
            history.match = -1
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(requester)
                .onFocusChanged { focus -> history.searchFocused = focus.hasFocus }
                .onPreviewKeyEvent { event ->
                    val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                    if (enter && event.type == KeyEventType.KeyDown) onStep(!event.isShiftPressed)
                    enter
                },
        singleLine = true,
        label = { Text("Search this conversation") },
        supportingText = {
            Text(HubThreadSearch.status(history.match, matchCount, history.query))
        },
    )
}

/**
 * The latest message window. A message that arrives (or is sent) while the latest is in view is
 * shown; while the user reads history or searches it is not scrolled to, and the new-message
 * pill says so instead ([HubNewMessagePill]). The pill, End and Esc return to latest.
 */
@Composable
private fun ConversationMessages(
    modifier: Modifier,
    messages: List<HubMessage>,
    listState: LazyListState,
    emptyText: String,
    historyMode: Boolean = false,
    highlighted: Long? = null,
    onLatest: () -> Unit = {},
) {
    val showLatest by remember(messages.size) {
        derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo
            SableLatestContentPolicy.showReturnToCurrent(
                firstVisibleIndex = visible.firstOrNull()?.index ?: 0,
                lastVisibleIndex = visible.lastOrNull()?.index ?: 0,
                count = messages.size,
                order = SableListOrder.NewestFirst,
            )
        }
    }
    val atLatest by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val newest = messages.lastOrNull()
    // The newest message the user has had in view; the pill compares arrivals with it.
    var seenNewestId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(atLatest, newest?.id) {
        if (atLatest || seenNewestId == null) seenNewestId = newest?.id
    }
    // AUTO_ADVANCE: reading history (or searching) is never yanked away, except by the user's
    // own sent message.
    LaunchedEffect(newest?.id) {
        if (newest != null && ((!historyMode && listState.firstVisibleItemIndex <= 1) || !newest.incoming)) {
            listState.animateScrollToItem(0)
        }
    }
    val pill = HubNewMessagePill.show(atLatest, seenNewestId, newest)
    Box(modifier = modifier.fillMaxWidth()) {
        if (messages.isEmpty()) {
            Text(
                text = emptyText,
                modifier = Modifier.align(Alignment.BottomStart).padding(vertical = SableSpacing.Md),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
        ) {
            items(
                items = messages.asReversed(),
                key = HubMessage::id,
            ) { message ->
                MessageBubble(message, highlighted = message.id == highlighted)
            }
        }
        SableReturnToCurrent(
            visible = pill || showLatest,
            onClick = onLatest,
            modifier = Modifier.align(Alignment.BottomEnd).padding(SableSpacing.Sm),
            label = if (pill) HubNewMessagePill.LABEL else "latest",
        )
    }
}

@Composable
private fun RowScope.ConversationActionButton(
    text: String,
    action: (() -> Unit)?,
) {
    action?.let { onClick ->
        SableActionButton(
            text = text,
            modifier = Modifier.weight(1f),
            primary = false,
            onClick = onClick,
        )
    }
}

/** The thread's composer, or the permission prompt when SMS sending is not allowed yet. */
@Composable
private fun ConversationComposer(
    conversation: HubConversation,
    composer: HubComposerSpec,
    draft: String,
    onDraftChange: (String) -> Unit,
    onRequestAccess: () -> Unit,
    onSent: () -> Unit,
    fieldModifier: Modifier = Modifier,
) {
    var composerStatus by remember(conversation.threadId, conversation.address) {
        mutableStateOf<String?>(null)
    }

    if (!composer.canSend) {
        SablePanel {
            Text(
                text = "SMS sending needs permission.",
                style = MaterialTheme.typography.bodyMedium,
            )
            SableActionButton(
                text = REVIEW_ACCESS_LABEL,
                primary = false,
                onClick = onRequestAccess,
            )
        }
        return
    }

    HubInlineComposer(
        label = composer.label,
        draft = draft,
        onDraftChange = onDraftChange,
        fieldModifier = fieldModifier,
        status = composerStatus,
        onSend = { body ->
            val result = composer.send(body)
            composerStatus = result.message.takeUnless { result.success }
            if (result.success) {
                onDraftChange("")
                onSent()
            }
        },
    )
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
    fieldModifier: Modifier = Modifier,
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
                fieldModifier
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
private fun MessageBubble(
    message: HubMessage,
    highlighted: Boolean = false,
) {
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
            // The current search match is outlined in the accent.
            border = if (highlighted) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
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
                visibleSenderLabel(message)?.let { sender ->
                    Text(
                        text = sender,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = messageBodyText(message),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = messageTimeText(message),
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
                text = REVIEW_ACCESS_LABEL,
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

/** Connected-app group threads name each incoming sender; SMS threads have one other party. */
private fun visibleSenderLabel(message: HubMessage): String? =
    message.senderLabel?.takeIf { label ->
        message.source == HubConversationSource.ConnectedApp && message.incoming && label.isNotBlank()
    }

private fun messageBodyText(message: HubMessage): String =
    when {
        message.body.isNotBlank() -> message.body
        message.source == HubConversationSource.ConnectedApp -> "Content hidden by source app"
        else -> "Empty SMS body"
    }

private fun messageTimeText(message: HubMessage): String =
    if (message.handoffOnly) {
        "Handed off · " + relativeTime(message.dateMillis)
    } else {
        relativeTime(message.dateMillis)
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
private val HubDoubleDividerGap = 2.dp
private val HubDockSlotSize = 48.dp
private val HubDockIconSize = 40.dp
private val HubDockCornerRadius = 28.dp
private val HubPillCornerRadius = 10.dp

private const val REVIEW_ACCESS_LABEL = "Review access"
private const val THREAD_KEY_HINT = "/ search · PageUp history · End latest · Esc back to latest"
private const val INBOX_KEY_HINT = "/ search · Alt+Enter actions · A P M X C E N O filters · D dock"
private const val SMS_PREVIEW_LENGTH = 120
private const val CONNECTED_PREVIEW_LENGTH = 100
private const val HUB_INLINE_COMPOSER_MAX_LINES = 4
private const val SMS_COMPOSE_MIN_LINES = 4
private const val MAX_VISIBLE_CONVERSATIONS = 120
private const val MAX_VISIBLE_THREAD_MESSAGES = 250
private const val MAX_VISIBLE_PEOPLE = 300
