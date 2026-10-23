package org.sableos.messages.core

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusAndLatestTest {
    @Test
    fun smsTypesMapToBubbleStates() {
        assertEquals(DeliveryState.Received, MessageStatus.smsState(MessageStatus.SMS_TYPE_INBOX))
        assertEquals(DeliveryState.Sent, MessageStatus.smsState(MessageStatus.SMS_TYPE_SENT))
        assertEquals(DeliveryState.Sending, MessageStatus.smsState(MessageStatus.SMS_TYPE_OUTBOX))
        assertEquals(DeliveryState.Sending, MessageStatus.smsState(MessageStatus.SMS_TYPE_QUEUED))
        assertEquals(DeliveryState.Failed, MessageStatus.smsState(MessageStatus.SMS_TYPE_FAILED))
        assertEquals(Direction.Incoming, MessageStatus.smsDirection(MessageStatus.SMS_TYPE_INBOX))
        assertEquals(Direction.Outgoing, MessageStatus.smsDirection(MessageStatus.SMS_TYPE_FAILED))
    }

    @Test
    fun mmsBoxesAndPendingDownloads() {
        assertEquals(DeliveryState.Received, MessageStatus.mmsState(MessageStatus.MMS_BOX_INBOX, 0x84, false))
        assertEquals(DeliveryState.Downloading, MessageStatus.mmsState(MessageStatus.MMS_BOX_INBOX, 0x82, false))
        assertEquals(DeliveryState.DownloadFailed, MessageStatus.mmsState(MessageStatus.MMS_BOX_INBOX, 0x82, true))
        assertEquals(DeliveryState.Sent, MessageStatus.mmsState(MessageStatus.MMS_BOX_SENT, 0x80, false))
        assertEquals(DeliveryState.Failed, MessageStatus.mmsState(MessageStatus.MMS_BOX_FAILED, 0x80, false))
    }

    @Test
    fun multipartSendIsSentAfterLastPartAndFailureIsFinal() {
        val outbox = MessageStatus.SMS_TYPE_OUTBOX
        val ok = MessageStatus.RESULT_OK
        assertEquals(outbox, MessageStatus.afterPartResult(outbox, ok, isLastPart = false))
        assertEquals(MessageStatus.SMS_TYPE_SENT, MessageStatus.afterPartResult(outbox, ok, isLastPart = true))
        assertEquals(MessageStatus.SMS_TYPE_FAILED, MessageStatus.afterPartResult(outbox, 4, isLastPart = false))
        assertEquals(
            MessageStatus.SMS_TYPE_FAILED,
            MessageStatus.afterPartResult(MessageStatus.SMS_TYPE_FAILED, ok, isLastPart = true),
        )
        // A late OK for an earlier part does not undo "sent".
        assertEquals(
            MessageStatus.SMS_TYPE_SENT,
            MessageStatus.afterPartResult(MessageStatus.SMS_TYPE_SENT, ok, isLastPart = false),
        )
    }

    @Test
    fun bubbleLabels() {
        assertEquals("Sent 19:10", MessageStatus.label(DeliveryState.Sent, 0, "19:10"))
        assertEquals("Sending…", MessageStatus.label(DeliveryState.Sending, 0, "19:10"))
        assertEquals("Not sent · no service · Enter to retry", MessageStatus.label(DeliveryState.Failed, 4, "19:10"))
        assertEquals("Not sent · error 99 · Enter to retry", MessageStatus.label(DeliveryState.Failed, 99, "x"))
        assertEquals("19:12", MessageStatus.label(DeliveryState.Received, 0, "19:12"))
    }

    @Test
    fun openSendAndReturnGoToLatest() {
        val history = LatestState(atLatest = false, scrollToLatest = false, unseenIncoming = 2)
        listOf(LatestEvent.Opened, LatestEvent.Sent, LatestEvent.ReturnToLatest).forEach { event ->
            val state = LatestPolicy.reduce(history, event)
            assertTrue(state.atLatest)
            assertTrue(state.scrollToLatest)
            assertEquals(0, state.unseenIncoming)
        }
    }

    @Test
    fun incomingAdvancesAtLatestButOnlyFlagsInHistory() {
        val atLatest = LatestState(atLatest = true, scrollToLatest = false)
        assertTrue(LatestPolicy.reduce(atLatest, LatestEvent.Arrived(1, 0)).scrollToLatest)

        val history = LatestState(atLatest = false, scrollToLatest = false)
        val flagged = LatestPolicy.reduce(history, LatestEvent.Arrived(2, 0))
        assertFalse(flagged.scrollToLatest)
        assertTrue(flagged.showNewMessageIndicator)
        assertEquals(2, flagged.unseenIncoming)

        // Our own sent message always brings the view back.
        assertTrue(LatestPolicy.reduce(history, LatestEvent.Arrived(0, 1)).scrollToLatest)
        assertEquals(history, LatestPolicy.reduce(history, LatestEvent.Arrived(0, 0)))
    }

    @Test
    fun viewportTracksWhetherLatestIsVisible() {
        val away = LatestPolicy.reduce(LatestState(), LatestEvent.Viewport(lastVisibleIndex = 3, count = 10))
        assertFalse(away.atLatest)
        assertTrue(away.showReturnToLatest)
        val back = LatestPolicy.reduce(away.copy(unseenIncoming = 1), LatestEvent.Viewport(9, 10))
        assertTrue(back.atLatest)
        assertEquals(0, back.unseenIncoming)
        assertFalse(LatestPolicy.reduce(away, LatestEvent.Scrolled).scrollToLatest)
    }

    @Test
    fun arrivalsCountsNewMessagesByDirection() {
        fun msg(
            id: Long,
            direction: Direction,
        ) = ConversationMessage(id, MessageKind.Sms, 1, "+1", "", id, direction, DeliveryState.Received)
        val messages = listOf(msg(1, Direction.Incoming), msg(2, Direction.Outgoing), msg(3, Direction.Incoming))
        assertEquals(LatestEvent.Arrived(1, 1), LatestPolicy.arrivals(setOf("Sms:1"), messages))
    }

    @Test
    fun conversationKeys() {
        fun cmd(
            key: Int,
            shift: Boolean = false,
            atLatest: Boolean = true,
            blank: Boolean = false,
        ) = ConversationKeys.command(key, shift, atLatest, blank)
        assertEquals(ConversationCommand.Send, cmd(KeyEvent.KEYCODE_ENTER))
        assertEquals(ConversationCommand.None, cmd(KeyEvent.KEYCODE_ENTER, shift = true))
        assertEquals(ConversationCommand.None, cmd(KeyEvent.KEYCODE_ENTER, blank = true))
        assertEquals(ConversationCommand.Back, cmd(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(ConversationCommand.ReturnToLatest, cmd(KeyEvent.KEYCODE_ESCAPE, atLatest = false))
        assertEquals(ConversationCommand.ReturnToLatest, cmd(KeyEvent.KEYCODE_MOVE_END, atLatest = false))
        assertEquals(ConversationCommand.None, cmd(KeyEvent.KEYCODE_MOVE_END))
        assertEquals(ConversationCommand.OlderPage, cmd(KeyEvent.KEYCODE_PAGE_UP))
        assertEquals(ConversationCommand.NewerPage, cmd(KeyEvent.KEYCODE_PAGE_DOWN))
        assertEquals(ConversationCommand.None, cmd(KeyEvent.KEYCODE_A))
    }

    @Test
    fun notificationText() {
        assertEquals("Maya", IncomingNotice.title("Maya", "+1"))
        assertEquals("+1", IncomingNotice.title(" ", "+1"))
        assertEquals("New message", IncomingNotice.publicText(1))
        assertEquals("3 new messages", IncomingNotice.publicText(3))
        assertEquals("Picture", IncomingNotice.mmsBody("", null, listOf("image/jpeg")))
        assertEquals("2 pictures", IncomingNotice.mmsBody("", null, listOf("image/jpeg", "image/png")))
        assertEquals("Video", IncomingNotice.mmsBody("", null, listOf("image/jpeg", "video/mp4")))
        assertEquals("Hi", IncomingNotice.mmsBody("Hi", "Subj", listOf("image/jpeg")))
        assertEquals("Subj", IncomingNotice.mmsBody("", "Subj", emptyList()))
        assertEquals("Multimedia message", IncomingNotice.mmsBody("", null, emptyList()))
    }
}
