package org.sableos.start.model

/**
 * What the Home page shows (Q25 owner request: "Sable Home page need to be
 * configurable"). Pure policy, unit tested in
 * apps/r8/android/sablestart-presentation-check/src/test.
 *
 * Home is built from the sections below, top to bottom. The user hides or
 * shows each one in Home settings; the hidden set is stored, so a section
 * added later is shown until the user hides it. Which cards appear is the
 * Start tile set (StartStateRepository start tiles), which apps sit in the
 * favorites row is the favorites set, and the quick bar slots are [QuickBar].
 */
object HomeLayout {
    const val CLOCK = "clock"
    const val STATUS_RINGS = "rings"
    const val GREETING = "greeting"
    const val CARDS = "cards"
    const val FAVORITES = "favorites"
    const val QUICK_BAR = "quickbar"

    /** Home's sections in screen order. */
    val SECTIONS: List<String> = listOf(CLOCK, STATUS_RINGS, GREETING, CARDS, FAVORITES, QUICK_BAR)

    /** Drops unknown names; null (never customised) shows every section. */
    fun normalizeHidden(stored: Set<String>?): Set<String> = stored.orEmpty().filter { it in SECTIONS }.toSet()

    fun isShown(
        hidden: Set<String>,
        section: String,
    ): Boolean = section !in hidden

    /** Shows a hidden section or hides a shown one; unknown names change nothing. */
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
            CLOCK -> "Clock and date"
            STATUS_RINGS -> "Status rings"
            GREETING -> "Greeting"
            CARDS -> "Cards"
            FAVORITES -> "Favorites"
            QUICK_BAR -> "Quick bar"
            else -> section
        }

    fun description(section: String): String =
        when (section) {
            CLOCK -> "Time and date at the top"
            STATUS_RINGS -> "Network, CPU, memory, battery and signal rings"
            GREETING -> "Good morning, good evening"
            CARDS -> "Calendar, Hub, Weather, Now Playing and other app cards"
            FAVORITES -> "Up to four favorite apps under the cards"
            QUICK_BAR -> "Phone, Hub and other slots along the bottom"
            else -> ""
        }

    /**
     * Adds or removes one card. [explicit] is the saved card set, or null when
     * the user never chose and Home shows [defaults]; the first change starts
     * from what Home showed.
     */
    fun toggleCard(
        explicit: List<String>?,
        defaults: List<String>,
        key: String,
    ): List<String> {
        val current = explicit ?: defaults
        return if (key in current) current.filterNot { it == key } else current + key
    }
}
