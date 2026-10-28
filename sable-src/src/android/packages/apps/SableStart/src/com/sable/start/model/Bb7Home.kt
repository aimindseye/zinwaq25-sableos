package org.sableos.start.model

/**
 * Sable Start's two home styles (owner request, 2026-10-28): the BlackBerry 10 layout (Hub,
 * Active Frames and app pages) and the BlackBerry 7 one (clock and status on the wallpaper over a
 * tray of apps). The choice is made in Sable Setup or from Start's own menu. Pure policy, unit
 * tested in apps/r8/android/sablestart-presentation-check/src/test.
 */
enum class HomeStyle(
    val stableValue: String,
    val title: String,
    val summary: String,
) {
    Bb10(
        "bb10",
        "BlackBerry 10 style",
        "Hub, Active Frames and app pages side by side",
    ),
    Bb7(
        "bb7",
        "BlackBerry 7 style",
        "Clock and status on the wallpaper, a tray of apps that opens into all apps",
    ),
    ;

    companion object {
        val DEFAULT = Bb10

        fun fromStableValue(value: String?): HomeStyle = entries.firstOrNull { it.stableValue == value } ?: DEFAULT
    }
}

/** The BlackBerry 7 tray's panels, switched with Alt+Left/Right or Space, as on BlackBerry 7. */
enum class Bb7Panel(
    val title: String,
) {
    Favorites("Favorites"),
    All("All"),
    Downloads("Downloads"),
}

/** Where a horizontal swipe on the BlackBerry 7 home goes: Hub is to the left, the apps to the right. */
enum class Bb7Swipe { Hub, Apps }

object Bb7Home {
    /** Icon cell width the tray is laid out from: 6 columns on the Q25's 411 dp. */
    const val CELL_DP = 64
    const val MIN_COLUMNS = 4
    const val MAX_COLUMNS = 8

    /** Rows of icons the closed tray shows on the home screen; one more opens the whole panel. */
    const val MIN_HOME_ROWS = 1
    const val MAX_HOME_ROWS = 3

    /** Favourites when the user has none yet: the apps a BlackBerry 7 home tray started with. */
    val DEFAULT_FAVORITE_PACKAGES =
        listOf(
            "com.android.dialer",
            "org.sableos.messages",
            "org.sableos.mail",
            "org.sableos.titan2.camera",
            "org.sableos.calendar",
            "org.lineageos.jelly",
            "org.sableos.media",
            "com.android.settings",
        )

    fun columns(widthDp: Int): Int = (widthDp / CELL_DP).coerceIn(MIN_COLUMNS, MAX_COLUMNS)

    /**
     * The apps of one panel, in order. Favorites: the user's Home apps ([favoriteKeys], stable
     * keys) or, without any, [DEFAULT_FAVORITE_PACKAGES] that are installed; All: every app by
     * name; Downloads: apps the user installed (removable ones).
     */
    fun panelApps(
        panel: Bb7Panel,
        apps: List<AppEntry>,
        favoriteKeys: List<String>?,
    ): List<AppEntry> {
        val byName = apps.sortedBy { it.label.lowercase() }
        return when (panel) {
            Bb7Panel.All -> byName
            Bb7Panel.Downloads -> byName.filter { it.canUninstall }
            Bb7Panel.Favorites -> {
                val byKey = apps.associateBy { it.stableKey() }
                val chosen = favoriteKeys.orEmpty().mapNotNull { byKey[it] }
                chosen.ifEmpty {
                    DEFAULT_FAVORITE_PACKAGES.mapNotNull { pkg -> byName.firstOrNull { it.component.packageName == pkg } }
                }
            }
        }
    }

    /** The next panel left ([step] -1) or right (+1), wrapping. */
    fun step(
        panel: Bb7Panel,
        step: Int,
    ): Bb7Panel {
        val all = Bb7Panel.entries
        return all[(panel.ordinal + step).mod(all.size)]
    }

    /**
     * Typing on the home screen searches apps, as on BlackBerry 7: names starting with the text
     * first, then names with a word starting with it, then any match.
     */
    fun search(
        apps: List<AppEntry>,
        query: String,
    ): List<AppEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val byName = apps.sortedBy { it.label.lowercase() }
        val starts = byName.filter { it.label.lowercase().startsWith(q) }
        val word = byName.filter { it !in starts && it.label.lowercase().split(' ').any { w -> w.startsWith(q) } }
        val any = byName.filter { it !in starts && it !in word && q in it.label.lowercase() }
        return starts + word + any
    }

    /**
     * Moving the selection in a grid of [count] items and [columns] columns: Left/Right by one,
     * Up/Down by a row, clamped to the items.
     */
    fun move(
        index: Int,
        count: Int,
        columns: Int,
        dx: Int,
        dy: Int,
    ): Int {
        if (count <= 0) return 0
        return (index + dx + dy * columns).coerceIn(0, count - 1)
    }

    /**
     * A finished horizontal drag of [dragged] px (positive = finger moved right): far enough
     * ([threshold] px) to the right reveals Hub, to the left the apps, as on the BlackBerry 10 home.
     */
    fun swipe(
        dragged: Float,
        threshold: Float,
    ): Bb7Swipe? =
        when {
            dragged >= threshold -> Bb7Swipe.Hub
            dragged <= -threshold -> Bb7Swipe.Apps
            else -> null
        }

    /**
     * The closed tray's rows after a vertical drag of [dragged] px (negative = finger moved up),
     * one row per [threshold] px, as the BlackBerry 7 home let users drag the rows of apps up or
     * down. Above [MAX_HOME_ROWS] means open the whole panel; the result is never under
     * [MIN_HOME_ROWS].
     */
    fun resizeRows(
        rows: Int,
        dragged: Float,
        threshold: Float,
    ): Int {
        if (threshold <= 0f) return rows
        val steps = (-dragged / threshold).toInt()
        return (rows + steps).coerceIn(MIN_HOME_ROWS, MAX_HOME_ROWS + 1)
    }

    /** Favourites as stable keys after adding or removing [key]; [current] is what the panel shows now. */
    fun toggleFavorite(
        current: List<String>,
        key: String,
    ): List<String> = HomeApps.toggle(current, key)
}
