package org.sableos.reader.ui.audio

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sableos.reader.audio.AudioSessionContract
import org.sableos.reader.core.audio.AudiobookSourceLoader
import org.sableos.reader.core.data.local.ItemBookmarkDao
import org.sableos.reader.core.data.local.ItemViewSettingsDao
import org.sableos.reader.core.domain.model.ItemBookmarkEntity
import org.sableos.reader.core.domain.model.ItemViewSettingsEntity
import org.sableos.reader.engine.audio.AudioPlaybackSettings
import org.sableos.reader.engine.audio.AudioSettingsCodec
import org.sableos.reader.engine.audio.AudioTimeFormat
import org.sableos.reader.engine.audio.AudiobookTimeline
import org.sableos.reader.engine.audio.SleepMode
import org.sableos.reader.model.LocatorCodec
import org.sableos.reader.model.TimeLocator
import org.sableos.reader.util.runCatchingCancellable
import org.vaachak.reader.core.common.AppCoroutineConfig
import org.vaachak.reader.core.data.local.BookDao
import org.vaachak.reader.core.data.repository.VaultRepository

/**
 * Owns the audiobook screen: it reads the manifest, connects to the playback service, mirrors the player into UI state
 * and turns user intents into player and session commands. The service keeps playing (and saving progress) when the
 * screen is closed; this ViewModel only persists what is the screen's own: skip/speed settings and bookmarks.
 */
@HiltViewModel
class AudiobookReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val bookmarkDao: ItemBookmarkDao,
    private val settingsDao: ItemViewSettingsDao,
    private val vaultRepository: VaultRepository,
    private val loader: AudiobookSourceLoader,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AudiobookUiState())
    val uiState: StateFlow<AudiobookUiState> = mutableState.asStateFlow()

    private val connection = PlaybackConnection(context)
    private var controller: MediaController? = null
    private var timeline: AudiobookTimeline? = null
    private var openedHash: String? = null
    private var profileId: String = ""
    private var pollJob: Job? = null

    fun open(bookHash: String) {
        if (openedHash == bookHash) return
        openedHash = bookHash
        viewModelScope.launch { load(bookHash) }
    }

    private suspend fun load(bookHash: String) {
        profileId = vaultRepository.activeVaultId.first()
        val book = bookDao.getBookByHash(bookHash, profileId)
        val manifest = book?.let { withContext(AppCoroutineConfig.io) { loader.loadOrBuild(it) } }
        if (book == null || manifest == null) {
            val error = if (book == null) AudiobookError.NotFound else AudiobookError.Unreadable
            mutableState.update { it.copy(isLoading = false, error = error) }
            return
        }
        val line = AudiobookTimeline(manifest)
        timeline = line
        val settings = settingsDao.get(bookHash, profileId)?.settingsJson?.let(AudioSettingsCodec::decode)
            ?: AudioPlaybackSettings()
        val saved = (LocatorCodec.decode(book.progressJson) as? TimeLocator)?.positionMs ?: 0L
        mutableState.update {
            it.copy(
                title = manifest.title,
                author = manifest.author,
                coverPath = book.coverPath,
                chapters = manifest.chapters,
                durationMs = line.durationMs,
                positionMs = saved,
                chapterIndex = line.chapterIndexAt(saved),
                settings = settings,
                isLoading = false,
            )
        }
        bookDao.markOpened(bookHash, profileId, System.currentTimeMillis())
        observeBookmarks(bookHash)
        connect(bookHash)
    }

    private suspend fun connect(bookHash: String) {
        val connected = runCatchingCancellable { connection.connect(ControllerListener()) }.getOrNull()
        if (connected == null) {
            mutableState.update { it.copy(error = AudiobookError.PlaybackFailed) }
            return
        }
        controller = connected
        connected.addListener(PlayerListener())
        val args = Bundle().apply {
            putString(AudioSessionContract.KEY_BOOK_HASH, bookHash)
            putLong(AudioSessionContract.KEY_START_MS, -1L)
            putBoolean(AudioSessionContract.KEY_PLAY, false)
        }
        runCatchingCancellable { connected.sendCustomCommand(AudioSessionContract.openBook, args).awaitResult() }
        applySleepExtras(connected.sessionExtras)
        refresh()
        startPolling()
    }

    private inner class ControllerListener : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) = applySleepExtras(extras)
    }

    private inner class PlayerListener : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = refresh()

        override fun onPlaybackStateChanged(playbackState: Int) = refresh()

        override fun onPlayerError(error: PlaybackException) {
            mutableState.update { it.copy(error = AudiobookError.PlaybackFailed, isPlaying = false) }
        }
    }

    private fun applySleepExtras(extras: Bundle) {
        sleepKind = when (extras.getString(AudioSessionContract.EXTRA_SLEEP_KIND)) {
            AudioSessionContract.SLEEP_AFTER -> SleepKind.AFTER
            AudioSessionContract.SLEEP_END_OF_CHAPTER -> SleepKind.END_OF_CHAPTER
            else -> SleepKind.OFF
        }
        endsAtElapsedMs = extras.getLong(AudioSessionContract.EXTRA_SLEEP_ENDS_AT, NO_DEADLINE).takeIf { it >= 0 }
        refresh()
    }

    private var sleepKind: SleepKind = SleepKind.OFF
    private var endsAtElapsedMs: Long? = null

    /** Mirrors the player into UI state. Cheap; called from listeners and the poll loop. */
    private fun refresh() {
        val player = controller
        val line = timeline
        if (player == null || line == null) return
        val position = line.globalPosition(player.currentMediaItemIndex, player.currentPosition)
        val remaining = endsAtElapsedMs?.let { (it - SystemClock.elapsedRealtime()).coerceAtLeast(0L) }
        mutableState.update {
            it.copy(
                positionMs = position,
                chapterIndex = line.chapterIndexAt(position),
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                sleep = SleepUi(sleepKind, remaining),
            )
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(POLL_MS)
            }
        }
    }

    private fun observeBookmarks(bookHash: String) {
        viewModelScope.launch {
            bookmarkDao.getBookmarks(bookHash, profileId).collect { rows ->
                val items = rows.mapNotNull { row ->
                    (LocatorCodec.decode(row.locatorJson) as? TimeLocator)
                        ?.let { AudioBookmarkUi(row.bookmarkId, it.positionMs, row.label) }
                }
                mutableState.update { it.copy(bookmarks = items.sortedBy(AudioBookmarkUi::positionMs)) }
            }
        }
    }

    /** Single entry point for user intents coming from the screen, keyboard and touch. */
    fun onAction(action: AudioUiAction) {
        when (action) {
            AudioUiAction.PlayPause, AudioUiAction.SkipBack, AudioUiAction.SkipForward, is AudioUiAction.SeekTo,
            AudioUiAction.PreviousChapter, AudioUiAction.NextChapter, is AudioUiAction.GoToChapter -> transport(action)
            is AudioUiAction.SetSpeed, is AudioUiAction.SetSkipBack, is AudioUiAction.SetSkipForward,
            is AudioUiAction.SetSleep -> playbackSettings(action)
            AudioUiAction.AddBookmark, is AudioUiAction.DeleteBookmark, is AudioUiAction.GoToBookmark,
            is AudioUiAction.Show -> library(action)
        }
    }

    private fun transport(action: AudioUiAction) {
        when (action) {
            AudioUiAction.PlayPause -> togglePlayback()
            AudioUiAction.SkipBack -> skip(-mutableState.value.settings.skipBackMs)
            AudioUiAction.SkipForward -> skip(mutableState.value.settings.skipForwardMs)
            is AudioUiAction.SeekTo -> seekTo(action.positionMs)
            AudioUiAction.PreviousChapter -> chapterTarget { it.previousChapterStart(mutableState.value.positionMs) }
            AudioUiAction.NextChapter -> chapterTarget { it.nextChapterStart(mutableState.value.positionMs) }
            is AudioUiAction.GoToChapter -> chapterTarget { it.chapterStart(action.index) }
            else -> Unit
        }
    }

    private fun playbackSettings(action: AudioUiAction) {
        when (action) {
            is AudioUiAction.SetSpeed -> setSpeed(action.speed)
            is AudioUiAction.SetSkipBack -> updateSettings { it.copy(skipBackMs = action.ms) }
            is AudioUiAction.SetSkipForward -> updateSettings { it.copy(skipForwardMs = action.ms) }
            is AudioUiAction.SetSleep -> setSleep(action.mode)
            else -> Unit
        }
    }

    private fun library(action: AudioUiAction) {
        when (action) {
            AudioUiAction.AddBookmark -> addBookmark()
            is AudioUiAction.DeleteBookmark -> viewModelScope.launch { bookmarkDao.deleteBookmark(action.id) }
            is AudioUiAction.GoToBookmark ->
                mutableState.value.bookmarks.firstOrNull { it.id == action.id }?.let { seekTo(it.positionMs) }
            is AudioUiAction.Show -> mutableState.update { it.copy(overlay = action.overlay) }
            else -> Unit
        }
    }

    private fun skip(deltaMs: Long) = seekTo(mutableState.value.positionMs + deltaMs)

    private fun chapterTarget(target: (AudiobookTimeline) -> Long?) {
        timeline?.let(target)?.let(::seekTo)
    }

    private fun togglePlayback() {
        val player = controller ?: return
        when {
            player.isPlaying -> player.pause()
            player.playbackState == Player.STATE_ENDED -> {
                seekTo(0L)
                player.play()
            }
            else -> player.play()
        }
    }

    private fun seekTo(globalMs: Long) {
        val line = timeline ?: return
        val player = controller ?: return
        val target = line.locate(globalMs)
        player.seekTo(target.trackIndex, target.offsetMs)
        refresh()
    }

    private fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        updateSettings { it.copy(speed = speed) }
    }

    private fun updateSettings(change: (AudioPlaybackSettings) -> AudioPlaybackSettings) {
        val hash = openedHash ?: return
        val updated = change(mutableState.value.settings)
        mutableState.update { it.copy(settings = updated) }
        viewModelScope.launch {
            settingsDao.upsert(ItemViewSettingsEntity(hash, profileId, AudioSettingsCodec.encode(updated)))
        }
        val args = Bundle().apply {
            putLong(AudioSessionContract.KEY_SKIP_BACK_MS, updated.skipBackMs)
            putLong(AudioSessionContract.KEY_SKIP_FORWARD_MS, updated.skipForwardMs)
        }
        controller?.sendCustomCommand(AudioSessionContract.setSkip, args)
    }

    private fun setSleep(mode: SleepMode?) {
        controller?.sendCustomCommand(AudioSessionContract.setSleep, AudioSessionContract.sleepArgs(mode))
    }

    private fun addBookmark() {
        val hash = openedHash
        val line = timeline
        val position = mutableState.value.positionMs
        val nearExisting =
            mutableState.value.bookmarks.any { kotlin.math.abs(it.positionMs - position) < DUPLICATE_WINDOW_MS }
        if (hash == null || line == null || nearExisting) return
        viewModelScope.launch {
            bookmarkDao.upsertBookmark(
                ItemBookmarkEntity(
                    bookHash = hash,
                    profileId = profileId,
                    locatorJson = LocatorCodec.encode(
                        TimeLocator(position, line.durationMs, line.chapterIndexAt(position)),
                    ),
                    label = "${AudioTimeFormat.clock(position)} · ${mutableState.value.chapterTitle}",
                ),
            )
        }
    }

    /** Releases the controller. Playback itself continues in the service. */
    fun closeScreen() {
        pollJob?.cancel()
        controller = null
        connection.release()
    }

    override fun onCleared() {
        closeScreen()
        super.onCleared()
    }

    private companion object {
        const val POLL_MS = 500L
        const val NO_DEADLINE = -1L
        const val DUPLICATE_WINDOW_MS = 2_000L
    }
}
