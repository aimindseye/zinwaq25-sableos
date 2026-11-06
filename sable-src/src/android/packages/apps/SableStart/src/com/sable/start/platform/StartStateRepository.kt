package org.sableos.start.platform

import android.content.Context
import org.sableos.start.model.Bb7Home
import org.sableos.start.model.Bb7Layout
import org.sableos.start.model.Bb7Tab
import org.sableos.start.model.Bb7Tabs
import org.sableos.start.model.FramesOptions
import org.sableos.start.model.GridFolder
import org.sableos.start.model.GridFolders
import org.sableos.start.model.GridShape
import org.sableos.start.model.HomeStyle

/**
 * Stores launcher-only presentation state.
 *
 * Global Sable appearance is intentionally NOT stored here. Appearance is
 * owned by the privileged Settings-hosted org.sableos.appearance provider so
 * Launcher3 cannot become the operating system's theme authority.
 *
 * Recent activity intentionally stays in-memory so Sable Start does not build
 * a persistent app-usage history.
 */
class StartStateRepository(
    context: Context,
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    fun loadFavoriteKeys(): Set<String> {
        val explicit =
            preferences.getStringSet(
                KEY_FAVORITE_APPS,
                null,
            )
        if (explicit != null) {
            return explicit.toSet()
        }

        return preferences
            .getStringSet(
                KEY_PINNED_APPS_LEGACY,
                emptySet(),
            )
            .orEmpty()
            .toSet()
    }

    fun saveFavoriteKeys(keys: Set<String>) {
        preferences
            .edit()
            .putStringSet(
                KEY_FAVORITE_APPS,
                keys.toSet(),
            )
            .remove(KEY_PINNED_APPS_LEGACY)
            .apply()
    }

    /**
     * Null means the user has never customized Start tiles and the production
     * UI should resolve the current preferred/default set from installed apps.
     * An empty set is an explicit user choice to keep Start tile-free.
     */
    fun loadStartTileKeys(): Set<String>? {
        if (!preferences.contains(KEY_START_TILES)) {
            return null
        }

        return preferences
            .getStringSet(
                KEY_START_TILES,
                emptySet(),
            )
            .orEmpty()
            .toSet()
    }

    fun saveStartTileKeys(keys: Set<String>) {
        preferences
            .edit()
            .putStringSet(
                KEY_START_TILES,
                keys.toSet(),
            )
            .apply()
    }

    /**
     * Ordered quick bar slot tokens (see QuickBar). Null means never
     * customised, so the UI shows the default bar. Stored as one string
     * because a string set would lose the order.
     */
    fun loadQuickBar(): List<String>? =
        preferences
            .getString(KEY_QUICK_BAR, null)
            ?.split(QUICK_BAR_SEPARATOR)
            ?.filter { it.isNotEmpty() }

    fun saveQuickBar(tokens: List<String>?) {
        preferences
            .edit()
            .apply {
                if (tokens == null) {
                    remove(KEY_QUICK_BAR)
                } else {
                    putString(KEY_QUICK_BAR, tokens.joinToString(QUICK_BAR_SEPARATOR))
                }
            }.apply()
    }

    /** Ordered Home apps (see HomeApps); null means never customised. */
    fun loadHomeApps(): List<String>? = loadOrdered(KEY_HOME_APPS)

    fun saveHomeApps(keys: List<String>?) = saveOrdered(KEY_HOME_APPS, keys)

    /** Rows of icons on the BlackBerry 7 home screen (see Bb7Home.MAX_HOME_ROWS). */
    fun loadBb7HomeRows(): Int =
        preferences.getInt(KEY_BB7_HOME_ROWS, Bb7Home.MIN_HOME_ROWS).coerceIn(Bb7Home.MIN_HOME_ROWS, Bb7Home.MAX_HOME_ROWS)

    fun saveBb7HomeRows(rows: Int) {
        preferences.edit().putInt(KEY_BB7_HOME_ROWS, rows).apply()
    }

    /** The BlackBerry 7 tray's view, grid columns and icon size (Bb7Layout). */
    fun loadBb7Layout(): Bb7Layout = Bb7Layout.decode(preferences.getString(KEY_BB7_LAYOUT, null))

    fun saveBb7Layout(layout: Bb7Layout) {
        preferences.edit().putString(KEY_BB7_LAYOUT, layout.encode()).apply()
    }

    /** The user's own BlackBerry 7 tray tabs (see Bb7Tabs). */
    fun loadBb7Tabs(): List<Bb7Tab> = Bb7Tabs.decode(preferences.getString(KEY_BB7_TABS, null))

    fun saveBb7Tabs(tabs: List<Bb7Tab>) {
        preferences.edit().putString(KEY_BB7_TABS, Bb7Tabs.encode(tabs)).apply()
    }

    /** The app grid order (AppGrid, the BB10 layout); null means never customised. */
    fun loadGridOrder(): List<String>? = loadOrdered(KEY_GRID_ORDER)

    fun saveGridOrder(keys: List<String>?) = saveOrdered(KEY_GRID_ORDER, keys)

    /** The BB7 home screen's own shortcuts, between the banner and the tray (see Bb7Shortcuts). */
    fun loadBb7Shortcuts(): List<String> = loadOrdered(KEY_BB7_SHORTCUTS).orEmpty()

    fun saveBb7Shortcuts(keys: List<String>) = saveOrdered(KEY_BB7_SHORTCUTS, keys)

    /** The folders on the BB10 app grid (see GridFolders). */
    fun loadGridFolders(): List<GridFolder> = GridFolders.decode(preferences.getString(KEY_GRID_FOLDERS, null))

    fun saveGridFolders(folders: List<GridFolder>) {
        preferences.edit().putString(KEY_GRID_FOLDERS, GridFolders.encode(folders)).apply()
    }

    /** The app grid's columns, rows and labels (see GridShape). */
    fun loadGridShape(): GridShape = GridShape.decode(preferences.getString(KEY_GRID_SHAPE, null))

    fun saveGridShape(shape: GridShape) {
        preferences.edit().putString(KEY_GRID_SHAPE, shape.encode()).apply()
    }

    /** Active Frames' maximum and live pictures (see FramesOptions). */
    fun loadFramesOptions(): FramesOptions = FramesOptions.decode(preferences.getString(KEY_FRAMES_OPTIONS, null))

    fun saveFramesOptions(options: FramesOptions) {
        preferences.edit().putString(KEY_FRAMES_OPTIONS, options.encode()).apply()
    }

    /** Apps hidden from the grid, All Apps and search (see HiddenApps). */
    fun loadHiddenApps(): Set<String> = preferences.getStringSet(KEY_HIDDEN_APPS, null)?.toSet().orEmpty()

    fun saveHiddenApps(keys: Set<String>) {
        preferences.edit().putStringSet(KEY_HIDDEN_APPS, keys.toSet()).apply()
    }

    /** Ordered Glance widgets, shown as Active Frames while no app is running (see GlanceWidgets); null means never customised. */
    fun loadGlanceWidgets(): List<String>? = loadOrdered(KEY_GLANCE_WIDGETS)

    fun saveGlanceWidgets(widgets: List<String>?) = saveOrdered(KEY_GLANCE_WIDGETS, widgets)

    /** Home modules the user hid in Edit Home (see HomeLayout); null means never customised. */
    fun loadHiddenHomeSections(): Set<String>? =
        if (preferences.contains(KEY_HOME_HIDDEN_SECTIONS)) {
            preferences.getStringSet(KEY_HOME_HIDDEN_SECTIONS, emptySet()).orEmpty().toSet()
        } else {
            null
        }

    fun saveHiddenHomeSections(sections: Set<String>?) {
        preferences
            .edit()
            .apply {
                if (sections == null) {
                    remove(KEY_HOME_HIDDEN_SECTIONS)
                } else {
                    putStringSet(KEY_HOME_HIDDEN_SECTIONS, sections.toSet())
                }
            }.apply()
    }

    /** "list" or "tiles" (see HomeLayout); null means never customised. */
    fun loadHomeMode(): String? = preferences.getString(KEY_HOME_MODE, null)

    fun saveHomeMode(mode: String) {
        preferences.edit().putString(KEY_HOME_MODE, mode).apply()
    }

    /** BlackBerry 10 or BlackBerry 7 home (HomeStyle); the BB10 layout until the user picks. */
    fun loadHomeStyle(): HomeStyle = HomeStyle.fromStableValue(preferences.getString(KEY_HOME_STYLE, null))

    fun saveHomeStyle(style: HomeStyle) {
        preferences.edit().putString(KEY_HOME_STYLE, style.stableValue).apply()
    }

    private fun loadOrdered(key: String): List<String>? =
        preferences
            .getString(key, null)
            ?.split(QUICK_BAR_SEPARATOR)
            ?.filter { it.isNotEmpty() }

    private fun saveOrdered(
        key: String,
        values: List<String>?,
    ) {
        preferences
            .edit()
            .apply {
                if (values == null) {
                    remove(key)
                } else {
                    putString(key, values.joinToString(QUICK_BAR_SEPARATOR))
                }
            }.apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "sable_start_state"
        const val KEY_FAVORITE_APPS = "favorite_apps"
        const val KEY_START_TILES = "start_tiles"
        const val KEY_PINNED_APPS_LEGACY = "pinned_apps"
        const val KEY_QUICK_BAR = "quick_bar"
        const val QUICK_BAR_SEPARATOR = "\n"
        const val KEY_HOME_APPS = "home_apps"
        const val KEY_BB7_HOME_ROWS = "bb7_home_rows"
        const val KEY_BB7_LAYOUT = "bb7_layout"
        const val KEY_BB7_TABS = "bb7_tabs"
        const val KEY_BB7_SHORTCUTS = "bb7_shortcuts"
        const val KEY_GLANCE_WIDGETS = "glance_widgets"
        // v2: the BB10 Home shows the status dials by default; sets saved by the
        // three-page Home hid them without the user choosing, so they are not read.
        const val KEY_HOME_HIDDEN_SECTIONS = "home_hidden_sections_v2"
        const val KEY_HOME_MODE = "home_mode"
        const val KEY_HOME_STYLE = "home_style"
        const val KEY_GRID_ORDER = "grid_order"
        const val KEY_GRID_FOLDERS = "grid_folders"
        const val KEY_GRID_SHAPE = "grid_shape"
        const val KEY_FRAMES_OPTIONS = "frames_options"
        const val KEY_HIDDEN_APPS = "hidden_apps"
    }
}
