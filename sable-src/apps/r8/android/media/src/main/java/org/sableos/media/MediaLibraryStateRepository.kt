package org.sableos.media

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class MediaPlaylist(
    val id: String,
    val name: String,
    val itemUris: List<String>,
)

internal class MediaLibraryStateRepository(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences(
            "sable_media",
            Context.MODE_PRIVATE,
        )

    fun loadFavoriteAudioUris(): Set<String> =
        preferences
            .getStringSet(KEY_FAVORITE_AUDIO, emptySet())
            ?.toSet()
            .orEmpty()

    fun toggleFavoriteAudio(
        current: Set<String>,
        uri: String,
    ): Set<String> =
        persistStringSet(
            key = KEY_FAVORITE_AUDIO,
            values = toggled(current, uri),
        )

    fun loadFavoriteStationIds(): Set<String> =
        preferences
            .getStringSet(KEY_FAVORITE_STATIONS, emptySet())
            ?.toSet()
            .orEmpty()

    fun toggleFavoriteStation(
        current: Set<String>,
        stationId: String,
    ): Set<String> =
        persistStringSet(
            key = KEY_FAVORITE_STATIONS,
            values = toggled(current, stationId),
        )

    fun loadPlaylists(): List<MediaPlaylist> {
        val raw = preferences.getString(KEY_PLAYLISTS, null) ?: return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                decodePlaylist(array.optJSONObject(index))
            }.filterNotNull()
        }.getOrDefault(emptyList())
    }

    fun savePlaylist(
        current: List<MediaPlaylist>,
        editingId: String?,
        name: String,
        itemUris: Set<String>,
    ): List<MediaPlaylist> {
        val playlist =
            MediaPlaylist(
                id = editingId ?: UUID.randomUUID().toString(),
                name = name.trim().ifBlank { "Playlist" },
                itemUris = itemUris.toList(),
            )

        val updated =
            if (editingId == null) {
                current + playlist
            } else {
                current.map { existing ->
                    if (existing.id == editingId) {
                        playlist
                    } else {
                        existing
                    }
                }
            }.sortedBy { it.name.lowercase() }

        persistPlaylists(updated)
        return updated
    }

    fun deletePlaylist(
        current: List<MediaPlaylist>,
        playlistId: String,
    ): List<MediaPlaylist> {
        val updated = current.filterNot { it.id == playlistId }
        persistPlaylists(updated)
        return updated
    }

    fun pruneMissingAudio(
        current: List<MediaPlaylist>,
        validUris: Set<String>,
    ): List<MediaPlaylist> {
        val updated =
            current.map { playlist ->
                playlist.copy(
                    itemUris =
                        playlist.itemUris.filter { uri ->
                            uri in validUris
                        },
                )
            }

        if (updated != current) {
            persistPlaylists(updated)
        }

        return updated
    }

    private fun decodePlaylist(item: JSONObject?): MediaPlaylist? =
        item?.let { json ->
            val id = json.optString("id")
            val name = json.optString("name")

            if (id.isBlank() || name.isBlank()) {
                null
            } else {
                val urisArray = json.optJSONArray("itemUris") ?: JSONArray()
                val uris =
                    List(urisArray.length()) { index ->
                        urisArray.optString(index)
                    }.filter(String::isNotBlank)

                MediaPlaylist(
                    id = id,
                    name = name,
                    itemUris = uris,
                )
            }
        }

    private fun toggled(
        current: Set<String>,
        value: String,
    ): Set<String> =
        current
            .toMutableSet()
            .apply {
                if (!add(value)) {
                    remove(value)
                }
            }.toSet()

    private fun persistStringSet(
        key: String,
        values: Set<String>,
    ): Set<String> {
        preferences
            .edit()
            .putStringSet(key, values)
            .apply()
        return values
    }

    private fun persistPlaylists(playlists: List<MediaPlaylist>) {
        val array = JSONArray()

        playlists.forEach { playlist ->
            array.put(
                JSONObject()
                    .put("id", playlist.id)
                    .put("name", playlist.name)
                    .put(
                        "itemUris",
                        JSONArray().apply {
                            playlist.itemUris.forEach(::put)
                        },
                    ),
            )
        }

        preferences
            .edit()
            .putString(KEY_PLAYLISTS, array.toString())
            .apply()
    }

    private companion object {
        const val KEY_FAVORITE_AUDIO = "favorite_audio_uris"
        const val KEY_FAVORITE_STATIONS = "favorite_station_ids"
        const val KEY_PLAYLISTS = "playlists"
    }
}
