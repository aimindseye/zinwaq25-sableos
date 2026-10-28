package org.sableos.weather

import android.content.Context
import android.os.Bundle
import org.sableos.weather.cities.CityCatalog
import org.sableos.weather.cities.Reason

/**
 * The `ContentProvider.call` contract Sable Setup uses to pick the Weather city (no location permission).
 * Setup cannot read or write Weather's private preferences, so it asks this provider instead: it searches the
 * bundled catalog here (single source of truth) and names one catalog city to make active. Only catalog
 * cities are accepted, so a caller cannot inject arbitrary names or coordinates.
 *
 * Mirrored by `org.sableos.titan2.setup.core.WeatherCityContract`; both sides pin the same literals in tests.
 */
internal object WeatherSetupContract {
    /** arg = typed filter text. Result: [KEY_NAMES] (catalog matches) and [KEY_SELECTED] (active city). */
    const val METHOD_SEARCH = "setup_search_cities"

    /** arg = a catalog city name. Result: [KEY_STATUS] (one of the STATUS_ values) and [KEY_SELECTED]. */
    const val METHOD_SELECT = "setup_select_city"

    const val KEY_NAMES = "names"
    const val KEY_SELECTED = "selected"
    const val KEY_STATUS = "status"

    const val STATUS_SELECTED = "selected"
    const val STATUS_UNCHANGED = "unchanged"
    const val STATUS_UNKNOWN = "unknown_city"
    const val STATUS_FULL = "list_full"
    const val STATUS_FAILED = "failed"

    /** Longest filter text read from a caller; longer input is cut, never rejected. */
    const val MAX_QUERY = 60

    const val SETUP_PACKAGE = "org.sableos.titan2.setup"
    const val LAUNCHER_PACKAGE = "org.sableos.launcher"

    /** Who may read the Start/Live snapshot. */
    val SNAPSHOT_CALLERS: Set<String> = setOf(LAUNCHER_PACKAGE)

    /** Who may search the catalog and set the active city. */
    val SETUP_CALLERS: Set<String> = setOf(SETUP_PACKAGE)
}

/** Pure caller check shared by the provider entry points (Weather itself is always allowed). */
internal object WeatherCallers {
    /**
     * True when [callingUid] is Weather's own uid, or when any package installed under that uid is in
     * [allowed]. [packagesForUid] is only consulted for a foreign uid.
     */
    fun allowed(
        callingUid: Int,
        ownUid: Int,
        allowed: Set<String>,
        packagesForUid: () -> List<String>,
    ): Boolean = callingUid == ownUid || packagesForUid().any { it in allowed }
}

/** The provider's `call` methods for Sable Setup, run after the caller check. */
internal object SetupCityHandOff {
    /** Null for an unknown [method]. */
    fun handle(
        context: Context,
        method: String,
        arg: String?,
    ): Bundle? {
        val text = arg.orEmpty().take(WeatherSetupContract.MAX_QUERY)
        return when (method) {
            WeatherSetupContract.METHOD_SEARCH -> search(context, text)
            WeatherSetupContract.METHOD_SELECT -> select(context, text)
            else -> null
        }
    }

    /** Catalog matches only (never a manual entry), plus the active city for the step's "Current" line. */
    private fun search(
        context: Context,
        query: String,
    ): Bundle =
        Bundle().apply {
            val names = CityCatalog.searchListed(query).map { it.name }
            putStringArrayList(WeatherSetupContract.KEY_NAMES, ArrayList(names))
            putString(
                WeatherSetupContract.KEY_SELECTED,
                WeatherRepository(context)
                    .cities
                    .load()
                    .selected.name,
            )
        }

    /**
     * Selects through [WeatherRepository.cities] and [WeatherRepository.afterCityEdit], so the Start/Live
     * snapshot is notified, then asks [WeatherRefreshJob] for a background fetch of the new city.
     */
    private fun select(
        context: Context,
        name: String,
    ): Bundle {
        val repository = WeatherRepository(context)
        val city = CityCatalog.named(name)
        val edit = city?.let { repository.cities.use(it) }
        if (edit != null) {
            repository.afterCityEdit(edit)
            if (edit.changed) WeatherRefreshJob.ensureScheduled(context, needsFetch = true)
        }
        val status =
            when {
                edit == null -> WeatherSetupContract.STATUS_UNKNOWN
                edit.reason == Reason.TooManyCities -> WeatherSetupContract.STATUS_FULL
                edit.reason != null -> WeatherSetupContract.STATUS_FAILED
                edit.refresh -> WeatherSetupContract.STATUS_SELECTED
                else -> WeatherSetupContract.STATUS_UNCHANGED
            }
        return Bundle().apply {
            putString(WeatherSetupContract.KEY_STATUS, status)
            putString(
                WeatherSetupContract.KEY_SELECTED,
                repository.cities
                    .load()
                    .selected.name,
            )
        }
    }
}
