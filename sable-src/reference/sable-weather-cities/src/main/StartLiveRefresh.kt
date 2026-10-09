package org.sableos.reference.weathercities

/**
 * What the Start/Live weather snapshot must do when the active city changes. The snapshot provider reads the
 * active city on every query; this decides when it has to be refreshed and when a cached forecast may be shown.
 */
object StartLiveRefresh {
    /** Manual cities never need a precise (or any) location permission. */
    const val MANUAL_CITY_PRECISE_LOCATION_REQUIRED = false
    const val NOTIFY_SNAPSHOT_ON_ACTIVE_CITY_CHANGE = true

    /** Locale-independent identity of what a forecast was fetched for. */
    fun cacheKey(city: City): String = "${city.latitude},${city.longitude},${city.timezone}"

    fun needsRefresh(before: City?, after: City?): Boolean {
        if (after == null) return false
        return before == null || cacheKey(before) != cacheKey(after) || before.name != after.name
    }

    /**
     * A cached forecast belongs to one city. Showing it under another city's name would be wrong, so it is
     * valid only when the stored key matches the active city.
     */
    fun cacheValidFor(storedKey: String?, active: City): Boolean =
        storedKey != null && storedKey == cacheKey(active)
}
