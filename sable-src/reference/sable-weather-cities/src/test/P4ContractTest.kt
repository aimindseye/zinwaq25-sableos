package org.sableos.reference.weathercities

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-level negatives: portable model only, with no framework, network, permission or device identity. */
class P4ContractTest {
    private fun moduleDir(): File? {
        var d: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (d != null) {
            val r = File(d, "reference/sable-weather-cities")
            if (r.isDirectory) return r
            d = d.parentFile
        }
        return null
    }

    private fun files(): List<File> = moduleDir()?.walkTopDown()?.filter {
        it.isFile
    }?.toList().orEmpty()

    private fun main(): List<File> = files().filter {
        it.path.contains("/src/main/") &&
            it.name.endsWith(".kt")
    }

    @Test fun theModuleExistsAndHasMainSources() {
        assertTrue(main().size >= 4)
    }

    @Test fun noManifestGradleBuildOrHomeCategory() {
        val home = "android.intent.category." + "HOME"
        files().forEach {
            assertFalse(
                it.name,
                it.name == "AndroidManifest.xml" || it.name.endsWith(".gradle.kts")
            )
            assertFalse(it.name, it.readText().contains(home))
        }
    }

    @Test fun mainSourceIsFrameworkFreeNetworkFreeAndDeviceFree() {
        val banned =
            listOf(
                "import android.",
                "java.net.",
                "HttpsURLConnection",
                "HttpURLConnection",
                "OkHttp",
                "Build." + "MODEL",
                "Build." + "DEVICE",
                "SystemProperties",
                "LocationManager",
                "Settings.Secure"
            )
        main().forEach { f ->
            banned.forEach { assertFalse("${f.name}: $it", f.readText().contains(it)) }
        }
    }

    @Test fun mainSourceNamesNoPermissionOrTelemetryOrAccount() {
        val banned =
            listOf(
                "ACCESS_" + "FINE_LOCATION",
                "ACCESS_" + "COARSE_LOCATION",
                "analytics",
                "telemetry",
                "crashlytics",
                "firebase",
                "account",
                "login"
            )
        main().forEach { f ->
            banned.forEach {
                assertFalse("${f.name}: $it", f.readText().lowercase().contains(it.lowercase()))
            }
        }
    }

    @Test fun mainSourceNamesNoUrl() {
        main().forEach { assertFalse(it.name, it.readText().contains("http")) }
    }

    @Test fun theDefaultsMatchTheCanonicalWeatherDefaults() {
        assertEquals(
            listOf("Jersey City", "New York", "Edison", "Mumbai"),
            DefaultCities.ALL.map { it.name }
        )
        assertEquals(
            listOf(40.7178, 40.7128, 40.5187, 19.0760),
            DefaultCities.ALL.map {
                it.latitude
            }
        )
    }
}
