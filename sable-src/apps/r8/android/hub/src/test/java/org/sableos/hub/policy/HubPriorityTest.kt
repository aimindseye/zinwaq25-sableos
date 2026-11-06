package org.sableos.hub.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.hub.HubConversation
import org.sableos.hub.HubConversationSource

/** Priority Hub from the row menu: one sender, or a connected app as a whole. */
class HubPriorityTest {
    private val sms =
        HubConversation(
            threadId = 3L,
            address = "+1 (555) 010-0100",
            displayName = "Maya",
            lastBody = "Hi",
            lastDateMillis = 2_000L,
            unreadCount = 0,
        )
    private val chat =
        HubConversation(
            threadId = -4L,
            address = "chat",
            displayName = " Sam ",
            lastBody = "Lunch?",
            lastDateMillis = 1_000L,
            unreadCount = 0,
            source = HubConversationSource.ConnectedApp,
            sourceLabel = "Signal",
            sourcePackage = "org.thoughtcrime.securesms",
            sourceUserSerial = 0L,
        )

    private fun menu(conversation: HubConversation) = HubPriority.menuActions(conversation).map { it.name }

    @Test
    fun senderKeysUseTheNumberOrTheNameInItsApp() {
        assertEquals("sms:+15550100100", HubPriority.personKey(sms))
        assertEquals("app:org.thoughtcrime.securesms:0:sam", HubPriority.personKey(chat))
        assertNull(HubPriority.personKey(chat.copy(displayName = " ")))
        assertNull(HubPriority.personKey(sms.copy(address = "")))
    }

    @Test
    fun aPrioritySenderOrAppMakesTheRowPriority() {
        val person = HubPriority.apply(sms, setOf("sms:+15550100100"))
        assertTrue(person.hubPriority && person.personPriority && !person.appPriority)
        val app = HubPriority.apply(chat, emptySet(), appPriority = true)
        assertTrue(app.hubPriority && app.appPriority && !app.personPriority)
        assertFalse(HubPriority.apply(chat, setOf("sms:+15550100100")).hubPriority)
        assertTrue(HubInbox.isPriority(person))
    }

    @Test
    fun theMenuAddsOrRemovesTheSenderAndOnlyConnectedAppsAsAWhole() {
        assertEquals(listOf("AddPersonPriority"), menu(HubPriority.apply(sms, emptySet())))
        assertEquals(listOf("AddPersonPriority", "AddAppPriority"), menu(HubPriority.apply(chat, emptySet())))
        val both = HubPriority.apply(chat, setOf("app:org.thoughtcrime.securesms:0:sam"), appPriority = true)
        assertEquals(listOf("RemovePersonPriority", "RemoveAppPriority"), menu(both))
    }

    @Test
    fun aHiddenNameCannotBeAddedAndPrivateModeHidesTheEntries() {
        val redacted = HubPriority.apply(chat, emptySet()).copy(priorityKey = null)
        assertEquals(listOf("AddAppPriority"), menu(redacted))
        val item = HubInboxItem.Conversation(HubPriority.apply(sms, emptySet()))
        val entries = HubMessageMenu.entries(item, HubPrivacyState(privateMode = true)).map { it.label }
        assertTrue(entries.none { it.contains("Priority") })
    }
}
