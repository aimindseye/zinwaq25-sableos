package org.sableos.weather

import android.content.Context
import android.net.Uri
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal class WeatherRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences(
            WeatherSnapshotContract.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    fun load(
        location: WeatherLocation = loadLocation(),
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
    ): WeatherUiState {
        val protocol =
            preferences.getString(
                WeatherSnapshotContract.KEY_PROTOCOL,
                null,
            )
        val observed =
            preferences.getLong(
                WeatherSnapshotContract.KEY_OBSERVED_EPOCH_SECONDS,
                0L,
            )

        if (protocol.isNullOrBlank() || observed <= 0L) {
            return WeatherUiState(
                location = location,
                availability = WeatherAvailability.Local,
                message = "Refresh to load the forecast.",
            )
        }

        val availability =
            availabilityFromNative(
                WeatherNative.providerState(
                    hasCachedSnapshot = true,
                    lastSuccessEpochSeconds = observed,
                    nowEpochSeconds = nowEpochSeconds,
                    fetchFailed = false,
                ),
            )

        val snapshot =
            WeatherProtocol.parse(
                protocol = protocol,
                location = location,
                availability = availability,
                observedAtEpochSeconds = observed,
            )

        return if (snapshot == null) {
            WeatherUiState(
                location = location,
                availability = WeatherAvailability.Failed,
                message = "Cached forecast could not be read.",
            )
        } else {
            WeatherUiState(
                location = location,
                snapshot = snapshot,
                availability = availability,
                message =
                    when (availability) {
                        WeatherAvailability.Live -> "Forecast is current."
                        WeatherAvailability.Stale -> "Showing cached forecast."
                        else -> "Forecast available."
                    },
            )
        }
    }

    fun refresh(
        location: WeatherLocation,
        fahrenheit: Boolean = true,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
    ): WeatherUiState {
        saveLocation(location)

        val cached = load(location, nowEpochSeconds)
        val url =
            WeatherNative.buildForecastUrl(
                latitude = location.latitude,
                longitude = location.longitude,
                timezone = location.timezone,
                fahrenheit = fahrenheit,
            )

        val protocol: String? =
            runCatching {
                fetchHttps(url)
            }.mapCatching { json ->
                WeatherNative.parseForecast(json)
            }.getOrNull()

        if (protocol.isNullOrBlank() || !protocol.startsWith("OK\n")) {
            val fallback = cached.snapshot
            if (fallback != null) {
                val stale =
                    fallback.copy(
                        availability = WeatherAvailability.Stale,
                    )
                return WeatherUiState(
                    location = location,
                    snapshot = stale,
                    availability = WeatherAvailability.Stale,
                    message = "Offline · showing last known forecast.",
                )
            }

            return WeatherUiState(
                location = location,
                availability = WeatherAvailability.Failed,
                message = "Forecast unavailable. Check the connection and retry.",
            )
        }

        val parsed =
            WeatherProtocol.parse(
                protocol = protocol,
                location = location,
                availability = WeatherAvailability.Live,
                observedAtEpochSeconds = nowEpochSeconds,
            )

        if (parsed == null) {
            return WeatherUiState(
                location = location,
                snapshot = cached.snapshot,
                availability =
                    if (cached.snapshot != null) {
                        WeatherAvailability.Stale
                    } else {
                        WeatherAvailability.Failed
                    },
                message = "Provider response could not be normalized.",
            )
        }

        preferences
            .edit()
            .putString(
                WeatherSnapshotContract.KEY_PROTOCOL,
                protocol,
            ).putLong(
                WeatherSnapshotContract.KEY_OBSERVED_EPOCH_SECONDS,
                nowEpochSeconds,
            ).apply()

        appContext.contentResolver.notifyChange(
            WeatherSnapshotContract.SNAPSHOT_URI,
            null,
        )

        return WeatherUiState(
            location = location,
            snapshot = parsed,
            availability = WeatherAvailability.Live,
            message = "Updated from Open-Meteo.",
        )
    }

    fun loadLocation(): WeatherLocation {
        val name =
            preferences.getString(
                WeatherSnapshotContract.KEY_LOCATION_NAME,
                null,
            )
        return DefaultWeatherLocations.firstOrNull { location ->
            location.name == name
        } ?: DefaultWeatherLocations.first()
    }

    private fun saveLocation(location: WeatherLocation) {
        preferences
            .edit()
            .putString(
                WeatherSnapshotContract.KEY_LOCATION_NAME,
                location.name,
            ).apply()
    }

    private fun fetchHttps(url: String): String {
        require(url.startsWith("https://")) {
            "Weather transport must use HTTPS."
        }

        val connection =
            URL(url).openConnection() as HttpsURLConnection

        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.requestMethod = "GET"
            connection.setRequestProperty(
                "Accept",
                "application/json",
            )
            connection.setRequestProperty(
                "User-Agent",
                "SableOS-Weather/0.1",
            )
            connection.instanceFollowRedirects = false

            val status = connection.responseCode
            check(status in 200..299) {
                "Weather provider HTTP status $status"
            }

            connection.inputStream
                .bufferedReader(Charsets.UTF_8)
                .use { reader ->
                    reader.readText()
                }
        } finally {
            connection.disconnect()
        }
    }

    private fun availabilityFromNative(value: String): WeatherAvailability =
        when (value) {
            "LIVE" -> WeatherAvailability.Live
            "STALE" -> WeatherAvailability.Stale
            "FETCHING" -> WeatherAvailability.Fetching
            "FAILED" -> WeatherAvailability.Failed
            else -> WeatherAvailability.Local
        }
}

internal object WeatherSnapshotContract {
    const val AUTHORITY = "org.sableos.weather.snapshot"
    const val PATH_CURRENT = "current"

    val SNAPSHOT_URI: Uri =
        Uri.parse("content://$AUTHORITY/$PATH_CURRENT")

    const val COLUMN_TITLE = "title"
    const val COLUMN_DETAIL = "detail"
    const val COLUMN_AVAILABILITY = "availability"
    const val COLUMN_OBSERVED_AT = "observed_at"

    const val PREFERENCES_NAME = "sable_weather_snapshot"
    const val KEY_PROTOCOL = "protocol"
    const val KEY_OBSERVED_EPOCH_SECONDS = "observed_epoch_seconds"
    const val KEY_LOCATION_NAME = "location_name"
}
