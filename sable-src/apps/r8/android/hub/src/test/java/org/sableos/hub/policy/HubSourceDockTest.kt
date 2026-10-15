package org.sableos.hub.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.hub.HubCall
import org.sableos.hub.HubCallKind
import org.sableos.hub.HubConversation
import org.sableos.hub.HubConversationSource

class HubSourceDockTest {
    private fun sms(
        id: Long,
        unread: Int,
    ) = HubConversation(
        threadId = id,
        address = "+1555010010$id",
        displayName = "Person $id",
        lastBody = "hi",
        lastDateMillis = id,
        unreadCount = unread,
    )

    private fun app(
        id: Long,
        packageName: String,
        label: String,
        unread: Int,
        serial: Long = 0L,
    ) = HubConversation(
        threadId = -id,
        address = "chat-$id",
        displayName = "Chat $id",
        lastBody = "hi",
        lastDateMillis = id,
        unreadCount = unread,
        source = HubConversationSource.ConnectedApp,
        sourcePackage = packageName,
        sourceUserSerial = serial,
        sourceLabel = label,
    )

    private val missed = HubCall(1L, "+15550100200", "Alex", 5L, HubCallKind.Missed)
    private val outgoing = HubCall(2L, "+15550100201", "Sam", 4L, HubCallKind.Outgoing)

    @Test
    fun countsMatchAttentionCountsPerSource() {
        val conversations =
            listOf(
                sms(1, unread = 2),
                sms(2, unread = 0),
                sms(3, unread = 1),
                app(4, "com.whatsapp", "WhatsApp", unread = 3),
                app(5, "com.whatsapp", "WhatsApp", unread = 0),
                app(6, "com.google.android.gm", "Gmail", unread = 1),
                app(7, "org.thoughtcrime.securesms", "Signal", unread = 0),
            )
        val entries = HubSourceDock.entries(conversations, listOf(missed, outgoing))
        val counts = entries.associate { it.label to it.count }
        // Unread conversations (not messages), as HubInbox.attentionCount counts them.
        assertEquals(2, counts["SMS"])
        assertEquals(1, counts["WhatsApp"])
        assertEquals(1, counts["Gmail"])
        assertEquals(0, counts["Signal"])
        assertEquals(1, counts[HubSourceDock.CALLS_LABEL])
        assertEquals(
            HubInbox.attentionCount(conversations, listOf(missed, outgoing), HubInboxFilter.All),
            entries.sumOf { it.count },
        )
        assertTrue(entries.single { it.label == "Gmail" }.mail)
        assertFalse(entries.single { it.label == "WhatsApp" }.mail)
    }

    @Test
    fun sourcesWithCountsComeFirstByCountThenName() {
        val conversations =
            listOf(
                app(1, "org.thoughtcrime.securesms", "Signal", unread = 0),
                app(2, "com.whatsapp", "WhatsApp", unread = 1),
                app(3, "com.google.android.gm", "Gmail", unread = 1),
                app(4, "com.google.android.gm", "Gmail", unread = 1),
                app(5, "com.Slack", "slack", unread = 1),
            )
        val labels = HubSourceDock.entries(conversations, emptyList()).map { it.label }
        assertEquals(listOf("Gmail", "slack", "WhatsApp", "Signal"), labels)
    }

    @Test
    fun zeroCountSourcesShowOnlyWhenExpandedOrSelected() {
        val entries =
            HubSourceDock.entries(
                listOf(app(1, "a.pkg", "Alpha", unread = 1), app(2, "b.pkg", "Beta", unread = 0)),
                listOf(outgoing),
            )
        assertEquals(listOf("Alpha"), HubSourceDock.visible(entries, expanded = false).map { it.label })
        assertEquals(3, HubSourceDock.visible(entries, expanded = true).size)
        val beta = HubDockSource.App("b.pkg", 0L)
        assertEquals(
            listOf("Alpha", "Beta"),
            HubSourceDock.visible(entries, expanded = false, selected = beta).map { it.label },
        )
    }

    @Test
    fun noRowsMeansNoEntries() {
        assertTrue(HubSourceDock.entries(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun sameAppInTwoProfilesIsTwoEntries() {
        val entries =
            HubSourceDock.entries(
                listOf(
                    app(1, "com.whatsapp", "WhatsApp", unread = 1),
                    app(2, "com.whatsapp", "WhatsApp", unread = 1, serial = 10L).copy(profileBadge = "Work"),
                ),
                emptyList(),
            )
        assertEquals(setOf("WhatsApp", "WhatsApp · Work"), entries.map { it.label }.toSet())
    }

    @Test
    fun genericProfileRowsShareOneEntryAndHideTheApp() {
        val hidden =
            listOf(
                app(1, "com.whatsapp", HubRedaction.WORK_PROFILE_LABEL, unread = 1, serial = 10L),
                app(2, "com.slack", HubRedaction.WORK_PROFILE_LABEL, unread = 1, serial = 10L),
            )
        val entry = HubSourceDock.entries(hidden, emptyList()).single()
        assertEquals(HubDockSource.Profile(10L), entry.source)
        assertEquals(HubRedaction.WORK_PROFILE_LABEL, entry.label)
        assertEquals(2, entry.count)
    }

    @Test
    fun narrowKeepsOnlyTheSourceAndToggleClears() {
        val conversations = listOf(sms(1, 0), app(2, "com.whatsapp", "WhatsApp", 0))
        val items = HubInbox.items(conversations, listOf(missed), HubInboxFilter.All)
        assertEquals(items, HubSourceDock.narrow(items, null))
        assertEquals(listOf("call:1"), HubSourceDock.narrow(items, HubDockSource.Calls).map { it.key })
        assertEquals(1, HubSourceDock.narrow(items, HubDockSource.Sms).size)
        assertEquals(1, HubSourceDock.narrow(items, HubDockSource.App("com.whatsapp", 0L)).size)

        assertEquals(HubDockSource.Sms, HubSourceDock.toggle(null, HubDockSource.Sms))
        assertNull(HubSourceDock.toggle(HubDockSource.Sms, HubDockSource.Sms))
        assertEquals(HubDockSource.Calls, HubSourceDock.toggle(HubDockSource.Sms, HubDockSource.Calls))
    }

    @Test
    fun dockShortcutIsNotAFilterLetter() {
        assertNull(HubInboxFilter.forShortcut(HubSourceDock.SHORTCUT))
    }
}
