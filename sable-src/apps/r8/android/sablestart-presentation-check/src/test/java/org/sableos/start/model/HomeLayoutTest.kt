package org.sableos.start.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Active Frames header (BB10 Home) and the Glance widgets shown as frames. */
class HomeLayoutTest {
    @Test
    fun neverCustomisedShowsStatusDialsAndWeather() {
        val hidden = HomeLayout.normalizeHidden(null)
        assertTrue(HomeLayout.isShown(hidden, HomeLayout.STATUS_RINGS))
        assertTrue(HomeLayout.isShown(hidden, HomeLayout.WEATHER))
        assertFalse(HomeLayout.isShown(hidden, HomeLayout.SEARCH_ROW))
        // Dials first, then weather and date: no clock, no greeting.
        assertEquals(listOf("rings", "weather", "search"), HomeLayout.SECTIONS)
    }

    @Test
    fun weatherHeaderReadsTheSnapshotLine() {
        assertEquals("72° Partly cloudy" to "Seattle", HomeHeader.weather("Seattle · 72° · Partly cloudy"))
        assertEquals("5° Snow" to "", HomeHeader.weather("5° · Snow"))
        assertEquals(null, HomeHeader.weather("Choose a city in Sable Weather"))
        assertEquals("EEEEMMMMd", HomeHeader.DATE_SKELETON)
    }

    @Test
    fun dateLineTurnsOverAtMidnight() {
        val day = 24L * 60 * 60 * 1000
        assertEquals(day, HomeHeader.millisToMidnight(0))
        assertEquals(1000L, HomeHeader.millisToMidnight(day - 1000))
        assertTrue(HomeHeader.millisToMidnight(day - 1) >= 1)
    }

    @Test
    fun hiddenModulesToggleAndUnknownNamesDrop() {
        assertEquals(setOf("rings"), HomeLayout.normalizeHidden(setOf("rings", "bogus")))
        val noWeather = HomeLayout.toggle(emptySet(), HomeLayout.WEATHER)
        assertFalse(HomeLayout.isShown(noWeather, HomeLayout.WEATHER))
        assertEquals(emptySet<String>(), HomeLayout.toggle(noWeather, HomeLayout.WEATHER))
        assertEquals(noWeather, HomeLayout.toggle(noWeather, "bogus"))
        HomeLayout.SECTIONS.forEach { assertTrue(HomeLayout.description(it).isNotEmpty()) }
    }

    @Test
    fun listModeIsTheDefault() {
        assertEquals(HomeLayout.MODE_LIST, HomeLayout.normalizeMode(null))
        assertEquals(HomeLayout.MODE_LIST, HomeLayout.normalizeMode("grid"))
        assertEquals(HomeLayout.MODE_TILES, HomeLayout.normalizeMode("tiles"))
    }

    @Test
    fun homeAppsKeepTheSavedOrder() {
        val defaults = listOf("hub", "mail", "calendar")
        assertEquals(defaults, HomeApps.resolve(null, null, defaults))
        assertEquals(listOf("files", "hub"), HomeApps.resolve(listOf("files", "hub", "files"), setOf("mail"), defaults))
        assertEquals(emptyList<String>(), HomeApps.resolve(emptyList(), null, defaults))
    }

    @Test
    fun oldStartTilesBecomeTheFirstHomeOrder() {
        val defaults = listOf("hub", "mail", "calendar")
        assertEquals(
            listOf("hub", "calendar", "files", "zz"),
            HomeApps.resolve(null, setOf("zz", "calendar", "files", "hub"), defaults),
        )
    }

    @Test
    fun homeAppsMoveAndToggle() {
        val keys = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), HomeApps.move(keys, 0, 1))
        assertEquals(keys, HomeApps.move(keys, 0, -1))
        assertEquals(keys, HomeApps.move(keys, 2, 1))
        assertEquals(listOf("a", "c"), HomeApps.toggle(keys, "b"))
        assertEquals(listOf("a", "b", "c", "d"), HomeApps.toggle(keys, "d"))
    }

    @Test
    fun glanceDefaultsAreWeatherCalendarBatteryMedia() {
        assertEquals(listOf("weather", "calendar", "battery", "media"), GlanceWidgets.normalize(null))
        assertEquals(listOf("media", "weather"), GlanceWidgets.normalize(listOf("media", "bogus", "weather", "media")))
        assertEquals(emptyList<String>(), GlanceWidgets.normalize(emptyList()))
    }

    @Test
    fun glanceWidgetsHideAndShow() {
        val shown = GlanceWidgets.toggle(GlanceWidgets.DEFAULT, GlanceWidgets.BATTERY)
        assertEquals(listOf("weather", "calendar", "media"), shown)
        assertEquals(
            listOf("weather", "calendar", "media", "battery"),
            GlanceWidgets.toggle(shown, GlanceWidgets.BATTERY),
        )
        assertEquals(shown, GlanceWidgets.toggle(shown, "bogus"))
        GlanceWidgets.ALL.forEach { assertTrue(GlanceWidgets.label(it) != it) }
    }

    @Test
    fun weatherHeadlineGetsAnIconForItsCondition() {
        assertEquals("☀", HomeHeader.weatherIcon("18° clear"))
        assertEquals("⛅", HomeHeader.weatherIcon("18° partly cloudy"))
        assertEquals("☁", HomeHeader.weatherIcon("12° cloudy"))
        assertEquals("🌧", HomeHeader.weatherIcon("9° rain"))
        assertEquals("⛈", HomeHeader.weatherIcon("20° storms"))
        assertEquals("🌡", HomeHeader.weatherIcon("20° conditions unavailable"))
    }
}
