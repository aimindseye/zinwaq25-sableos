package org.sableos.hub.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** HUB_REMOTEINPUT_PROVIDER_SAFE=PASS, HUB_INVENTS_PROVIDER_ACTIONS=NO. */
class ReplyEligibilityTest {
    private val reply =
        ReplyActionFacts(
            hasPendingIntent = true,
            hasFreeFormRemoteInput = true,
            isContextual = false,
            semanticReply = true,
        )
    private val unlocked = PrivacyContext(deviceLocked = false)

    @Test
    fun onlySourceOwnedFreeFormRemoteInputsQualify() {
        assertTrue(ReplyEligibility.isSourceReplyAction(reply))
        assertFalse(ReplyEligibility.isSourceReplyAction(reply.copy(hasFreeFormRemoteInput = false)))
        assertFalse(ReplyEligibility.isSourceReplyAction(reply.copy(hasPendingIntent = false)))
        assertFalse(ReplyEligibility.isSourceReplyAction(reply.copy(isContextual = true)))
    }

    @Test
    fun semanticReplyIsPreferredAndSmartActionsAreSkipped() {
        val smart = reply.copy(isContextual = true)
        val plain = reply.copy(semanticReply = false)
        assertEquals("plain", ReplyEligibility.chooseReplyAction(listOf(smart to "smart", plain to "plain"))?.second)
        assertEquals("reply", ReplyEligibility.chooseReplyAction(listOf(plain to "plain", reply to "reply"))?.second)
        assertNull(ReplyEligibility.chooseReplyAction(listOf(smart to "smart")))
    }

    @Test
    fun hubNeverOffersReplyWithoutSourceActionOrUserConsent() {
        assertEquals(ReplyAvailability.OpenSourceApp, ReplyEligibility.availability(null, true, unlocked))
        assertEquals(ReplyAvailability.OpenSourceApp, ReplyEligibility.availability(reply, false, unlocked))
        assertEquals(ReplyAvailability.Inline, ReplyEligibility.availability(reply, true, unlocked))
    }

    @Test
    fun lockedDeviceOrProfileRequiresUnlock() {
        assertEquals(
            ReplyAvailability.RequiresUnlock,
            ReplyEligibility.availability(reply, true, PrivacyContext(deviceLocked = true)),
        )
        assertEquals(
            ReplyAvailability.RequiresUnlock,
            ReplyEligibility.availability(
                reply,
                true,
                PrivacyContext(deviceLocked = false, profile = ProfileKind.Work, profileLocked = true),
            ),
        )
    }
}
