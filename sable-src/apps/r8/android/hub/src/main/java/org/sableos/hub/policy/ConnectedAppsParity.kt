package org.sableos.hub.policy

import org.sableos.hub.ConnectedAppKey
import org.sableos.hub.ConnectedAppPolicy

/** Hub-side state of one connected app, derived from Hub policy plus Android facts. */
enum class ConnectedAppStatus {
    /** Not included in Hub. */
    Off,

    /** Included, and Android currently delivers its notifications to Hub's listener. */
    Connected,

    /** Included, but Android notification access for Hub's listener is off. */
    PausedNoNotificationAccess,

    /** Included, but its work profile / private space is paused or locked. */
    PausedProfileLocked,
}

/**
 * `HUB_CONNECTED_APPS_PARITY=PASS`: what Hub claims about connected apps must match what Android
 * actually grants. Hub's ingestion depends entirely on Android notification access for its
 * NotificationListenerService, so when that access is off:
 *
 * - no app is reported as connected;
 * - Sable Start must not hide any app because of Hub (`content://org.sableos.hub.connected_apps/hidden`
 *   returns nothing), otherwise an app would vanish from Start while Hub cannot show it either;
 * - Hub policy itself is kept, so restoring access restores the user's choices.
 */
object ConnectedAppsParity {
    fun status(
        policy: ConnectedAppPolicy?,
        notificationAccessGranted: Boolean,
        profileLocked: Boolean,
    ): ConnectedAppStatus {
        val normalized = policy?.normalized()
        return when {
            normalized?.includeInMessages != true -> ConnectedAppStatus.Off
            !notificationAccessGranted -> ConnectedAppStatus.PausedNoNotificationAccess
            profileLocked -> ConnectedAppStatus.PausedProfileLocked
            else -> ConnectedAppStatus.Connected
        }
    }

    /**
     * Keys Sable Start may hide. [liveKeys] is the set of launchable package/profile pairs that
     * still exist; null when unknown (then no installed-app filtering happens).
     */
    fun effectiveHiddenKeys(
        policies: Collection<ConnectedAppPolicy>,
        notificationAccessGranted: Boolean,
        liveKeys: Set<ConnectedAppKey>? = null,
    ): Set<ConnectedAppKey> {
        if (!notificationAccessGranted) return emptySet()
        return policies
            .asSequence()
            .map(ConnectedAppPolicy::normalized)
            .filter { it.includeInMessages && it.hideFromLauncher }
            .map(ConnectedAppPolicy::key)
            .filter { liveKeys == null || it in liveKeys }
            .toSet()
    }

    fun statusLabel(status: ConnectedAppStatus): String =
        when (status) {
            ConnectedAppStatus.Off -> "Not in Hub"
            ConnectedAppStatus.Connected -> "In Hub"
            ConnectedAppStatus.PausedNoNotificationAccess -> "Paused: Android notification access is off"
            ConnectedAppStatus.PausedProfileLocked -> "Paused: profile is locked or paused"
        }
}
