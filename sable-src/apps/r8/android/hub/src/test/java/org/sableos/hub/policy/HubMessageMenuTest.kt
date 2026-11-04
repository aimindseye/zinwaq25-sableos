package org.sableos.hub.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.sableos.hub.HubConversation
import org.sableos.hub.HubConversationSource

/** The BB10 row menu: Hub actions, then what the live notification allows, then the hand-offs. */
class HubMessageMenuTest {
    private val gmail =
        HubConversation(
            threadId = -7L,
            address = "demo",
            displayName = "Demo project",
            lastBody = "The latest launcher preview is ready.",
            lastDateMillis = 1_000L,
            unreadCount = 1,
            source = HubConversationSource.ConnectedApp,
            sourceLabel = "Gmail",
            sourcePackage = "com.google.android.gm",
            sourceUserSerial = 0L,
            sourceNotificationKey = "k",
            canQuickReply = true,
        )
    private val sms =
        HubConversation(
            threadId = 3L,
            address = "+15550100100",
            displayName = "Maya",
            lastBody = "Hi",
            lastDateMillis = 2_000L,
            unreadCount = 1,
        )
    private val live =
        HubNotificationFacts(
            live = true,
            clearable = true,
            markReadIndex = 2,
            appActions =
                listOf(
                    HubAppAction(0, "Archive"),
                    HubAppAction(1, " Delete "),
                    HubAppAction(2, "Mark as read"),
                    HubAppAction(3, "archive"),
                    HubAppAction(4, ""),
                ),
        )

    private fun labels(entries: List<HubMenuEntry>) =
        entries.map { (it as? HubMenuEntry.Hub)?.action?.name ?: "app:" + it.label }

    @Test
    fun liveNotificationAddsMarkReadSnoozeDismissAndTheAppsOwnActions() {
        assertEquals(
            listOf(
                "Open",
                "Reply",
                "MarkRead",
                "Snooze",
                "Dismiss",
                "app:Archive",
                "app:Delete",
                "OpenSourceApp",
                "SourceNotifications",
                "PrivacyOptions",
            ),
            labels(HubMessageMenu.entries(HubInboxItem.Conversation(gmail), facts = live)),
        )
    }

    @Test
    fun noMarkReadWithoutTheSourcesOwnActionAndNoDismissForOngoing() {
        val facts = live.copy(clearable = false, markReadIndex = -1, appActions = emptyList())
        assertEquals(
            listOf("Open", "Reply", "Snooze", "OpenSourceApp", "SourceNotifications", "PrivacyOptions"),
            labels(HubMessageMenu.entries(HubInboxItem.Conversation(gmail), facts = facts)),
        )
    }

    @Test
    fun goneNotificationLockedPhoneAndSmsGetNoNotificationCommands() {
        val base = HubRowActions.forConversation(gmail).map { it.name }
        assertEquals(base, labels(HubMessageMenu.entries(HubInboxItem.Conversation(gmail))))
        val locked = HubPrivacyState(deviceLocked = true)
        assertEquals(
            HubRowActions.forConversation(gmail, locked).map { it.name },
            labels(HubMessageMenu.entries(HubInboxItem.Conversation(gmail), locked, live)),
        )
        val smsBase = HubRowActions.forConversation(sms).map { it.name }
        assertEquals(smsBase, labels(HubMessageMenu.entries(HubInboxItem.Conversation(sms), facts = live)))
    }

    @Test
    fun rowKeysRunOnlyWhatTheMenuHas() {
        val entries = HubMessageMenu.entries(HubInboxItem.Conversation(gmail), facts = live)
        assertEquals(HubMenuEntry.Hub(HubRowAction.Reply), HubMessageMenu.forKey(HubRowKey.R, entries))
        assertEquals(HubMenuEntry.Hub(HubRowAction.MarkRead), HubMessageMenu.forKey(HubRowKey.U, entries))
        assertEquals(HubMenuEntry.Hub(HubRowAction.Snooze), HubMessageMenu.forKey(HubRowKey.Z, entries))
        assertEquals(HubMenuEntry.Hub(HubRowAction.Dismiss), HubMessageMenu.forKey(HubRowKey.Delete, entries))
        val smsEntries = HubMessageMenu.entries(HubInboxItem.Conversation(sms), facts = live)
        assertNull(HubMessageMenu.forKey(HubRowKey.Z, smsEntries))
        assertEquals("Del", HubMessageMenu.shortcutLabel(HubRowAction.Dismiss))
    }
}
