package org.sableos.start.model

/**
 * Base quick bar on Start (platform_sable SABLE_START_SCREEN_DESIGN.md
 * "Base quick bar", SABLE_START_VISUAL_CONFIRMATION.md "Base quick bar
 * contract"). Pure policy, unit tested in
 * apps/r8/android/sablestart-presentation-check/src/test.
 *
 * A slot is stored as a token:
 *   "phone", "hub", "camera"  role slots, resolved to the installed app
 *   "app:<stableKey>"         one app the user picked
 *
 * Q25: All Apps and Search are pages to the right of Home (see [StartPages]),
 * so they are no longer bar slots. Bars saved with the old "command" and
 * "apps" tokens drop them on load ([LEGACY_PAGE_TOKENS]).
 */
object QuickBar {
    const val PHONE = "phone"
    const val HUB = "hub"
    const val CAMERA = "camera"
    private const val APP_PREFIX = "app:"

    const val MAX_SLOTS = 5

    /** Phone | Hub. */
    val DEFAULT: List<String> = listOf(PHONE, HUB)

    /** Search/Command and All Apps slots of earlier bars; now pages, dropped by [normalize]. */
    val LEGACY_PAGE_TOKENS: Set<String> = setOf("command", "apps")

    private val ROLES = setOf(PHONE, HUB, CAMERA)

    fun appToken(stableKey: String): String = APP_PREFIX + stableKey

    /** The app's stable key for an "app:" token, else null. */
    fun appKey(token: String): String? = token.removePrefix(APP_PREFIX).takeIf { token.startsWith(APP_PREFIX) && it.isNotEmpty() }

    fun isValid(token: String): Boolean = token in ROLES || appKey(token) != null

    /**
     * Drops unknown, legacy and duplicate tokens and caps the bar at
     * [MAX_SLOTS]. Null (never customised) gives [DEFAULT]; an empty list is
     * the user's choice of no bar.
     */
    fun normalize(tokens: List<String>?): List<String> {
        if (tokens == null) return DEFAULT
        return tokens.filter(::isValid).distinct().take(MAX_SLOTS)
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

    /** BASE_BAR_REPLACE_SLOTS; unknown tokens and duplicates are refused. */
    fun replace(
        tokens: List<String>,
        index: Int,
        token: String,
    ): List<String> {
        if (index !in tokens.indices || !isValid(token) || token in tokens) return tokens
        return tokens.toMutableList().also { it[index] = token }
    }

    /** Any slot can go, down to an empty bar (Home then shows none). */
    fun remove(
        tokens: List<String>,
        index: Int,
    ): List<String> {
        if (index !in tokens.indices) return tokens
        return tokens.filterIndexed { i, _ -> i != index }
    }

    /** Adds at the end, up to [MAX_SLOTS]. */
    fun add(
        tokens: List<String>,
        token: String,
    ): List<String> {
        if (tokens.size >= MAX_SLOTS || !isValid(token) || token in tokens) return tokens
        return tokens + token
    }

    /**
     * Whether Peek offers Remove from base bar (true), Add to base bar (false)
     * or neither (null: another profile's app, or the bar is full).
     */
    fun appState(
        launcherUser: Boolean,
        inBar: Boolean,
        tokens: List<String>,
    ): Boolean? =
        when {
            !launcherUser -> null
            inBar -> true
            tokens.size < MAX_SLOTS -> false
            else -> null
        }

    /** Removes the slot holding [slotToken], or adds [stableKey] as an app slot at the end when it has none. */
    fun toggleApp(
        tokens: List<String>,
        slotToken: String?,
        stableKey: String,
    ): List<String> =
        if (slotToken != null) remove(tokens, tokens.indexOf(slotToken)) else add(tokens, appToken(stableKey))

    /**
     * Slot shortcuts: Fn+1..Fn+5 on keyboards with an Fn key (Titan 2), and
     * Alt+1..Alt+5 on keyboards that type digits on the Alt layer (the Q25's
     * Alt+W/E/R/S/D). [unicode] is the character the platform key map produced
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

    /** "Alt+1".."Alt+5": the shortcut as printed on the keys (Fn devices accept it too). */
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
 * Start pages (Q25 owner request): Home first, then All Apps, Search and Live
 * to its right, in that order. Swiping right to left, or Alt+Right (Fn+Right
 * on Fn keyboards), moves one page right; Alt+Left moves back toward Home.
 * Home has nothing to its left.
 */
object StartPages {
    const val HOME = 0
    const val APPS = 1
    const val SEARCH = 2
    const val LIVE = 3
    const val COUNT = 4

    private const val KEYCODE_DPAD_LEFT = 21
    private const val KEYCODE_DPAD_RIGHT = 22

    /** -1 toward Home, +1 toward Live, or null when the key does not move pages. */
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

    /** The page [delta] pages from [current], kept within Home..Live. */
    fun target(
        current: Int,
        delta: Int,
    ): Int = (current + delta).coerceIn(HOME, COUNT - 1)

    /** The page a screen name opens on ("apps", "search", "live"), or null for a non-page screen. */
    fun forScreen(screen: String): Int? =
        when (screen.lowercase(java.util.Locale.ROOT)) {
            "start", "home" -> HOME
            "apps" -> APPS
            "search" -> SEARCH
            "live", "glance" -> LIVE
            else -> null
        }
}
