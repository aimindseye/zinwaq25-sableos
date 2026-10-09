package org.sableos.hub.ui

import android.text.format.DateUtils
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import org.sableos.hub.HubConversation
import org.sableos.hub.HubConversationSource
import org.sableos.hub.HubMessage
import org.sableos.hub.HubSendResult
import org.sableos.hub.HubSnapshot
import java.text.DateFormat
import java.util.Date

private enum class HubPivot {
    Priority,
    Messages,
    Email,
    People,
}

private sealed interface HubRoute {
    data class Root(
        val pivot: HubPivot,
    ) : HubRoute

    /** [origin]: the pivot Back returns to (BACK_RETURNS_ONE_LOGICAL_LAYER). */
    data class Conversation(
        val threadId: Long,
        val origin: HubPivot = HubPivot.Messages,
    ) : HubRoute

    /** [back]: the route Back returns to (the pivot or conversation it was opened from). */
    data class Compose(
        val recipient: String = "",
        val back: HubRoute = Root(HubPivot.Messages),
    ) : HubRoute
}

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
    var statusMessage by remember {
        mutableStateOf<String?>(null)
    }
    // Back from a conversation lands on the row it was opened from (FOCUS_RESTORATION).
    val focusMemory = remember { SableFocusMemory() }

    if (snapshot == null) {
        SableScreen {
            SablePageHeader(
                title = "hub",
                subtitle = "Priority, messages, email and people, kept local to this device.",
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

                    if (current.pivot == HubPivot.Messages) {
                        SableActionButton(
                            text = "connected apps",
                            primary = false,
                            onClick = onOpenConnectedApps,
                        )
                    }

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
                                    route =
                                        HubRoute.Conversation(
                                            threadId = conversation.threadId,
                                            origin = HubPivot.Priority,
                                        )
                                },
                                onOpenSableMail = onOpenSableMail,
                            )
                        }

                        HubPivot.Messages -> {
                            MessagesPivot(
                                modifier = Modifier.weight(1f),
                                snapshot = snapshot,
                                focusMemory = focusMemory,
                                search = search,
                                onSearchChange = { search = it },
                                onConversation = { conversation ->
                                    route =
                                        HubRoute.Conversation(
                                            threadId = conversation.threadId,
                                        )
                                },
                                onCompose = {
                                    route = HubRoute.Compose()
                                },
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
                                    route = HubRoute.Compose(number, back = HubRoute.Root(HubPivot.People))
                                },
                                onRequestAccess = onRequestAccess,
                            )
                        }
                    }
                }
            }

            is HubRoute.Conversation -> {
                val conversation =
                    snapshot.conversations.firstOrNull { candidate ->
                        candidate.threadId == current.threadId
                    }

                if (conversation == null) {
                    MissingConversationView(
                        onBack = {
                            statusMessage = null
                            route = HubRoute.Root(HubPivot.Messages)
                        },
                    )
                } else {
                    val threadMessages =
                        snapshot.messages
                            .filter { message ->
                                message.threadId == conversation.threadId
                            }.sortedBy { message ->
                                message.dateMillis
                            }.takeLast(MAX_VISIBLE_THREAD_MESSAGES)
                    val sourcePackage = conversation.sourcePackage
                    val sourceUserSerial = conversation.sourceUserSerial

                    SableScreen {
                        ConversationView(
                            modifier = Modifier.weight(1f),
                            conversation = conversation,
                            messages = threadMessages,
                            onBack = {
                                statusMessage = null
                                route = HubRoute.Root(current.origin)
                            },
                            onReply =
                                when {
                                    conversation.source == HubConversationSource.Sms -> {
                                        {
                                            route =
                                                HubRoute.Compose(
                                                    recipient = conversation.address,
                                                    back = current,
                                                )
                                        }
                                    }

                                    else -> {
                                        null
                                    }
                                },
                            onSendConnectedReply =
                                if (
                                    conversation.source == HubConversationSource.ConnectedApp &&
                                    conversation.canQuickReply &&
                                    conversation.sourceNotificationKey != null
                                ) {
                                    { body ->
                                        val result =
                                            onReplyConnected(
                                                conversation.sourceNotificationKey,
                                                body,
                                            )
                                        result
                                    }
                                } else {
                                    null
                                },
                            onOpenSource =
                                if (
                                    conversation.source == HubConversationSource.ConnectedApp &&
                                    sourcePackage != null &&
                                    sourceUserSerial != null
                                ) {
                                    {
                                        val opened =
                                            onOpenConnectedApp(
                                                sourcePackage,
                                                sourceUserSerial,
                                            )
                                        statusMessage =
                                            if (opened) {
                                                "Opened ${conversation.sourceLabel ?: sourcePackage}."
                                            } else {
                                                "The source app is unavailable."
                                            }
                                    }
                                } else {
                                    null
                                },
                            onOpenNotificationSettings =
                                if (
                                    conversation.source == HubConversationSource.ConnectedApp &&
                                    sourcePackage != null &&
                                    sourceUserSerial != null
                                ) {
                                    {
                                        // Delivery policy is Android's: hand off, never mirror it.
                                        if (!onOpenSourceNotificationSettings(sourcePackage, sourceUserSerial)) {
                                            statusMessage = "Android notification settings are unavailable."
                                        }
                                    }
                                } else {
                                    null
                                },
                        )
                    }
                }
            }

            is HubRoute.Compose -> {
                SableScrollableScreen {
                    ComposeView(
                        initialRecipient = current.recipient,
                        canSendSms = snapshot.capabilities.canSendSms,
                        onBack = {
                            route = current.back
                        },
                        onRequestAccess = onRequestAccess,
                        onSendSms = { recipient, body ->
                            val result = onSendSms(recipient, body)
                            statusMessage = result.message
                            if (result.success) {
                                route = HubRoute.Root(HubPivot.Messages)
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

@Composable
private fun MissingConversationView(onBack: () -> Unit) {
    SableScreen {
        SablePageHeader(
            title = "conversation unavailable",
            subtitle = "The source conversation is no longer available in local Sable history.",
        )
        SableActionButton(
            text = "Back to messages",
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
            itemsIndexed(
                items = visibleConversations,
                key = { _, conversation -> conversationKey(conversation) },
            ) { index, conversation ->
                val key = conversationKey(conversation)
                ConversationRow(
                    conversation = conversation,
                    onClick = {
                        focusMemory.remember(FOCUS_SURFACE_MESSAGES, key, index)
                        onConversation(conversation)
                    },
                    modifier = Modifier.focusRequester(requesters.getOrPut(key) { FocusRequester() }),
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
private fun ColumnScope.MessagesPivot(
    modifier: Modifier,
    snapshot: HubSnapshot,
    focusMemory: SableFocusMemory,
    search: String,
    onSearchChange: (String) -> Unit,
    onConversation: (HubConversation) -> Unit,
    onCompose: () -> Unit,
    onRequestAccess: () -> Unit,
) {
    if (!snapshot.capabilities.canReadSms || !snapshot.capabilities.canSendSms) {
        MessagesAccessPrompt(
            snapshot = snapshot,
            onRequestAccess = onRequestAccess,
        )
    }

    SableActionButton(
        text = "new message",
        onClick = onCompose,
    )

    OutlinedTextField(
        value = search,
        onValueChange = onSearchChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Search conversations") },
    )

    val query = search.trim()
    val conversations =
        if (query.isBlank()) {
            snapshot.conversations
        } else {
            snapshot.conversations.filter { conversation ->
                conversation.displayName.contains(query, ignoreCase = true) ||
                    conversation.address.contains(query, ignoreCase = true) ||
                    conversation.lastBody.contains(query, ignoreCase = true)
            }
        }
    val listState = rememberLazyListState()
    val newestConversation = conversations.firstOrNull()
    val visibleConversations = conversations.take(MAX_VISIBLE_CONVERSATIONS)
    val rowKeys = visibleConversations.map(::conversationKey)
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var restoreDone by remember { mutableStateOf(false) }

    LaunchedEffect(
        query,
        newestConversation?.threadId,
        newestConversation?.lastDateMillis,
    ) {
        // First visit after Back: restore the conversation row the user opened.
        if (!restoreDone) {
            restoreDone = true
            val target = if (query.isBlank()) focusMemory.restore(FOCUS_SURFACE_MESSAGES, rowKeys) else null
            if (target != null) {
                listState.scrollToItem(target)
                withFrameNanos { }
                runCatching { requesters[rowKeys[target]]?.requestFocus() }
                return@LaunchedEffect
            }
        }
        // Otherwise the newest conversation is what is visible on open.
        if (query.isBlank() && newestConversation != null) {
            listState.scrollToItem(0)
        }
    }

    if (conversations.isEmpty()) {
        Text(
            text =
                when {
                    !snapshot.capabilities.canReadSms -> {
                        "Grant Messages access to show SMS conversations."
                    }

                    query.isNotBlank() -> {
                        "No matching conversations."
                    }

                    else -> {
                        "No conversations yet."
                    }
                },
            modifier = Modifier.padding(vertical = SableSpacing.Lg),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(SableSpacing.Xs),
        ) {
            items(
                items = conversations.take(MAX_VISIBLE_CONVERSATIONS),
                key = { conversation ->
                    conversation.threadId.toString() + ":" + conversation.address
                },
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
private fun MessagesAccessPrompt(
    snapshot: HubSnapshot,
    onRequestAccess: () -> Unit,
) {
    val detail =
        when {
            snapshot.capabilities.canReadSms -> {
                "Allow sending access to send SMS from Sable Messages."
            }

            else -> {
                "Allow SMS access to read conversations and send messages."
            }
        }

    SablePanel {
        Text(
            text = "SMS access needed",
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
                        conversation.source != HubConversationSource.ConnectedApp -> {
                            conversation.lastBody.take(SMS_PREVIEW_LENGTH)
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

private const val FOCUS_SURFACE_MESSAGES = "hub-messages"

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
    onBack: () -> Unit,
    onReply: (() -> Unit)?,
    onSendConnectedReply: ((String) -> HubSendResult)?,
    onOpenSource: (() -> Unit)?,
    onOpenNotificationSettings: (() -> Unit)? = null,
) {
    var connectedReplyBody by remember {
        mutableStateOf("")
    }
    var connectedReplyStatus by remember {
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
        onReply?.let { action ->
            SableActionButton(
                text = "Reply",
                modifier = Modifier.weight(1f),
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
        onOpenNotificationSettings?.let { action ->
            SableActionButton(
                text = "Notifications",
                modifier = Modifier.weight(1f),
                primary = false,
                onClick = action,
            )
        }
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
    Box(modifier = modifier.fillMaxWidth()) {
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

    onSendConnectedReply?.let { sendReply ->
        OutlinedTextField(
            value = connectedReplyBody,
            onValueChange = { connectedReplyBody = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = HUB_INLINE_REPLY_MIN_LINES,
            label = { Text("Reply") },
        )
        connectedReplyStatus?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SableActionButton(
            text = "Send reply",
            onClick = {
                val result = sendReply(connectedReplyBody)
                connectedReplyStatus = result.message
                if (result.success) {
                    connectedReplyBody = ""
                }
            },
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
    initialRecipient: String,
    canSendSms: Boolean,
    onBack: () -> Unit,
    onRequestAccess: () -> Unit,
    onSendSms: (String, String) -> HubSendResult,
    onOpenMmsComposer: (String, String) -> Unit,
) {
    var recipient by remember(initialRecipient) {
        mutableStateOf(initialRecipient)
    }
    var body by remember {
        mutableStateOf("")
    }
    var localStatus by remember {
        mutableStateOf<String?>(null)
    }

    SablePageHeader(
        title = "new message",
        subtitle = "Send SMS, or hand off to the system MMS composer for attachments.",
    )

    OutlinedTextField(
        value = recipient,
        onValueChange = { recipient = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Phone number") },
    )

    OutlinedTextField(
        value = body,
        onValueChange = { body = it },
        modifier = Modifier.fillMaxWidth(),
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
private const val HUB_INLINE_REPLY_MIN_LINES = 2
private const val SMS_COMPOSE_MIN_LINES = 4
private const val MAX_VISIBLE_CONVERSATIONS = 120
private const val MAX_VISIBLE_THREAD_MESSAGES = 250
private const val MAX_VISIBLE_PEOPLE = 300
