package org.sableos.hub.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.hub.ConnectedAppKey
import org.sableos.hub.ConnectedAppPolicy

/** HUB_CONNECTED_APPS_PARITY=PASS. */
class ConnectedAppsParityTest {
    private val chat = ConnectedAppKey("org.example.chat", 0L)
    private val workChat = ConnectedAppKey("org.example.chat", 10L)
    private val hiddenChat = ConnectedAppPolicy(chat, includeInMessages = true, hideFromLauncher = true)
    private val hiddenWork = ConnectedAppPolicy(workChat, includeInMessages = true, hideFromLauncher = true)

    @Test
    fun withoutNotificationAccessNothingIsHiddenFromStart() {
        assertTrue(ConnectedAppsParity.effectiveHiddenKeys(listOf(hiddenChat), notificationAccessGranted = false).isEmpty())
        assertEquals(setOf(chat), ConnectedAppsParity.effectiveHiddenKeys(listOf(hiddenChat), true))
    }

    @Test
    fun hidingIsPerProfileAndOnlyForLiveIncludedApps() {
        val excluded = ConnectedAppPolicy(chat, includeInMessages = false, hideFromLauncher = true)
        assertTrue(ConnectedAppsParity.effectiveHiddenKeys(listOf(excluded), true).isEmpty())
        assertEquals(
            setOf(workChat),
            ConnectedAppsParity.effectiveHiddenKeys(listOf(hiddenChat, hiddenWork), true, liveKeys = setOf(workChat)),
        )
    }

    @Test
    fun statusReflectsAndroidAccessAndProfileState() {
        assertEquals(ConnectedAppStatus.Off, ConnectedAppsParity.status(null, true, false))
        assertEquals(ConnectedAppStatus.Connected, ConnectedAppsParity.status(hiddenChat, true, false))
        assertEquals(
            ConnectedAppStatus.PausedNoNotificationAccess,
            ConnectedAppsParity.status(hiddenChat, false, false),
        )
        assertEquals(ConnectedAppStatus.PausedProfileLocked, ConnectedAppsParity.status(hiddenWork, true, true))
    }
}
