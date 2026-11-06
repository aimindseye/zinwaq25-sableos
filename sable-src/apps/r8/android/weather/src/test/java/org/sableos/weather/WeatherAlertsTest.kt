package org.sableos.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherAlertsTest {
    private val place = WeatherLocation("Toronto", 43.65, -79.38, "America/Toronto")

    private fun hour(
        time: String,
        chance: Int,
        condition: WeatherCondition = WeatherCondition.Cloudy,
    ) = HourWeather(time, 10.0, chance, 3, condition)

    private fun snapshot(
        hourly: List<HourWeather> = listOf(hour("2026-11-06T08:00", 10), hour("2026-11-06T09:00", 20)),
        now: WeatherCondition = WeatherCondition.Cloudy,
        availability: WeatherAvailability = WeatherAvailability.Live,
    ) = WeatherSnapshot(
        location = place,
        current = CurrentWeather(9.6, 7.0, 80, 3, 5.0, 180, now),
        hourly = hourly,
        daily =
            listOf(
                DayWeather("2026-11-06", 61, 12.4, 4.6, 70, WeatherCondition.Rain),
                DayWeather("2026-11-07", 1, 9.0, 2.0, 0, WeatherCondition.Clear),
            ),
        availability = availability,
        observedAtEpochSeconds = NOW,
    )

    private fun due(
        settings: AlertSettings,
        memory: AlertMemory = AlertMemory(),
        snap: WeatherSnapshot? = snapshot(),
        hour: Int = 8,
    ) = WeatherAlerts.due(settings, memory, snap, "2026-11-06", hour, NOW)

    @Test
    fun nothingIsPostedUntilTurnedOn() {
        val wet = snapshot(hourly = listOf(hour("2026-11-06T09:00", 90)))
        assertTrue(due(AlertSettings(), snap = wet).isEmpty())
        assertTrue(due(AlertSettings(morning = true, rain = true), snap = null).isEmpty())
        val cached = snapshot(hourly = wet.hourly, availability = WeatherAvailability.Stale)
        assertTrue(due(AlertSettings(morning = true, rain = true), snap = cached).isEmpty())
    }

    @Test
    fun theMorningForecastGoesOutOnceInTheMorning() {
        val alert = due(AlertSettings(morning = true)).single() as WeatherAlert.Morning
        assertEquals("Today in Toronto", alert.title)
        assertEquals("rain · 12° / 5° · rain 70%", alert.text)
        assertEquals("2026-11-06", alert.date)
        assertTrue(due(AlertSettings(morning = true), AlertMemory(morningDate = "2026-11-06")).isEmpty())
        assertTrue(due(AlertSettings(morning = true), hour = 5).isEmpty())
        assertTrue(due(AlertSettings(morning = true), hour = 14).isEmpty())
    }

    @Test
    fun rainSoonNamesTheFirstWetHourOncePerSpell() {
        val wet = snapshot(hourly = listOf(hour("2026-11-06T13:00", 20), hour("2026-11-06T14:00", 65)))
        val alert = due(AlertSettings(rain = true), snap = wet, hour = 13).single()
        assertEquals("Rain soon in Toronto", alert.title)
        assertEquals("Likely around 14:00 (65%)", alert.text)
        val justSent = AlertMemory(rainAtEpochSeconds = NOW - 60)
        assertTrue(due(AlertSettings(rain = true), justSent, wet).isEmpty())
        val longAgo = AlertMemory(rainAtEpochSeconds = NOW - WeatherAlerts.RAIN_QUIET_SECONDS)
        assertEquals(1, due(AlertSettings(rain = true), longAgo, wet).size)
    }

    @Test
    fun noRainAlertWhenDryFarOffOrAlreadyRaining() {
        assertNull(WeatherAlerts.rainSoon(snapshot()))
        val later =
            snapshot(
                hourly =
                    listOf(
                        hour("2026-11-06T13:00", 0),
                        hour("2026-11-06T14:00", 0),
                        hour("2026-11-06T15:00", 0),
                        hour("2026-11-06T16:00", 95),
                    ),
            )
        assertNull(WeatherAlerts.rainSoon(later))
        val raining = snapshot(hourly = listOf(hour("2026-11-06T13:00", 90)), now = WeatherCondition.Rain)
        assertNull(WeatherAlerts.rainSoon(raining))
        val storm = snapshot(hourly = listOf(hour("2026-11-06T13:00", 30, WeatherCondition.Storm)))
        assertEquals("Likely around 13:00 (30%)", WeatherAlerts.rainSoon(storm)?.text)
    }

    private companion object {
        const val NOW = 1_794_000_000L
    }
}
