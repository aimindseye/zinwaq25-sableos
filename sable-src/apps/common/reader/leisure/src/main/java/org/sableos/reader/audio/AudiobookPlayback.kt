package org.sableos.reader.audio

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import org.sableos.reader.engine.audio.AudioManifest
import org.sableos.reader.engine.audio.AudioPlaybackSettings
import org.sableos.reader.engine.audio.AudiobookTimeline
import org.sableos.reader.engine.audio.MonotonicClock
import org.sableos.reader.engine.audio.ProgressThrottle
import org.sableos.reader.engine.audio.SleepAction
import org.sableos.reader.engine.audio.SleepMode
import org.sableos.reader.engine.audio.SleepTimer
import org.sableos.reader.model.ReadingProgress

/** Listening position to record. */
internal class ProgressSnapshot(val bookHash: String, val progress: ReadingProgress)

/**
 * The service's view of one audiobook: the whole-book timeline over the player's playlist, the skip intervals, the
 * sleep timer and progress throttling. All state changes happen on the main thread. The decisions (what a position
 * means, when the timer fires, when progress is written) come from the pure `reader-engine-audio` classes.
 */
internal class AudiobookPlayback(
    private val player: Player,
    private val clock: MonotonicClock,
    private val record: (ProgressSnapshot) -> Unit,
    private val publish: (Bundle) -> Unit,
) {
    private val throttle = ProgressThrottle(clock)
    private var timeline: AudiobookTimeline? = null
    private var sleep: SleepTimer? = null

    var bookHash: String? = null
        private set
    var skipBackMs: Long = AudioPlaybackSettings.DEFAULT_SKIP_BACK_MS
    var skipForwardMs: Long = AudioPlaybackSettings.DEFAULT_SKIP_FORWARD_MS

    /** True while something needs the one-second tick: playback, or an armed sleep timer. */
    val needsTick: Boolean get() = player.isPlaying || sleep?.isArmed == true

    fun open(hash: String, manifest: AudioManifest, items: List<MediaItem>, startMs: Long, play: Boolean) {
        val line = AudiobookTimeline(manifest)
        timeline = line
        sleep = SleepTimer(clock, line)
        bookHash = hash
        throttle.reset()
        val start = line.locate(startMs)
        player.setMediaItems(items, start.trackIndex, start.offsetMs)
        player.prepare()
        player.playWhenReady = play
        publishState()
    }

    fun globalPosition(): Long =
        timeline?.globalPosition(player.currentMediaItemIndex, player.currentPosition) ?: 0L

    fun seekToGlobal(globalMs: Long) {
        val line = timeline ?: return
        val target = line.locate(globalMs)
        player.seekTo(target.trackIndex, target.offsetMs)
    }

    fun skipBack() = seekToGlobal(globalPosition() - skipBackMs)

    fun skipForward() = seekToGlobal(globalPosition() + skipForwardMs)

    fun nextChapter() {
        val line = timeline ?: return
        line.nextChapterStart(globalPosition())?.let(::seekToGlobal)
    }

    fun previousChapter() {
        val line = timeline ?: return
        seekToGlobal(line.previousChapterStart(globalPosition()))
    }

    fun setSleep(mode: SleepMode?) {
        val timer = sleep ?: return
        if (mode == null) timer.cancel() else timer.start(mode, globalPosition())
        publishState()
    }

    /** About once a second: fires the sleep timer if due and records progress at most every ten seconds. */
    fun tick() {
        val position = globalPosition()
        when (val action = sleep?.tick(position) ?: SleepAction.None) {
            SleepAction.None -> Unit
            is SleepAction.Pause -> {
                player.pause()
                action.seekToMs?.let(::seekToGlobal)
                publishState()
                persist(force = true)
            }
        }
        persist(force = false)
    }

    /** Writes the current position: throttled while playing, immediately when [force] (pause, stop, close). */
    fun persist(force: Boolean) {
        val hash = bookHash
        val line = timeline
        if (hash == null || line == null || !throttle.shouldWrite(force)) return
        val position = globalPosition()
        val chapter = line.chapterIndexAt(position)
        val progress = ReadingProgress.time(position, line.durationMs, chapter, System.currentTimeMillis())
        record(ProgressSnapshot(hash, progress))
    }

    private fun publishState() {
        val timer = sleep
        val extras = Bundle().apply {
            putString(AudioSessionContract.EXTRA_BOOK_HASH, bookHash)
            val kind = when (timer?.activeMode) {
                is SleepMode.After -> AudioSessionContract.SLEEP_AFTER
                SleepMode.EndOfChapter -> AudioSessionContract.SLEEP_END_OF_CHAPTER
                null -> AudioSessionContract.SLEEP_OFF
            }
            putString(AudioSessionContract.EXTRA_SLEEP_KIND, kind)
            putLong(AudioSessionContract.EXTRA_SLEEP_ENDS_AT, timer?.endsAtElapsedMs ?: NO_DEADLINE)
        }
        publish(extras)
    }

    private companion object {
        const val NO_DEADLINE = -1L
    }
}
