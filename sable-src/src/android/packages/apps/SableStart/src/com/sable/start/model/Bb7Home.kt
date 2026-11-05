package org.sableos.start.model

import com.sable.start.model.StartText.t

/**
 * Sable Start's two home styles (owner request, 2026-10-28): the BlackBerry 10 layout (Hub,
 * Active Frames and app pages) and the BlackBerry 7 one (clock and status on the wallpaper over a
 * tray of apps). The choice is made in Sable Setup or from Start's own menu. Pure policy, unit
 * tested in apps/r8/android/sablestart-presentation-check/src/test.
 */
enum class HomeStyle(
    val stableValue: String,
    private val titleKey: String,
    private val summaryKey: String,
) {
    Bb10(
        "bb10",
        "blackberry_10_style",
        "hub_active_frames_and_app",
    ),
    Bb7(
        "bb7",
        "blackberry_7_style",
        "clock_and_status_on_the",
    ),
    ;

    val title: String get() = t(titleKey)
    val summary: String get() = t(summaryKey)

    companion object {
        val DEFAULT = Bb10

        fun fromStableValue(value: String?): HomeStyle = entries.firstOrNull { it.stableValue == value } ?: DEFAULT
    }
}

/** The BlackBerry 7 tray's panels, switched with Alt+Left/Right or Space, as on BlackBerry 7. */
enum class Bb7Panel(
    private val titleKey: String,
) {
    Favorites("favorites"),
    All("all"),
    Downloads("downloads"),
    ;

    val title: String get() = t(titleKey)
}

/** How the open tray lists apps: icons in a grid, or BlackBerry 7's list of icon and name rows. */
enum class Bb7TrayView(
    private val titleKey: String,
) {
    Grid("view_grid"),
    List("view_list"),
    ;

    val title: String get() = t(titleKey)
}

/** Tray icon sizes, in dp. */
enum class Bb7IconSize(
    val dp: Int,
    private val titleKey: String,
) {
    Small(32, "icon_small"),
    Medium(40, "icon_medium"),
    Large(48, "icon_large"),
    ;

    val title: String get() = t(titleKey)
}

/**
 * The tray's look: [view] for the open tray (the closed tray on the home screen is always icons),
 * [columns] for the grid ([AUTO] fits the screen) and [iconSize].
 */
data class Bb7Layout(
    val view: Bb7TrayView = Bb7TrayView.Grid,
    val columns: Int = AUTO,
    val iconSize: Bb7IconSize = Bb7IconSize.Medium,
) {
    /** Saved as "view:columns:size"; anything unreadable gives the default. */
    fun encode(): String = "${view.name}:$columns:${iconSize.name}"

    companion object {
        const val AUTO = 0

        private val CHOSEN = Bb7Home.MIN_COLUMNS..Bb7Home.MAX_COLUMNS

        private fun validColumns(columns: Int): Boolean = columns == AUTO || columns in CHOSEN

        fun decode(saved: String?): Bb7Layout {
            val parts = saved?.split(':').orEmpty()
            if (parts.size != 3) return Bb7Layout()
            return Bb7Layout(
                view = Bb7TrayView.entries.firstOrNull { it.name == parts[0] } ?: Bb7TrayView.Grid,
                columns = parts[1].toIntOrNull()?.takeIf(::validColumns) ?: AUTO,
                iconSize = Bb7IconSize.entries.firstOrNull { it.name == parts[2] } ?: Bb7IconSize.Medium,
            )
        }
    }
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

    /** Room around an icon in its cell; a Medium icon gives [CELL_DP]. */
    private const val CELL_PAD_DP = CELL_DP - 40

    /** Columns for [layout]: one in the open list view, the chosen number, or as many as fit. */
    fun columns(
        widthDp: Int,
        layout: Bb7Layout,
        open: Boolean,
    ): Int =
        when {
            open && layout.view == Bb7TrayView.List -> 1
            layout.columns != Bb7Layout.AUTO -> layout.columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
            else -> (widthDp / (layout.iconSize.dp + CELL_PAD_DP)).coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        }

    /** The next choice in the home menu: Auto, 4 ... 8, then Auto again. */
    fun nextColumns(columns: Int): Int =
        when (columns) {
            Bb7Layout.AUTO -> MIN_COLUMNS
            in MIN_COLUMNS until MAX_COLUMNS -> columns + 1
            else -> Bb7Layout.AUTO
        }

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
        val q = SearchFold.fold(query.trim())
        if (q.isEmpty()) return emptyList()
        val byName = apps.sortedBy { it.label.lowercase() }
        val name = byName.associateWith { SearchFold.fold(it.label) }
        val starts = byName.filter { name.getValue(it).startsWith(q) }
        val word = byName.filter { it !in starts && name.getValue(it).split(' ').any { w -> w.startsWith(q) } }
        val any = byName.filter { it !in starts && it !in word && q in name.getValue(it) }
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

/**
 * A visit from the BlackBerry 7 home to the BlackBerry 10 screen for Hub or Edit Home. It ends,
 * back on the BlackBerry 7 home, once the BlackBerry 10 screen has left its home page and come
 * back to it (a swipe or Back from Hub, Done in Edit Home). A Recents visit (Active Frames, the
 * BlackBerry 10 home page itself) does not [returnOnHome]; Home ends it.
 */
data class Bb7Detour(
    val returnOnHome: Boolean = false,
    val leftHome: Boolean = false,
) {
    /** The BlackBerry 10 screen reported whether Back has somewhere to go (it is off its home page). */
    fun onCanNavigateBack(canNavigateBack: Boolean): Bb7Detour = if (canNavigateBack) copy(leftHome = true) else this

    /** True when the BlackBerry 10 screen is back on its home page after leaving it. */
    fun endsAt(canNavigateBack: Boolean): Boolean = returnOnHome && leftHome && !canNavigateBack
}
