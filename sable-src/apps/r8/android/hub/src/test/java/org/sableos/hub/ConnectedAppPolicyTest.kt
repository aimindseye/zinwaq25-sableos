package org.sableos.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectedAppPolicyTest {
    @Test
    fun policyCodecRoundTripsPackageProfileAndOptions() {
        val policy =
            ConnectedAppPolicy(
                key =
                    ConnectedAppKey(
                        packageName = "org.example.messaging",
                        userSerial = 12L,
                    ),
                includeInMessages = true,
                allowQuickReply = true,
                hideFromLauncher = true,
                retention = HistoryRetention.SevenDays,
            )

        assertEquals(
            policy,
            ConnectedAppPolicyCodec.decode(
                ConnectedAppPolicyCodec.encode(policy),
            ),
        )
    }

    @Test
    fun disablingAppClearsReplyAndLauncherFlags() {
        val normalized =
            ConnectedAppPolicy(
                key = ConnectedAppKey("org.example.messaging", 0L),
                includeInMessages = false,
                allowQuickReply = true,
                hideFromLauncher = true,
            ).normalized()

        assertFalse(normalized.allowQuickReply)
        assertFalse(normalized.hideFromLauncher)
    }

    @Test(expected = IllegalArgumentException::class)
    fun notificationRecordRejectsBlankStableIdentity() {
        ConnectedNotificationRecord(
            id = "",
            key = ConnectedAppKey("org.example.messaging", 0L),
            notificationKey = "notification",
            conversationId = "conversation",
            conversationTitle = "Alice",
            sourceLabel = "Example",
            senderLabel = null,
            body = null,
            timestampMillis = 1_000L,
            incoming = true,
        )
    }

    @Test
    fun retentionIsBoundedUnlessUserChoosesUntilDeleted() {
        val record =
            ConnectedNotificationRecord(
                id = "record",
                key = ConnectedAppKey("org.example.messaging", 0L),
                notificationKey = "notification",
                conversationId = "conversation",
                conversationTitle = "Alice",
                sourceLabel = "Example",
                senderLabel = "Alice",
                body = "hello",
                timestampMillis = 1_000L,
                incoming = true,
            )
        val afterEightDays =
            1_000L + 8L * 24L * 60L * 60L * 1_000L

        assertFalse(
            ConnectedNotificationRetention.shouldKeep(
                record,
                HistoryRetention.SevenDays,
                afterEightDays,
            ),
        )
        assertTrue(
            ConnectedNotificationRetention.shouldKeep(
                record,
                HistoryRetention.UntilDeleted,
                afterEightDays,
            ),
        )
    }
}
