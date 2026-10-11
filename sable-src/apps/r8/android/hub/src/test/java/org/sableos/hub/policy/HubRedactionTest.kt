package org.sableos.hub.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.hub.ConnectedAppKey
import org.sableos.hub.HubConversation
import org.sableos.hub.HubConversationSource
import org.sableos.hub.HubMessage

/** WORK_PROFILE_SEPARATION and CROSS_PROFILE_PREVIEW_LEAK=NO at the Hub snapshot boundary. */
class HubRedactionTest {
    private val personal = ConnectedAppKey("org.example.chat", 0L)
    private val work = ConnectedAppKey("org.example.chat", 10L)

    private fun conversation(
        key: ConnectedAppKey,
        threadId: Long,
    ) = HubConversation(
        threadId = threadId,
        address = "c",
        displayName = "Alex",
        lastBody = "secret plan",
        lastDateMillis = 1L,
        unreadCount = 0,
        source = HubConversationSource.ConnectedApp,
        sourcePackage = key.packageName,
        sourceUserSerial = key.userSerial,
        sourceLabel = "Chat",
        canQuickReply = true,
    )

    private fun message(threadId: Long) =
        HubMessage(
            id = threadId * 10,
            threadId = threadId,
            address = "Alex",
            body = "secret plan",
            dateMillis = 1L,
            incoming = true,
            read = true,
            source = HubConversationSource.ConnectedApp,
            senderLabel = "Alex",
        )

    private val sms =
        HubConversation(
            threadId = 5L,
            address = "+1",
            displayName = "Bo",
            lastBody = "sms",
            lastDateMillis = 2L,
            unreadCount = 1,
        )

    @Test
    fun lockedWorkProfileContentNeverReachesUi() {
        val (conversations, messages) =
            HubRedaction.apply(
                listOf(conversation(personal, -1L), conversation(work, -2L), sms),
                listOf(message(-1L), message(-2L)),
            ) { key ->
                if (key == work) {
                    HubSourcePrivacy(HubContentLevel.GenericProfile, HubContentLevel.GenericProfile, "Work")
                } else {
                    HubSourcePrivacy(HubContentLevel.Full, HubContentLevel.Full)
                }
            }
        val workRow = conversations.first { it.threadId == -2L }
        assertEquals(HubRedaction.WORK_PROFILE_LABEL, workRow.displayName)
        assertEquals("", workRow.lastBody)
        assertFalse(workRow.canQuickReply)
        assertEquals("Work", workRow.profileBadge)
        assertTrue(messages.none { it.threadId == -2L })
        assertEquals("secret plan", conversations.first { it.threadId == -1L }.lastBody)
        assertTrue(conversations.contains(sms))
    }

    @Test
    fun previewPreferenceRedactsListButNotOpenedConversation() {
        val (conversations, messages) =
            HubRedaction.apply(listOf(conversation(personal, -1L)), listOf(message(-1L))) {
                HubSourcePrivacy(HubContentLevel.SourceAndCount, HubContentLevel.Full)
            }
        assertEquals("Chat", conversations.single().displayName)
        assertEquals("", conversations.single().lastBody)
        assertEquals("secret plan", messages.single().body)
    }

    @Test
    fun restrictedContentRedactsMessagesToo() {
        val (_, messages) =
            HubRedaction.apply(listOf(conversation(personal, -1L)), listOf(message(-1L))) {
                HubSourcePrivacy(HubContentLevel.SourceAndCount, HubContentLevel.SourceAndCount)
            }
        assertEquals("", messages.single().body)
        assertEquals(null, messages.single().senderLabel)
    }

    @Test
    fun hiddenProfilesDisappearAndOrphanMessagesAreDropped() {
        val (conversations, messages) =
            HubRedaction.apply(listOf(conversation(work, -2L)), listOf(message(-2L), message(-9L))) {
                HubSourcePrivacy(HubContentLevel.Hidden, HubContentLevel.Hidden)
            }
        assertTrue(conversations.isEmpty())
        assertTrue(messages.isEmpty())
    }

    private fun smsMessage() =
        HubMessage(
            id = 50L,
            threadId = 5L,
            address = "+1",
            body = "sms",
            dateMillis = 2L,
            incoming = true,
            read = false,
        )

    @Test
    fun smsIsUnchangedWhenSmsPrivacyIsFull() {
        val smsMessage = smsMessage()
        val (conversations, messages) =
            HubRedaction.apply(listOf(sms), listOf(smsMessage)) {
                HubSourcePrivacy(HubContentLevel.Hidden, HubContentLevel.Hidden)
            }
        assertEquals(listOf(sms), conversations)
        assertEquals(listOf(smsMessage), messages)
    }

    @Test
    fun privateModeRedactsSmsBodiesLikeOtherSources() {
        val smsMessage = smsMessage()
        val privateMode = HubSourcePrivacy(HubContentLevel.SourceAndCount, HubContentLevel.SourceAndCount)
        val (conversations, messages) =
            HubRedaction.apply(
                listOf(sms, conversation(personal, -1L)),
                listOf(smsMessage, message(-1L)),
                smsPrivacy = privateMode,
            ) { privateMode }
        val smsRow = conversations.first { it.threadId == 5L }
        assertEquals(HubRedaction.SMS_LABEL, smsRow.displayName)
        assertEquals("", smsRow.lastBody)
        assertEquals(1, smsRow.unreadCount)
        assertEquals("+1", smsRow.address)
        val smsBody = messages.first { it.threadId == 5L }
        assertEquals("", smsBody.body)
        assertEquals("", smsBody.address)
        assertEquals("", conversations.first { it.threadId == -1L }.lastBody)
        assertTrue(messages.none { it.body.isNotEmpty() })
    }
}
