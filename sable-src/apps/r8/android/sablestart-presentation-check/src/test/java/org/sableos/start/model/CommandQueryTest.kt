package org.sableos.start.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Command prefixes, Settings matches and recents-first ranking (SABLE_COMMAND_LAYER.md). */
class CommandQueryTest {
    @Test
    fun prefixesPickTheGroup() {
        assertEquals(CommandQuery(CommandScope.All, "wifi"), CommandQuery.parse("wifi"))
        assertEquals(CommandQuery(CommandScope.Apps, "cam"), CommandQuery.parse("?cam"))
        assertEquals(CommandQuery(CommandScope.People, "ana"), CommandQuery.parse("@ana"))
        assertEquals(CommandQuery(CommandScope.People, "ana"), CommandQuery.parse("#ana"))
        assertEquals(CommandQuery(CommandScope.Actions, "theme"), CommandQuery.parse("!theme"))
        assertEquals(CommandQuery(CommandScope.Settings, "bat"), CommandQuery.parse("/settings bat"))
        assertEquals(CommandQuery(CommandScope.Hub, ""), CommandQuery.parse("/hub"))
        assertEquals(CommandQuery(CommandScope.Apps, "m"), CommandQuery.parse("/apps m"))
        // "/hubx" is not the /hub prefix.
        assertEquals(CommandScope.All, CommandQuery.parse("/hubx").scope)
        assertTrue(CommandQuery.parse("x").includes(CommandScope.Settings))
    }

    @Test
    fun onlyNumbersAreDialable() {
        assertEquals("+15551234", CommandQuery.dialable("+1 555-1234"))
        assertNull(CommandQuery.dialable("12"))
        assertNull(CommandQuery.dialable("ana 555"))
    }

    @Test
    fun settingsMatchTitleThenKeyword() {
        assertEquals("Wi-Fi", CommandRanking.settings("wi").first().title)
        assertEquals("Battery", CommandRanking.settings("charging").single().title)
        assertTrue(CommandRanking.settings(" ").isEmpty())
    }

    @Test
    fun recentsRankFirstWithinATier() {
        val apps = listOf("Mail", "Maps", "Media", "Camera")
        val recent = mapOf("Media" to 0)
        assertEquals(
            listOf("Media", "Mail", "Maps", "Camera"),
            CommandRanking.apps(apps, { it }, { recent[it] }, "m"),
        )
        assertEquals(
            listOf("Media", "Camera", "Mail", "Maps"),
            CommandRanking.apps(apps, { it }, { recent[it] }, ""),
        )
        // A label-prefix match beats a recent contains match ("Cinema" holds "ma").
        val recentCinema = mapOf("Cinema" to 0)
        assertEquals(listOf("Mail", "Cinema"), CommandRanking.apps(listOf("Cinema", "Mail"), { it }, { recentCinema[it] }, "ma"))
    }
}
