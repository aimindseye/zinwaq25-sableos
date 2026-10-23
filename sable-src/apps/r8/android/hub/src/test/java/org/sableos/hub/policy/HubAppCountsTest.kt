package org.sableos.hub.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.hub.ConnectedAppKey

/** Sable Start's unread bar: what counts, per app, and what privacy leaves of it. */
class HubAppCountsTest {
    private val chat =
        ActiveNotificationFacts(
            packageName = "org.example.chat",
            userSerial = 0L,
            ongoing = false,
            foregroundService = false,
            clearable = true,
            groupSummary = false,
            category = null,
            number = 0,
        )

    private fun counted(
        pkg: String,
        weight: Int = 1,
        serial: Long = 0L,
    ) = CountedNotification(pkg, serial, weight)

    @Test
    fun aClearableMessageCountsOneOrItsNumber() {
        assertEquals(1, HubAppCounts.weight(chat.copy(category = "msg")))
        assertEquals(4, HubAppCounts.weight(chat.copy(number = 4)))
        assertEquals(HubAppCounts.MAX_WEIGHT, HubAppCounts.weight(chat.copy(number = 5000)))
        assertEquals(1, HubAppCounts.weight(chat.copy(category = "missed_call")))
        assertEquals(1, HubAppCounts.weight(chat.copy(category = "email")))
    }

    @Test
    fun ongoingServiceSummaryAndStatusNotificationsDoNotCount() {
        assertEquals(0, HubAppCounts.weight(chat.copy(ongoing = true)))
        assertEquals(0, HubAppCounts.weight(chat.copy(foregroundService = true)))
        assertEquals(0, HubAppCounts.weight(chat.copy(clearable = false)))
        assertEquals(0, HubAppCounts.weight(chat.copy(groupSummary = true, number = 3)))
        listOf("transport", "progress", "service", "sys", "status", "call", "alarm", "navigation").forEach {
            assertEquals(it, 0, HubAppCounts.weight(chat.copy(category = it)))
        }
        listOf("android", "com.android.systemui", "org.sableos.hub", "org.sableos.launcher").forEach {
            assertNull(it, HubAppCounts.counted(chat.copy(packageName = it)))
        }
    }

    @Test
    fun rowsAreMostUnreadFirstThenByPackage() {
        val active =
            listOf(
                counted("b.app"),
                counted("a.app"),
                counted("c.app", weight = 2),
                counted("b.app"),
            )
        val rows = HubAppCounts.rows(active, emptySet(), 0L, HubPrivacyState())
        assertEquals(
            listOf(HubAppCount("b.app", 2), HubAppCount("c.app", 2), HubAppCount("a.app", 1)),
            rows,
        )
    }

    @Test
    fun otherProfilesAndHiddenAppsAreLeftOut() {
        val active = listOf(counted("a.app"), counted("p.app", serial = 11L), counted("h.app"))
        val rows =
            HubAppCounts.rows(active, setOf(ConnectedAppKey("h.app", 0L)), 0L, HubPrivacyState())
        assertEquals(listOf(HubAppCount("a.app", 1)), rows)
    }

    @Test
    fun lockedOrPrivateModeLeavesOneTotalWithoutAppNames() {
        val active = listOf(counted("a.app", weight = 2), counted("b.app"))
        listOf(HubPrivacyState(deviceLocked = true), HubPrivacyState(privateMode = true)).forEach { privacy ->
            assertEquals(
                listOf(HubAppCount(HubAppCounts.REDACTED_PACKAGE, 3)),
                HubAppCounts.rows(active, emptySet(), 0L, privacy),
            )
        }
        assertTrue(HubAppCounts.rows(emptyList(), emptySet(), 0L, HubPrivacyState(privateMode = true)).isEmpty())
    }

    @Test
    fun rowsAreCapped() {
        val active = (1..50).map { counted("app$it") }
        assertEquals(HubAppCounts.MAX_ROWS, HubAppCounts.rows(active, emptySet(), 0L, HubPrivacyState()).size)
    }
}
