package org.sableos.start.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home settings (Q25 owner request: a configurable Home page). */
class HomeLayoutTest {
    @Test
    fun neverCustomisedShowsEverySection() {
        val hidden = HomeLayout.normalizeHidden(null)
        HomeLayout.SECTIONS.forEach { assertTrue(it, HomeLayout.isShown(hidden, it)) }
    }

    @Test
    fun sectionsReadTopToBottom() {
        assertEquals(
            listOf("clock", "rings", "greeting", "cards", "favorites", "quickbar"),
            HomeLayout.SECTIONS,
        )
    }

    @Test
    fun unknownStoredNamesAreDropped() {
        assertEquals(setOf("greeting"), HomeLayout.normalizeHidden(setOf("greeting", "bogus")))
    }

    @Test
    fun toggleHidesAndShowsAgain() {
        val hidden = HomeLayout.toggle(emptySet(), HomeLayout.GREETING)
        assertFalse(HomeLayout.isShown(hidden, HomeLayout.GREETING))
        assertTrue(HomeLayout.isShown(hidden, HomeLayout.CARDS))
        assertEquals(emptySet<String>(), HomeLayout.toggle(hidden, HomeLayout.GREETING))
        assertEquals(hidden, HomeLayout.toggle(hidden, "bogus"))
    }

    @Test
    fun everySectionHasALabel() {
        HomeLayout.SECTIONS.forEach {
            assertTrue(HomeLayout.label(it) != it)
            assertTrue(HomeLayout.description(it).isNotEmpty())
        }
    }

    @Test
    fun firstCardChangeStartsFromWhatHomeShowed() {
        val defaults = listOf("calendar", "hub", "weather")
        assertEquals(listOf("calendar", "weather"), HomeLayout.toggleCard(null, defaults, "hub"))
        assertEquals(listOf("calendar", "hub", "weather", "files"), HomeLayout.toggleCard(null, defaults, "files"))
        assertEquals(listOf("files"), HomeLayout.toggleCard(emptyList(), defaults, "files"))
        assertEquals(emptyList<String>(), HomeLayout.toggleCard(listOf("files"), defaults, "files"))
    }
}
