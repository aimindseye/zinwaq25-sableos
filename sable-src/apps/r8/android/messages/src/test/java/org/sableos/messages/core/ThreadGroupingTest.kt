package org.sableos.messages.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadGroupingTest {
    private val addresses = mapOf(1L to "+15550101010", 2L to "+15550202020", 3L to "+15550303030", 4L to "611")
    private val names = mapOf("+15550101010" to "Maya Patel", "+15550202020" to "Alex Kim")

    private fun row(
        id: Long,
        date: Long,
        recipients: List<Long>,
        count: Int = 1,
        read: Boolean = true,
    ) = ThreadRow(id, date, "snippet $id", recipients, count, read, hasError = false)

    @Test
    fun summariesAreNewestFirstWithNamesAndUnreadCounts() {
        val rows =
            listOf(row(10, 100, listOf(4)), row(11, 300, listOf(1)), row(12, 200, listOf(1, 2, 3, 4), read = false))
        val summaries = ThreadGrouping.summaries(rows, addresses, mapOf(11L to 2)) { names[it] }
        assertEquals(listOf(11L, 12L, 10L), summaries.map { it.threadId })
        assertEquals("Maya Patel", summaries[0].title)
        assertEquals(2, summaries[0].unreadCount)
        assertEquals("Maya, Alex, +15550303030 +1", summaries[1].title)
        // No count from the provider: an unread thread counts as one.
        assertEquals(1, summaries[1].unreadCount)
        assertEquals("611", summaries[2].title)
        assertEquals(0, summaries[2].unreadCount)
    }

    @Test
    fun emptyThreadsAreHidden() {
        val summaries =
            ThreadGrouping.summaries(
                listOf(row(1, 1, listOf(1), count = 0)),
                addresses,
                emptyMap(),
            ) { null }
        assertEquals(emptyList<ThreadSummary>(), summaries)
    }

    @Test
    fun unknownRecipient() {
        assertEquals("Unknown sender", ThreadGrouping.title(emptyList()) { null })
    }

    private fun message(
        id: Long,
        kind: MessageKind,
        date: Long,
    ) = ConversationMessage(id, kind, 1, "+1", "m$id", date, Direction.Incoming, DeliveryState.Received)

    @Test
    fun timelineMergesSmsAndMmsOldestFirst() {
        val timeline =
            ThreadGrouping.timeline(
                listOf(
                    message(5, MessageKind.Sms, 300),
                    message(5, MessageKind.Mms, 100),
                    message(1, MessageKind.Sms, 200),
                    message(1, MessageKind.Sms, 200),
                    message(2, MessageKind.Sms, 100),
                ),
            )
        assertEquals(listOf("Sms:2", "Mms:5", "Sms:1", "Sms:5"), timeline.map { it.key })
    }

    @Test
    fun filterMatchesNameNumberAndSnippet() {
        val summaries =
            ThreadGrouping.summaries(
                listOf(row(11, 3, listOf(1)), row(10, 1, listOf(4))),
                addresses,
                emptyMap(),
            ) {
                names[it]
            }
        assertEquals(listOf(11L), ThreadGrouping.filter(summaries, "maya").map { it.threadId })
        assertEquals(listOf(11L), ThreadGrouping.filter(summaries, "010 1010").map { it.threadId })
        assertEquals(listOf(10L), ThreadGrouping.filter(summaries, "snippet 10").map { it.threadId })
        assertEquals(2, ThreadGrouping.filter(summaries, "  ").size)
    }

    @Test
    fun oneToOneMmsJoinsTheSmsThread() {
        assertEquals(
            listOf("+15550101010"),
            MmsRecipients.threadRecipients("+15550101010", listOf("+15559999999"), emptyList(), emptyList()),
        )
        assertEquals(
            listOf("+15550101010"),
            MmsRecipients.threadRecipients("+15550101010", emptyList(), emptyList(), emptyList()),
        )
    }

    @Test
    fun groupMmsDropsOwnNumber() {
        val recipients =
            MmsRecipients.threadRecipients(
                from = "+15550101010",
                to = listOf("+1 555 999 9999", "+15550202020"),
                cc = listOf("+15550101010"),
                selfNumbers = listOf("5559999999"),
            )
        assertEquals(listOf("+15550101010", "+15550202020"), recipients)
    }
}
