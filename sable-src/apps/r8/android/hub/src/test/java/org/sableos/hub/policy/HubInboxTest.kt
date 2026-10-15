package org.sableos.hub.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.hub.HubCall
import org.sableos.hub.HubCallKind
import org.sableos.hub.HubConversation
import org.sableos.hub.HubConversationSource

class HubInboxTest {
    private val sms =
        HubConversation(
            threadId = 7L,
            address = "+15550100100",
            displayName = "Maya",
            lastBody = "Lunch at noon?",
            lastDateMillis = 3_000L,
            unreadCount = 2,
        )
    private val connected =
        HubConversation(
            threadId = -42L,
            address = "team",
            displayName = "Team chat",
            lastBody = "Deploy done",
            lastDateMillis = 2_000L,
            unreadCount = 0,
            source = HubConversationSource.ConnectedApp,
            sourceLabel = "Signal",
        )
    private val missed =
        HubCall(id = 1L, number = "+15550100200", displayName = "Alex", dateMillis = 4_000L, kind = HubCallKind.Missed)
    private val outgoing =
        HubCall(
            id = 2L,
            number = "+15550100100",
            displayName = "Maya",
            dateMillis = 1_000L,
            kind = HubCallKind.Outgoing,
        )
    private val conversations = listOf(sms, connected)
    private val calls = listOf(missed, outgoing)

    @Test
    fun allMergesEverySourceNewestFirst() {
        val keys = HubInbox.items(conversations, calls, HubInboxFilter.All).map(HubInboxItem::key)
        assertEquals(listOf("call:1", "conversation:7:+15550100100", "conversation:-42:team", "call:2"), keys)
    }

    @Test
    fun eachFilterKeepsOnlyItsSource() {
        assertEquals(
            listOf(sms),
            HubInbox.items(conversations, calls, HubInboxFilter.Messages).map {
                (it as HubInboxItem.Conversation).conversation
            },
        )
        assertEquals(
            listOf(connected),
            HubInbox.items(conversations, calls, HubInboxFilter.Notifications).map {
                (it as HubInboxItem.Conversation).conversation
            },
        )
        assertEquals(
            listOf(missed, outgoing),
            HubInbox.items(conversations, calls, HubInboxFilter.Calls).map { (it as HubInboxItem.Call).call },
        )
    }

    @Test
    fun searchMatchesNamesNumbersTextAndSourceLabel() {
        assertEquals(2, HubInbox.items(conversations, calls, HubInboxFilter.All, "maya").size)
        assertEquals(1, HubInbox.items(conversations, calls, HubInboxFilter.All, "  lunch ").size)
        assertEquals(1, HubInbox.items(conversations, calls, HubInboxFilter.All, "signal").size)
        assertEquals(1, HubInbox.items(conversations, calls, HubInboxFilter.Calls, "0200").size)
        assertTrue(HubInbox.items(conversations, calls, HubInboxFilter.Messages, "alex").isEmpty())
    }

    @Test
    fun attentionCountsUnreadThreadsAndMissedCalls() {
        assertEquals(2, HubInbox.attentionCount(conversations, calls, HubInboxFilter.All))
        assertEquals(1, HubInbox.attentionCount(conversations, calls, HubInboxFilter.Messages))
        assertEquals(1, HubInbox.attentionCount(conversations, calls, HubInboxFilter.Calls))
        assertEquals(0, HubInbox.attentionCount(conversations, calls, HubInboxFilter.Notifications))
    }

    @Test
    fun filterShortcutsAreSingleLettersInAnyCase() {
        assertEquals(HubInboxFilter.All, HubInboxFilter.forShortcut('a'))
        assertEquals(HubInboxFilter.Messages, HubInboxFilter.forShortcut('M'))
        assertEquals(HubInboxFilter.Calls, HubInboxFilter.forShortcut('c'))
        assertEquals(HubInboxFilter.Notifications, HubInboxFilter.forShortcut('n'))
        assertNull(HubInboxFilter.forShortcut('x'))
        assertEquals(
            HubInboxFilter.entries.size,
            HubInboxFilter.entries
                .map { it.shortcut }
                .toSet()
                .size,
        )
    }

    @Test
    fun mailFilterKeepsConnectedMailByPackageOrCategory() {
        val gmail =
            connected.copy(threadId = -43L, address = "inbox", sourcePackage = "com.google.android.gm", unreadCount = 1)
        val categorized =
            connected.copy(threadId = -44L, address = "news", sourcePackage = "org.example.mail", category = "email")
        val all = conversations + gmail + categorized
        assertEquals(
            listOf(gmail, categorized),
            HubInbox.items(all, calls, HubInboxFilter.Mail).map { (it as HubInboxItem.Conversation).conversation },
        )
        assertEquals(1, HubInbox.attentionCount(all, calls, HubInboxFilter.Mail))
        // Mail stays a connected-app notification too.
        assertEquals(3, HubInbox.items(all, calls, HubInboxFilter.Notifications).size)
        assertEquals(HubInboxFilter.Mail, HubInboxFilter.forShortcut('e'))
    }

    @Test
    fun callKindsMapFromCallLogTypes() {
        assertEquals(HubCallKind.Incoming, HubCallKind.fromCallLogType(1))
        assertEquals(HubCallKind.Outgoing, HubCallKind.fromCallLogType(2))
        assertEquals(HubCallKind.Missed, HubCallKind.fromCallLogType(3))
        assertEquals(HubCallKind.Voicemail, HubCallKind.fromCallLogType(4))
        assertEquals(HubCallKind.Rejected, HubCallKind.fromCallLogType(5))
        assertEquals(HubCallKind.Blocked, HubCallKind.fromCallLogType(6))
        assertEquals(HubCallKind.Other, HubCallKind.fromCallLogType(99))
        assertTrue(HubCallKind.Missed.needsAttention)
    }

    @Test
    fun callRedactionFollowsHubPrivacyAndFailsClosed() {
        assertEquals(calls, HubCallRedaction.apply(calls, HubContentLevel.Full))
        assertEquals(calls, HubCallRedaction.apply(calls, HubContentLevel.SenderOnly))
        val redacted = HubCallRedaction.apply(calls, HubContentLevel.SourceAndCount)
        assertTrue(redacted.all { it.number.isEmpty() && it.displayName == HubCallRedaction.REDACTED_LABEL })
        assertEquals(calls.map { it.kind }, redacted.map { it.kind })
        assertTrue(HubCallRedaction.apply(calls, HubContentLevel.GenericProfile).isEmpty())
        assertTrue(HubCallRedaction.apply(calls, HubContentLevel.Hidden).isEmpty())
    }

    @Test
    fun threadResolverPrefersIdThenSmsAddress() {
        assertEquals(sms, HubThreadResolver.resolve(conversations, 7L, null))
        assertEquals(
            sms,
            HubThreadResolver.resolve(conversations, HubThreadResolver.NEW_THREAD_ID, "+1 (555) 010-0100"),
        )
        // A missing thread id still finds the SMS thread for its address (thread re-created by the provider).
        assertEquals(sms, HubThreadResolver.resolve(conversations, 99L, "15550100100".let { "+$it" }))
        // Connected-app conversations are never matched by address.
        assertNull(HubThreadResolver.resolve(conversations, HubThreadResolver.NEW_THREAD_ID, "team"))
        assertNull(HubThreadResolver.resolve(conversations, HubThreadResolver.NEW_THREAD_ID, null))
    }
}
