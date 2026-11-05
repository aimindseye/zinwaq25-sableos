package org.sableos.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherCoreTest {
    private val fixture =
        """
        {
          "current": {
            "time": "2026-09-21T09:37",
            "temperature_2m": 72.4,
            "apparent_temperature": 71.8,
            "relative_humidity_2m": 58,
            "weather_code": 2,
            "wind_speed_10m": 8.2,
            "wind_direction_10m": 315
          },
          "hourly": {
            "time": ["2026-09-21T00:00", "2026-09-21T09:00", "2026-09-21T10:00"],
            "temperature_2m": [60.0, 72.4, 73.1],
            "precipitation_probability": [0, 12, 14],
            "weather_code": [0, 2, 1]
          },
          "daily": {
            "time": ["2026-09-21", "2026-09-22"],
            "weather_code": [2, 0],
            "temperature_2m_max": [78.0, 80.0],
            "temperature_2m_min": [64.0, 65.0],
            "precipitation_probability_max": [18, 5]
          }
        }
        """.trimIndent()

    @Test
    fun parserNormalizesCurrentHourlyAndDaily() {
        val lines = WeatherCore.parseForecast(fixture).lines()
        assertEquals(
            listOf(
                "OK",
                "CURRENT|72.4|71.8|58|2|8.2|315|PARTLY_CLOUDY",
                // Hours start at the current hour, so 00:00 is dropped.
                "HOURLY|2026-09-21T09:00|72.4|12|2|PARTLY_CLOUDY",
                "HOURLY|2026-09-21T10:00|73.1|14|1|PARTLY_CLOUDY",
                "DAILY|2026-09-21|2|78.0|64.0|18|PARTLY_CLOUDY",
                "DAILY|2026-09-22|0|80.0|65.0|5|CLEAR",
            ),
            lines,
        )
    }

    @Test
    fun missingSectionsAreReported() {
        assertEquals("ERR|daily object missing", WeatherCore.parseForecast(fixture.replace("\"daily\"", "\"dayly\"")))
    }

    @Test
    fun providerStateIsTruthfulAboutStaleCache() {
        assertEquals("LOCAL", WeatherCore.providerState(false, 0, 10, false))
        assertEquals("FAILED", WeatherCore.providerState(false, 0, 10, true))
        assertEquals("LIVE", WeatherCore.providerState(true, 1000, 1500, false))
        assertEquals("STALE", WeatherCore.providerState(true, 1000, 5000, false))
        assertEquals("STALE", WeatherCore.providerState(true, 1000, 1200, true))
        assertEquals("LIVE", WeatherCore.providerState(true, Long.MAX_VALUE, Long.MIN_VALUE, false))
    }

    @Test
    fun requestIsHttpsAndUsesProviderContract() {
        val url = WeatherCore.buildForecastUrl(40.7178, -74.0430, "America/New_York", true)
        assertTrue(url.startsWith("https://api.open-meteo.com/v1/forecast?latitude=40.71780&longitude=-74.04300&"))
        assertTrue(url.contains("temperature_unit=fahrenheit"))
        assertTrue(url.contains("timezone=America%2FNew_York"))
        assertFalse(url.startsWith("http://"))
    }

    @Test
    fun weatherCodesMapToConditionGroups() {
        assertEquals("CLEAR", WeatherCore.conditionFor(0))
        assertEquals("PARTLY_CLOUDY", WeatherCore.conditionFor(2))
        assertEquals("RAIN", WeatherCore.conditionFor(63))
        assertEquals("SNOW", WeatherCore.conditionFor(75))
        assertEquals("STORM", WeatherCore.conditionFor(95))
        assertEquals("UNKNOWN", WeatherCore.conditionFor(4))
    }
}
