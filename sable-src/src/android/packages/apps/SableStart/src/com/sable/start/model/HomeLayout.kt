package org.sableos.start.model

import com.sable.start.model.StartText.t
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
            WEATHER -> t("weather_and_date")
            SEARCH_ROW -> t("search_or_command_row")
            STATUS_RINGS -> t("status_dials")
            else -> section
        }

    fun description(section: String): String =
        when (section) {
            WEATHER -> t("temperature_condition_and_place_with")
            SEARCH_ROW -> t("a_command_row_under_the")
            STATUS_RINGS -> t("wi_fi_mobile_cpu_ram")
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

    /** This month with Sable Calendar's busy rings and the next events; a full-width frame. */
    const val MONTH = "month"

    val ALL: List<String> = listOf(MONTH, WEATHER, CALENDAR, BATTERY, MEDIA, HUB, MAIL, PHOTOS)

    /**
     * "Curated widgets": Month (owner decision 2026-11-04, in place of the one-line Calendar frame,
     * which Edit Home can still add), Weather, Battery, Media.
     */
    val DEFAULT: List<String> = listOf(MONTH, WEATHER, BATTERY, MEDIA)

    /** Widgets drawn across both columns. */
    val FULL_WIDTH: Set<String> = setOf(MONTH)

    /** Frame rows in order: a full-width widget alone, the others two to a row. */
    fun rows(
        shown: List<String>,
        columns: Int,
    ): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var pending = mutableListOf<String>()
        shown.forEach { widget ->
            if (widget in FULL_WIDTH) {
                if (pending.isNotEmpty()) rows += pending
                pending = mutableListOf()
                rows += listOf(widget)
            } else {
                pending += widget
                if (pending.size == columns) {
                    rows += pending
                    pending = mutableListOf()
                }
            }
        }
        if (pending.isNotEmpty()) rows += pending
        return rows
    }

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
            WEATHER -> t("weather")
            CALENDAR -> t("calendar")
            MONTH -> t("month")
            BATTERY -> t("battery")
            MEDIA -> t("media")
            HUB -> t("hub")
            MAIL -> t("mail")
            PHOTOS -> t("photos")
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
            LiveAvailability.LOADING -> t("weather_updating")
            LiveAvailability.ERROR -> t("weather_update_failed_retrying")
            else -> t("weather_open_sable_weather_to")
        }

    /** Milliseconds from [nowMillisOfDay] to the next midnight, when the date line changes. */
    fun millisToMidnight(nowMillisOfDay: Long): Long = (DAY_MS - nowMillisOfDay.mod(DAY_MS)).coerceAtLeast(1L)

    private const val DAY_MS = 24L * 60 * 60 * 1000
}

/**
 * The BB10 app grid's size and labels, set in Edit Home (owner request 2026-11-05, after
 * JLauncher's Home screen options; ideas only): 4 to 6 columns, Fit or 3 to 5 rows, app
 * names on or off. Saved as "columns:rows:names"; anything unreadable gives the default.
 */
data class GridShape(
    val columns: Int = DEFAULT_COLUMNS,
    /** [FIT]: as many rows as the screen holds ([AppGrid.rowsFor]). */
    val rows: Int = FIT,
    val names: Boolean = true,
) {
    fun encode(): String = "$columns:$rows:${if (names) 1 else 0}"

    fun nextColumns(): GridShape = copy(columns = if (columns >= MAX_COLUMNS) MIN_COLUMNS else columns + 1)

    fun nextRows(): GridShape =
        copy(
            rows =
                when {
                    rows == FIT -> MIN_ROWS
                    rows >= MAX_ROWS -> FIT
                    else -> rows + 1
                },
        )

    fun toggleNames(): GridShape = copy(names = !names)

    /** The rows to draw when the screen fits [fitted]. */
    fun rowsFor(fitted: Int): Int = if (rows == FIT) fitted else rows

    companion object {
        const val FIT = 0
        const val MIN_COLUMNS = 4
        const val MAX_COLUMNS = 6
        const val DEFAULT_COLUMNS = 5
        const val MIN_ROWS = 3
        const val MAX_ROWS = 5

        fun decode(text: String?): GridShape {
            val parts = text.orEmpty().split(':')
            if (parts.size != 3) return GridShape()
            val columns = parts[0].toIntOrNull()?.takeIf { it in MIN_COLUMNS..MAX_COLUMNS } ?: DEFAULT_COLUMNS
            val rows = parts[1].toIntOrNull()?.takeIf { it == FIT || it in MIN_ROWS..MAX_ROWS } ?: FIT
            return GridShape(columns, rows, parts[2] != "0")
        }
    }
}

/**
 * Active Frames options in Edit Home (after JLauncher's Active Frames page; ideas only): at most
 * 2, 4, 6 or 8 frames, and live frames, which show each app's last picture; off shows only its
 * icon and name, so no picture is kept. Saved as "max:live".
 */
data class FramesOptions(
    val max: Int = ActiveFrames.MAX,
    val live: Boolean = true,
) {
    fun encode(): String = "$max:${if (live) 1 else 0}"

    fun nextMax(): FramesOptions = copy(max = if (max >= ActiveFrames.MAX) STEP else max + STEP)

    fun toggleLive(): FramesOptions = copy(live = !live)

    companion object {
        const val STEP = 2

        fun decode(text: String?): FramesOptions {
            val parts = text.orEmpty().split(':')
            if (parts.size != 2) return FramesOptions()
            val max = parts[0].toIntOrNull()?.takeIf { it in STEP..ActiveFrames.MAX && it % STEP == 0 } ?: ActiveFrames.MAX
            return FramesOptions(max, parts[1] != "0")
        }
    }
}

/**
 * Hidden apps (after JLauncher's Hidden apps; ideas only): Menu on an app hides it from the
 * app grid, All Apps and search. Searching "private" or "hidden" (or "/private") lists them,
 * where Menu shows it again. Hiding is not a lock: the app still runs and still notifies.
 */
object HiddenApps {
    private val WORDS = setOf("private", "hidden")

    /** Whether a search for [term] lists the hidden apps. */
    fun reveals(term: String): Boolean = term.trim().lowercase() in WORDS

    fun toggle(
        keys: Set<String>,
        key: String,
    ): Set<String> = if (key in keys) keys - key else keys + key
}
