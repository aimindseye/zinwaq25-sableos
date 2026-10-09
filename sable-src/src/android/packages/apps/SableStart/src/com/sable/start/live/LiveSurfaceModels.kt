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
