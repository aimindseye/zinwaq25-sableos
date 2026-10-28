package org.sableos.start.model

/**
 * BlackBerry speed dial on the home screen: holding a letter that Phone has a speed dial number
 * for calls it; a short press still starts type-to-search with that letter. Only assigned letters
 * wait for the key's release, so every other key searches at once. Phone owns the list (Phone >
 * Menu > Speed dial) and places the call. Pure policy, unit tested in
 * apps/r8/android/sablestart-presentation-check/src/test.
 */
class SpeedDialKeys {
    sealed interface Step {
        /** Not a speed dial key: handle it as before. */
        data object Pass : Step

        /** Swallow the key (an assigned letter waiting for its release or long press). */
        data object Consume : Step

        data class Call(val letter: Char) : Step

        /** Start search with [seed]: the waiting letter was released, or another key followed it. */
        data class Search(val seed: String) : Step
    }

    private var pendingKey = NONE
    private var pendingSeed = ""
    private var called = false

    /**
     * One key event on the home screen. [seed] is what type-to-search would start with for this
     * key ([StartTypeToSearch.seed]), or null; [assigned] the letters Phone has numbers for.
     */
    fun onKey(
        keyCode: Int,
        down: Boolean,
        repeatCount: Int,
        metaState: Int,
        seed: Char?,
        assigned: Set<Char>,
    ): Step {
        if (pendingKey != NONE) return onPending(keyCode, down, repeatCount, seed)
        val letter = letterFor(keyCode)
        val waits =
            down && repeatCount == 0 && letter != null && letter in assigned && seed != null &&
                metaState and CHORD_META == 0
        if (!waits) return Step.Pass
        pendingKey = keyCode
        pendingSeed = seed.toString()
        called = false
        return Step.Consume
    }

    private fun onPending(
        keyCode: Int,
        down: Boolean,
        repeatCount: Int,
        seed: Char?,
    ): Step {
        if (keyCode == pendingKey) {
            return when {
                down && repeatCount >= 1 && !called -> {
                    called = true
                    Step.Call(letterFor(keyCode) ?: return Step.Consume)
                }
                down -> Step.Consume
                else -> finish()
            }
        }
        // Another key while the letter is still down (fast typing): search with both, in order.
        if (!down) return Step.Pass
        val typed = if (called) null else pendingSeed + (seed?.toString() ?: "")
        pendingKey = NONE
        return typed?.let { Step.Search(it) } ?: Step.Pass
    }

    private fun finish(): Step {
        val wasCalled = called
        pendingKey = NONE
        return if (wasCalled) Step.Consume else Step.Search(pendingSeed)
    }

    companion object {
        const val DIALER_PACKAGE = "com.android.dialer"
        const val AUTHORITY = "org.sableos.dialer.speeddial"
        const val ACTION_SPEED_DIAL = "org.sableos.dialer.action.SPEED_DIAL"
        const val EXTRA_LETTER = "org.sableos.dialer.extra.LETTER"
        const val COLUMN_LETTER = "letter"

        private const val NONE = -1
        private const val KEYCODE_A = 29
        private const val KEYCODE_Z = 54

        // Shift, Alt, Sym, Fn, Ctrl and Meta: a chord is never a speed dial.
        private const val CHORD_META = 0xc1 or 0x32 or 0x4 or 0x8 or 0x7000 or 0x70000

        fun letterFor(keyCode: Int): Char? =
            if (keyCode in KEYCODE_A..KEYCODE_Z) 'a' + (keyCode - KEYCODE_A) else null
    }
}
