package org.sableos.hub.policy

import org.sableos.hub.HubConversationSource

/**
 * What Android exposes for a row's live notification, as the listener captured it. Pure data:
 * the PendingIntents stay in ConnectedActionRegistry.
 *
 * [markReadIndex] is the source's own action with SEMANTIC_ACTION_MARK_AS_READ, or -1. [appActions]
 * are the source's other visible actions (Archive, Delete, Like…) under the source's own titles,
 * never a system-generated contextual action and never the reply action (Reply covers it).
 */
data class HubNotificationFacts(
    val live: Boolean = false,
    val clearable: Boolean = false,
    val markReadIndex: Int = -1,
    val appActions: List<HubAppAction> = emptyList(),
)

/** One of the source app's own notification actions, by its index in Notification.actions. */
data class HubAppAction(
    val index: Int,
    val label: String,
)

/** One line of the row menu: a Hub action, or one of the source app's own actions. */
sealed interface HubMenuEntry {
    val label: String

    /** The single key that runs the entry on a focused row, shown beside it; null for none. */
    val shortcut: String?

    data class Hub(
        val action: HubRowAction,
    ) : HubMenuEntry {
        override val label: String get() = action.label
        override val shortcut: String? get() = HubMessageMenu.shortcutLabel(action)
    }

    data class App(
        val action: HubAppAction,
    ) : HubMenuEntry {
        override val label: String get() = action.label
        override val shortcut: String? get() = null
    }
}

/**
 * Row menu entries the user chooses in Sable Setup or Hub > Connected apps. Both are off unless
 * turned on: [snooze] adds Snooze 1 hour (and its Z key), [remindMe] adds Remind me, which puts a
 * follow-up into Sable Tasks with the row's source and key only, never the message text.
 */
data class HubMenuOptions(
    val snooze: Boolean = false,
    val remindMe: Boolean = false,
)

/** A key pressed on a focused row, as [HubMessageMenu.forKey] reads it. */
enum class HubRowKey { R, U, Z, Delete }

/**
 * The BB10 row menu of a Hub message (Menu, Alt+Enter or a long press; it opens on the right
 * edge): Open and Reply first, then Mark read, Snooze (when turned on) and Dismiss when the row's
 * notification is live, then the source's own actions under their own names, then Priority Hub
 * (the sender, and a connected app as a whole), Mark prior read and Remind me (when turned on), then
 * the routes that leave Hub.
 *
 * Mark read and the app actions run the source's own PendingIntent, so Hub still invents nothing
 * (`INVENT_UNSUPPORTED_ACTIONS=NO`): an SMS cannot be marked read from Hub and gets none of them.
 * Everything that acts on the notification needs the phone unlocked, like Reply.
 */
object HubMessageMenu {
    const val SNOOZE_MILLIS: Long = 60L * 60L * 1000L

    /** At most this many source actions are listed; Android shows at most three. */
    const val MAX_APP_ACTIONS = 3

    fun entries(
        item: HubInboxItem,
        privacy: HubPrivacyState = HubPrivacyState(),
        facts: HubNotificationFacts = HubNotificationFacts(),
        priorToMarkRead: Int = 0,
        options: HubMenuOptions = HubMenuOptions(),
    ): List<HubMenuEntry> {
        val entries = rowEntries(item, privacy, facts, options.snooze)
        if (privacy.deviceLocked) return entries
        val extra = mutableListOf<HubMenuEntry>()
        // Priority Hub: not in private mode, where the row may not show who it is from.
        val conversation = (item as? HubInboxItem.Conversation)?.conversation
        if (conversation != null && !privacy.privateMode) {
            extra += HubPriority.menuActions(conversation).map { HubMenuEntry.Hub(it) }
        }
        if (priorToMarkRead > 0) extra += HubMenuEntry.Hub(HubRowAction.MarkPriorRead)
        if (options.remindMe && canRemind(item)) extra += HubMenuEntry.Hub(HubRowAction.RemindMe)
        // These go after the row's own commands, before the routes that leave Hub.
        val handOff = entries.indexOfFirst { it is HubMenuEntry.Hub && it.action in HAND_OFFS }
        val at = if (handOff >= 0) handOff else entries.size
        return entries.take(at) + extra + entries.drop(at)
    }

    /** Remind me needs an app to come back to: the source app, or the phone's SMS app. */
    fun canRemind(item: HubInboxItem): Boolean {
        val conversation = (item as? HubInboxItem.Conversation)?.conversation ?: return false
        return conversation.source == HubConversationSource.Sms || conversation.sourcePackage != null
    }

    /**
     * Mark prior read (BB10): the live notifications of rows older than [item] whose source offers
     * its own mark-as-read action, by key. Running it sends each source's own action once, like
     * Mark read on each row; it never dismisses or snoozes anything (DESIGN-KF-A).
     */
    fun priorToMarkRead(
        item: HubInboxItem,
        items: List<HubInboxItem>,
        facts: (String) -> HubNotificationFacts,
    ): List<String> =
        items
            .asSequence()
            .filter { it.dateMillis < item.dateMillis }
            .mapNotNull { (it as? HubInboxItem.Conversation)?.conversation }
            .filter { it.source != HubConversationSource.Sms }
            .mapNotNull { it.sourceNotificationKey }
            .distinct()
            .filter { key -> facts(key).let { it.live && it.markReadIndex >= 0 } }
            .toList()

    private val HAND_OFFS =
        setOf(
            HubRowAction.OpenInMessages,
            HubRowAction.OpenSourceApp,
            HubRowAction.SourceNotifications,
            HubRowAction.PrivacyOptions,
        )

    private fun rowEntries(
        item: HubInboxItem,
        privacy: HubPrivacyState,
        facts: HubNotificationFacts,
        snooze: Boolean,
    ): List<HubMenuEntry> {
        val base = HubRowActions.forItem(item, privacy)
        val conversation = (item as? HubInboxItem.Conversation)?.conversation
        val notificationActions =
            conversation != null &&
                conversation.source != HubConversationSource.Sms &&
                conversation.sourceNotificationKey != null &&
                facts.live &&
                !privacy.deviceLocked
        if (!notificationActions) return base.map { HubMenuEntry.Hub(it) }

        val head = base.takeWhile { it == HubRowAction.Open || it == HubRowAction.Reply }
        val tail = base.drop(head.size)
        val middle = mutableListOf<HubMenuEntry>()
        if (facts.markReadIndex >= 0) middle += HubMenuEntry.Hub(HubRowAction.MarkRead)
        if (snooze) middle += HubMenuEntry.Hub(HubRowAction.Snooze)
        if (facts.clearable) middle += HubMenuEntry.Hub(HubRowAction.Dismiss)
        facts.appActions
            .asSequence()
            .filter { it.index != facts.markReadIndex && it.label.isNotBlank() }
            .distinctBy { it.label.trim().lowercase() }
            .take(MAX_APP_ACTIONS)
            .forEach { middle += HubMenuEntry.App(it.copy(label = it.label.trim())) }
        return head.map { HubMenuEntry.Hub(it) } + middle + tail.map { HubMenuEntry.Hub(it) }
    }

    /** The entry a row key runs, when the row's menu has it; R reply, U mark read, Z snooze, Del dismiss. */
    fun forKey(
        key: HubRowKey,
        entries: List<HubMenuEntry>,
    ): HubMenuEntry? {
        val wanted =
            when (key) {
                HubRowKey.R -> HubRowAction.Reply
                HubRowKey.U -> HubRowAction.MarkRead
                HubRowKey.Z -> HubRowAction.Snooze
                HubRowKey.Delete -> HubRowAction.Dismiss
            }
        return entries.firstOrNull { it is HubMenuEntry.Hub && it.action == wanted }
    }

    fun shortcutLabel(action: HubRowAction): String? =
        when (action) {
            HubRowAction.Reply -> "R"
            HubRowAction.MarkRead -> "U"
            HubRowAction.Snooze -> "Z"
            HubRowAction.Dismiss -> "Del"
            else -> null
        }
}
