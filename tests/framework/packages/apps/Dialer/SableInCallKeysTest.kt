package com.android.dialer.sable

import org.junit.Assert.assertEquals
import org.junit.Test
import com.android.dialer.sable.SableInCallKeys.Action

/** Pure tests for the in-call single-key commands (Dialer patches 0703 and 0704). */
class SableInCallKeysTest {
    private fun decide(
        key: Int,
        meta: Int = 0,
        repeat: Int = 0,
        inCall: Boolean = true,
        dialpad: Boolean = false,
    ) = SableInCallKeys.decide(key, meta, repeat, inCall, dialpad)

    @Test fun androidConstantsArePinned() {
        // android.view.KeyEvent values.
        assertEquals(32, SableInCallKeys.KEYCODE_D)
        assertEquals(41, SableInCallKeys.KEYCODE_M)
        assertEquals(47, SableInCallKeys.KEYCODE_S)
        assertEquals(62, SableInCallKeys.KEYCODE_SPACE)
        assertEquals(0xc1, SableInCallKeys.META_SHIFT_MASK)
        assertEquals(0x32, SableInCallKeys.META_ALT_MASK)
        assertEquals(0x7000, SableInCallKeys.META_CTRL_MASK)
        assertEquals(0x70000, SableInCallKeys.META_META_MASK)
    }

    @Test fun lettersDuringACall() {
        assertEquals(Action.TOGGLE_MUTE, decide(SableInCallKeys.KEYCODE_M))
        assertEquals(Action.TOGGLE_SPEAKER, decide(SableInCallKeys.KEYCODE_S))
        assertEquals(Action.SHOW_DIALPAD, decide(SableInCallKeys.KEYCODE_D))
        assertEquals(Action.NONE, decide(29)) // A
    }

    @Test fun ringingOrNoCallDoesNothing() {
        assertEquals(Action.NONE, decide(SableInCallKeys.KEYCODE_M, inCall = false))
    }

    @Test fun openDialpadKeepsLettersAsDtmf() {
        assertEquals(Action.NONE, decide(SableInCallKeys.KEYCODE_M, dialpad = true))
        assertEquals(Action.NONE, decide(SableInCallKeys.KEYCODE_D, dialpad = true))
    }

    @Test fun heldOrModifiedKeysKeepUpstreamMeaning() {
        assertEquals(Action.NONE, decide(SableInCallKeys.KEYCODE_M, repeat = 1))
        for (meta in listOf(0x1, 0x2, 0x4, 0x8, 0x1000, 0x10000)) {
            assertEquals(Action.NONE, decide(SableInCallKeys.KEYCODE_S, meta = meta))
        }
    }

    private fun incoming(
        key: Int = SableInCallKeys.KEYCODE_SPACE,
        meta: Int = 0,
        repeat: Int = 0,
        ringingAlone: Boolean = true,
        enabled: Boolean = SableInCallKeys.DEFAULT_SPACE_ANSWERS,
    ) = SableInCallKeys.decideIncoming(key, meta, repeat, ringingAlone, enabled)

    @Test fun spaceAnswersARingingCallByDefault() {
        assertEquals(true, SableInCallKeys.DEFAULT_SPACE_ANSWERS)
        assertEquals("sable_space_answers", SableInCallKeys.PREF_SPACE_ANSWERS)
        assertEquals(Action.ANSWER, incoming())
    }

    @Test fun spaceAnswerSettingOffDoesNothing() {
        assertEquals(Action.NONE, incoming(enabled = false))
    }

    @Test fun onlySpaceAnswers() {
        for (key in listOf(SableInCallKeys.KEYCODE_M, SableInCallKeys.KEYCODE_S, SableInCallKeys.KEYCODE_D, 66, 29)) {
            assertEquals(Action.NONE, incoming(key = key))
        }
    }

    @Test fun callWaitingOrNoRingingCallIsNotAnswered() {
        assertEquals(Action.NONE, incoming(ringingAlone = false))
    }

    @Test fun heldOrModifiedSpaceDoesNotAnswer() {
        assertEquals(Action.NONE, incoming(repeat = 1))
        for (meta in listOf(0x1, 0x2, 0x4, 0x8, 0x1000, 0x10000)) {
            assertEquals(Action.NONE, incoming(meta = meta))
        }
    }

    @Test fun spaceNeverHangsUpOrActsDuringACall() {
        // Ending a call stays on the red end key; Space has no in-call command.
        assertEquals(Action.NONE, decide(SableInCallKeys.KEYCODE_SPACE))
        assertEquals(Action.NONE, decide(SableInCallKeys.KEYCODE_SPACE, dialpad = true))
    }
}
