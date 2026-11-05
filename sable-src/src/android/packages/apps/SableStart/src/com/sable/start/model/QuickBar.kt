package org.sableos.start.model

import com.sable.start.model.StartText.t

/**
 * The bottom bar of Sable Start (BlackBerry 10 layout, owner decision
 * 2026-11-04): Hub at the left end, the page dots in the middle and Camera at
 * the right end, under Active Frames and the app grid. The Q25 has a call
 * key, so Phone is not in the bar by default; Search is reached by typing on
 * any page or Alt+Space, so it needs no slot. Pure policy, unit tested in
 * apps/r8/android/sablestart-presentation-check/src/test.
 *
 * A slot is stored as a token:
 *   "phone", "hub", "camera"  role slots, resolved to the installed app
 *   "command"                 Search/Command (optional: typing and Alt+Space open it)
 *   "apps"                    the A-Z All Apps list (optional: the app grid
 *                             pages hold every app, Alt+Right reaches them)
 *   "app:<stableKey>"         one app the user picked
 */
object QuickBar {
    const val PHONE = "phone"
    const val HUB = "hub"
    const val CAMERA = "camera"
    const val COMMAND = "command"
    const val ALL_APPS = "apps"
    private const val APP_PREFIX = "app:"

    /** Two slots, one at each end of the bar around the page dots (Alt+1, Alt+2). */
    const val MAX_SLOTS = 2

    /** Hub | page dots | Camera. */
    val DEFAULT: List<String> = listOf(HUB, CAMERA)

    /** The bar of the Titan 2 three-page Start; a saved copy of it means "never customised". */
    val LEGACY_DEFAULT: List<String> = listOf(PHONE, HUB, COMMAND, ALL_APPS)

    /** The Phone · Search · Camera bar before 2026-11-04; a saved copy of it means "never customised". */
    val PHONE_SEARCH_CAMERA: List<String> = listOf(PHONE, COMMAND, CAMERA)

    /**
     * Slots that can never be removed or replaced. None: COMMAND_ACCESS_REQUIRED is met by
     * typing on any Start page and by Alt+Space, which open Search without a slot.
     */
    val REQUIRED: Set<String> = emptySet()

    private val ROLES = setOf(PHONE, HUB, CAMERA, COMMAND, ALL_APPS)

    fun appToken(stableKey: String): String = APP_PREFIX + stableKey

    /** The app's stable key for an "app:" token, else null. */
    fun appKey(token: String): String? = token.removePrefix(APP_PREFIX).takeIf { token.startsWith(APP_PREFIX) && it.isNotEmpty() }

    fun isRequired(token: String): Boolean = token in REQUIRED

    fun isValid(token: String): Boolean = token in ROLES || token in REQUIRED || appKey(token) != null

    /**
     * Drops unknown and duplicate tokens, puts back any missing required slot
     * and caps the bar at [MAX_SLOTS]; required slots win the cap. Null (never
     * customised) and the earlier defaults give [DEFAULT].
     */
    fun normalize(tokens: List<String>?): List<String> {
        if (tokens == null || tokens == LEGACY_DEFAULT || tokens == PHONE_SEARCH_CAMERA) return DEFAULT
        val kept = tokens.filter(::isValid).distinct().toMutableList()
        REQUIRED.filterNot { it in kept }.forEach { kept += it }
        while (kept.size > MAX_SLOTS) {
            kept.removeAt(kept.indexOfLast { !isRequired(it) })
        }
        return kept
    }

    fun move(
        tokens: List<String>,
        index: Int,
        delta: Int,
    ): List<String> {
        val target = index + delta
        if (index !in tokens.indices || target !in tokens.indices) return tokens
        return tokens.toMutableList().also { list ->
            list[index] = list[target].also { list[target] = list[index] }
        }
    }

    /** BASE_BAR_REPLACE_SLOTS; required slots and duplicates are refused. */
    fun replace(
        tokens: List<String>,
        index: Int,
        token: String,
    ): List<String> {
        if (index !in tokens.indices || isRequired(tokens[index])) return tokens
        if (!isValid(token) || isRequired(token) || token in tokens) return tokens
        return tokens.toMutableList().also { it[index] = token }
    }

    fun remove(
        tokens: List<String>,
        index: Int,
    ): List<String> {
        if (index !in tokens.indices || isRequired(tokens[index])) return tokens
        return tokens.filterIndexed { i, _ -> i != index }
    }

    /** Adds before Command when the bar has it, else at the end. */
    fun add(
        tokens: List<String>,
        token: String,
    ): List<String> {
        if (tokens.size >= MAX_SLOTS || !isValid(token) || token in tokens) return tokens
        val at = tokens.indexOf(COMMAND).takeIf { it >= 0 } ?: tokens.size
        return tokens.toMutableList().also { it.add(at, token) }
    }

    /**
     * Whether Peek offers Remove from base bar (true), Add to base bar (false)
     * or neither (null: another profile's app). With a full bar, Add opens the
     * slot picker ([needsSlotPicker]).
     */
    fun appState(
        launcherUser: Boolean,
        inBar: Boolean,
    ): Boolean? =
        when {
            !launcherUser -> null
            inBar -> true
            else -> false
        }

    /** All Apps "Choose base bar slot": the bar is full, so adding replaces a slot the user picks. */
    fun needsSlotPicker(tokens: List<String>): Boolean = tokens.size >= MAX_SLOTS

    /** What the slot picker says under each slot: "Keep", "Replace with X" or "Required path". */
    fun pickerLine(
        token: String,
        focused: Boolean,
        appLabel: String,
    ): String =
        when {
            isRequired(token) -> t("required_path")
            focused -> t("replace_with", appLabel)
            else -> t("keep")
        }

    /** Removes the slot holding [slotToken], or adds [stableKey] as an app slot when it has none. */
    fun toggleApp(
        tokens: List<String>,
        slotToken: String?,
        stableKey: String,
    ): List<String> =
        if (slotToken != null) remove(tokens, tokens.indexOf(slotToken)) else add(tokens, appToken(stableKey))

    /**
     * Slot shortcuts: Fn+1 and Fn+2 on keyboards with an Fn key (Titan 2), and
     * Alt+1 and Alt+2 on keyboards that type digits on the Alt layer (the Q25's
     * Alt+W/E). [unicode] is the character the platform key map produced
     * with the event's own meta state. Returns the slot index, or null when the
     * key is not a quick-bar shortcut.
     */
    fun shortcutSlot(
        keyCode: Int,
        metaState: Int,
        slotCount: Int,
        unicode: Int = 0,
    ): Int? {
        val index =
            when {
                metaState and META_FUNCTION_ON != 0 -> keyCode - KEYCODE_1
                metaState and META_ALT_ON != 0 ->
                    if (unicode in '1'.code..'9'.code) unicode - '1'.code else keyCode - KEYCODE_1
                else -> return null
            }
        return index.takeIf { it in 0 until minOf(slotCount, MAX_SLOTS) }
    }

    /**
     * Phone slot type-to-dial (SABLE_START_SCREEN_DESIGN.md "Type digits while
     * Phone quick icon focused"): the dial character a key typed, or null. On
     * the Q25 the digits are the Alt layer (W E R S D F Z X C = 1..9), so the
     * character the key map produced decides, not the key code.
     */
    fun dialChar(unicode: Int): Char? {
        if (unicode <= 0) return null
        val char = unicode.toChar()
        return char.takeIf { it in '0'..'9' || it in DIAL_SYMBOLS }
    }

    private const val DIAL_SYMBOLS = "*#+"

    /** "Alt+1", "Alt+2": the shortcut as printed on the keys (Fn devices accept it too). */
    fun shortcutLabel(index: Int): String = "Alt+${index + 1}"

    const val KEYCODE_1 = 8
    const val META_ALT_ON = 0x2
    const val META_FUNCTION_ON = 0x8
}

/**
 * Type-to-launch on Start (SABLE_START_SCREEN_DESIGN.md "typing on Home goes
 * straight to Search"): a printable key with no Alt, Ctrl or Fn held opens
 * Search seeded with that character. Returns the character, or null when the
 * key is not one that starts a search.
 */
object StartTypeToSearch {
    private const val META_CTRL_ON = 0x1000

    fun seed(
        unicode: Int,
        metaState: Int,
        repeat: Boolean,
    ): Char? {
        if (repeat || unicode <= 0) return null
        if (metaState and (QuickBar.META_ALT_ON or QuickBar.META_FUNCTION_ON or META_CTRL_ON) != 0) return null
        val char = unicode.toChar()
        return char.takeUnless { it.isISOControl() || it.isWhitespace() }
    }
}

/**
 * The Start pages in the BlackBerry 10 order: Hub on the left, Active Frames
 * (Home) next, then one or more app grid pages to the right. Swipe or
 * Alt+Left/Right (Fn+Left/Right on Fn keyboards) moves one page; Home and
 * Back return to Active Frames.
 */
object StartPages {
    const val HUB = 0
    const val HOME = 1
    const val FIRST_GRID = 2

    private const val KEYCODE_DPAD_UP = 19
    private const val KEYCODE_DPAD_DOWN = 20
    private const val KEYCODE_DPAD_LEFT = 21
    private const val KEYCODE_DPAD_RIGHT = 22

    /** Hub, Active Frames and [gridPages] app grid pages (at least one). */
    fun count(gridPages: Int): Int = FIRST_GRID + gridPages.coerceAtLeast(1)

    /** -1 toward Hub, +1 toward the app grid, or null when the key does not move pages. */
    fun keyDelta(
        keyCode: Int,
        metaState: Int,
    ): Int? {
        if (metaState and (QuickBar.META_ALT_ON or QuickBar.META_FUNCTION_ON) == 0) return null
        return when (keyCode) {
            KEYCODE_DPAD_LEFT -> -1
            KEYCODE_DPAD_RIGHT -> 1
            else -> null
        }
    }

    /**
     * Edit Home reorder keys ("Fn+arrows to reorder"; Alt on the Q25): -1 moves
     * the focused item up, +1 down, or null when the key is not Alt/Fn+Up/Down.
     */
    fun reorderDelta(
        keyCode: Int,
        metaState: Int,
    ): Int? {
        if (metaState and (QuickBar.META_ALT_ON or QuickBar.META_FUNCTION_ON) == 0) return null
        return when (keyCode) {
            KEYCODE_DPAD_UP -> -1
            KEYCODE_DPAD_DOWN -> 1
            else -> null
        }
    }

    /** The page [delta] pages from [current], kept within Hub..the last grid page. */
    fun target(
        current: Int,
        delta: Int,
        count: Int,
    ): Int = (current + delta).coerceIn(HUB, (count - 1).coerceAtLeast(HUB))

    /** The page a screen name opens ("hub", "grid"; anything else is Active Frames). */
    fun forScreen(screen: String): Int =
        when (screen.lowercase(java.util.Locale.ROOT)) {
            "hub" -> HUB
            "grid" -> FIRST_GRID
            else -> HOME
        }
}

/**
 * Trackpad flicks on the Start pages. The Q25 trackpad sends plain arrow keys: a quick stroke
 * is a burst of presses 15-60 ms apart, a slow one single presses 100 ms or more apart (getevent
 * on the Q25, 2026-11-05). [FLICK_PRESSES] same-direction Left or Right presses within
 * [FLICK_GAP_MS] of each other turn one page; the rest of that burst is swallowed so focus does
 * not run on in the new page, and no second turn comes until [TURN_PAUSE_MS] later. Slower
 * strokes and Up/Down keep moving focus. A flick never opens anything.
 */
class TrackpadFlick {
    /** What to do with one key: pass it on, turn a page toward [Turn.delta], or swallow it. */
    sealed interface Step {
        data object Pass : Step

        data class Turn(
            val delta: Int,
        ) : Step

        data object Swallow : Step
    }

    private var direction = 0
    private var presses = 0
    private var lastPress = Long.MIN_VALUE / 2
    private var lastTurn = Long.MIN_VALUE / 2
    private var spent = false

    /** One key down at [timeMs] (KeyEvent.getEventTime). Modified keys and repeats always pass. */
    fun onKeyDown(
        keyCode: Int,
        metaState: Int,
        repeat: Int,
        timeMs: Long,
    ): Step {
        val delta = horizontal(keyCode)
        if (delta == null || repeat > 0 || metaState and MODIFIERS != 0) {
            presses = 0
            return Step.Pass
        }
        val sameBurst = delta == direction && timeMs - lastPress <= FLICK_GAP_MS
        lastPress = timeMs
        if (!sameBurst) {
            direction = delta
            presses = 0
            spent = false
        }
        presses++
        return when {
            spent -> {
                Step.Swallow
            }

            presses >= FLICK_PRESSES && timeMs - lastTurn >= TURN_PAUSE_MS -> {
                spent = true
                lastTurn = timeMs
                Step.Turn(delta)
            }

            else -> {
                Step.Pass
            }
        }
    }

    private fun horizontal(keyCode: Int): Int? =
        when (keyCode) {
            KEYCODE_DPAD_LEFT -> -1
            KEYCODE_DPAD_RIGHT -> 1
            else -> null
        }

    companion object {
        const val FLICK_PRESSES = 3
        const val FLICK_GAP_MS = 70L
        const val TURN_PAUSE_MS = 350L
        private const val KEYCODE_DPAD_LEFT = 21
        private const val KEYCODE_DPAD_RIGHT = 22
        private const val META_SHIFT_ON = 0x1
        private const val META_CTRL_ON = 0x1000
        private const val MODIFIERS =
            QuickBar.META_ALT_ON or QuickBar.META_FUNCTION_ON or META_SHIFT_ON or META_CTRL_ON
    }
}

/**
 * Stray touches from the trackpad: a thumb stroking up the Q25 trackpad brushes the bottom of the
 * screen (getevent shows a short touch at the bottom edge during most Up bursts). A touch that
 * starts in the bottom [EDGE_FRACTION] of the screen within [GUARD_MS] of a trackpad key is
 * ignored with the rest of its gesture.
 */
object TrackpadTouchGuard {
    const val GUARD_MS = 150L
    const val EDGE_FRACTION = 0.15f

    fun ignores(
        touchY: Float,
        screenHeight: Int,
        touchTimeMs: Long,
        lastTrackpadKeyMs: Long?,
    ): Boolean {
        if (lastTrackpadKeyMs == null || screenHeight <= 0) return false
        val nearKey = touchTimeMs - lastTrackpadKeyMs in 0..GUARD_MS
        return nearKey && touchY >= screenHeight * (1f - EDGE_FRACTION)
    }

    /** The arrow keys and centre press the trackpad sends. */
    fun isTrackpadKey(keyCode: Int): Boolean = keyCode in KEYCODE_DPAD_UP..KEYCODE_DPAD_CENTER

    private const val KEYCODE_DPAD_UP = 19
    private const val KEYCODE_DPAD_CENTER = 23
}
