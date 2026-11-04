package org.sableos.hub.notifications

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.StatusBarNotification
import org.sableos.hub.policy.HubAppAction
import org.sableos.hub.policy.HubNotificationFacts
import java.util.concurrent.ConcurrentHashMap

/** A source app's own notification action, kept so Hub's row menu can run it. */
internal data class ConnectedSourceAction(
    val index: Int,
    val label: String,
    val pendingIntent: PendingIntent,
    val semanticAction: Int,
)

internal data class ConnectedNotificationActions(
    val clearable: Boolean,
    val actions: List<ConnectedSourceAction>,
)

/**
 * The live notification behind each Hub row: whether it can be dismissed and the source's own
 * actions. Only actions the source put on the notification are kept (never a system-generated
 * contextual one) and never an action that asks for text (Reply covers those).
 */
internal object ConnectedActionRegistry {
    private const val MAX_ACTIONS = 6
    private val entries = ConcurrentHashMap<String, ConnectedNotificationActions>()

    fun register(sbn: StatusBarNotification) {
        val actions =
            sbn.notification.actions
                .orEmpty()
                .asSequence()
                .take(MAX_ACTIONS)
                .mapIndexedNotNull { index, action ->
                    val intent = action.actionIntent ?: return@mapIndexedNotNull null
                    val title = action.title?.toString()?.trim().orEmpty()
                    val asksForText = !action.remoteInputs.isNullOrEmpty()
                    if (title.isEmpty() || action.isContextual || asksForText) {
                        null
                    } else {
                        ConnectedSourceAction(index, title, intent, action.semanticAction)
                    }
                }.toList()
        entries[sbn.key] = ConnectedNotificationActions(sbn.isClearable, actions)
    }

    fun remove(notificationKey: String) {
        entries.remove(notificationKey)
    }

    fun clear() {
        entries.clear()
    }

    fun facts(notificationKey: String): HubNotificationFacts {
        val entry = entries[notificationKey] ?: return HubNotificationFacts()
        val markRead =
            entry.actions.firstOrNull {
                it.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ
            }
        return HubNotificationFacts(
            live = true,
            clearable = entry.clearable,
            markReadIndex = markRead?.index ?: -1,
            appActions = entry.actions.map { HubAppAction(it.index, it.label) },
        )
    }

    fun action(
        notificationKey: String,
        index: Int,
    ): ConnectedSourceAction? = entries[notificationKey]?.actions?.firstOrNull { it.index == index }
}
