package org.sableos.reader.audio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sableos.reader.core.audio.AudiobookSourceLoader
import org.sableos.reader.core.data.local.ItemViewSettingsDao
import org.sableos.reader.engine.audio.AudioManifest
import org.sableos.reader.engine.audio.AudioPlaybackSettings
import org.sableos.reader.engine.audio.AudioSettingsCodec
import org.sableos.reader.engine.audio.MonotonicClock
import org.sableos.reader.engine.audio.PlaybackSourcePolicy
import org.sableos.reader.model.LocatorCodec
import org.sableos.reader.model.TimeLocator
import org.vaachak.reader.core.data.local.BookDao
import org.vaachak.reader.core.data.repository.VaultRepository
import org.vaachak.reader.core.domain.model.BookEntity

/**
 * Local-only audiobook playback: a Media3 `MediaLibraryService` over one ExoPlayer, with a media session (lock screen,
 * notification, headset buttons), foreground playback (`mediaPlayback`), chapters, skip intervals, sleep timer and
 * progress saved every ten seconds, on pause and on stop. The service is exported so that system and other trusted
 * media controllers (notification, lock screen, Bluetooth) can bind, but [AudioSessionCallback.onConnect] admits only
 * this app and system-trusted controllers and rejects everyone else. It accepts only `content:`/`file:` sources
 * (enforced again in the data source) and has no network access: the app holds no INTERNET permission.
 *
 * Media3 unstable APIs (ExoPlayer, data sources) are opted into with `androidx.annotation.OptIn`, the form the Media3
 * lint check recognises; Kotlin's own `OptIn` is deliberately not used for this contract.
 */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class AudiobookPlaybackService : MediaLibraryService() {
    @Inject lateinit var bookDao: BookDao

    @Inject lateinit var vaultRepository: VaultRepository

    @Inject lateinit var settingsDao: ItemViewSettingsDao

    @Inject lateinit var loader: AudiobookSourceLoader

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var player: ExoPlayer
    private var session: MediaLibrarySession? = null
    private var playback: AudiobookPlayback? = null
    private var profileId: String = ""

    private val tick = object : Runnable {
        override fun run() {
            val active = playback
            active?.tick()
            if (active?.needsTick == true) handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = buildPlayer()
        val skipping = SkippingPlayer(player) { playback }
        val callback = AudioSessionCallback(
            ownPackage = packageName,
            onOpenBook = ::openBook,
            onSetSleep = ::setSleep,
            onSetSkip = ::setSkip,
        )
        val mediaSession = MediaLibrarySession.Builder(this, skipping, callback).build()
        session = mediaSession
        playback = AudiobookPlayback(
            player = player,
            clock = MonotonicClock { SystemClock.elapsedRealtime() },
            record = ::recordProgress,
            publish = { mediaSession.setSessionExtras(it) },
        )
        player.addListener(PlaybackListener())
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        playback?.persist(force = true)
        handler.removeCallbacksAndMessages(null)
        session?.release()
        session = null
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildPlayer(): ExoPlayer {
        val localOnly = ResolvingDataSource.Factory(DefaultDataSource.Factory(this)) { dataSpec ->
            if (!PlaybackSourcePolicy.isLocal(dataSpec.uri.toString())) {
                throw IOException("Only audiobooks stored on this device can be played")
            }
            dataSpec
        }
        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(localOnly))
            .setAudioAttributes(
                AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).setUsage(C.USAGE_MEDIA).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
    }

    private inner class PlaybackListener : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                handler.removeCallbacks(tick)
                handler.post(tick)
            } else {
                playback?.persist(force = true)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) playback?.persist(force = true)
        }
    }

    private fun openBook(args: Bundle): ListenableFuture<SessionResult> {
        val hash = args.getString(AudioSessionContract.KEY_BOOK_HASH)
        val result = SettableFuture.create<SessionResult>()
        if (hash.isNullOrBlank()) {
            result.set(SessionResult(SessionError.ERROR_BAD_VALUE))
            return result
        }
        val startMs = args.getLong(AudioSessionContract.KEY_START_MS, CONTINUE)
        val play = args.getBoolean(AudioSessionContract.KEY_PLAY, false)
        scope.launch { result.set(loadAndStart(hash, startMs, play)) }
        return result
    }

    private suspend fun loadAndStart(hash: String, startMs: Long, play: Boolean): SessionResult {
        val active = playback
        val alreadyOpen = active?.bookHash == hash && startMs == CONTINUE
        return when {
            active == null -> SessionResult(SessionError.ERROR_UNKNOWN)
            alreadyOpen -> SessionResult(SessionResult.RESULT_SUCCESS)
            else -> switchBook(active, hash, startMs, play)
        }
    }

    private suspend fun switchBook(
        active: AudiobookPlayback,
        hash: String,
        startMs: Long,
        play: Boolean,
    ): SessionResult {
        profileId = vaultRepository.activeVaultId.first()
        val book = bookDao.getBookByHash(hash, profileId)
        val manifest = book?.let { withContext(Dispatchers.IO) { loader.loadOrBuild(it) } }
        val settings = settingsDao.get(hash, profileId)?.settingsJson?.let(AudioSettingsCodec::decode)
        return if (book == null || manifest == null) {
            SessionResult(SessionError.ERROR_IO)
        } else {
            active.persist(force = true)
            applySettings(settings ?: AudioPlaybackSettings(), active)
            active.open(hash, manifest, mediaItems(book, manifest), resolveStart(book, startMs), play)
            SessionResult(SessionResult.RESULT_SUCCESS)
        }
    }

    private fun applySettings(settings: AudioPlaybackSettings, active: AudiobookPlayback) {
        active.skipBackMs = settings.skipBackMs
        active.skipForwardMs = settings.skipForwardMs
        player.setPlaybackSpeed(settings.speed)
    }

    /** A negative start means "where this title was left": the saved listening position, or the beginning. */
    private fun resolveStart(book: BookEntity, requestedMs: Long): Long {
        val saved = (LocatorCodec.decode(book.progressJson) as? TimeLocator)?.positionMs
        return if (requestedMs >= 0) requestedMs else saved ?: 0L
    }

    private fun mediaItems(book: BookEntity, manifest: AudioManifest): List<MediaItem> {
        val artwork = book.coverPath?.let { Uri.fromFile(File(it)) }
        return manifest.tracks.mapIndexed { index, track ->
            MediaItem.Builder()
                .setMediaId("${book.bookHash}#$index")
                .setUri(track.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(manifest.author)
                        .setAlbumTitle(manifest.title)
                        .setArtworkUri(artwork)
                        .build(),
                )
                .build()
        }
    }

    private fun setSleep(args: Bundle): ListenableFuture<SessionResult> {
        val kind = args.getString(AudioSessionContract.KEY_SLEEP_KIND)
        val mode = AudioSessionContract.sleepModeOf(args)
        val valid = kind == AudioSessionContract.SLEEP_OFF || mode != null
        if (valid) {
            playback?.setSleep(mode)
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
        return ImmediateFuture(
            if (valid) SessionResult(SessionResult.RESULT_SUCCESS) else SessionResult(SessionError.ERROR_BAD_VALUE),
        )
    }

    private fun setSkip(args: Bundle): ListenableFuture<SessionResult> {
        playback?.apply {
            skipBackMs = args.getLong(AudioSessionContract.KEY_SKIP_BACK_MS, skipBackMs)
            skipForwardMs = args.getLong(AudioSessionContract.KEY_SKIP_FORWARD_MS, skipForwardMs)
        }
        return ImmediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    private fun recordProgress(snapshot: ProgressSnapshot) {
        val progress = snapshot.progress
        scope.launch {
            bookDao.updateTypedProgress(
                bookHash = snapshot.bookHash,
                profileId = profileId,
                progress = progress.fraction,
                progressJson = LocatorCodec.encode(progress.locator),
                finished = progress.isFinished,
                timestamp = progress.updatedAt,
            )
        }
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val CONTINUE = -1L
    }
}
