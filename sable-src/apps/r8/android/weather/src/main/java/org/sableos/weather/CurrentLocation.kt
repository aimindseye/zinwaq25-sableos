package org.sableos.weather

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import org.sableos.weather.cities.City
import org.sableos.weather.cities.CityCatalog
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * "Use current location": while it is on and coarse location is granted, the forecast is for where the
 * phone is, not the chosen city. The background job ([WeatherRefreshJob]) finds the place and stores it
 * here, so the Start snapshot never waits on a location fix. Choosing a city turns it off.
 */
internal class CurrentLocationStore(
    private val preferences: SharedPreferences,
) {
    var enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, true)
        set(value) {
            preferences.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    fun lastPlace(): WeatherLocation? = CurrentPlace.decode(preferences.getString(KEY_PLACE, null))

    fun save(place: WeatherLocation) {
        preferences.edit().putString(KEY_PLACE, CurrentPlace.encode(place)).apply()
    }

    private companion object {
        const val KEY_ENABLED = "current_location_enabled"
        const val KEY_PLACE = "current_location_place"
    }
}

/** Pure parts: rounding a fix to a place, naming it after the nearest listed city, and the stored form. */
internal object CurrentPlace {
    /** A listed city this close (km) names the place; farther away it is just "Current location". */
    const val NEAR_KM = 40.0
    const val UNNAMED = "Current location"
    private const val EARTH_KM = 6371.0
    private const val COORD_SCALE = 100.0

    /** Two decimals (about 1 km): enough for a forecast, and the cache key stays put while the phone barely moves. */
    fun round(value: Double): Double = (value * COORD_SCALE).roundToLong() / COORD_SCALE

    fun place(
        latitude: Double,
        longitude: Double,
        timezone: String,
        catalog: List<City> = CityCatalog.ALL,
    ): WeatherLocation {
        val nearest = catalog.minByOrNull { distanceKm(latitude, longitude, it.latitude, it.longitude) }
        val name =
            nearest
                ?.takeIf { distanceKm(latitude, longitude, it.latitude, it.longitude) <= NEAR_KM }
                ?.name ?: UNNAMED
        return WeatherLocation(name, round(latitude), round(longitude), timezone)
    }

    fun distanceKm(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
            sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_KM * asin(sqrt(a))
    }

    fun encode(place: WeatherLocation): String =
        listOf(place.name.replace("|", " "), place.latitude, place.longitude, place.timezone).joinToString("|")

    fun decode(text: String?): WeatherLocation? {
        val parts = text?.split('|') ?: return null
        if (parts.size != 4) return null
        val lat = parts[1].toDoubleOrNull() ?: return null
        val lon = parts[2].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0 || parts[3].isBlank()) return null
        return WeatherLocation(parts[0].ifBlank { UNNAMED }, lat, lon, parts[3])
    }
}

/** Finds where the phone is from Android's location providers, coarse only. Never throws. */
internal object CurrentLocationFinder {
    private const val FRESH_MS = 30L * 60 * 1000
    private const val FIX_TIMEOUT_S = 30L

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** The newest recent last-known fix, else one fresh fix (waits up to 30 s); null when none is available. */
    fun locate(context: Context): WeatherLocation? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val fix =
            runCatching {
                newestKnown(manager)?.takeIf { System.currentTimeMillis() - it.time < FRESH_MS }
                    ?: freshFix(manager)
                    ?: newestKnown(manager)
            }.getOrNull() ?: return null
        return CurrentPlace.place(fix.latitude, fix.longitude, TimeZone.getDefault().id)
    }

    @Suppress("MissingPermission") // hasPermission() is checked by locate().
    private fun newestKnown(manager: LocationManager): Location? =
        manager
            .getProviders(true)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }

    @Suppress("MissingPermission") // hasPermission() is checked by locate().
    private fun freshFix(manager: LocationManager): Location? {
        val provider =
            listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
                ?: return null
        val latch = CountDownLatch(1)
        var result: Location? = null
        val cancel = CancellationSignal()
        val executor = Executors.newSingleThreadExecutor()
        try {
            manager.getCurrentLocation(provider, cancel, executor) { location ->
                result = location
                latch.countDown()
            }
            if (!latch.await(FIX_TIMEOUT_S, TimeUnit.SECONDS)) cancel.cancel()
        } finally {
            executor.shutdown()
        }
        return result
    }
}
