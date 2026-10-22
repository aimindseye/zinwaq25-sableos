package org.sableos.start.live

enum class LiveAvailability {
    LOADING,
    LIVE,
    EMPTY,
    PERMISSION_REQUIRED,
    UNAVAILABLE,
    ERROR,
}

data class LiveDatum(
    val title: String,
    val detail: String,
    val availability: LiveAvailability,
    val observedAtEpochMs: Long = 0L,
)

/**
 * One app's unread notifications for the Hub page's unread bar, from Sable Hub's listener
 * (`content://org.sableos.hub.snapshot/apps`): the package and a count, never message text.
 * Package "*" is Hub's counts-only total (device locked or Sable private mode on).
 */
data class AppUnreadCount(
    val packageName: String,
    val count: Int,
) {
    init {
        require(packageName.isNotBlank())
        require(count >= 0)
    }
}

data class LiveSurfaceSnapshot(
    val dateText: String,
    val timeText: String,
    val weather: LiveDatum,
    val calendar: LiveDatum,
    val messages: LiveDatum,
    val mail: LiveDatum,
    val music: LiveDatum,
    val photos: LiveDatum,
    val observedAtEpochMs: Long,
    /** The Hub page's unread bar, most unread first; empty when Hub has nothing or is missing. */
    val hubApps: List<AppUnreadCount> = emptyList(),
) {
    init {
        require(dateText.isNotBlank())
        require(timeText.isNotBlank())
        require(observedAtEpochMs >= 0L)
    }

    fun isFresh(
        nowEpochMs: Long,
        maxAgeMs: Long,
    ): Boolean {
        require(nowEpochMs >= 0L)
        require(maxAgeMs >= 0L)
        if (observedAtEpochMs > nowEpochMs) return false
        return nowEpochMs - observedAtEpochMs <= maxAgeMs
    }
}
