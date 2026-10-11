package org.sableos.start.model

/**
 * Base quick bar on Center Start (platform_sable SABLE_START_SCREEN_DESIGN.md
 * "Base quick bar", SABLE_START_VISUAL_CONFIRMATION.md "Base quick bar
 * contract"). Pure policy, unit tested in
 * apps/r8/android/sablestart-presentation-check/src/test.
 *
 * A slot is stored as a token:
 *   "phone", "hub", "camera"  role slots, resolved to the installed app
 *   "command", "apps"         Search/Command and All Apps (always present)
 *   "app:<stableKey>"         one app the user picked
 */
object QuickBar {
    const val PHONE = "phone"
    const val HUB = "hub"
    const val CAMERA = "camera"
    const val COMMAND = "command"
    const val ALL_APPS = "apps"
    private const val APP_PREFIX = "app:"

    const val MAX_SLOTS = 5

    /** Phone | Hub | Command | All Apps. */
    val DEFAULT: List<String> = listOf(PHONE, HUB, COMMAND, ALL_APPS)

    /** COMMAND_ACCESS_REQUIRED and ALL_APPS_ACCESS_REQUIRED: never removed or replaced. */
    val REQUIRED: Set<String> = setOf(COMMAND, ALL_APPS)

    private val ROLES = setOf(PHONE, HUB, CAMERA)

    fun appToken(stableKey: String): String = APP_PREFIX + stableKey

    /** The app's stable key for an "app:" token, else null. */
    fun appKey(token: String): String? = token.removePrefix(APP_PREFIX).takeIf { token.startsWith(APP_PREFIX) && it.isNotEmpty() }

    fun isRequired(token: String): Boolean = token in REQUIRED

    fun isValid(token: String): Boolean = token in ROLES || token in REQUIRED || appKey(token) != null

    /**
     * Drops unknown and duplicate tokens, puts back a missing Command or All
     * Apps slot and caps the bar at [MAX_SLOTS]; required slots win the cap.
     * Null (never customised) gives [DEFAULT].
     */
    fun normalize(tokens: List<String>?): List<String> {
        if (tokens == null) return DEFAULT
        val kept = tokens.filter(::isValid).distinct().toMutableList()
        REQUIRED.filterNot { it in kept }.forEach { kept += it }
        while (kept.size > MAX_SLOTS) {
            val drop = kept.indexOfLast { !isRequired(it) }
            kept.removeAt(drop)
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

    /** Adds before Command so the default order reads naturally. */
    fun add(
        tokens: List<String>,
        token: String,
    ): List<String> {
        if (tokens.size >= MAX_SLOTS || !isValid(token) || token in tokens) return tokens
        val at = tokens.indexOf(COMMAND).takeIf { it >= 0 } ?: tokens.size
        return tokens.toMutableList().also { it.add(at, token) }
    }

    /**
     * Fn+1..Fn+5 open slots 1..5. Returns the slot index, or null when the
     * key is not a quick-bar shortcut.
     */
    fun shortcutSlot(
        keyCode: Int,
        metaState: Int,
        slotCount: Int,
    ): Int? {
        if (metaState and META_FUNCTION_ON == 0) return null
        val index = keyCode - KEYCODE_1
        return index.takeIf { it in 0 until minOf(slotCount, MAX_SLOTS) }
    }

    const val KEYCODE_1 = 8
    const val META_FUNCTION_ON = 0x8
}
