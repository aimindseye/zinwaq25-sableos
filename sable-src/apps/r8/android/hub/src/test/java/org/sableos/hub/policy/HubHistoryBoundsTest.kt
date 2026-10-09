package org.sableos.hub.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.hub.ConnectedAppKey
import org.sableos.hub.ConnectedAppPolicy
import org.sableos.hub.ConnectedAppPolicyCodec
import org.sableos.hub.ConnectedNotificationRecord
import org.sableos.hub.HistoryRetention

/** HUB_HISTORY_BOUNDED_DERIVED=PASS. */
class HubHistoryBoundsTest {
    private val day = 24L * 60L * 60L * 1000L
    private val now = 100L * day
    private val chat = ConnectedAppKey("org.example.chat", 0L)
    private val work = ConnectedAppKey("org.example.chat", 10L)

    private fun record(
        key: ConnectedAppKey,
        index: Int,
        timestamp: Long,
    ) = ConnectedNotificationRecord(
        id = "${key.userSerial}-$index",
        key = key,
        notificationKey = "n$index",
        conversationId = "c",
        conversationTitle = "Alex",
        sourceLabel = "Chat",
        senderLabel = "Alex",
        body = "hi $index",
        timestampMillis = timestamp,
        incoming = true,
    )

    private fun included(
        key: ConnectedAppKey,
        retention: HistoryRetention = HistoryRetention.ThirtyDays,
    ) = ConnectedAppPolicy(key = key, includeInMessages = true, retention = retention)

    @Test
    fun excludedSourcesKeepNothing() {
        val records = listOf(record(chat, 0, now))
        assertTrue(HubHistoryBounds.retain(records, mapOf(chat to ConnectedAppPolicy(chat)), now).isEmpty())
        assertTrue(HubHistoryBounds.retain(records, emptyMap(), now).isEmpty())
    }

    @Test
    fun retentionIsAlwaysTimeBounded() {
        HistoryRetention.entries.forEach { retention ->
            assertTrue(retention.days in 1..HubHistoryBounds.MAX_RETENTION_DAYS)
        }
        val old = record(chat, 0, now - 31L * day)
        val fresh = record(chat, 1, now - 2L * day)
        val kept = HubHistoryBounds.retain(listOf(old, fresh), mapOf(chat to included(chat)), now)
        assertEquals(listOf(fresh), kept)
        val oneDay = HubHistoryBounds.retain(listOf(fresh), mapOf(chat to included(chat, HistoryRetention.OneDay)), now)
        assertTrue(oneDay.isEmpty())
    }

    @Test
    fun legacyUntilDeletedDecodesToTheLongestBoundedOption() {
        val legacy = "2|0|b3JnLmV4YW1wbGUuY2hhdA|1|0|0|UntilDeleted|0"
        assertEquals(HistoryRetention.ThirtyDays, ConnectedAppPolicyCodec.decode(legacy)?.retention)
    }

    @Test
    fun perSourceAndTotalCountsAreCapped() {
        val many = (0 until HubHistoryBounds.MAX_RECORDS_PER_SOURCE + 50).map { record(chat, it, now - it * 1000L) }
        val kept = HubHistoryBounds.retain(many, mapOf(chat to included(chat)), now)
        assertEquals(HubHistoryBounds.MAX_RECORDS_PER_SOURCE, kept.size)
        assertEquals(now, kept.first().timestampMillis)

        val sources = (0 until 6).map { ConnectedAppKey("org.example.app$it", 0L) }
        val flood =
            sources.flatMap { key ->
                (0 until HubHistoryBounds.MAX_RECORDS_PER_SOURCE).map { record(key, it, now - it * 1000L) }
            }
        val total = HubHistoryBounds.retain(flood, sources.associateWith { included(it) }, now)
        assertEquals(HubHistoryBounds.MAX_RECORDS_TOTAL, total.size)
    }

    @Test
    fun removedProfilesAndFutureTimestampsAreDropped() {
        val records = listOf(record(chat, 0, now), record(work, 1, now), record(chat, 2, now + 10L * day))
        val kept =
            HubHistoryBounds.retain(
                records,
                mapOf(chat to included(chat), work to included(work)),
                now,
                liveUserSerials = setOf(0L),
            )
        assertEquals(listOf(record(chat, 0, now)), kept)
    }
}
