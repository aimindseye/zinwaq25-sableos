// SableOS: Hub additions after a review of JL10's Hub (owner, 2026-11-12): date and Unread sections, the
// source line under the title, each app with its count in Views, the ⋮ menu, Mark Unread, Delete,
// Priority Hub and the SableOS rows in Hub settings. Part of the SableOS home flavor.
package io.github.aimindseye.tenseven.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.HubSettingsActivity
import io.github.aimindseye.tenseven.SwitchRow
import io.github.aimindseye.tenseven.conversations.ConversationFilter
import io.github.aimindseye.tenseven.conversations.ConversationHub
import io.github.aimindseye.tenseven.conversations.HubConversation
import io.github.aimindseye.tenseven.conversations.SableHubKeep
import io.github.aimindseye.tenseven.conversations.SableHubOptions
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.HubFilter

private const val SABLE_MAIL = "org.sableos.mail"
private const val DAVX5 = "at.bitfire.davdroid"

/** A section title in the Hub list: "Unread", "Today", "Thursday, November 12". */
@Suppress("FunctionNaming")
@Composable
internal fun SableHubSectionHeader(label: String) {
    Text(
        label,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp).semantics { heading() },
        color = if (label == "Unread") MaterialTheme.colorScheme.primary else Bb10Muted,
        style = MaterialTheme.typography.labelMedium,
    )
}

/** "All accounts · 2 unread" under the Hub title, or the app or Priority Hub being shown. */
@Suppress("FunctionNaming")
@Composable
internal fun SableHubSubtitle(ui: HubConversationsUi) {
    val source =
        when {
            ui.filter.priority -> "Priority Hub"
            ui.sourcePackage != null ->
                ui.conversations
                    .firstOrNull { ui.sourcePackage in it.sourcePackages }
                    ?.latestNotification
                    ?.appName
            else -> null
        }
    val unread =
        ui.conversations
            .filter { ui.sourcePackage == null || ui.sourcePackage in it.sourcePackages }
            .filter { !ui.filter.priority || SableHubOptions.isPriority(it) }
            .count { ui.state.unreadCount(it) > 0 && !ui.state.isSnoozed(it.id, ui.now) }
    Text(
        SableHubKeep.subtitle(source, unread),
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        color = Bb10Muted,
        style = MaterialTheme.typography.bodySmall,
    )
}

/** Views, after the fixed views: Priority Hub, each app with its unread count, and Email accounts. */
@Suppress("FunctionNaming")
@Composable
internal fun SableHubViewsExtras(
    ui: HubConversationsUi,
    onTabChange: (HubFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ViewsRow("Priority Hub", "") {
        onTabChange(HubFilter.All)
        ui.onFilterChange(ConversationFilter(priority = true))
        ui.onSourceChange(null)
        onDismiss()
    }
    SableHubKeep.sources(ui.conversations) { ui.state.unreadCount(it) }.forEach { source ->
        ViewsRow(source.label, if (source.unread > 0) source.unread.toString() else "") {
            onTabChange(HubFilter.All)
            ui.onFilterChange(ConversationFilter())
            ui.onSourceChange(source.packageName)
            onDismiss()
        }
    }
    ViewsRow("Email accounts…", "") {
        openSableMail(context)
        onDismiss()
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ViewsRow(
    label: String,
    count: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .sableFocusRing()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = Bb10Text, style = MaterialTheme.typography.bodyMedium)
        if (count.isNotEmpty()) Text(count, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
    }
}

/** The ⋮ at the right of the Hub bar: Hub settings, Email accounts, Calendar accounts, Connected apps. */
@Suppress("FunctionNaming")
@Composable
internal fun RowScope.SableHubMoreControl() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier
            .weight(1f)
            .heightIn(min = 52.dp)
            .sableFocusRing()
            .clickable { open = true }
            .semantics { contentDescription = "More" }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LineIcon(
            listOf("M12 5h.01", "M12 12h.01", "M12 19h.01"),
            Modifier.size(22.dp),
            Bb10Text,
        )
        Text("More", style = MaterialTheme.typography.labelSmall, color = Bb10Muted)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Hub settings") }, onClick = {
                open = false
                openHubSettings(context)
            })
            DropdownMenuItem(text = { Text("Email accounts") }, onClick = {
                open = false
                openSableMail(context)
            })
            DropdownMenuItem(text = { Text("Calendar accounts") }, onClick = {
                open = false
                openCalendarAccounts(context)
            })
            DropdownMenuItem(text = { Text("Connected apps") }, onClick = {
                open = false
                openHubSettings(context)
            })
        }
    }
}

/** Row menu additions: Mark Unread, Priority Hub for the person and for the app, and Delete. */
@Suppress("FunctionNaming")
@Composable
internal fun SableHubRowActions(
    conversation: HubConversation,
    ui: HubConversationsUi,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    if (ui.state.unreadCount(conversation) == 0 && SableHubKeep.unreadMark(conversation) != null) {
        PanelAction("Mark unread", iconName = "mail") {
            ConversationHub.markUnread(context, conversation)
            onClose()
        }
    }
    val person = SableHubOptions.isPriorityPerson(conversation)
    PanelAction(if (person) "Remove from Priority Hub" else "Add ${conversation.name} to Priority Hub") {
        SableHubOptions.togglePerson(context, conversation)
        onClose()
    }
    val app = conversation.latestNotification
    val appIn = app.packageName in SableHubOptions.priorityApps
    PanelAction(if (appIn) "Remove all of ${app.appName} from Priority Hub" else "Add all of ${app.appName} to Priority Hub") {
        SableHubOptions.toggleApp(context, app.packageName)
        onClose()
    }
    PanelAction("Delete") {
        ConversationHub.delete(context, conversation)
        onClose()
    }
}

/** Hub settings rows SableOS adds: what the Hub keeps, how it lists chats, and the account apps. */
@Suppress("FunctionNaming")
@Composable
internal fun SableHubSettingsSection() {
    val context = LocalContext.current
    remember(context) { SableHubOptions.init(context) }
    var keep by remember { mutableStateOf(SableHubOptions.keep) }
    var perMessage by remember { mutableStateOf(SableHubOptions.perMessage) }
    var system by remember { mutableStateOf(SableHubOptions.system) }
    Text("Accounts and messages", style = MaterialTheme.typography.titleMedium)
    io.github.aimindseye.tenseven.Card(
        "Email accounts",
        "Gmail, Outlook, Yahoo, iCloud or any IMAP account, in Sable Mail. Its inboxes show in the Hub.",
        false,
    ) { openSableMail(context) }
    io.github.aimindseye.tenseven.Card(
        "Calendar accounts",
        "Google, iCloud, Nextcloud, Fastmail or any CalDAV server, in DAVx⁵",
        false,
    ) { openCalendarAccounts(context) }
    SwitchRow("Keep messages", "Until you delete them, on this phone only. Off forgets what the Hub kept.", keep) {
        keep = it
        SableHubOptions.set(context, SableHubOptions.KEY_KEEP, it)
    }
    SwitchRow(
        "Every message on its own",
        "Each message in a chat is its own item, as on BBM, instead of one row per chat",
        perMessage,
    ) {
        perMessage = it
        SableHubOptions.set(context, SableHubOptions.KEY_PER_MESSAGE, it)
    }
    SwitchRow("System notifications", "Also Android's own notices, such as USB, setup and downloads", system) {
        system = it
        SableHubOptions.set(context, SableHubOptions.KEY_SYSTEM, it)
    }
}

private fun openHubSettings(context: Context) {
    runCatching { context.startActivity(Intent(context, HubSettingsActivity::class.java)) }
}

/** Sable Mail opens on adding an account when it has none, and on the inbox otherwise. */
internal fun openSableMail(context: Context) {
    launch(context, SABLE_MAIL)
}

private fun openCalendarAccounts(context: Context) {
    if (!launch(context, DAVX5)) {
        runCatching {
            context.startActivity(Intent(android.provider.Settings.ACTION_SYNC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

private fun launch(
    context: Context,
    packageName: String,
): Boolean {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
    return runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}
