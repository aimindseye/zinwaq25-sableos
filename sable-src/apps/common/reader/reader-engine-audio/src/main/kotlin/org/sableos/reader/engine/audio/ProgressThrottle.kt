package org.sableos.reader.engine.audio

/**
 * Decides when listening progress is written to storage: at most once per [intervalMs] while playing, and always when
 * forced (pause, stop, close). Keeps the database quiet during hours of playback without losing more than one interval.
 */
class ProgressThrottle(private val clock: MonotonicClock, private val intervalMs: Long = DEFAULT_INTERVAL_MS) {
    private var lastWriteMs: Long = NEVER

    fun shouldWrite(force: Boolean = false): Boolean {
        val now = clock.nowMs()
        val due = force || lastWriteMs == NEVER || now - lastWriteMs >= intervalMs
        if (due) lastWriteMs = now
        return due
    }

    fun reset() {
        lastWriteMs = NEVER
    }

    companion object {
        const val DEFAULT_INTERVAL_MS: Long = 10_000L
        private const val NEVER = Long.MIN_VALUE
    }
}
