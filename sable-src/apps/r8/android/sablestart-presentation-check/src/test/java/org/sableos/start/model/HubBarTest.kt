package org.sableos.start.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.start.live.AppUnreadCount

/** The Hub page's unread bar (Ripe/BB7 style) and its tabs. */
class HubBarTest {
    private fun row(
        pkg: String,
        count: Int,
    ) = AppUnreadCount(pkg, count)

    @Test
    fun itemsAreMostUnreadFirstThenByPackageAndOnlyAppsStartCanOpen() {
        val rows =
            listOf(
                row("b.app", 1),
                row("gone.app", 9),
                row("a.app", 1),
                row("c.app", 3),
                row("zero.app", 0),
            )
        val items = HubBar.items(rows) { it != "gone.app" }
        assertEquals(listOf("c.app", "a.app", "b.app"), items.map { it.packageName })
        assertEquals(listOf(3, 1, 1), items.map { it.count })
    }

    @Test
    fun theCountsOnlyTotalAlwaysStays() {
        val items = HubBar.items(listOf(row(HubBar.REDACTED, 4))) { false }
        assertEquals(1, items.size)
        assertTrue(items.single().redacted)
        assertEquals(HubBar.Target.Private, HubBar.target(HubBar.REDACTED))
        assertEquals("4 new · apps hidden", HubBar.summary(items))
    }

    @Test
    fun overflowTakesTheLastSlot() {
        val items = (1..9).map { HubBar.Item("app$it", 10 - it) }
        val layout = HubBar.layout(items, 6)
        assertEquals(5, layout.visible.size)
        assertEquals(4, layout.overflow)
        assertEquals("+4", HubBar.overflowLabel(layout.overflow))
        val fits = HubBar.layout(items.take(6), 6)
        assertEquals(6, fits.visible.size)
        assertEquals(0, fits.overflow)
        assertEquals(0, HubBar.layout(items, 1).visible.size)
        assertEquals(9, HubBar.layout(items, 1).overflow)
    }

    @Test
    fun sixItemsFitTheQ25HubPage() {
        // 720x720 at density 280 is about 411dp; the page has 16dp gutters on each side.
        assertEquals(6, HubBar.slots(411 - 32))
        assertEquals(1, HubBar.slots(10))
    }

    @Test
    fun messagesPhoneAndContactsOpenTheirTabsOtherAppsOpen() {
        assertEquals(HubBar.Target.Tab(HubFilter.Messages), HubBar.target("org.sableos.messages"))
        assertEquals(HubBar.Target.Tab(HubFilter.Calls), HubBar.target("com.android.dialer"))
        assertEquals(HubBar.Target.Tab(HubFilter.Contacts), HubBar.target("com.android.contacts"))
        assertEquals(HubBar.Target.OpenApp, HubBar.target("com.spotify.music"))
    }

    @Test
    fun countsAndSummary() {
        assertEquals("7", HubBar.countLabel(7))
        assertEquals("99+", HubBar.countLabel(140))
        assertEquals("nothing new", HubBar.summary(emptyList()))
        assertEquals("1 new in 1 app", HubBar.summary(listOf(HubBar.Item("a", 1))))
        assertEquals("3 new in 2 apps", HubBar.summary(listOf(HubBar.Item("a", 2), HubBar.Item("b", 1))))
    }

    @Test
    fun tabsAreAllMessagesCallsContacts() {
        assertEquals(listOf("All", "Messages", "Calls", "Contacts"), HubFilter.entries.map { it.label })
        assertEquals(HubFilter.Contacts, HubFilter.fromName(" contacts "))
        assertNull(HubFilter.fromName("people"))
        assertNull(HubFilter.fromName(null))
    }
}
