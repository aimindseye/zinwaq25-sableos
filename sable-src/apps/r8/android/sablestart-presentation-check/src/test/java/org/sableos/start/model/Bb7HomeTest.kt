package org.sableos.start.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** BB7 home style: the saved choice, the tray grid and its panels. */
class Bb7HomeTest {
    @Test
    fun homeStyleRoundTripsAndDefaultsToBb10() {
        HomeStyle.entries.forEach { assertEquals(it, HomeStyle.fromStableValue(it.stableValue)) }
        assertEquals(HomeStyle.Bb10, HomeStyle.fromStableValue(null))
        assertEquals(HomeStyle.Bb10, HomeStyle.fromStableValue("bb5"))
    }

    @Test
    fun trayHasSixColumnsOnTheQ25AndStaysInRange() {
        assertEquals(6, Bb7Home.columns(411))
        assertEquals(Bb7Home.MIN_COLUMNS, Bb7Home.columns(200))
        assertEquals(Bb7Home.MAX_COLUMNS, Bb7Home.columns(1200))
    }

    @Test
    fun panelsWrapBothWays() {
        assertEquals(Bb7Panel.All, Bb7Home.step(Bb7Panel.Favorites, 1))
        assertEquals(Bb7Panel.Downloads, Bb7Home.step(Bb7Panel.Favorites, -1))
        assertEquals(Bb7Panel.Favorites, Bb7Home.step(Bb7Panel.Downloads, 1))
    }

    @Test
    fun selectionMovesByCellAndRowWithinTheItems() {
        assertEquals(1, Bb7Home.move(0, 10, 6, 1, 0))
        assertEquals(7, Bb7Home.move(1, 10, 6, 0, 1))
        assertEquals(9, Bb7Home.move(5, 10, 6, 0, 1))
        assertEquals(0, Bb7Home.move(3, 10, 6, 0, -1))
        assertEquals(0, Bb7Home.move(4, 0, 6, 1, 0))
    }

    @Test
    fun aSwipeRightOpensHubAndLeftTheAppsLikeTheBb10Home() {
        assertEquals(Bb7Swipe.Hub, Bb7Home.swipe(200f, 150f))
        assertEquals(Bb7Swipe.Apps, Bb7Home.swipe(-200f, 150f))
        assertEquals(null, Bb7Home.swipe(100f, 150f))
        assertEquals(null, Bb7Home.swipe(-149f, 150f))
    }

    @Test
    fun draggingTheTrayShowsMoreOrFewerRowsAndPastTheLastOpensIt() {
        assertEquals(2, Bb7Home.resizeRows(1, -70f, 64f))
        assertEquals(1, Bb7Home.resizeRows(2, 70f, 64f))
        assertEquals(1, Bb7Home.resizeRows(1, 300f, 64f))
        assertEquals(Bb7Home.MAX_HOME_ROWS + 1, Bb7Home.resizeRows(3, -500f, 64f))
        assertEquals(2, Bb7Home.resizeRows(2, -30f, 64f))
    }

    @Test
    fun addToFavoritesAppendsAndRemoveTakesItOff() {
        assertEquals(listOf("a", "b", "c"), Bb7Home.toggleFavorite(listOf("a", "b"), "c"))
        assertEquals(listOf("a"), Bb7Home.toggleFavorite(listOf("a", "b"), "b"))
    }

    @Test
    fun aHubVisitEndsBackOnTheBb7HomeOnlyAfterLeavingTheBb10HomePage() {
        val start = Bb7Detour(returnOnHome = true)
        // The BlackBerry 10 screen first composes on its home page, before the Hub request moves it.
        assertEquals(false, start.endsAt(false))
        val onHub = start.onCanNavigateBack(true)
        assertEquals(false, onHub.endsAt(true))
        // Swiping or Back from Hub to the BlackBerry 10 home page returns to the BlackBerry 7 home.
        assertEquals(true, onHub.onCanNavigateBack(false).endsAt(false))
    }

    @Test
    fun aRecentsVisitStaysOnActiveFrames() {
        val recents = Bb7Detour(returnOnHome = false).onCanNavigateBack(true).onCanNavigateBack(false)
        assertEquals(false, recents.endsAt(false))
    }

    @Test
    fun trayLayoutPicksColumnsAndListAndSurvivesSaving() {
        val auto = Bb7Layout()
        assertEquals(6, Bb7Home.columns(411, auto, open = false))
        assertEquals(1, Bb7Home.columns(411, auto.copy(view = Bb7TrayView.List), open = true))
        assertEquals(6, Bb7Home.columns(411, auto.copy(view = Bb7TrayView.List), open = false))
        assertEquals(5, Bb7Home.columns(411, auto.copy(iconSize = Bb7IconSize.Large), open = false))
        assertEquals(7, Bb7Home.columns(411, auto.copy(iconSize = Bb7IconSize.Small), open = false))
        assertEquals(4, Bb7Home.columns(411, auto.copy(columns = 4), open = true))
        val cycle =
            generateSequence(Bb7Home.nextColumns(Bb7Layout.AUTO)) {
                if (it == Bb7Layout.AUTO) null else Bb7Home.nextColumns(it)
            }.toList()
        assertEquals(listOf(4, 5, 6, 7, 8, Bb7Layout.AUTO), cycle)
        val chosen = Bb7Layout(Bb7TrayView.List, 5, Bb7IconSize.Small)
        assertEquals(chosen, Bb7Layout.decode(chosen.encode()))
        assertEquals(Bb7Layout(), Bb7Layout.decode("nonsense"))
        assertEquals(Bb7Layout(), Bb7Layout.decode(null))
        assertEquals(Bb7Layout.AUTO, Bb7Layout.decode("Grid:42:Medium").columns)
    }

    @Test
    fun ownTabsAreNamedFilledAndSaved() {
        var tabs = Bb7Tabs.add(emptyList(), "  Games\t ")
        assertEquals(listOf(Bb7Tab("Games")), tabs)
        assertEquals(tabs, Bb7Tabs.add(tabs, "games"))
        assertEquals(tabs, Bb7Tabs.add(tabs, "   "))
        tabs = Bb7Tabs.add(tabs, "Travel and maps for the weekend")
        assertEquals("Travel and maps for", tabs[1].name)
        tabs = Bb7Tabs.toggle(tabs, 0, "0:a/.A")
        tabs = Bb7Tabs.toggle(tabs, 0, "0:b/.B")
        assertEquals(listOf("0:a/.A", "0:b/.B"), tabs[0].keys)
        assertEquals(listOf("0:b/.B"), Bb7Tabs.toggle(tabs, 0, "0:a/.A")[0].keys)
        assertEquals(tabs, Bb7Tabs.decode(Bb7Tabs.encode(tabs)))
        assertEquals(listOf(tabs[1]), Bb7Tabs.remove(tabs, 0))
        val full = (1..9).fold(emptyList<Bb7Tab>()) { acc, i -> Bb7Tabs.add(acc, "Tab $i") }
        assertEquals(Bb7Tabs.MAX, full.size)
        assertEquals(emptyList<Bb7Tab>(), Bb7Tabs.decode(null))
        assertEquals(listOf(Bb7Tab("Work", listOf("0:w/.W"))), Bb7Tabs.decode("\t0:x/.X\nWork\t0:w/.W,,0:w/.W"))
    }

    @Test
    fun trayPlacesRunThroughThePanelsThenTheUsersTabs() {
        assertEquals(5, Bb7Home.trayPlaces(2))
        assertEquals(Bb7Panel.All, Bb7Home.panelAt(1))
        assertEquals(null, Bb7Home.panelAt(3))
        assertEquals(3, Bb7Home.stepPlace(2, 1, tabs = 2))
        assertEquals(0, Bb7Home.stepPlace(4, 1, tabs = 2))
        assertEquals(4, Bb7Home.stepPlace(0, -1, tabs = 2))
        assertEquals(0, Bb7Home.stepPlace(2, 1, tabs = 0))
    }

    @Test
    fun aTabNameIsTypedInTheTrayTitle() {
        assertEquals("Ga", Bb7Naming.onKey(35, 'a'.code, "G"))
        assertEquals("G", Bb7Naming.onKey(67, 0, "Ga"))
        assertEquals("My ", Bb7Naming.onKey(62, ' '.code, "My"))
        assertEquals(null, Bb7Naming.onKey(66, '\n'.code, "Games"))
        assertEquals(null, Bb7Naming.onKey(111, 0, "Games"))
        val long = "x".repeat(Bb7Tabs.MAX_NAME)
        assertEquals(long, Bb7Naming.onKey(29, 'a'.code, long))
    }
}
