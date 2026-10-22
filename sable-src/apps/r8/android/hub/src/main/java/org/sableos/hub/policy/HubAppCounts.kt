package org.sableos.hub.policy

import org.sableos.hub.ConnectedAppKey

/**
 * What Hub's listener knows of one posted notification when counting it for Sable Start's
 * unread bar (the Ripe/BB7 row of app icons with counts on Start's Hub page). Flags and the
 * source only: never a title or text.
 */
data class ActiveNotificationFacts(
    val packageName: String,
    val userSerial: Long,
    val ongoing: Boolean,
    val foregroundService: Boolean,
    val clearable: Boolean,
    val groupSummary: Boolean,
    /** `Notification.category`, or null. */
    val category: String?,
    /** `Notification.number` (0 when the app sets none). */
    val number: Int,
)

/** One notification as the bar counts it. */
data class CountedNotification(
    val packageName: String,
    val userSerial: Long,
    val weight: Int,
)

/** One row of `content://org.sableos.hub.snapshot/apps`: a package and its unread count. */
data class HubAppCount(
    val packageName: String,
    val count: Int,
)

/**
 * Per-app unread counts for Sable Start's Hub bar. Pure policy, unit tested.
 *
 * - Unread: a posted notification the user can clear that is not ongoing, a foreground service,
 *   a group summary (its children count instead) or a status/progress/media kind of category.
 *   Its weight is `Notification.number` when the app sets one (1 to [MAX_WEIGHT]), else 1.
 * - Only the personal profile: Private Space and other profiles never show on Start's pages.
 * - Apps Hub hides from Start (Connected apps "Hide in Start", through [ConnectedAppsParity]) are
 *   left out, so the bar never shows an app Start hides.
 * - While the device is locked or Sable private mode is on ([HubPrivacyState.countsOnly]) the
 *   rows collapse into one [REDACTED_PACKAGE] row with the total: counts, no app names.
 */
object HubAppCounts {
    /** The package of the one counts-only row (locked device or private mode). */
    const val REDACTED_PACKAGE = "*"
    const val MAX_WEIGHT = 99
    const val MAX_ROWS = 32

    // android.app.Notification.CATEGORY_* that are state, not something to read.
    private val STATUS_CATEGORIES =
        setOf(
            "alarm",
            "call",
            "location_sharing",
            "navigation",
            "progress",
            "recommendation",
            "service",
            "status",
            "stopwatch",
            "sys",
            "transport",
        )

    // System sources, Hub itself and Start: never an app the bar should show.
    private val SKIPPED_PACKAGES =
        setOf(
            "android",
            "com.android.systemui",
            HubIntents.HUB_PACKAGE,
            "org.sableos.launcher",
        )

    /** The weight [facts] adds to its app's count; 0 when it is not unread. */
    fun weight(facts: ActiveNotificationFacts): Int =
        when {
            facts.ongoing || facts.foregroundService || !facts.clearable || facts.groupSummary -> 0
            facts.category in STATUS_CATEGORIES -> 0
            facts.packageName in SKIPPED_PACKAGES || facts.packageName.isBlank() -> 0
            else -> facts.number.coerceIn(1, MAX_WEIGHT)
        }

    /** [facts] as the registry keeps it, or null when it does not count. */
    fun counted(facts: ActiveNotificationFacts): CountedNotification? =
        weight(facts)
            .takeIf { it > 0 }
            ?.let { CountedNotification(facts.packageName, facts.userSerial, it) }

    /**
     * Rows for the provider: most unread first, then by package; at most [MAX_ROWS]. Empty when
     * nothing is unread.
     */
    fun rows(
        active: Collection<CountedNotification>,
        hidden: Set<ConnectedAppKey>,
        personalSerial: Long,
        privacy: HubPrivacyState,
    ): List<HubAppCount> {
        val byPackage =
            active
                .asSequence()
                .filter { it.userSerial == personalSerial && it.weight > 0 }
                .filterNot { ConnectedAppKey(it.packageName, it.userSerial) in hidden }
                .groupBy { it.packageName }
                .mapValues { (_, notifications) -> notifications.sumOf { it.weight } }
        if (privacy.countsOnly) {
            val total = byPackage.values.sum()
            return if (total > 0) listOf(HubAppCount(REDACTED_PACKAGE, total)) else emptyList()
        }
        return byPackage
            .map { (pkg, count) -> HubAppCount(pkg, count) }
            .sortedWith(compareByDescending<HubAppCount> { it.count }.thenBy { it.packageName })
            .take(MAX_ROWS)
    }
}
