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

/** A key pressed on a focused row, as [HubMessageMenu.forKey] reads it. */
enum class HubRowKey { R, U, Z, Delete }

/**
 * The BB10 row menu of a Hub message (Menu, Alt+Enter or a long press; it opens on the right
 * edge): Open and Reply first, then Mark read, Snooze and Dismiss when the row's notification is
 * live, then the source's own actions under their own names, then the routes that leave Hub.
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
        middle += HubMenuEntry.Hub(HubRowAction.Snooze)
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
