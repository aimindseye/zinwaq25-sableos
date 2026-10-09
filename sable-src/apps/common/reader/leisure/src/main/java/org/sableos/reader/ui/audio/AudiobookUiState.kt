package org.sableos.reader.ui.audio

import org.sableos.reader.engine.audio.AudioChapter
import org.sableos.reader.engine.audio.AudioPlaybackSettings

/** Transient surfaces the audiobook screen can show. At most one is open at a time. */
enum class AudioOverlay { NONE, CHAPTERS, BOOKMARKS, SETTINGS, HINTS }

data class AudioBookmarkUi(val id: String, val positionMs: Long, val label: String)

enum class SleepKind { OFF, AFTER, END_OF_CHAPTER }

/** What the sleep timer is doing right now, as reported by the playback service. */
data class SleepUi(val kind: SleepKind = SleepKind.OFF, val remainingMs: Long? = null)

sealed interface AudiobookError {
    data object NotFound : AudiobookError

    data object Unreadable : AudiobookError

    data object PlaybackFailed : AudiobookError
}

data class AudiobookUiState(
    val title: String = "",
    val author: String? = null,
    val coverPath: String? = null,
    val chapters: List<AudioChapter> = emptyList(),
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val chapterIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val settings: AudioPlaybackSettings = AudioPlaybackSettings(),
    val sleep: SleepUi = SleepUi(),
    val bookmarks: List<AudioBookmarkUi> = emptyList(),
    val overlay: AudioOverlay = AudioOverlay.NONE,
    val isLoading: Boolean = true,
    val error: AudiobookError? = null,
) {
    val chapterTitle: String get() = chapters.getOrNull(chapterIndex)?.title.orEmpty()

    val hasTransientSurface: Boolean get() = overlay != AudioOverlay.NONE
}
