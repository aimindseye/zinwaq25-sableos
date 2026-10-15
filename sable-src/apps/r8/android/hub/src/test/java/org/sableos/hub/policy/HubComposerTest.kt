package org.sableos.hub.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HubComposerTest {
    @Test
    fun enterSendsAndShiftEnterIsANewline() {
        assertEquals(ComposerEnterAction.Send, HubComposerKeys.enterAction(false, false, "hi", canSend = true))
        assertEquals(ComposerEnterAction.Newline, HubComposerKeys.enterAction(true, false, "hi", canSend = true))
        assertEquals(ComposerEnterAction.Newline, HubComposerKeys.enterAction(true, false, "", canSend = false))
    }

    @Test
    fun altEnterIsLeftForOtherHandlers() {
        assertEquals(ComposerEnterAction.PassThrough, HubComposerKeys.enterAction(false, true, "hi", canSend = true))
    }

    @Test
    fun emptyDraftOrNoSendAccessSwallowsEnter() {
        assertEquals(ComposerEnterAction.Swallow, HubComposerKeys.enterAction(false, false, "", canSend = true))
        assertEquals(ComposerEnterAction.Swallow, HubComposerKeys.enterAction(false, false, "  \n", canSend = true))
        assertEquals(ComposerEnterAction.Swallow, HubComposerKeys.enterAction(false, false, "hi", canSend = false))
    }

    @Test
    fun draftsAreKeptPerThreadAndClearedWhenEmpty() {
        var drafts = HubDrafts.update(emptyMap(), HubDrafts.smsKey("+1 555 010 0100"), "see you")
        drafts = HubDrafts.update(drafts, HubDrafts.connectedKey(-4L), "ok")
        assertEquals("see you", drafts[HubDrafts.smsKey("+15550100100")])
        assertEquals("ok", drafts[HubDrafts.connectedKey(-4L)])

        drafts = HubDrafts.update(drafts, HubDrafts.smsKey("+15550100100"), "")
        assertNull(drafts[HubDrafts.smsKey("+15550100100")])
        assertEquals(1, drafts.size)
    }

    @Test
    fun draftsAreBoundedDroppingTheOldestEdit() {
        var drafts = emptyMap<String, String>()
        repeat(HubDrafts.MAX_DRAFTS + 3) { index ->
            drafts = HubDrafts.update(drafts, HubDrafts.connectedKey(index.toLong()), "draft $index")
        }
        assertEquals(HubDrafts.MAX_DRAFTS, drafts.size)
        assertFalse(drafts.containsKey(HubDrafts.connectedKey(0L)))
        // Editing an old draft makes it the newest again.
        drafts = HubDrafts.update(drafts, HubDrafts.connectedKey(3L), "again")
        drafts = HubDrafts.update(drafts, HubDrafts.connectedKey(1000L), "new")
        assertTrue(drafts.containsKey(HubDrafts.connectedKey(3L)))
        assertFalse(drafts.containsKey(HubDrafts.connectedKey(4L)))
    }

    @Test
    fun addressesCompareWithoutFormatting() {
        assertTrue(HubAddress.sameAddress("+1 (555) 010-0100", "+15550100100"))
        assertTrue(HubAddress.sameAddress("BANK", "bank"))
        assertFalse(HubAddress.sameAddress("+15550100100", "+15550100101"))
        assertFalse(HubAddress.sameAddress("", ""))
        assertEquals("1234", HubAddress.normalize("12-34"))
        assertEquals("+44", HubAddress.normalize(" +44 "))
    }

    @Test
    fun launchQueryIsTrimmedAndOptional() {
        assertNull(HubIntents.launchQuery(null))
        assertNull(HubIntents.launchQuery(""))
        assertNull(HubIntents.launchQuery("   \n "))
        assertEquals("maya", HubIntents.launchQuery("  maya "))
        assertEquals("lunch at noon", HubIntents.launchQuery("lunch\nat noon"))
        assertEquals(HubIntents.MAX_QUERY_LENGTH, HubIntents.launchQuery("x".repeat(500))?.length)
        assertEquals("org.sableos.hub.extra.QUERY", HubIntents.EXTRA_QUERY)
    }
}
