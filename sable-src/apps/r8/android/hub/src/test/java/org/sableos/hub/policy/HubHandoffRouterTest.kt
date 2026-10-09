package org.sableos.hub.policy

import org.junit.Assert.assertEquals
import org.junit.Test
import org.sableos.hub.ConnectedAppKey
import org.sableos.hub.ConnectedAppPolicy
import org.sableos.hub.HubConversation
import org.sableos.hub.HubConversationSource

class HubHandoffRouterTest {
    private val chat = ConnectedAppKey("org.example.chat", 0L)

    private fun conversation(
        threadId: Long,
        notificationKey: String,
        date: Long,
    ) = HubConversation(
        threadId = threadId,
        address = "c$threadId",
        displayName = "Alex",
        lastBody = "",
        lastDateMillis = date,
        unreadCount = 0,
        source = HubConversationSource.ConnectedApp,
        sourcePackage = chat.packageName,
        sourceUserSerial = chat.userSerial,
        sourceNotificationKey = notificationKey,
    )

    private val conversations = listOf(conversation(-1L, "a", 10L), conversation(-2L, "b", 20L))

    @Test
    fun excludedSourceOpensItsSettingsInsteadOfBeingIncludedSilently() {
        assertEquals(
            HubHandoffTarget.ConnectedAppSettings(chat),
            HubHandoffRouter.route(chat, "a", conversations, mapOf(chat to ConnectedAppPolicy(chat))),
        )
    }

    @Test
    fun includedSourceOpensTheMatchingConversation() {
        val policies = mapOf(chat to ConnectedAppPolicy(chat, includeInMessages = true))
        assertEquals(HubHandoffTarget.Conversation(-1L), HubHandoffRouter.route(chat, "a", conversations, policies))
        assertEquals(HubHandoffTarget.Conversation(-2L), HubHandoffRouter.route(chat, "zzz", conversations, policies))
        assertEquals(
            HubHandoffTarget.ConnectedAppSettings(chat),
            HubHandoffRouter.route(chat, "a", emptyList(), policies),
        )
        assertEquals(HubHandoffTarget.Home, HubHandoffRouter.route(null, "a", conversations, policies))
    }
}
