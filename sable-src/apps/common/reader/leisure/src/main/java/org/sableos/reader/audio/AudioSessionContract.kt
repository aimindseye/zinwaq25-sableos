package org.sableos.reader.audio

import android.os.Bundle
import androidx.media3.session.SessionCommand
import org.sableos.reader.engine.audio.SleepMode

/**
 * The private protocol between the audiobook screen and the playback service. The service is exported for system and
 * trusted media controllers, but only this app's own package is given these commands (see [AudioSessionCallback]); each
 * one carries plain values and the service validates them again.
 */
object AudioSessionContract {
    private const val PREFIX = "org.sableos.reader.audio."

    const val ACTION_OPEN_BOOK = PREFIX + "OPEN_BOOK"
    const val ACTION_SET_SLEEP = PREFIX + "SET_SLEEP"
    const val ACTION_SET_SKIP = PREFIX + "SET_SKIP"

    const val KEY_BOOK_HASH = "bookHash"
    const val KEY_START_MS = "startMs"
    const val KEY_PLAY = "play"
    const val KEY_SLEEP_KIND = "sleepKind"
    const val KEY_SLEEP_DURATION_MS = "sleepDurationMs"
    const val KEY_SKIP_BACK_MS = "skipBackMs"
    const val KEY_SKIP_FORWARD_MS = "skipForwardMs"

    /** Session extras published by the service so the screen can show the sleep timer. */
    const val EXTRA_SLEEP_KIND = "sleepKind"
    const val EXTRA_SLEEP_ENDS_AT = "sleepEndsAtElapsedMs"
    const val EXTRA_BOOK_HASH = "activeBookHash"

    const val SLEEP_OFF = "OFF"
    const val SLEEP_AFTER = "AFTER"
    const val SLEEP_END_OF_CHAPTER = "END_OF_CHAPTER"

    val openBook = SessionCommand(ACTION_OPEN_BOOK, Bundle.EMPTY)
    val setSleep = SessionCommand(ACTION_SET_SLEEP, Bundle.EMPTY)
    val setSkip = SessionCommand(ACTION_SET_SKIP, Bundle.EMPTY)

    fun sleepArgs(mode: SleepMode?): Bundle = Bundle().apply {
        when (mode) {
            null -> putString(KEY_SLEEP_KIND, SLEEP_OFF)
            is SleepMode.After -> {
                putString(KEY_SLEEP_KIND, SLEEP_AFTER)
                putLong(KEY_SLEEP_DURATION_MS, mode.durationMs)
            }
            SleepMode.EndOfChapter -> putString(KEY_SLEEP_KIND, SLEEP_END_OF_CHAPTER)
        }
    }

    fun sleepModeOf(args: Bundle): SleepMode? = when (args.getString(KEY_SLEEP_KIND)) {
        SLEEP_AFTER -> args.getLong(KEY_SLEEP_DURATION_MS).takeIf { it > 0 }?.let { SleepMode.After(it) }
        SLEEP_END_OF_CHAPTER -> SleepMode.EndOfChapter
        else -> null
    }
}
