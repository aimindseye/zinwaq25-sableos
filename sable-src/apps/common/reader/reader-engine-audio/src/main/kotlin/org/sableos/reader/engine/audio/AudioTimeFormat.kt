package org.sableos.reader.engine.audio

/** Time formatting for audiobook UI, pure so it is tested once and used everywhere. */
object AudioTimeFormat {
    private const val MS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L

    /** `h:mm:ss`, or `m:ss` under an hour. Negative input is shown as zero. */
    fun clock(ms: Long): String {
        val total = (ms / MS_PER_SECOND).coerceAtLeast(0L)
        val hours = total / SECONDS_PER_HOUR
        val minutes = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val seconds = total % SECONDS_PER_MINUTE
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }

    /** A short spoken-style description of a skip interval: `15 s`, `1 min`. */
    fun interval(ms: Long): String {
        val seconds = ms / MS_PER_SECOND
        return if (seconds >= SECONDS_PER_MINUTE && seconds % SECONDS_PER_MINUTE == 0L) {
            "${seconds / SECONDS_PER_MINUTE} min"
        } else {
            "$seconds s"
        }
    }
}
