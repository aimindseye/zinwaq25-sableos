package org.sableos.weather.cities

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.weather.DefaultWeatherLocations

/** Source-level negatives for the pure city/cache/key layers: no framework, network, permission or device identity. */
class WeatherCitiesContractTest {
    private fun weatherDir(): File? {
        var d: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (d != null) {
            listOf("", "apps/r8/android/", "sable-src/apps/r8/android/").forEach { prefix ->
                val w = File(d, prefix + "weather/src/main/java/org/sableos/weather")
                if (w.isDirectory) return w
            }
            d = d.parentFile
        }
        return null
    }

    private fun pureSources(): List<File> =
        listOf("cities", "input").flatMap { sub ->
            File(weatherDir() ?: File("/nonexistent"), sub).walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.toList()
        }

    @Test fun thePureLayersExist() {
        assertTrue(pureSources().size >= 6)
    }

    @Test fun pureLayersAreFrameworkFreeNetworkFreeAndDeviceFree() {
        val banned =
            listOf(
                "import android.",
                "import androidx.",
                "java.net.",
                "HttpsURLConnection",
                "Build." + "MODEL",
                "Build." + "DEVICE",
                "SystemProperties",
                "LocationManager",
                "ACCESS_" + "FINE_LOCATION",
                "http"
            )
        pureSources().forEach { f -> banned.forEach { assertFalse("${f.name}: $it", f.readText().contains(it)) } }
    }

    @Test fun manualCitiesNeedNoLocationPermission() {
        assertFalse(StartLiveRefresh.MANUAL_CITY_PRECISE_LOCATION_REQUIRED)
    }

    @Test fun theAppDefaultsAreTheCityDefaults() {
        assertEquals(DefaultCities.ALL.map { it.name }, DefaultWeatherLocations.map { it.name })
        assertEquals(
            listOf("Jersey City", "New York", "Edison", "Mumbai"),
            DefaultCities.ALL.map { it.name }
        )
    }
}
