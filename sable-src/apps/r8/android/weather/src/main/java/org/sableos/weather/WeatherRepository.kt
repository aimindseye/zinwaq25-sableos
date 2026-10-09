package org.sableos.weather

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.sableos.weather.cities.CityEdit
import org.sableos.weather.cities.CityRepository
import org.sableos.weather.cities.ForecastCache
import org.sableos.weather.cities.KeyValueStore
import org.sableos.weather.cities.LegacyWeatherCache

internal class WeatherRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences(
            WeatherSnapshotContract.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
    private val store = PreferencesStore(preferences)

    /** The user-managed city list and selection (IR-015). */
    val cities = CityRepository(store)

    /** One cached forecast per city, keyed by coordinates and timezone (IR-015). */
    private val forecasts = ForecastCache(store)

    init {
        LegacyWeatherCache.migrate(store)
    }

    fun load(
        location: WeatherLocation = loadLocation(),
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
    ): WeatherUiState {
        // Only this city's own cached forecast may be shown under its name.
        val cached = forecasts.read(location.toCity())
        val protocol = cached?.protocol
        val observed = cached?.observedEpochSeconds ?: 0L

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

        forecasts.write(location.toCity(), protocol, nowEpochSeconds)

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

    fun loadLocation(): WeatherLocation = cities.load().selected.toLocation()

    /**
     * Applies the result of a city edit: drops cached forecasts of removed cities and, when the active city
     * changed, tells the Start/Live snapshot to re-query (it reads the active city's own cache entry).
     */
    fun afterCityEdit(edit: CityEdit) {
        if (!edit.changed) return
        forecasts.retainOnly(edit.state.cities)
        if (edit.refresh) {
            appContext.contentResolver.notifyChange(
                WeatherSnapshotContract.SNAPSHOT_URI,
                null,
            )
        }
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
}

/**
 * [KeyValueStore] over Weather's private preferences. Reads go through [SharedPreferences.getAll] so values that
 * older builds stored as longs read back as text instead of throwing.
 */
internal class PreferencesStore(
    private val preferences: SharedPreferences,
) : KeyValueStore {
    override fun get(key: String): String? = preferences.all[key]?.toString()

    override fun put(
        key: String,
        value: String,
    ) {
        preferences.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }

    override fun keys(): Set<String> = preferences.all.keys.toSet()
}
