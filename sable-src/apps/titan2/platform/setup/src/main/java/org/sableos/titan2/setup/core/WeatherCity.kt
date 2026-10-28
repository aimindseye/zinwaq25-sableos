package org.sableos.titan2.setup.core

import org.sableos.titan2.setup.core.SetupText.t

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
    val TITLE get() = t("weather_title")
    val HINT get() = t("weather_hint")
    val HELP get() = t("weather_help")
    val NO_MATCH get() = t("weather_no_match")
    val UNREACHABLE get() = t("weather_unreachable")
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
        selected?.let { t("weather_current", it.take(MAX_NAME)) } ?: t("weather_current_unknown")

    fun skipLabel(selected: String?): String =
        selected?.let { t("weather_skip_keep", it.take(MAX_NAME)) } ?: t("weather_skip")

    /** Result line after choosing [name]; null [status] means Weather gave no answer. */
    fun resultMessage(status: String?, name: String): String = when (status) {
        WeatherCityContract.STATUS_SELECTED ->
            t("weather_selected", name)

        WeatherCityContract.STATUS_UNCHANGED -> t("weather_unchanged", name)

        WeatherCityContract.STATUS_FULL ->
            t("weather_full")

        WeatherCityContract.STATUS_UNKNOWN -> t("weather_unknown_city", name)

        null -> UNREACHABLE

        else -> t("weather_failed", name)
    }

    /** True when the step is finished and the checklist can come back. */
    fun succeeded(status: String?): Boolean =
        status in setOf(WeatherCityContract.STATUS_SELECTED, WeatherCityContract.STATUS_UNCHANGED)

    /** Checklist row. A city is a preference, not a readiness check, so every outcome is informational. */
    fun row(o: Observed): Item {
        val detail = when (o) {
            is Observed.Value -> t("weather_row_city", o.text.take(MAX_NAME))
            Observed.Absent -> t("weather_row_none")
            Observed.Failed -> t("weather_row_failed")
        }
        return Item("weather", TITLE, detail, Readiness.Informational, Action.WeatherCity)
    }
}
