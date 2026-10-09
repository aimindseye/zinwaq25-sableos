package org.sableos.hub.policy

import org.sableos.hub.ConnectedAppKey
import org.sableos.hub.ConnectedAppPolicy
import org.sableos.hub.ConnectedNotificationRecord
import org.sableos.hub.ConnectedNotificationRetention

/**
 * `HUB_NOTIFICATION_HISTORY=BOUNDED_DERIVED_CACHE_ONLY`.
 *
 * Hub history is a cache derived from notifications Android delivered to an included source. It is
 * bounded in time (per-source retention, at most [MAX_RETENTION_DAYS]), in count (per source and
 * in total) and in scope (only included sources of profiles that still exist). Android remains the
 * owner of system notification history.
 */
object HubHistoryBounds {
    const val MAX_RECORDS_TOTAL = 1_200
    const val MAX_RECORDS_PER_SOURCE = 300
    const val MAX_RETENTION_DAYS = 30
    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

    /**
     * Records to keep, newest first. [liveUserSerials] is the set of Android user serials that
     * still exist for this user; null when unknown (then no profile pruning happens).
     */
    fun retain(
        records: List<ConnectedNotificationRecord>,
        policies: Map<ConnectedAppKey, ConnectedAppPolicy>,
        nowMillis: Long,
        liveUserSerials: Set<Long>? = null,
    ): List<ConnectedNotificationRecord> {
        val hardCutoff = nowMillis - MAX_RETENTION_DAYS * MILLIS_PER_DAY
        val perSource = mutableMapOf<ConnectedAppKey, Int>()
        return records
            .asSequence()
            .sortedWith(compareByDescending<ConnectedNotificationRecord> { it.timestampMillis }.thenBy { it.id })
            .filter { record ->
                val policy = policies[record.key]?.normalized()
                policy?.includeInMessages == true &&
                    (liveUserSerials == null || record.key.userSerial in liveUserSerials) &&
                    record.timestampMillis >= hardCutoff &&
                    record.timestampMillis <= nowMillis + MAX_FUTURE_SKEW_MILLIS &&
                    ConnectedNotificationRetention.shouldKeep(record, policy.retention, nowMillis)
            }.filter { record ->
                val count = perSource.getOrDefault(record.key, 0)
                perSource[record.key] = count + 1
                count < MAX_RECORDS_PER_SOURCE
            }.take(MAX_RECORDS_TOTAL)
            .toList()
    }

    /** Records with a timestamp far in the future would never age out; drop them. */
    private const val MAX_FUTURE_SKEW_MILLIS = MILLIS_PER_DAY
}
