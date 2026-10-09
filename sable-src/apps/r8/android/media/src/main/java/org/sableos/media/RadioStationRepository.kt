package org.sableos.media

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class RadioStation(
    val id: String,
    val name: String,
    val url: String,
)

internal class RadioStationRepository(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences(
            "sable_media",
            Context.MODE_PRIVATE,
        )

    fun loadStations(): List<RadioStation> {
        val raw = preferences.getString(KEY_STATIONS, null) ?: return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val name = item.optString("name")
                    val url = item.optString("url")

                    if (
                        id.isNotBlank() &&
                        name.isNotBlank() &&
                        isSupportedUrl(url)
                    ) {
                        add(
                            RadioStation(
                                id = id,
                                name = name,
                                url = url,
                            ),
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveStation(
        current: List<RadioStation>,
        name: String,
        url: String,
    ): List<RadioStation> {
        val normalizedUrl = url.trim()
        require(isSupportedUrl(normalizedUrl)) {
            "Station URL must use HTTP or HTTPS."
        }

        val normalizedName =
            name.trim().ifBlank {
                Uri.parse(normalizedUrl).host ?: "Saved station"
            }

        val existing =
            current.firstOrNull {
                it.url.equals(normalizedUrl, ignoreCase = true)
            }

        val updated =
            if (existing != null) {
                current.map { station ->
                    if (station.id == existing.id) {
                        station.copy(name = normalizedName)
                    } else {
                        station
                    }
                }
            } else {
                current +
                    RadioStation(
                        id = UUID.randomUUID().toString(),
                        name = normalizedName,
                        url = normalizedUrl,
                    )
            }

        persist(updated)
        return updated
    }

    fun updateStation(
        current: List<RadioStation>,
        id: String,
        name: String,
        url: String,
    ): List<RadioStation> {
        val normalizedUrl = url.trim()
        require(isSupportedUrl(normalizedUrl)) {
            "Station URL must use HTTP or HTTPS."
        }

        val normalizedName =
            name.trim().ifBlank {
                Uri.parse(normalizedUrl).host ?: "Saved station"
            }

        val updated =
            current.map { station ->
                if (station.id == id) {
                    station.copy(
                        name = normalizedName,
                        url = normalizedUrl,
                    )
                } else {
                    station
                }
            }

        persist(updated)
        return updated
    }

    fun deleteStation(
        current: List<RadioStation>,
        id: String,
    ): List<RadioStation> {
        val updated = current.filterNot { it.id == id }
        persist(updated)
        return updated
    }

    private fun persist(stations: List<RadioStation>) {
        val array = JSONArray()

        stations.forEach { station ->
            array.put(
                JSONObject()
                    .put("id", station.id)
                    .put("name", station.name)
                    .put("url", station.url),
            )
        }

        preferences
            .edit()
            .putString(KEY_STATIONS, array.toString())
            .apply()
    }

    companion object {
        private const val KEY_STATIONS = "radio_stations"

        fun isSupportedUrl(url: String): Boolean {
            val scheme = Uri.parse(url.trim()).scheme?.lowercase()
            return scheme == "http" || scheme == "https"
        }
    }
}
