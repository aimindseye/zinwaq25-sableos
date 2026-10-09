package org.sableos.hub.notifications

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.sableos.hub.ConnectedAppKey

class ConnectedNotificationActivityRegistryTest {
    @Before
    fun setUp() {
        ConnectedNotificationActivityRegistry.clear()
    }

    @After
    fun tearDown() {
        ConnectedNotificationActivityRegistry.clear()
    }

    @Test
    fun activeConversationCountDeduplicatesNotificationChildren() {
        val source =
            ConnectedAppKey(
                packageName = "org.example.messaging",
                userSerial = 0L,
            )

        ConnectedNotificationActivityRegistry.upsert(
            notificationKey = "child-1",
            sourceKey = source,
            conversationId = "alice",
        )
        ConnectedNotificationActivityRegistry.upsert(
            notificationKey = "summary-1",
            sourceKey = source,
            conversationId = "alice",
        )
        ConnectedNotificationActivityRegistry.upsert(
            notificationKey = "child-2",
            sourceKey = source,
            conversationId = "bob",
        )

        assertEquals(
            2,
            ConnectedNotificationActivityRegistry.activeConversationCount(),
        )

        ConnectedNotificationActivityRegistry.remove("child-2")

        assertEquals(
            1,
            ConnectedNotificationActivityRegistry.activeConversationCount(),
        )
    }
}
