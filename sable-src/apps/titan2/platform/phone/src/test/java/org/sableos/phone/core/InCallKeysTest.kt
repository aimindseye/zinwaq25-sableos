package org.sableos.phone.core

import org.junit.Assert.assertEquals
import org.junit.Test
import org.sableos.phone.core.InCallKeys.Action

class InCallKeysTest {
    private fun decide(
        key: Int,
        ringing: Boolean,
        keypad: Boolean = false,
        space: Boolean = true,
        meta: Int = 0
    ) = InCallKeys.decide(key, meta, 0, InCallKeys.Screen(ringing, keypad, space))

    @Test
    fun ringingCallKeyAndSpaceAnswerEndDeclines() {
        assertEquals(Action.ANSWER, decide(DialKeys.KEYCODE_CALL, ringing = true))
        assertEquals(Action.ANSWER, decide(DialKeys.KEYCODE_SPACE, ringing = true))
        assertEquals(Action.NONE, decide(DialKeys.KEYCODE_SPACE, ringing = true, space = false))
        assertEquals(Action.DECLINE, decide(DialKeys.KEYCODE_ENDCALL, ringing = true))
        assertEquals(Action.NONE, decide(InCallKeys.KEYCODE_M, ringing = true))
    }

    @Test
    fun spaceNeverEndsACall() {
        assertEquals(Action.NONE, decide(DialKeys.KEYCODE_SPACE, ringing = false))
    }

    @Test
    fun singleKeysInACall() {
        assertEquals(Action.MUTE, decide(InCallKeys.KEYCODE_M, ringing = false))
        assertEquals(Action.SPEAKER, decide(InCallKeys.KEYCODE_S, ringing = false))
        assertEquals(Action.HOLD, decide(InCallKeys.KEYCODE_H, ringing = false))
        assertEquals(Action.KEYPAD, decide(InCallKeys.KEYCODE_D, ringing = false))
        assertEquals(Action.HANG_UP, decide(DialKeys.KEYCODE_ENDCALL, ringing = false))
    }

    @Test
    fun keypadOpenLeavesLettersAsTonesButEndStillHangsUp() {
        assertEquals(Action.NONE, decide(InCallKeys.KEYCODE_M, ringing = false, keypad = true))
        assertEquals(
            Action.HANG_UP,
            decide(DialKeys.KEYCODE_ENDCALL, ringing = false, keypad = true)
        )
    }

    @Test
    fun heldKeysAndChordsDoNothing() {
        val ringing = InCallKeys.Screen(ringing = true, keypadOpen = false, spaceAnswers = true)
        assertEquals(Action.NONE, InCallKeys.decide(DialKeys.KEYCODE_CALL, 0, 1, ringing))
        assertEquals(
            Action.NONE,
            decide(InCallKeys.KEYCODE_M, ringing = false, meta = DialKeys.META_ALT_MASK)
        )
    }
}
