package org.sableos.hub.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.hub.HubConversation
import org.sableos.hub.HubConversationSource

class HubHomeRowsTest {
    private val sms =
        HubConversation(
            threadId = 3L,
            address = "+15550100100",
            displayName = "Maya",
            lastBody = "Train\narrives 7:18",
            lastDateMillis = 2_000L,
            unreadCount = 2,
        )
    private val gmail =
        HubConversation(
            threadId = -7L,
            address = "demo",
            displayName = "Demo project",
            lastBody = "The latest launcher preview is ready.",
            lastDateMillis = 3_000L,
            unreadCount = 1,
            source = HubConversationSource.ConnectedApp,
            sourcePackage = "com.google.android.gm",
            sourceUserSerial = 0L,
        )
    private val work = gmail.copy(threadId = -8L, sourceUserSerial = 10L, lastDateMillis = 4_000L)

    @Test
    fun newestPersonalRowsFirstWithOneLinePreviews() {
        val rows = HubHomeRows.rows(listOf(sms, gmail, work), HubPrivacyState(), personalSerial = 0L)
        assertEquals(listOf(-7L, 3L), rows.map { it.threadId })
        assertEquals("Train arrives 7:18", rows[1].preview)
        assertTrue(rows[1].sms)
        assertEquals("com.google.android.gm", rows[0].packageName)
    }

    @Test
    fun lockedPrivateModeOrUnknownProfileShowNothing() {
        val all = listOf(sms, gmail)
        assertTrue(HubHomeRows.rows(all, HubPrivacyState(deviceLocked = true), 0L).isEmpty())
        assertTrue(HubHomeRows.rows(all, HubPrivacyState(privateMode = true), 0L).isEmpty())
        assertTrue(HubHomeRows.rows(all, HubPrivacyState(), -1L).isEmpty())
    }

    @Test
    fun capsRowsAndText() {
        val many = (1..40).map { sms.copy(threadId = it.toLong(), lastDateMillis = it.toLong(), lastBody = "x".repeat(300)) }
        val rows = HubHomeRows.rows(many, HubPrivacyState(), 0L)
        assertEquals(HubHomeRows.MAX_ROWS, rows.size)
        assertEquals(40L, rows.first().threadId)
        assertEquals(HubHomeRows.MAX_PREVIEW, rows.first().preview.length)
    }
}
