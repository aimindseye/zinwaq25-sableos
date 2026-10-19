package org.sableos.start.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home and Glance layout (SABLE_START_THREE_PAGE_MODEL.md, the Titan 2 three-page visual). */
class HomeLayoutTest {
    @Test
    fun neverCustomisedShowsWeatherOnly() {
        val hidden = HomeLayout.normalizeHidden(null)
        assertTrue(HomeLayout.isShown(hidden, HomeLayout.WEATHER))
        assertFalse(HomeLayout.isShown(hidden, HomeLayout.SEARCH_ROW))
        assertFalse(HomeLayout.isShown(hidden, HomeLayout.STATUS_RINGS))
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
    fun bb10ModeIsDefaultAndLegacyModesStillWork() {
        assertEquals(HomeLayout.MODE_BB10, HomeLayout.normalizeMode(null))
        assertEquals(HomeLayout.MODE_BB10, HomeLayout.normalizeMode("grid"))
        assertEquals(HomeLayout.MODE_BB10, HomeLayout.normalizeMode("bb10"))
        assertEquals(HomeLayout.MODE_LIST, HomeLayout.normalizeMode("list"))
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
        assertEquals(listOf("hub", "calendar", "files", "zz"), HomeApps.resolve(null, setOf("zz", "calendar", "files", "hub"), defaults))
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
        assertEquals(listOf("weather", "calendar", "media", "battery"), GlanceWidgets.toggle(shown, GlanceWidgets.BATTERY))
        assertEquals(shown, GlanceWidgets.toggle(shown, "bogus"))
        GlanceWidgets.ALL.forEach { assertTrue(GlanceWidgets.label(it) != it) }
    }
}
