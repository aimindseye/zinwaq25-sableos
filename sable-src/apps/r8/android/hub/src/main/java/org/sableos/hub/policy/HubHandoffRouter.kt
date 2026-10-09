package org.sableos.hub.policy

import org.sableos.hub.ConnectedAppKey
import org.sableos.hub.ConnectedAppPolicy
import org.sableos.hub.HubConversation
import org.sableos.hub.HubConversationSource

sealed interface HubHandoffTarget {
    /** Open this Hub conversation. */
    data class Conversation(
        val threadId: Long,
    ) : HubHandoffTarget

    /** The source is not in Hub (or nothing is cached yet): show its Hub/Attention settings. */
    data class ConnectedAppSettings(
        val key: ConnectedAppKey,
    ) : HubHandoffTarget

    /** Nothing specific to show. */
    data object Home : HubHandoffTarget
}

/**
 * Resolves a shade `H` / Settings handoff. Hub never claims a notification it does not represent:
 * a source that is not included opens its connected-app entry, where the user can choose to
 * include it; Hub does not include it silently.
 */
object HubHandoffRouter {
    fun route(
        key: ConnectedAppKey?,
        notificationKey: String?,
        conversations: List<HubConversation>,
        policies: Map<ConnectedAppKey, ConnectedAppPolicy>,
    ): HubHandoffTarget {
        if (key == null) return HubHandoffTarget.Home
        if (policies[key]?.normalized()?.includeInMessages != true) {
            return HubHandoffTarget.ConnectedAppSettings(key)
        }
        val fromSource =
            conversations.filter { conversation ->
                conversation.source == HubConversationSource.ConnectedApp &&
                    conversation.sourcePackage == key.packageName &&
                    conversation.sourceUserSerial == key.userSerial
            }
        val match =
            fromSource.firstOrNull { notificationKey != null && it.sourceNotificationKey == notificationKey }
                ?: fromSource.maxByOrNull(HubConversation::lastDateMillis)
        return match?.let { HubHandoffTarget.Conversation(it.threadId) }
            ?: HubHandoffTarget.ConnectedAppSettings(key)
    }
}
