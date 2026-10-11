package org.sableos.start.platform

import android.content.Context

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

    private companion object {
        const val PREFERENCES_NAME = "sable_start_state"
        const val KEY_FAVORITE_APPS = "favorite_apps"
        const val KEY_START_TILES = "start_tiles"
        const val KEY_PINNED_APPS_LEGACY = "pinned_apps"
        const val KEY_QUICK_BAR = "quick_bar"
        const val QUICK_BAR_SEPARATOR = "\n"
    }
}
