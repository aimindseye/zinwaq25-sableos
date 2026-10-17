package org.sableos.start.model

/**
 * What the center Home page and the Glance page show
 * (SABLE_START_THREE_PAGE_MODEL.md, SABLE_START_VISUAL_CONFIRMATION.md and
 * the Titan 2 three-page visual). Pure policy, unit tested in
 * apps/r8/android/sablestart-presentation-check/src/test.
 *
 * Home, top to bottom: the Weather widget (HOME_TOP_MODULE, on by default),
 * an optional "Search or command" row (in the screen design, not in the
 * visual, so off by default), optional status rings, the user's Home apps (a list by default, tiles as the
 * optional touch mode) and the base quick bar ([QuickBar], always shown).
 */
object HomeLayout {
    const val WEATHER = "weather"
    const val SEARCH_ROW = "search"
    const val STATUS_RINGS = "rings"

    /** Home modules the user can show or hide, in screen order. */
    val SECTIONS: List<String> = listOf(WEATHER, SEARCH_ROW, STATUS_RINGS)

    /** Never customised: Weather shown; the search row and status rings hidden. */
    val DEFAULT_HIDDEN: Set<String> = setOf(SEARCH_ROW, STATUS_RINGS)

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
            WEATHER -> "Weather widget"
            SEARCH_ROW -> "Search or command row"
            STATUS_RINGS -> "Status rings"
            else -> section
        }

    fun description(section: String): String =
        when (section) {
            WEATHER -> "Weather at the top of Home"
            SEARCH_ROW -> "A Command row under the weather; typing works without it"
            STATUS_RINGS -> "Network, CPU, memory, battery and signal rings"
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
