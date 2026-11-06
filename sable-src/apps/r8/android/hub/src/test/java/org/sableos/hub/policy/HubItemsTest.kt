package org.sableos.hub.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubItemsTest {
    private fun shows(
        ongoing: Boolean = false,
        foregroundService: Boolean = false,
        clearable: Boolean = true,
        groupSummary: Boolean = false,
        category: String? = null,
        hasText: Boolean = true,
    ) = HubItems.shows(HubItemFacts(ongoing, foregroundService, clearable, groupSummary, category, hasText))

    @Test
    fun clearableAlertsFromAnyAppAreHubItems() {
        assertTrue(shows())
        assertTrue(shows(category = "missed_call"))
        assertTrue(shows(category = "reminder"))
        assertTrue(shows(category = "event"))
        assertTrue(shows(category = "social"))
    }

    @Test
    fun ongoingWorkAndStatusStayOut() {
        assertFalse(shows(ongoing = true))
        assertFalse(shows(foregroundService = true))
        assertFalse(shows(clearable = false))
        assertFalse(shows(groupSummary = true))
        assertFalse(shows(hasText = false))
        assertFalse(shows(category = "progress"))
        assertFalse(shows(category = "transport"))
        assertFalse(shows(category = "call"))
        assertFalse(shows(category = "sys"))
    }
}
