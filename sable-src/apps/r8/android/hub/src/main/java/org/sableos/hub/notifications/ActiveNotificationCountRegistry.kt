package org.sableos.hub.notifications

import org.sableos.hub.policy.CountedNotification
import java.util.concurrent.ConcurrentHashMap

/**
 * The posted notifications that count for Sable Start's unread bar, by notification key
 * ([org.sableos.hub.policy.HubAppCounts]). In memory only: the listener seeds it from Android's
 * active notifications whenever it connects and clears it when it disconnects. Holds the source
 * package, its profile serial and a weight; never notification text.
 */
internal object ActiveNotificationCountRegistry {
    private val byKey = ConcurrentHashMap<String, CountedNotification>()

    /** Records or drops [notificationKey]; true when that changed what the bar shows. */
    fun put(
        notificationKey: String,
        counted: CountedNotification?,
    ): Boolean =
        if (counted == null) {
            byKey.remove(notificationKey) != null
        } else {
            byKey.put(notificationKey, counted) != counted
        }

    fun remove(notificationKey: String): Boolean = byKey.remove(notificationKey) != null

    fun replaceAll(entries: Map<String, CountedNotification>) {
        byKey.clear()
        byKey.putAll(entries)
    }

    fun clear() {
        byKey.clear()
    }

    fun snapshot(): List<CountedNotification> = byKey.values.toList()
}
