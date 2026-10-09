package org.sableos.hub

import org.junit.Assert.assertEquals
import org.junit.Test

class HubModelsTest {
    @Test
    fun reducerKeepsNewestMessageAndCountsUnreadIncoming() {
        val messages =
            listOf(
                HubMessage(
                    id = 1,
                    threadId = 10,
                    address = "+15551234567",
                    body = "older",
                    dateMillis = 100,
                    incoming = true,
                    read = false,
                ),
                HubMessage(
                    id = 2,
                    threadId = 10,
                    address = "+15551234567",
                    body = "newest",
                    dateMillis = 300,
                    incoming = false,
                    read = true,
                ),
                HubMessage(
                    id = 3,
                    threadId = 11,
                    address = "+15557654321",
                    body = "middle",
                    dateMillis = 200,
                    incoming = true,
                    read = false,
                ),
            )

        val reduced =
            HubConversationReducer.reduce(messages) { address ->
                if (address.endsWith("4567")) "Ada" else "Grace"
            }

        assertEquals(2, reduced.size)
        assertEquals("Ada", reduced.first().displayName)
        assertEquals("newest", reduced.first().lastBody)
        assertEquals(1, reduced.first().unreadCount)
        assertEquals("Grace", reduced[1].displayName)
    }

    @Test
    fun connectedReducerUsesNewestActiveReplyHandleEvenWhenLatestRecordCannotReply() {
        val key =
            ConnectedAppKey(
                packageName = "org.example.messaging",
                userSerial = 10L,
            )
        val records =
            listOf(
                ConnectedNotificationRecord(
                    id = "reply-capable",
                    key = key,
                    notificationKey = "child-with-reply",
                    conversationId = "alice",
                    conversationTitle = "Alice",
                    sourceLabel = "Example",
                    senderLabel = "Alice",
                    body = "hello",
                    timestampMillis = 100L,
                    incoming = true,
                ),
                ConnectedNotificationRecord(
                    id = "latest-summary",
                    key = key,
                    notificationKey = "summary-without-reply",
                    conversationId = "alice",
                    conversationTitle = "Alice",
                    sourceLabel = "Example",
                    senderLabel = null,
                    body = "2 new messages",
                    timestampMillis = 200L,
                    incoming = true,
                ),
            )

        val reduced =
            HubConnectedConversationReducer.reduce(records) { notificationKey ->
                notificationKey == "child-with-reply"
            }

        val conversation = reduced.conversations.single()
        assertEquals("2 new messages", conversation.lastBody)
        assertEquals(200L, conversation.lastDateMillis)
        assertEquals("child-with-reply", conversation.sourceNotificationKey)
        assertEquals(true, conversation.canQuickReply)
    }

    @Test
    fun connectedReducerMarksLocalReplyEchoAsHandoffOnly() {
        val key =
            ConnectedAppKey(
                packageName = "org.example.messaging",
                userSerial = 10L,
            )
        val record =
            ConnectedNotificationRecord(
                id = CONNECTED_LOCAL_REPLY_PREFIX + "notification-1:200:1",
                key = key,
                notificationKey = "notification-1",
                conversationId = "alice",
                conversationTitle = "Alice",
                sourceLabel = "Example",
                senderLabel = null,
                body = "reply",
                timestampMillis = 200L,
                incoming = false,
            )

        val reduced =
            HubConnectedConversationReducer.reduce(listOf(record)) {
                true
            }

        val message = reduced.messages.single()
        assertEquals(false, message.incoming)
        assertEquals(true, message.handoffOnly)
        assertEquals("reply", message.body)
    }

    @Test
    fun connectedReducerCollapsesDuplicateRowsAcrossNotificationKeys() {
        val key =
            ConnectedAppKey(
                packageName = "org.example.messaging",
                userSerial = 10L,
            )
        val records =
            listOf(
                ConnectedNotificationRecord(
                    id = "child:100",
                    key = key,
                    notificationKey = "child",
                    conversationId = "alice",
                    conversationTitle = "Alice",
                    sourceLabel = "Example",
                    senderLabel = "Alice",
                    body = "hello",
                    timestampMillis = 100L,
                    incoming = true,
                ),
                ConnectedNotificationRecord(
                    id = "replacement:100",
                    key = key,
                    notificationKey = "replacement",
                    conversationId = "alice",
                    conversationTitle = "Alice",
                    sourceLabel = "Example",
                    senderLabel = null,
                    body = "hello",
                    timestampMillis = 100L,
                    incoming = true,
                ),
            )

        val reduced = HubConnectedConversationReducer.reduce(records)

        assertEquals(1, reduced.messages.size)
        assertEquals("hello", reduced.messages.single().body)
        assertEquals("Alice", reduced.messages.single().senderLabel)
    }

    @Test
    fun connectedReducerCollapsesSourceEchoOfLocalReply() {
        val key =
            ConnectedAppKey(
                packageName = "org.example.messaging",
                userSerial = 10L,
            )
        val records =
            listOf(
                ConnectedNotificationRecord(
                    id = CONNECTED_LOCAL_REPLY_PREFIX + "notification-1:1000:1",
                    key = key,
                    notificationKey = "notification-1",
                    conversationId = "alice",
                    conversationTitle = "Alice",
                    sourceLabel = "Example",
                    senderLabel = null,
                    body = "reply",
                    timestampMillis = 1_000L,
                    incoming = false,
                ),
                ConnectedNotificationRecord(
                    id = "provider-echo",
                    key = key,
                    notificationKey = "notification-2",
                    conversationId = "alice",
                    conversationTitle = "You",
                    sourceLabel = "Example",
                    senderLabel = null,
                    body = "reply",
                    timestampMillis = 1_010L,
                    incoming = false,
                ),
            )

        val reduced = HubConnectedConversationReducer.reduce(records)

        assertEquals(1, reduced.messages.size)
        assertEquals(true, reduced.messages.single().handoffOnly)
        assertEquals("reply", reduced.messages.single().body)
    }

    @Test
    fun connectedReducerKeepsIncomingIdentityWhenLatestMessageIsOutgoing() {
        val key =
            ConnectedAppKey(
                packageName = "org.example.messaging",
                userSerial = 10L,
            )
        val records =
            listOf(
                ConnectedNotificationRecord(
                    id = "incoming",
                    key = key,
                    notificationKey = "notification-1",
                    conversationId = "alice",
                    conversationTitle = "Alice",
                    sourceLabel = "Example",
                    senderLabel = "Alice",
                    body = "hello",
                    timestampMillis = 100L,
                    incoming = true,
                ),
                ConnectedNotificationRecord(
                    id = "outgoing",
                    key = key,
                    notificationKey = "notification-2",
                    conversationId = "alice",
                    conversationTitle = "You",
                    sourceLabel = "Example",
                    senderLabel = null,
                    body = "reply",
                    timestampMillis = 200L,
                    incoming = false,
                ),
            )

        val reduced = HubConnectedConversationReducer.reduce(records)

        val conversation = reduced.conversations.single()
        assertEquals("Alice", conversation.displayName)
        assertEquals("reply", conversation.lastBody)
        assertEquals(200L, conversation.lastDateMillis)
    }

    @Test
    fun connectedReducerKeepsSourceBoundaryAndReplyCapability() {
        val key =
            ConnectedAppKey(
                packageName = "org.example.messaging",
                userSerial = 10L,
            )
        val records =
            listOf(
                ConnectedNotificationRecord(
                    id = "record-1",
                    key = key,
                    notificationKey = "notification-1",
                    conversationId = "alice",
                    conversationTitle = "Alice",
                    sourceLabel = "Example",
                    senderLabel = "Alice",
                    body = "hello",
                    timestampMillis = 100L,
                    incoming = true,
                ),
                ConnectedNotificationRecord(
                    id = "record-2",
                    key = key,
                    notificationKey = "notification-2",
                    conversationId = "alice",
                    conversationTitle = "Alice",
                    sourceLabel = "Example",
                    senderLabel = null,
                    body = "reply",
                    timestampMillis = 200L,
                    incoming = false,
                ),
            )

        val reduced =
            HubConnectedConversationReducer.reduce(records) { notificationKey ->
                notificationKey == "notification-2"
            }

        assertEquals(1, reduced.conversations.size)
        assertEquals(2, reduced.messages.size)

        val conversation = reduced.conversations.single()
        assertEquals(HubConversationSource.ConnectedApp, conversation.source)
        assertEquals("org.example.messaging", conversation.sourcePackage)
        assertEquals(10L, conversation.sourceUserSerial)
        assertEquals("notification-2", conversation.sourceNotificationKey)
        assertEquals("reply", conversation.lastBody)
        assertEquals(200L, conversation.lastDateMillis)
        assertEquals(true, conversation.canQuickReply)
        assertEquals(0, conversation.unreadCount)
        assertEquals(true, conversation.threadId < 0L)

        assertEquals(listOf("reply", "hello"), reduced.messages.map(HubMessage::body))
        assertEquals(
            listOf(200L, 100L),
            reduced.messages.map(HubMessage::dateMillis),
        )
        assertEquals(
            listOf(conversation.threadId, conversation.threadId),
            reduced.messages.map(HubMessage::threadId),
        )
    }
}
