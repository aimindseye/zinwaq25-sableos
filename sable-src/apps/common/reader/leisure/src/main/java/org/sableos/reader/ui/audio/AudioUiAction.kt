package org.sableos.reader.ui.audio

import org.sableos.reader.engine.audio.SleepMode

/** Every user intent the audiobook screen understands; one entry point keeps the ViewModel small and testable. */
sealed interface AudioUiAction {
    data object PlayPause : AudioUiAction

    data object SkipBack : AudioUiAction

    data object SkipForward : AudioUiAction

    /** Seek to a whole-book position in milliseconds. */
    data class SeekTo(val positionMs: Long) : AudioUiAction

    data object PreviousChapter : AudioUiAction

    data object NextChapter : AudioUiAction

    data class GoToChapter(val index: Int) : AudioUiAction

    data class SetSpeed(val speed: Float) : AudioUiAction

    data class SetSkipBack(val ms: Long) : AudioUiAction

    data class SetSkipForward(val ms: Long) : AudioUiAction

    /** A null [mode] cancels the timer. */
    data class SetSleep(val mode: SleepMode?) : AudioUiAction

    data object AddBookmark : AudioUiAction

    data class DeleteBookmark(val id: String) : AudioUiAction

    data class GoToBookmark(val id: String) : AudioUiAction

    data class Show(val overlay: AudioOverlay) : AudioUiAction
}
