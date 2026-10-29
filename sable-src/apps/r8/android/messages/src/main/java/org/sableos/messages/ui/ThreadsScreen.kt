@file:Suppress("FunctionNaming")

package org.sableos.messages.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.sableos.design.SableActionButton
import org.sableos.design.SableListFocus
import org.sableos.design.SableListKeys
import org.sableos.design.SableSize
import org.sableos.design.SableSpacing
import org.sableos.design.sableFocusRing
import org.sableos.design.sableListKeys
import org.sableos.messages.core.MessagesText.t
import org.sableos.messages.core.ThreadSummary

/**
 * Conversation list, newest first; letters filter it, Enter opens the first match at latest. Down moves into the
 * list, where T, B, Space, Shift+Space, N and P move as on a BlackBerry (SableListKeys).
 */
@Composable
internal fun ColumnScope.ThreadsScreen(
    state: MessagesUiState,
    threads: List<ThreadSummary>,
    actions: MessagesActions,
) {
    ThreadsHeader(state.threads, actions)
    ThreadsBanners(state.access, actions)
    FilterField(state.filter, threads, actions)
    if (state.threadsLoaded && threads.isEmpty()) {
        Text(
            text =
                if (state.filter.isBlank()) {
                    t("no_conversations_yet_choose_new")
                } else {
                    t("no_conversation_matches")
                },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val listState = rememberLazyListState()
    val focus = remember(listState) { SableListFocus(listState) }
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .sableListKeys(focus, threads.map { it.threadId }, scope),
        state = focus.state,
        verticalArrangement = Arrangement.spacedBy(SableSpacing.Xs),
    ) {
        itemsIndexed(threads, key = { _, thread -> thread.threadId }) { index, thread ->
            ThreadRow(thread, focus.row(index, thread.threadId)) { actions.openThread(thread) }
        }
    }
    if (threads.isNotEmpty()) {
        Text(
            text = SableListKeys.HINT,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ThreadsHeader(
    threads: List<ThreadSummary>,
    actions: MessagesActions,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = t("messages"),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            val unread = threads.sumOf { it.unreadCount }
            Text(
                text =
                    if (unread >
                        0
                    ) {
                        t("conversations_unread", threads.size, unread)
                    } else {
                        t("conversations", threads.size)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SableActionButton(text = t("new_label"), onClick = actions.newMessage)
    }
}

@Composable
private fun ThreadsBanners(
    access: Access,
    actions: MessagesActions,
) {
    if (!access.hasTelephony) {
        Banner(t("no_mobile_network_on_this"), null) {}
    } else if (access.roleAvailable && !access.isDefault) {
        Banner(
            t("another_app_is_the_default"),
            t("make_default"),
            actions.requestDefaultRole,
        )
    }
    if (!access.canReadContacts) {
        Banner(
            t("contact_names_are_hidden_until"),
            t("show_names"),
            actions.requestPermissions,
        )
    }
}

@Composable
private fun FilterField(
    filter: String,
    threads: List<ThreadSummary>,
    actions: MessagesActions,
) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    OutlinedTextField(
        value = filter,
        onValueChange = actions.setFilter,
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            threads.firstOrNull()?.let(actions.openThread)
                            true
                        }

                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            focusManager.moveFocus(FocusDirection.Down)
                        }

                        KeyEvent.KEYCODE_ESCAPE -> {
                            filter.isNotEmpty().also { if (it) actions.setFilter("") }
                        }

                        else -> {
                            false
                        }
                    }
                },
        singleLine = true,
        placeholder = { Text(t("type_to_filter_enter_opens")) },
    )
}

@Composable
private fun ThreadRow(
    thread: ThreadSummary,
    modifier: Modifier,
    onOpen: () -> Unit,
) {
    val unread = thread.unreadCount > 0
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(if (unread) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                .sableFocusRing()
                .clickable(onClick = onOpen)
                .heightIn(min = SableSize.ListRowHeight)
                .padding(end = SableSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(SableSize.AccentBarWidth)
                    .height(SableSpacing.Xxl)
                    .background(
                        if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
        )
        Spacer(Modifier.width(SableSpacing.Md))
        Column(modifier = Modifier.weight(1f).padding(vertical = SableSpacing.Sm)) {
            Text(
                text = thread.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (thread.hasError) t("not_sent", thread.snippet) else thread.snippet,
                style = MaterialTheme.typography.bodyMedium,
                color = if (thread.hasError) colors.error else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = shortTime(thread.dateMillis),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (unread) UnreadBadge(thread.unreadCount)
        }
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    Box(
        modifier =
            Modifier
                .padding(top = SableSpacing.Xs)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = SableSpacing.Sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
