package org.sableos.test.frameworks.base

import com.android.systemui.statusbar.notification.sable.SableHubRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class SableHubRouteTest {
    @Test
    fun hubIsStartsHubPage() {
        // Must match SableStartActivity.ACTION_SHOW_HUB / EXTRA_HUB_FILTER and Start's package.
        assertEquals("org.sableos.launcher", SableHubRoute.START_PACKAGE)
        assertEquals("org.sableos.start.action.SHOW_HUB", SableHubRoute.ACTION_SHOW_HUB)
        assertEquals("org.sableos.start.extra.HUB_FILTER", SableHubRoute.EXTRA_HUB_FILTER)
    }

    @Test
    fun messagesAndMailOpenTheMessagesTab() {
        assertEquals(SableHubRoute.TAB_MESSAGES, SableHubRoute.tabFor("msg"))
        assertEquals(SableHubRoute.TAB_MESSAGES, SableHubRoute.tabFor("email"))
        assertEquals("messages", SableHubRoute.TAB_MESSAGES)
    }

    @Test
    fun callsOpenTheCallsTab() {
        assertEquals(SableHubRoute.TAB_CALLS, SableHubRoute.tabFor("call"))
        assertEquals(SableHubRoute.TAB_CALLS, SableHubRoute.tabFor("missed_call"))
        assertEquals("calls", SableHubRoute.TAB_CALLS)
    }

    @Test
    fun everythingElseOpensAll() {
        assertEquals(SableHubRoute.TAB_ALL, SableHubRoute.tabFor(null))
        assertEquals(SableHubRoute.TAB_ALL, SableHubRoute.tabFor("alarm"))
        assertEquals(SableHubRoute.TAB_ALL, SableHubRoute.tabFor("social"))
        assertEquals(SableHubRoute.TAB_ALL, SableHubRoute.tabFor(""))
        assertEquals("all", SableHubRoute.TAB_ALL)
    }
}
