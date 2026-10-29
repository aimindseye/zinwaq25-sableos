package org.sableos.start.model

import com.sable.start.model.StartText.t

/**
 * Command layer query model (platform_sable SABLE_COMMAND_LAYER.md "Prefix
 * syntax" and "Result groups"). Pure policy, unit tested in
 * apps/r8/android/sablestart-presentation-check/src/test.
 */
enum class CommandScope {
    All,
    Apps,
    People,
    Settings,
    Hub,
    Actions,

    /** "/private": Private Space apps (a locked space shows only the locked state). */
    Private,

    /** "/dual": Dual (cloned) apps. */
    Dual,

    /** "/work": work profile apps. */
    Work,
}

data class CommandQuery(
    val scope: CommandScope,
    val term: String,
) {
    fun includes(scope: CommandScope): Boolean = this.scope == CommandScope.All || this.scope == scope

    /** "/private", "/dual" and "/work" list the apps of one profile. */
    val profileScope: Boolean
        get() = scope == CommandScope.Private || scope == CommandScope.Dual || scope == CommandScope.Work

    companion object {
        /**
         * `?app`, `/apps` apps only; `@name`, `#name` people; `/settings`
         * Settings; `/hub` Hub; `!action` Sable actions; `/private`, `/dual`,
         * `/work` one profile's apps. Anything else searches every group.
         */
        fun parse(raw: String): CommandQuery {
            val text = raw.trimStart()
            val words =
                listOf(
                    "/settings" to CommandScope.Settings,
                    "/apps" to CommandScope.Apps,
                    "/hub" to CommandScope.Hub,
                    "/private" to CommandScope.Private,
                    "/dual" to CommandScope.Dual,
                    "/work" to CommandScope.Work,
                )
            words.firstOrNull { (word, _) ->
                text.equals(word, ignoreCase = true) || text.startsWith("$word ", ignoreCase = true)
            }?.let { (word, scope) ->
                return CommandQuery(scope, text.drop(word.length).trim())
            }
            val scope =
                when (text.firstOrNull()) {
                    '?' -> CommandScope.Apps
                    '@', '#' -> CommandScope.People
                    '!' -> CommandScope.Actions
                    else -> return CommandQuery(CommandScope.All, text.trim())
                }
            return CommandQuery(scope, text.drop(1).trim())
        }

        /** A dialable number: digits with optional + ( ) - . and spaces, at least three digits. */
        fun dialable(term: String): String? {
            if (term.isEmpty() || term.any { !it.isDigit() && it !in "+()-. " }) return null
            val digits = term.filter { it.isDigit() || it == '+' }
            return digits.takeIf { it.count(Char::isDigit) >= 3 }
        }
    }
}

/** One Settings page Command can open directly. */
data class SettingsDestination(
    val title: String,
    val action: String,
    val keywords: List<String> = emptyList(),
)

object CommandRanking {
    /**
     * Settings destinations every LineageOS build has, by their public
     * android.provider.Settings actions.
     */
    val SETTINGS: List<SettingsDestination> get() =
        listOf(
            SettingsDestination("Wi-Fi", "android.settings.WIFI_SETTINGS", listOf("wifi", "wlan", "network")),
            SettingsDestination(t("bluetooth"), "android.settings.BLUETOOTH_SETTINGS", listOf("pair", "headphones")),
            SettingsDestination(t("mobile_network"), "android.settings.NETWORK_OPERATOR_SETTINGS", listOf("sim", "data", "cellular")),
            SettingsDestination(t("airplane_mode"), "android.settings.AIRPLANE_MODE_SETTINGS", listOf("flight")),
            SettingsDestination(t("display"), "android.settings.DISPLAY_SETTINGS", listOf("brightness", "dark", "theme", "accent")),
            SettingsDestination(t("sound"), "android.settings.SOUND_SETTINGS", listOf("volume", "ringtone", "vibrate")),
            SettingsDestination(t("notifications"), "android.settings.NOTIFICATION_SETTINGS", listOf("hub", "attention")),
            SettingsDestination(t("do_not_disturb"), "android.settings.ZEN_MODE_SETTINGS", listOf("dnd", "quiet", "focus")),
            SettingsDestination(t("battery"), "android.intent.action.POWER_USAGE_SUMMARY", listOf("power", "charging", "health")),
            SettingsDestination(t("apps"), "android.settings.MANAGE_APPLICATIONS_SETTINGS", listOf("applications", "uninstall")),
            SettingsDestination(t("storage"), "android.settings.INTERNAL_STORAGE_SETTINGS", listOf("space", "files")),
            SettingsDestination(t("location"), "android.settings.LOCATION_SOURCE_SETTINGS", listOf("gps")),
            SettingsDestination(t("security"), "android.settings.SECURITY_SETTINGS", listOf("lock", "pin", "password")),
            SettingsDestination(t("privacy"), "android.settings.PRIVACY_SETTINGS", listOf("permissions")),
            SettingsDestination(t("keyboard"), "android.settings.HARD_KEYBOARD_SETTINGS", listOf("physical", "layout", "backlight")),
            SettingsDestination(t("language"), "android.settings.LOCALE_SETTINGS", listOf("locale")),
            SettingsDestination(t("date_time"), "android.settings.DATE_SETTINGS", listOf("clock", "timezone")),
            SettingsDestination(t("accessibility"), "android.settings.ACCESSIBILITY_SETTINGS", listOf("talkback", "font")),
            SettingsDestination(t("hotspot_tethering"), "android.settings.TETHER_SETTINGS", listOf("tether", "hotspot")),
            SettingsDestination(t("about_phone"), "android.settings.DEVICE_INFO_SETTINGS", listOf("version", "build", "sableos")),
        )

    /** Title prefix first, then title contains, then keyword prefix. */
    fun settings(
        term: String,
        limit: Int = 4,
    ): List<SettingsDestination> {
        if (term.isBlank()) return emptyList()
        val t = term.trim().lowercase()
        fun score(d: SettingsDestination): Int? {
            val title = d.title.lowercase()
            return when {
                title.startsWith(t) -> 0
                title.contains(t) -> 1
                d.keywords.any { it.startsWith(t) } -> 2
                else -> null
            }
        }
        return SETTINGS.mapNotNull { d -> score(d)?.let { d to it } }
            .sortedBy { it.second }
            .take(limit)
            .map { it.first }
    }

    /**
     * Apps for a term: with no term, recents then the rest; with a term,
     * label-prefix matches before contains matches, recents first within
     * each, then everything else A to Z.
     */
    fun <T> apps(
        items: List<T>,
        label: (T) -> String,
        recentRank: (T) -> Int?,
        term: String,
    ): List<T> {
        val t = term.trim()
        if (t.isEmpty()) {
            return items.sortedWith(compareBy({ recentRank(it) ?: Int.MAX_VALUE }, { label(it).lowercase() }))
        }
        return items.mapNotNull { item ->
            val l = label(item)
            val tier =
                when {
                    l.startsWith(t, ignoreCase = true) -> 0
                    l.split(' ', '-', '.').any { it.startsWith(t, ignoreCase = true) } -> 1
                    l.contains(t, ignoreCase = true) -> 2
                    else -> null
                }
            tier?.let { Triple(item, it, recentRank(item) ?: Int.MAX_VALUE) }
        }.sortedWith(compareBy({ it.second }, { it.third }, { label(it.first).lowercase() }))
            .map { it.first }
    }
}

/**
 * The group chips over Command results ("All · Apps · People · Settings ·
 * Hub · Actions"): Left/Right on a result, or at either end of the query,
 * moves between them (sable-command-titan2 "←→ groups"). Up/Down still move
 * through one result list.
 */
object CommandGroups {
    val CHIPS: List<CommandScope> =
        listOf(
            CommandScope.All,
            CommandScope.Apps,
            CommandScope.People,
            CommandScope.Settings,
            CommandScope.Hub,
            CommandScope.Actions,
        )

    fun label(scope: CommandScope): String =
        when (scope) {
            CommandScope.All -> t("all")
            CommandScope.Apps -> t("apps")
            CommandScope.People -> t("people")
            CommandScope.Settings -> t("settings")
            CommandScope.Hub -> t("hub")
            CommandScope.Actions -> t("actions")
            CommandScope.Private -> t("private_label")
            CommandScope.Dual -> t("dual")
            CommandScope.Work -> t("work")
        }

    /** The chip [delta] away from [current], stopping at the ends; a profile scope starts from All. */
    fun next(
        current: CommandScope,
        delta: Int,
    ): CommandScope {
        val index = CHIPS.indexOf(current).coerceAtLeast(0)
        return CHIPS[(index + delta).coerceIn(0, CHIPS.lastIndex)]
    }

    /**
     * The query a chip runs: the typed prefix is replaced by the chip's own
     * scope, so `?cam` on the People chip searches people for "cam".
     */
    fun apply(
        parsed: CommandQuery,
        chip: CommandScope?,
    ): CommandQuery = if (chip == null || parsed.profileScope) parsed else CommandQuery(chip, parsed.term)
}

/**
 * The "Best match" row on top of Command results: the strongest of the first
 * app and the first other result for the term; apps win a tie.
 */
object CommandBestMatch {
    /** 0 title prefix, 1 word prefix, 2 contains, 3 anything else (a keyword or alias hit). */
    fun tier(
        title: String,
        term: String,
    ): Int {
        val t = term.trim()
        return when {
            t.isEmpty() -> NO_MATCH
            title.startsWith(t, ignoreCase = true) -> 0
            title.split(' ', '-', '.').any { it.startsWith(t, ignoreCase = true) } -> 1
            title.contains(t, ignoreCase = true) -> 2
            else -> NO_MATCH
        }
    }

    /** True when the app result is the best match, false for the other result, null for none. */
    fun appWins(
        appTitle: String?,
        otherTitle: String?,
        term: String,
    ): Boolean? {
        if (term.isBlank() || (appTitle == null && otherTitle == null)) return null
        if (otherTitle == null) return true
        if (appTitle == null) return false
        return tier(appTitle, term) <= tier(otherTitle, term)
    }

    const val NO_MATCH = 3
}
