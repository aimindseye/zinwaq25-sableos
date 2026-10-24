package org.sableos.titan2.setup.core

/**
 * Sable Weather's city hand-off, as Setup calls it through `ContentResolver.call`. Weather owns the catalog
 * and the city list: Setup only sends the typed filter text and one catalog name. Mirrors
 * `org.sableos.weather.WeatherSetupContract`; both sides pin the same literals in tests.
 */
object WeatherCityContract {
    const val AUTHORITY = "org.sableos.weather.snapshot"
    const val METHOD_SEARCH = "setup_search_cities"
    const val METHOD_SELECT = "setup_select_city"
    const val KEY_NAMES = "names"
    const val KEY_SELECTED = "selected"
    const val KEY_STATUS = "status"
    const val STATUS_SELECTED = "selected"
    const val STATUS_UNCHANGED = "unchanged"
    const val STATUS_UNKNOWN = "unknown_city"
    const val STATUS_FULL = "list_full"
}

/** What Weather answered to a search. [selected] is the active city, or null when it was not reported. */
data class CityMatches(val names: List<String>, val selected: String?)

/** Pure text and list rules for the Weather city step; the Android side only draws them. */
object WeatherCityStep {
    const val TITLE = "Weather city"
    const val HINT = "Type a city"
    const val HELP = "Type to filter, ↑ ↓ to move, Enter to choose. No location permission is used."
    const val NO_MATCH = "No listed city matches. Try a nearby larger city."
    const val UNREACHABLE = "Sable Weather did not answer. Skip, and pick a city in Weather later."
    const val MAX_QUERY = 60
    const val MAX_NAME = 60

    /** Rows shown at once, so the field, help and Skip fit the 411 dp square screen. */
    const val MAX_SHOWN = 6

    /** Filter text as sent to Weather: control characters dropped, spaces collapsed, length bounded. */
    fun cleanQuery(text: CharSequence?): String = text?.toString().orEmpty()
        .filterNot { it.isISOControl() }
        .trim()
        .replace(Regex("\\s+"), " ")
        .take(MAX_QUERY)

    /** Names from Weather made safe to show: no blanks or control characters, bounded, unique. */
    fun cleanNames(raw: List<String?>?): List<String> = raw.orEmpty()
        .mapNotNull { it?.filterNot { c -> c.isISOControl() }?.trim()?.take(MAX_NAME) }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(MAX_SHOWN)

    fun currentLine(selected: String?): String =
        selected?.let { "Current: ${it.take(MAX_NAME)}" } ?: "Current city not reported"

    fun skipLabel(selected: String?): String =
        selected?.let { "Skip · keep ${it.take(MAX_NAME)}" } ?: "Skip"

    /** Result line after choosing [name]; null [status] means Weather gave no answer. */
    fun resultMessage(status: String?, name: String): String = when (status) {
        WeatherCityContract.STATUS_SELECTED ->
            "Weather now uses $name. The forecast loads in the background."

        WeatherCityContract.STATUS_UNCHANGED -> "Weather already uses $name."

        WeatherCityContract.STATUS_FULL ->
            "Weather's city list is full. Remove a city in Sable Weather, then try again."

        WeatherCityContract.STATUS_UNKNOWN -> "$name is not in Weather's city list."

        null -> UNREACHABLE

        else -> "Weather could not switch to $name."
    }

    /** True when the step is finished and the checklist can come back. */
    fun succeeded(status: String?): Boolean =
        status in setOf(WeatherCityContract.STATUS_SELECTED, WeatherCityContract.STATUS_UNCHANGED)

    /** Checklist row. A city is a preference, not a readiness check, so every outcome is informational. */
    fun row(o: Observed): Item {
        val detail = when (o) {
            is Observed.Value -> "Forecasts for ${o.text.take(MAX_NAME)}; no location permission"
            Observed.Absent -> "Choose the city Weather shows; no location permission"
            Observed.Failed -> "Could not read the Weather city"
        }
        return Item("weather", TITLE, detail, Readiness.Informational, Action.WeatherCity)
    }
}
