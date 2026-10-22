package org.sableos.test.frameworks.base

import com.android.systemui.shade.sable.SableHubSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SableHubSummaryTest {
    @Test
    fun onlyAlertingNotOngoingNotificationsArePriority() {
        assertTrue(SableHubSummary.isPriority(true, false))
        assertFalse(SableHubSummary.isPriority(true, true))
        assertFalse(SableHubSummary.isPriority(false, false))
    }

    @Test
    fun lockedShadeShowsCountsOnly() {
        assertEquals(SableHubSummary.LOCKED, SableHubSummary.rowState(true, true, 3))
        assertEquals(SableHubSummary.LOCKED, SableHubSummary.rowState(true, true, 0))
        assertEquals(SableHubSummary.COUNT, SableHubSummary.rowState(true, false, 3))
        assertEquals(SableHubSummary.EMPTY, SableHubSummary.rowState(true, false, 0))
    }

    @Test
    fun noRowWithoutHub() {
        assertEquals(SableHubSummary.HIDDEN, SableHubSummary.rowState(false, false, 3))
        assertEquals(SableHubSummary.HIDDEN, SableHubSummary.rowState(false, true, 3))
    }

    @Test
    fun countIsCapped() {
        assertEquals(0, SableHubSummary.shownCount(-1))
        assertEquals(42, SableHubSummary.shownCount(42))
        assertEquals(99, SableHubSummary.shownCount(250))
        assertTrue(SableHubSummary.isCapped(100))
        assertFalse(SableHubSummary.isCapped(99))
    }
}
