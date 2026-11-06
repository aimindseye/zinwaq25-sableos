package org.sableos.phone.core

/**
 * What a hardware key does on Sable Phone's call screen. Pure, so the host tests cover every case.
 *
 * Ringing: the Call key answers, and so does Space when "Space answers" is on; End declines. Space never ends a
 * call. In a call with the keypad closed: M mute, S speaker, H hold, D opens the keypad, End hangs up. With the
 * keypad open the letters stay DTMF digits. Held keys and chords keep their usual meaning.
 */
object InCallKeys {
    enum class Action { NONE, ANSWER, DECLINE, HANG_UP, MUTE, SPEAKER, HOLD, KEYPAD }

    const val KEYCODE_D = 32
    const val KEYCODE_H = 36
    const val KEYCODE_M = 41
    const val KEYCODE_S = 47

    /** What the call screen shows when the key comes. */
    data class Screen(val ringing: Boolean, val keypadOpen: Boolean, val spaceAnswers: Boolean)

    fun decide(keyCode: Int, metaState: Int, repeatCount: Int, screen: Screen): Action = when {
        repeatCount > 0 || metaState and DialKeys.CHORD_META != 0 -> Action.NONE
        screen.ringing -> ringingAction(keyCode, screen.spaceAnswers)
        keyCode == DialKeys.KEYCODE_ENDCALL -> Action.HANG_UP
        screen.keypadOpen -> Action.NONE
        else -> callAction(keyCode)
    }

    private fun ringingAction(keyCode: Int, spaceAnswers: Boolean): Action = when (keyCode) {
        DialKeys.KEYCODE_CALL -> Action.ANSWER
        DialKeys.KEYCODE_SPACE -> if (spaceAnswers) Action.ANSWER else Action.NONE
        DialKeys.KEYCODE_ENDCALL -> Action.DECLINE
        else -> Action.NONE
    }

    private fun callAction(keyCode: Int): Action = when (keyCode) {
        KEYCODE_M -> Action.MUTE
        KEYCODE_S -> Action.SPEAKER
        KEYCODE_H -> Action.HOLD
        KEYCODE_D -> Action.KEYPAD
        else -> Action.NONE
    }
}
