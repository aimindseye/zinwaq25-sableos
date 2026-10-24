package org.sableos.titan2.setup.android

import android.content.Context
import android.os.Bundle
import org.sableos.titan2.setup.core.CityMatches
import org.sableos.titan2.setup.core.Observed
import org.sableos.titan2.setup.core.WeatherCityContract
import org.sableos.titan2.setup.core.WeatherCityStep

/**
 * Talks to Sable Weather's setup hand-off. Weather checks that the caller is this package; Setup
 * never touches Weather's storage. Every failure (Weather missing, refused, crashed) is null.
 */
class WeatherCityClient(private val context: Context) {
    /** True when Weather's provider is installed and visible to Setup. */
    fun available(): Boolean = try {
        context.packageManager.resolveContentProvider(WeatherCityContract.AUTHORITY, 0) != null
    } catch (_: RuntimeException) {
        false
    }

    fun search(query: String): CityMatches? {
        val result = call(WeatherCityContract.METHOD_SEARCH, WeatherCityStep.cleanQuery(query))
            ?: return null
        return CityMatches(
            WeatherCityStep.cleanNames(result.getStringArrayList(WeatherCityContract.KEY_NAMES)),
            selected(result)
        )
    }

    fun current(): Observed {
        val result = call(WeatherCityContract.METHOD_SEARCH, "") ?: return Observed.Failed
        return selected(result)?.let { Observed.Value(it) } ?: Observed.Absent
    }

    /** Weather's status word for the choice, or null when it gave no answer. */
    fun select(name: String): String? =
        call(WeatherCityContract.METHOD_SELECT, name)?.getString(WeatherCityContract.KEY_STATUS)

    private fun selected(result: Bundle): String? =
        WeatherCityStep.cleanNames(listOf(result.getString(WeatherCityContract.KEY_SELECTED)))
            .firstOrNull()

    private fun call(method: String, arg: String): Bundle? = try {
        context.contentResolver.call(WeatherCityContract.AUTHORITY, method, arg, null)
    } catch (_: SecurityException) {
        null
    } catch (_: RuntimeException) {
        null
    }
}
