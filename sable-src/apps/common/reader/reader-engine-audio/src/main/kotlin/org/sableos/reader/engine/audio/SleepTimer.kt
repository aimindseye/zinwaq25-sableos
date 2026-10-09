package org.sableos.reader.engine.audio

/** Monotonic time source, injected so the timer is deterministic in tests (Android: `SystemClock.elapsedRealtime`). */
fun interface MonotonicClock {
    fun nowMs(): Long
}

/** What a sleep timer was asked to do. */
sealed interface SleepMode {
    /** Stop after [durationMs] of wall-clock time. */
    data class After(val durationMs: Long) : SleepMode

    /** Stop when the chapter that is playing now ends. */
    data object EndOfChapter : SleepMode
}

/** What the caller must do after a [SleepTimer.tick]. */
sealed interface SleepAction {
    data object None : SleepAction

    /** The timer fired: pause playback. When [seekToMs] is set, first seek there (the end of the armed chapter). */
    data class Pause(val seekToMs: Long? = null) : SleepAction
}

/**
 * Sleep timer for audiobook playback. It owns no thread and reads time only from the injected [clock], so everything
 * is deterministic. The service calls [tick] about once a second with the current global position; a fired timer
 * disarms itself, so it can never pause playback twice or after the user resumed on purpose.
 */
class SleepTimer(private val clock: MonotonicClock, private val timeline: AudiobookTimeline) {
    private var mode: SleepMode? = null
    private var endsAtMs: Long = NOT_ARMED
    private var armedChapter: Int = NO_CHAPTER

    val isArmed: Boolean get() = mode != null

    /** The armed mode, for display. */
    val activeMode: SleepMode? get() = mode

    /** Monotonic clock reading at which a time-based timer fires, or null when none is armed. */
    val endsAtElapsedMs: Long? get() = endsAtMs.takeIf { it != NOT_ARMED }

    fun start(newMode: SleepMode, currentPositionMs: Long) {
        when (newMode) {
            is SleepMode.After -> {
                require(newMode.durationMs > 0) { "a sleep timer needs a positive duration" }
                endsAtMs = clock.nowMs() + newMode.durationMs
                armedChapter = NO_CHAPTER
            }
            SleepMode.EndOfChapter -> {
                endsAtMs = NOT_ARMED
                armedChapter = timeline.chapterIndexAt(currentPositionMs)
            }
        }
        mode = newMode
    }

    fun cancel() {
        mode = null
        endsAtMs = NOT_ARMED
        armedChapter = NO_CHAPTER
    }

    /** Adds [extraMs] to a running time-based timer; ignored for other modes. Returns whether it was applied. */
    fun extend(extraMs: Long): Boolean {
        val running = mode is SleepMode.After && endsAtMs != NOT_ARMED && extraMs > 0
        if (running) endsAtMs += extraMs
        return running
    }

    fun remainingMs(): Long? = if (mode is SleepMode.After) (endsAtMs - clock.nowMs()).coerceAtLeast(0L) else null

    fun tick(positionMs: Long): SleepAction {
        val action = when (mode) {
            null -> SleepAction.None
            is SleepMode.After -> if (clock.nowMs() >= endsAtMs) SleepAction.Pause() else SleepAction.None
            SleepMode.EndOfChapter -> endOfChapterAction(positionMs)
        }
        if (action is SleepAction.Pause) cancel()
        return action
    }

    private fun endOfChapterAction(positionMs: Long): SleepAction {
        val current = timeline.chapterIndexAt(positionMs)
        val finished = current != armedChapter || positionMs >= timeline.durationMs
        return if (finished) SleepAction.Pause(seekToMs = timeline.chapterEnd(armedChapter)) else SleepAction.None
    }

    private companion object {
        const val NOT_ARMED = -1L
        const val NO_CHAPTER = -1
    }
}
