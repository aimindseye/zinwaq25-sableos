package org.sableos.weather.cities

/**
 * A cached forecast for exactly one city. [cacheKey] is [StartLiveRefresh.cacheKey] of the city it was fetched
 * for, so a forecast can never be shown under another city's name.
 */
data class CachedForecast(val cacheKey: String, val protocol: String, val observedEpochSeconds: Long)

/**
 * Per-city forecast cache over the same [KeyValueStore] as the city list (IR-015).
 *
 * Each city has its own entry under `forecast_v1:<cacheKey>`. The entry repeats its cache key on its first line;
 * [read] returns it only when that key matches the active city ([StartLiveRefresh.cacheValidFor]). Entries of
 * cities that are no longer in the list are dropped by [retainOnly].
 */
class ForecastCache(private val store: KeyValueStore) {
    fun read(city: City): CachedForecast? {
        val text = store.get(entryKey(city)) ?: return null
        val entry = decode(text) ?: return null
        val valid =
            StartLiveRefresh.cacheValidFor(entry.cacheKey, city) &&
                entry.observedEpochSeconds > 0L &&
                entry.protocol.isNotBlank()
        return if (valid) entry else null
    }

    fun write(city: City, protocol: String, observedEpochSeconds: Long): CachedForecast {
        val entry = CachedForecast(StartLiveRefresh.cacheKey(city), protocol, observedEpochSeconds)
        store.put(entryKey(city), encode(entry))
        return entry
    }

    /** Removes cached forecasts for cities not in [cities]. Returns the removed store keys. */
    fun retainOnly(cities: List<City>): Set<String> {
        val keep = cities.map { entryKey(it) }.toSet()
        val drop = store.keys().filter { it.startsWith(PREFIX) && it !in keep }.toSet()
        drop.forEach { store.remove(it) }
        return drop
    }

    companion object {
        const val PREFIX = "forecast_v1:"
        private const val HEADER = "SABLE_FORECAST_V1"

        fun entryKey(city: City): String = PREFIX + StartLiveRefresh.cacheKey(city)

        fun encode(entry: CachedForecast): String =
            listOf(HEADER, entry.cacheKey, entry.observedEpochSeconds.toString()).joinToString("\n") +
                "\n" + entry.protocol

        fun decode(text: String): CachedForecast? {
            val parts = text.split('\n', limit = 4)
            if (parts.size != 4 || parts[0] != HEADER) return null
            val observed = parts[2].toLongOrNull() ?: return null
            return CachedForecast(parts[1], parts[3], observed)
        }
    }
}

/**
 * One-time move from the single-cache layout of earlier Weather builds (one `protocol`, one `observed_epoch_seconds`
 * and a `location_name` for all cities) to the per-city layout.
 *
 * The old protocol is dropped, not migrated: `location_name` was written before each fetch, so after a failed fetch
 * it named a different city than the cached forecast, and the forecast's real city cannot be recovered. The old
 * selection is kept when it names a built-in city and no city list has been stored yet.
 */
object LegacyWeatherCache {
    const val KEY_PROTOCOL = "protocol"
    const val KEY_OBSERVED_EPOCH_SECONDS = "observed_epoch_seconds"
    const val KEY_LOCATION_NAME = "location_name"

    /** Returns true when anything was changed. */
    fun migrate(store: KeyValueStore): Boolean {
        val legacyName = store.get(KEY_LOCATION_NAME)
        val hasLegacy =
            listOf(KEY_LOCATION_NAME, KEY_PROTOCOL, KEY_OBSERVED_EPOCH_SECONDS).any { store.get(it) != null }
        if (!hasLegacy) return false
        if (store.get(CityRepository.KEY) == null) {
            val state = CityState.of(DefaultCities.ALL, legacyName)
            store.put(CityRepository.KEY, CityCodec.encode(state))
        }
        store.remove(KEY_PROTOCOL)
        store.remove(KEY_OBSERVED_EPOCH_SECONDS)
        store.remove(KEY_LOCATION_NAME)
        return true
    }
}
