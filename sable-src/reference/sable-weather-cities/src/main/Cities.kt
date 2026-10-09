package org.sableos.reference.weathercities

import java.time.ZoneId
import kotlin.math.abs

/** A user-visible city: the only things Weather needs to ask Open-Meteo for a forecast. */
data class City(val name: String, val latitude: Double, val longitude: Double, val timezone: String)

enum class Reason {
    BlankName,
    NameTooLong,
    NotFinite,
    LatitudeOutOfRange,
    LongitudeOutOfRange,
    BadTimezone,
    Duplicate,
    TooManyCities,
    NotFound,
    LastCity
}

/** The four built-in cities of canonical Weather (same values as `DefaultWeatherLocations`). */
object DefaultCities {
    val ALL: List<City> =
        listOf(
            City("Jersey City", 40.7178, -74.0430, "America/New_York"),
            City("New York", 40.7128, -74.0060, "America/New_York"),
            City("Edison", 40.5187, -74.4121, "America/New_York"),
            City("Mumbai", 19.0760, 72.8777, "Asia/Kolkata")
        )
}

object CityRules {
    const val MAX_NAME = 60
    const val MAX_CITIES = 20
    const val DUPLICATE_DEGREES = 0.01

    fun normalizedName(name: String): String = name.trim().replace(Regex("\\s+"), " ")

    fun sameName(a: String, b: String): Boolean =
        normalizedName(a).equals(normalizedName(b), ignoreCase = true)

    fun validTimezone(timezone: String): Boolean = timezone in ZoneId.getAvailableZoneIds()

    /** Null means the city is valid. Nothing here needs a network or a location permission. */
    fun validate(city: City): Reason? {
        val name = normalizedName(city.name)
        val finite = city.latitude.isFinite() && city.longitude.isFinite()
        return when {
            name.isEmpty() -> Reason.BlankName
            name.length > MAX_NAME -> Reason.NameTooLong
            !finite -> Reason.NotFinite
            city.latitude !in -90.0..90.0 -> Reason.LatitudeOutOfRange
            city.longitude !in -180.0..180.0 -> Reason.LongitudeOutOfRange
            !validTimezone(city.timezone) -> Reason.BadTimezone
            else -> null
        }
    }

    fun samePlace(a: City, b: City): Boolean {
        val near = abs(a.latitude - b.latitude) < DUPLICATE_DEGREES &&
            abs(a.longitude - b.longitude) < DUPLICATE_DEGREES
        return sameName(a.name, b.name) || near
    }
}

/** What an edit did. [refresh] is true when the active city changed, so Start/Live must refresh. */
data class CityEdit(
    val state: CityState,
    val reason: Reason? = null,
    val refresh: Boolean = false
) {
    val changed: Boolean get() = reason == null
}

/**
 * The user's city list and the selected city. It is never empty and always has a selection.
 * Every edit returns a new state and says whether the active city changed.
 */
data class CityState(val cities: List<City>, val selectedName: String) {
    val selected: City
        get() = cities.firstOrNull { CityRules.sameName(it.name, selectedName) } ?: cities.first()

    fun add(city: City, select: Boolean = true): CityEdit {
        val clean = city.copy(name = CityRules.normalizedName(city.name))
        val reason = CityRules.validate(clean) ?: addConflict(clean)
        if (reason != null) return CityEdit(this, reason)
        val next =
            copy(cities = cities + clean, selectedName = if (select) clean.name else selectedName)
        return done(next)
    }

    fun remove(name: String): CityEdit {
        val index = cities.indexOfFirst { CityRules.sameName(it.name, name) }
        val reason = removeConflict(index)
        if (reason != null) return CityEdit(this, reason)
        val left = cities.filterIndexed { i, _ -> i != index }
        val keep = CityRules.sameName(selected.name, name).not()
        val selectedNext = if (keep) selected.name else neighbour(left, index)
        return done(copy(cities = left, selectedName = selectedNext))
    }

    fun select(name: String): CityEdit {
        val found = cities.firstOrNull { CityRules.sameName(it.name, name) }
        if (found == null) return CityEdit(this, Reason.NotFound)
        return done(copy(selectedName = found.name))
    }

    /** Back to the built-in cities; the selection is kept only if it is one of them. */
    fun reset(): CityEdit {
        val keep = DefaultCities.ALL.firstOrNull { CityRules.sameName(it.name, selected.name) }
        return done(CityState(DefaultCities.ALL, (keep ?: DefaultCities.ALL.first()).name))
    }

    private fun neighbour(left: List<City>, index: Int): String =
        left[index.coerceAtMost(left.lastIndex)].name

    private fun removeConflict(index: Int): Reason? = when {
        index < 0 -> Reason.NotFound
        cities.size == 1 -> Reason.LastCity
        else -> null
    }

    private fun addConflict(city: City): Reason? = when {
        cities.any { CityRules.samePlace(it, city) } -> Reason.Duplicate
        cities.size >= CityRules.MAX_CITIES -> Reason.TooManyCities
        else -> null
    }

    private fun done(next: CityState) =
        CityEdit(next, null, StartLiveRefresh.needsRefresh(selected, next.selected))

    companion object {
        fun defaults() = CityState(DefaultCities.ALL, DefaultCities.ALL.first().name)

        /** Repairs anything unusable: no valid city means the defaults, an unknown selection means the first. */
        fun of(cities: List<City>, selectedName: String?): CityState {
            val valid = cities.filter { CityRules.validate(it) == null }
            val unique = valid.fold(emptyList<City>()) { acc, c ->
                if (acc.any { CityRules.samePlace(it, c) }) {
                    acc
                } else {
                    acc +
                        c
                }
            }
            val list = unique.take(CityRules.MAX_CITIES)
            if (list.isEmpty()) return defaults()
            val chosen =
                list.firstOrNull {
                    selectedName != null && CityRules.sameName(it.name, selectedName)
                }
                    ?: list.first()
            return CityState(list, chosen.name)
        }
    }
}
