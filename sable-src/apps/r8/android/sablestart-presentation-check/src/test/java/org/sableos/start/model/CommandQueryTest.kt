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
    fun askAndTheAssistantsNameAskTheAssistant() {
        assertEquals(CommandQuery(CommandScope.Assistant, "what is on today"), CommandQuery.parse("/ask what is on today"))
        assertEquals(CommandQuery(CommandScope.Assistant, "hello"), CommandQuery.parse("/cosgrove hello", "cosgrove"))
        assertEquals(CommandQuery(CommandScope.Assistant, ""), CommandQuery.parse("/Cosgrove", "cosgrove"))
        // Without the name (not installed), "/cosgrove" is plain text; a name can't take another command's word.
        assertEquals(CommandScope.All, CommandQuery.parse("/cosgrove hello").scope)
        assertEquals(CommandScope.Hub, CommandQuery.parse("/hub x", "hub").scope)
        assertEquals(CommandScope.All, CommandQuery.parse("/a b", "a b").scope)
        // A group chip does not turn a question into a search.
        val ask = CommandQuery.parse("/ask hi")
        assertEquals(ask, CommandGroups.apply(ask, CommandScope.People))
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
        assertEquals(
            listOf("Mail", "Cinema"),
            CommandRanking.apps(listOf("Cinema", "Mail"), {
                it
            }, { recentCinema[it] }, "ma"),
        )
    }
}

class CommandGroupsTest {
    @Test
    fun profilePrefixesListOneProfile() {
        assertEquals(CommandQuery(CommandScope.Private, ""), CommandQuery.parse("/private"))
        assertEquals(CommandQuery(CommandScope.Dual, "chat"), CommandQuery.parse("/dual chat"))
        assertEquals(CommandQuery(CommandScope.Work, ""), CommandQuery.parse("/work"))
        assertEquals(true, CommandQuery.parse("/work").profileScope)
    }

    @Test
    fun leftRightMoveBetweenGroupChips() {
        assertEquals(CommandScope.Apps, CommandGroups.next(CommandScope.All, 1))
        assertEquals(CommandScope.All, CommandGroups.next(CommandScope.All, -1))
        assertEquals(CommandScope.Actions, CommandGroups.next(CommandScope.Actions, 1))
        assertEquals(CommandScope.Apps, CommandGroups.next(CommandScope.Dual, 1))
        assertEquals(
            CommandQuery(CommandScope.People, "cam"),
            CommandGroups.apply(CommandQuery.parse("?cam"), CommandScope.People),
        )
        assertEquals(
            CommandQuery.parse("/dual x"),
            CommandGroups.apply(CommandQuery.parse("/dual x"), CommandScope.Hub),
        )
    }

    @Test
    fun bestMatchPrefersTheStrongerTitleAndAppsOnATie() {
        assertEquals(true, CommandBestMatch.appWins("Settings", "Settings", "set"))
        assertEquals(false, CommandBestMatch.appWins("Reset Tool", "Settings", "set"))
        assertEquals(true, CommandBestMatch.appWins("Sable Start", null, "start"))
        assertEquals(null, CommandBestMatch.appWins("Sable Start", "Wi-Fi", " "))
        assertEquals(0, CommandBestMatch.tier("Settings", "set"))
        assertEquals(1, CommandBestMatch.tier("Sable Settings", "set"))
    }
}

class SearchFoldTest {
    @Test
    fun accentsAndCaseDoNotMatterWhenSearching() {
        assertEquals("ky vuong", SearchFold.fold("Kỳ Vương"))
        assertEquals("cafe", SearchFold.fold("Café"))
        assertEquals("dien thoai", SearchFold.fold("Điện thoại"))
        assertEquals(0, CommandBestMatch.tier("Kỳ Vương", "ky v"))
        assertEquals(1, CommandBestMatch.tier("Le Café", "cafe"))
        val apps = listOf("Mail", "Kỳ Vương", "Café Bar")
        assertEquals(listOf("Kỳ Vương"), CommandRanking.apps(apps, { it }, { null }, "vuong"))
    }
}
