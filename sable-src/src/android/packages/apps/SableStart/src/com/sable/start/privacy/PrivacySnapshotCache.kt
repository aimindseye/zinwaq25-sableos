package org.sableos.start.privacy

/**
 * One app instance: a package in one Android user/profile.
 *
 * ```text
 * PACKAGE_ONLY_CACHE_KEY=FORBIDDEN   CROSS_PROFILE_PERMISSION_MERGE=NO
 * ```
 * The same package in the personal and the work profile are two keys with two
 * independent summaries.
 */
data class AppInstanceKey(
    val userId: Int,
    val packageName: String,
)

/**
 * Ephemeral, process-scoped privacy snapshot cache.
 *
 * ```text
 * PERSISTENT_LAUNCHER_PERMISSION_DB=NO   BACKGROUND_SNAPSHOT=YES
 * INVALIDATE_ON_PACKAGE_CHANGE=YES
 * INVALIDATE_ON_PERMISSION_CHANGE_WHERE_SIGNAL_AVAILABLE=YES
 * ```
 *
 * Lives only in memory. [getOrCompute] is called from the inventory
 * background thread, never from row binding; rows read the already-computed
 * value carried on the app entry. Entries older than [maxAgeMs] are recomputed
 * on the next background refresh, which covers app-op changes (overlay,
 * usage access, install apps) that have no change signal for a launcher.
 */
class PrivacySnapshotCache(
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(
        val privacy: AppPrivacy?,
        val computedAtMs: Long,
    )

    private val entries = HashMap<AppInstanceKey, Entry>()
    private val lock = Any()

    /** Returns the cached snapshot, or runs [compute] (null = unreadable) and caches its result. */
    fun getOrCompute(
        key: AppInstanceKey,
        compute: () -> AppPrivacy?,
    ): AppPrivacy? {
        val now = clock()
        synchronized(lock) {
            val cached = entries[key]
            if (cached != null && now - cached.computedAtMs in 0..maxAgeMs) return cached.privacy
        }
        val fresh = compute()
        synchronized(lock) { entries[key] = Entry(fresh, now) }
        return fresh
    }

    fun peek(key: AppInstanceKey): AppPrivacy? = synchronized(lock) { entries[key]?.privacy }

    fun contains(key: AppInstanceKey): Boolean = synchronized(lock) { key in entries }

    /** Package added/changed/removed or its permissions changed, in one user. */
    fun invalidate(
        userId: Int,
        packageName: String,
    ) {
        synchronized(lock) { entries.remove(AppInstanceKey(userId, packageName)) }
    }

    /** A permission-change signal carries a uid; the reader maps it to the packages sharing it. */
    fun invalidatePackages(
        userId: Int,
        packageNames: Collection<String>,
    ) {
        synchronized(lock) { packageNames.forEach { entries.remove(AppInstanceKey(userId, it)) } }
    }

    /** Profile added/removed/locked/unlocked/paused: drop everything for that user. */
    fun invalidateUser(userId: Int) {
        synchronized(lock) { entries.keys.removeAll { it.userId == userId } }
    }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }

    /** Drops entries for app instances that are no longer installed/launchable. */
    fun retainOnly(live: Set<AppInstanceKey>) {
        synchronized(lock) { entries.keys.retainAll(live) }
    }

    val size: Int get() = synchronized(lock) { entries.size }

    companion object {
        const val DEFAULT_MAX_AGE_MS = 60_000L

        /** Android user id of a uid (UserHandle.PER_USER_RANGE). */
        fun userIdOfUid(uid: Int): Int = uid / PER_USER_RANGE

        private const val PER_USER_RANGE = 100_000
    }
}
