package org.sableos.hub.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.hub.HubCall
import org.sableos.hub.HubCallKind
import org.sableos.hub.HubConversation
import org.sableos.hub.HubConversationSource

class HubConversationRollupTest {
    private fun sms(
        id: Long,
        name: String,
        address: String,
    ): HubInboxItem =
        HubInboxItem.Conversation(
            HubConversation(
                threadId = id,
                address = address,
                displayName = name,
                lastBody = "text",
                lastDateMillis = 1_000L - id,
                unreadCount = 0,
            ),
        )

    private fun chat(
        id: Long,
        title: String,
        label: String = "WhatsApp",
    ): HubInboxItem =
        HubInboxItem.Conversation(
            HubConversation(
                threadId = -id,
                address = "chat-$id",
                displayName = title,
                lastBody = "text",
                lastDateMillis = 1_000L - id,
                unreadCount = 0,
                source = HubConversationSource.ConnectedApp,
                sourcePackage = "pkg.$label",
                sourceUserSerial = 0L,
                sourceLabel = label,
            ),
        )

    private fun call(
        id: Long,
        name: String,
    ): HubInboxItem = HubInboxItem.Call(HubCall(id, "+15550100100", name, 1_000L - id, HubCallKind.Missed))

    private fun shape(clusters: List<HubInboxCluster>): List<Int> = clusters.map { it.children.size }

    @Test
    fun adjacentRowsFromOnePersonRollUpUnderTheNewest() {
        val items = listOf(chat(1, "Maya"), chat(2, "Maya", "Signal"), chat(3, "Maya", "Gmail"), chat(4, "Leo"))
        val clusters = HubConversationRollup.clusters(items, enabled = true)
        assertEquals(listOf(2, 0), shape(clusters))
        assertEquals(items[0], clusters[0].parent)
        assertEquals(items[0].key, clusters[0].id)
        assertEquals(listOf("Signal", "Gmail"), clusters[0].childSourceLabels)
    }

    @Test
    fun anotherChatInBetweenBreaksTheRun() {
        val items = listOf(chat(1, "Maya"), chat(2, "Leo"), chat(3, "Maya"))
        assertEquals(listOf(0, 0, 0), shape(HubConversationRollup.clusters(items, enabled = true)))
    }

    @Test
    fun crossSourceSamePersonByNameIgnoringCaseAndWhitespace() {
        val items = listOf(sms(1, "Maya  Lee", "+15550100100"), chat(2, " maya lee"))
        assertEquals(listOf(1), shape(HubConversationRollup.clusters(items, enabled = true)))
    }

    @Test
    fun smsRowsMatchBySameAddressEvenWithDifferentNames() {
        val items = listOf(sms(1, "Maya", "+1 (555) 010-0100"), sms(2, "+15550100100", "+15550100100"))
        assertEquals(listOf(1), shape(HubConversationRollup.clusters(items, enabled = true)))
    }

    @Test
    fun groupChatNeverMergesWithAOneToOneChat() {
        val items = listOf(chat(1, "Maya"), chat(2, "Maya, Leo, Sam"), chat(3, "Maya & Leo"))
        assertEquals(listOf(0, 0, 0), shape(HubConversationRollup.clusters(items, enabled = true)))
    }

    @Test
    fun callsNeverCombine() {
        val items = listOf(call(1, "Maya"), call(2, "Maya"), sms(3, "Maya", "+15550100100"), call(4, "Maya"))
        assertEquals(listOf(0, 0, 0, 0), shape(HubConversationRollup.clusters(items, enabled = true)))
        assertFalse(HubConversationRollup.samePerson(sms(3, "Maya", "+15550100100"), call(4, "Maya")))
    }

    @Test
    fun redactedIdentitiesNeverCombine() {
        // "App and count only": rows show the app label as the name.
        val items = listOf(chat(1, "WhatsApp"), chat(2, "WhatsApp"))
        assertEquals(listOf(0, 0), shape(HubConversationRollup.clusters(items, enabled = true)))
        val number = "+15550100100"
        val smsRedacted = listOf(sms(3, HubRedaction.SMS_LABEL, number), sms(4, HubRedaction.SMS_LABEL, number))
        assertEquals(listOf(0, 0), shape(HubConversationRollup.clusters(smsRedacted, enabled = true)))
        val work = HubRedaction.WORK_PROFILE_LABEL
        val profile = listOf(chat(5, work), chat(6, work, "Slack"))
        assertEquals(listOf(0, 0), shape(HubConversationRollup.clusters(profile, enabled = true)))
    }

    @Test
    fun settingOffIsIdentity() {
        val items = listOf(chat(1, "Maya"), chat(2, "Maya"), call(3, "Maya"), sms(4, "Leo", "+15550100101"))
        val clusters = HubConversationRollup.clusters(items, enabled = false)
        assertEquals(items, clusters.map { it.parent })
        assertTrue(clusters.none { it.rolledUp })
        val rows = HubConversationRollup.rows(clusters, expanded = emptySet())
        assertEquals(items, rows.map { it.item })
        assertTrue(rows.all { it.role == HubInboxRowRole.Single && it.divider == HubInboxDivider.Single })
    }

    @Test
    fun collapsedParentHidesChildrenAndDrawsTheDoubleDivider() {
        val items = listOf(chat(1, "Maya"), chat(2, "Maya"), chat(3, "Leo"))
        val rows = HubConversationRollup.rows(HubConversationRollup.clusters(items, true), expanded = emptySet())
        assertEquals(listOf(items[0], items[2]), rows.map { it.item })
        assertEquals(listOf(HubInboxRowRole.Parent, HubInboxRowRole.Single), rows.map { it.role })
        assertEquals(listOf(HubInboxDivider.Double, HubInboxDivider.Single), rows.map { it.divider })
    }

    @Test
    fun expandedClusterShowsChildrenAndClosesWithTheDoubleDivider() {
        val items = listOf(chat(1, "Maya"), chat(2, "Maya"), chat(3, "Maya"), chat(4, "Leo"))
        val clusters = HubConversationRollup.clusters(items, true)
        val rows = HubConversationRollup.rows(clusters, expanded = setOf(clusters[0].id))
        assertEquals(items, rows.map { it.item })
        assertEquals(
            listOf(HubInboxRowRole.Parent, HubInboxRowRole.Child, HubInboxRowRole.Child, HubInboxRowRole.Single),
            rows.map { it.role },
        )
        assertEquals(
            listOf(HubInboxDivider.Single, HubInboxDivider.Single, HubInboxDivider.Double, HubInboxDivider.Single),
            rows.map { it.divider },
        )
        assertTrue(rows.take(3).all { it.expanded })
    }
}
