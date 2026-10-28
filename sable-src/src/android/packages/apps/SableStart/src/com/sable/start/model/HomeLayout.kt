package org.sableos.start.model

import org.sableos.start.live.LiveAvailability

/**
 * The header over Active Frames (the BB10 Home) and what Edit Home can hide.
 * Pure policy, unit tested in
 * apps/r8/android/sablestart-presentation-check/src/test.
 *
 * Top to bottom: the status dials (Wi-Fi, Mobile, CPU, RAM, battery time
 * left, Cell, and the Settings gear; on by default, owner request), the
 * location's weather (temperature, condition, place) with the date under it
 * in place of a clock (the status bar keeps the only clock, so the two never
 * disagree), an optional Search or command row (off by default), then the
 * frames. No greeting.
 */
object HomeLayout {
    const val WEATHER = "weather"
    const val SEARCH_ROW = "search"
    const val STATUS_RINGS = "rings"

    /** Home modules the user can show or hide, in screen order. */
    val SECTIONS: List<String> = listOf(STATUS_RINGS, WEATHER, SEARCH_ROW)

    /** Never customised: status dials and weather shown; the search row hidden. */
    val DEFAULT_HIDDEN: Set<String> = setOf(SEARCH_ROW)

    /** Keyboard-first list mode (default) or the optional touch tile mode. */
    const val MODE_LIST = "list"
    const val MODE_TILES = "tiles"

    fun normalizeMode(stored: String?): String = if (stored == MODE_TILES) MODE_TILES else MODE_LIST

    /** Drops unknown names; null (never customised) gives [DEFAULT_HIDDEN]. */
    fun normalizeHidden(stored: Set<String>?): Set<String> = stored?.filter { it in SECTIONS }?.toSet() ?: DEFAULT_HIDDEN

    fun isShown(
        hidden: Set<String>,
        section: String,
    ): Boolean = section !in hidden

    /** Shows a hidden module or hides a shown one; unknown names change nothing. */
    fun toggle(
        hidden: Set<String>,
        section: String,
    ): Set<String> =
        when {
            section !in SECTIONS -> hidden
            section in hidden -> hidden - section
            else -> hidden + section
        }

    fun label(section: String): String =
        when (section) {
            WEATHER -> "Weather and date"
            SEARCH_ROW -> "Search or command row"
            STATUS_RINGS -> "Status dials"
            else -> section
        }

    fun description(section: String): String =
        when (section) {
            WEATHER -> "Temperature, condition and place, with the date under it"
            SEARCH_ROW -> "A Command row under the weather; typing works without it"
            STATUS_RINGS -> "Wi-Fi, Mobile, CPU, RAM, battery time left, Cell and Settings"
            else -> ""
        }
}

/**
 * The user's ordered Home apps ("user-selected Home apps", HOME_APPS_REORDER).
 * Keys are AppEntry stable keys.
 */
object HomeApps {
    /**
     * The saved order; else the start tiles chosen before Home became a list
     * (put in [defaults] order, the rest after); else [defaults].
     */
    fun resolve(
        saved: List<String>?,
        legacyTiles: Set<String>?,
        defaults: List<String>,
    ): List<String> {
        if (saved != null) return saved.filter { it.isNotEmpty() }.distinct()
        if (legacyTiles != null) {
            return defaults.filter { it in legacyTiles } + legacyTiles.filterNot { it in defaults }.sorted()
        }
        return defaults.distinct()
    }

    /** Adds [key] at the end, or removes it when present (Peek's pin / remove). */
    fun toggle(
        keys: List<String>,
        key: String,
    ): List<String> = if (key in keys) keys.filterNot { it == key } else keys + key

    /** Swaps the item at [index] with its neighbour [delta] away; out of range changes nothing. */
    fun move(
        keys: List<String>,
        index: Int,
        delta: Int,
    ): List<String> {
        val target = index + delta
        if (index !in keys.indices || target !in keys.indices) return keys
        return keys.toMutableList().also { list ->
            list[index] = list[target].also { list[target] = list[index] }
        }
    }
}

/**
 * Curated Glance widgets on the left page (CURATED_SABLE_WIDGETS,
 * REORDER_WIDGETS, HIDE_WIDGETS, FREEFORM_PLACEMENT=NO). The list is the
 * shown widgets in order; the rest can be added back in Edit Home.
 */
object GlanceWidgets {
    const val WEATHER = "weather"
    const val CALENDAR = "calendar"
    const val BATTERY = "battery"
    const val MEDIA = "media"
    const val HUB = "hub"
    const val MAIL = "mail"
    const val PHOTOS = "photos"

    val ALL: List<String> = listOf(WEATHER, CALENDAR, BATTERY, MEDIA, HUB, MAIL, PHOTOS)

    /** "Curated widgets: Weather, Calendar, Battery, Media." */
    val DEFAULT: List<String> = listOf(WEATHER, CALENDAR, BATTERY, MEDIA)

    /** Drops unknown and duplicate names; null (never customised) gives [DEFAULT]. */
    fun normalize(saved: List<String>?): List<String> = saved?.filter { it in ALL }?.distinct() ?: DEFAULT

    fun toggle(
        shown: List<String>,
        widget: String,
    ): List<String> =
        when {
            widget !in ALL -> shown
            widget in shown -> shown - widget
            else -> shown + widget
        }

    fun label(widget: String): String =
        when (widget) {
            WEATHER -> "Weather"
            CALENDAR -> "Calendar"
            BATTERY -> "Battery"
            MEDIA -> "Media"
            HUB -> "Hub"
            MAIL -> "Mail"
            PHOTOS -> "Photos"
            else -> widget
        }
}

/**
 * The weather and date block at the top of Active Frames, in place of a
 * clock. Sable Weather's snapshot line is "Place · 72° · Condition".
 */
object HomeHeader {
    /** Skeleton for the date line under the weather ("Wednesday, October 21" in English). */
    const val DATE_SKELETON = "EEEEMMMMd"

    /**
     * ("72° Partly cloudy", "Seattle") from the snapshot line, or null when
     * the line has no temperature (still loading, no city, no permission).
     */
    fun weather(detail: String): Pair<String, String>? {
        val parts = detail.split(" · ").map { it.trim() }.filter { it.isNotEmpty() }
        val tempIndex = parts.indexOfFirst { it.endsWith("°") }
        if (tempIndex < 0) return null
        val condition = parts.getOrNull(tempIndex + 1)
        val headline = listOfNotNull(parts[tempIndex], condition).joinToString(" ")
        val place = parts.filterIndexed { i, _ -> i != tempIndex && i != tempIndex + 1 }.joinToString(" · ")
        return headline to place
    }

    /**
     * A weather symbol for the condition in a [weather] headline ("18° partly cloudy" gives ⛅), so the
     * home header reads as icon and temperature; an unknown condition gets a thermometer.
     */
    fun weatherIcon(headline: String): String {
        val text = headline.lowercase()
        return when {
            "storm" in text -> "⛈"
            "snow" in text -> "❄"
            "rain" in text -> "🌧"
            "fog" in text -> "🌫"
            "partly" in text -> "⛅"
            "cloud" in text -> "☁"
            "clear" in text -> "☀"
            else -> "🌡"
        }
    }

    /**
     * The header line while there is no forecast to show, saying what to do: Sable Weather could not be
     * read (open it once to choose a city), or its last update failed (it retries on its own).
     */
    fun weatherFallback(availability: LiveAvailability): String =
        when (availability) {
            LiveAvailability.LOADING -> "Weather · updating"
            LiveAvailability.ERROR -> "Weather · update failed, retrying"
            else -> "Weather · open Sable Weather to set it up"
        }

    /** Milliseconds from [nowMillisOfDay] to the next midnight, when the date line changes. */
    fun millisToMidnight(nowMillisOfDay: Long): Long = (DAY_MS - nowMillisOfDay.mod(DAY_MS)).coerceAtLeast(1L)

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
