package org.sableos.hub.notifications

import org.sableos.hub.ConnectedAppKey
import java.util.concurrent.ConcurrentHashMap

internal object ConnectedNotificationActivityRegistry {
    private val activeByNotification =
        ConcurrentHashMap<String, ConnectedConversationKey>()

    fun upsert(
        notificationKey: String,
        sourceKey: ConnectedAppKey,
        conversationId: String,
    ) {
        activeByNotification[notificationKey] =
            ConnectedConversationKey(
                sourceKey = sourceKey,
                conversationId = conversationId,
            )
    }

    fun remove(notificationKey: String) {
        activeByNotification.remove(notificationKey)
    }

    fun clear() {
        activeByNotification.clear()
    }

    /** Whether a notification of this conversation is still posted (Hub's "unread" for connected apps). */
    fun isActive(
        sourceKey: ConnectedAppKey,
        conversationId: String,
    ): Boolean = activeByNotification.containsValue(ConnectedConversationKey(sourceKey, conversationId))

    fun activeConversationCount(): Int =
        activeByNotification
            .values
            .toSet()
            .size

    private data class ConnectedConversationKey(
        val sourceKey: ConnectedAppKey,
        val conversationId: String,
    )
}
