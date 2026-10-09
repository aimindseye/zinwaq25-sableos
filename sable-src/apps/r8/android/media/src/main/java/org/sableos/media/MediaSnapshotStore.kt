package org.sableos.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.Player

object MediaSnapshotStore {
    val SNAPSHOT_URI: Uri =
        Uri.parse("content://org.sableos.media.snapshot/current")

    private const val PREFS = "sable_media_live_snapshot"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_PLAYING = "playing"
    private const val KEY_OBSERVED_AT = "observed_at"

    fun update(
        context: Context,
        player: Player,
    ) {
        val metadata = player.currentMediaItem?.mediaMetadata
        val title =
            metadata
                ?.title
                ?.toString()
                ?.trim()
                .orEmpty()
        val artist =
            metadata
                ?.artist
                ?.toString()
                ?.trim()
                .orEmpty()

        preferences(context)
            .edit()
            .putString(KEY_TITLE, title)
            .putString(KEY_ARTIST, artist)
            .putBoolean(KEY_PLAYING, player.isPlaying)
            .putLong(KEY_OBSERVED_AT, System.currentTimeMillis())
            .apply()

        context.contentResolver.notifyChange(SNAPSHOT_URI, null)
    }

    fun clear(context: Context) {
        preferences(context)
            .edit()
            .clear()
            .putLong(KEY_OBSERVED_AT, System.currentTimeMillis())
            .apply()

        context.contentResolver.notifyChange(SNAPSHOT_URI, null)
    }

    fun read(context: Context): MediaSnapshot {
        val prefs = preferences(context)
        return MediaSnapshot(
            title = prefs.getString(KEY_TITLE, "").orEmpty(),
            artist = prefs.getString(KEY_ARTIST, "").orEmpty(),
            playing = prefs.getBoolean(KEY_PLAYING, false),
            observedAt = prefs.getLong(KEY_OBSERVED_AT, 0L),
        )
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE,
        )
}

data class MediaSnapshot(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val observedAt: Long,
)
