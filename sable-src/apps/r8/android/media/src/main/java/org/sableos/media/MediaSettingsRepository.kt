package org.sableos.media

import android.content.Context

internal enum class MediaStartPage {
    Collection,
    Radio,
}

internal data class MediaSettings(
    val startPage: MediaStartPage = MediaStartPage.Collection,
    val showMiniPlayer: Boolean = true,
    val keepScreenAwakeWhilePlaying: Boolean = false,
)

internal class MediaSettingsRepository(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences(
            "sable_media",
            Context.MODE_PRIVATE,
        )

    fun load(): MediaSettings =
        MediaSettings(
            startPage =
                runCatching {
                    MediaStartPage.valueOf(
                        preferences.getString(
                            KEY_START_PAGE,
                            MediaStartPage.Collection.name,
                        ) ?: MediaStartPage.Collection.name,
                    )
                }.getOrDefault(MediaStartPage.Collection),
            showMiniPlayer =
                preferences.getBoolean(
                    KEY_SHOW_MINI_PLAYER,
                    true,
                ),
            keepScreenAwakeWhilePlaying =
                preferences.getBoolean(
                    KEY_KEEP_SCREEN_AWAKE,
                    false,
                ),
        )

    fun save(settings: MediaSettings) {
        preferences
            .edit()
            .putString(KEY_START_PAGE, settings.startPage.name)
            .putBoolean(KEY_SHOW_MINI_PLAYER, settings.showMiniPlayer)
            .putBoolean(
                KEY_KEEP_SCREEN_AWAKE,
                settings.keepScreenAwakeWhilePlaying,
            ).apply()
    }

    private companion object {
        const val KEY_START_PAGE = "start_page"
        const val KEY_SHOW_MINI_PLAYER = "show_mini_player"
        const val KEY_KEEP_SCREEN_AWAKE = "keep_screen_awake_while_playing"
    }
}
