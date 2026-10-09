package org.sableos.media

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var queueBuildJob: Job? = null
    private var queueGeneration = 0L

    override fun onCreate() {
        super.onCreate()
        val exoPlayer =
            ExoPlayer.Builder(this).build().also { resolvedPlayer ->
                resolvedPlayer.addListener(
                    object : Player.Listener {
                        override fun onMediaItemTransition(
                            mediaItem: MediaItem?,
                            reason: Int,
                        ) {
                            MediaSnapshotStore.update(
                                this@PlaybackService,
                                resolvedPlayer,
                            )
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            MediaSnapshotStore.update(
                                this@PlaybackService,
                                resolvedPlayer,
                            )
                        }
                    },
                )
            }

        player = exoPlayer
        session =
            MediaSession
                .Builder(this, exoPlayer)
                .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_PLAY_URI -> {
                intent.data?.let { uri ->
                    play(
                        uri = uri,
                        title = intent.getStringExtra(EXTRA_TITLE),
                        source = intent.getStringExtra(EXTRA_SOURCE),
                    )
                }
            }

            ACTION_PLAY_QUEUE -> {
                val request =
                    intent
                        .getStringExtra(EXTRA_QUEUE_TOKEN)
                        ?.let(PlaybackQueueRequestStore::consume)
                if (request != null) {
                    playQueue(request)
                }
            }

            ACTION_PAUSE -> {
                player?.pause()
            }

            ACTION_RESUME -> {
                player?.play()
            }

            ACTION_STOP -> {
                cancelPendingQueueBuild()
                player?.stop()
                MediaSnapshotStore.clear(this)
            }

            ACTION_PREVIOUS -> {
                player?.seekToPreviousMediaItem()
            }

            ACTION_NEXT -> {
                player?.seekToNextMediaItem()
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun play(
        uri: Uri,
        title: String?,
        source: String?,
    ) {
        cancelPendingQueueBuild()
        val metadata =
            MediaMetadata
                .Builder()
                .setTitle(title?.takeIf { it.isNotBlank() } ?: "Sable Media")
                .setArtist(source?.takeIf { it.isNotBlank() })
                .build()

        val mediaItem =
            MediaItem
                .Builder()
                .setUri(uri)
                .setMediaMetadata(metadata)
                .build()

        player?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }
    }

    private fun playQueue(request: PlaybackQueueRequest) {
        cancelPendingQueueBuild()
        val generation = ++queueGeneration
        queueBuildJob =
            serviceScope.launch {
                val mediaItems =
                    request.items.map { item ->
                        mediaItem(
                            uri = item.uri,
                            title = item.title,
                            artist = item.artist,
                        )
                    }

                if (mediaItems.isEmpty()) {
                    return@launch
                }

                mainHandler.post {
                    if (generation != queueGeneration) {
                        return@post
                    }
                    player?.apply {
                        setMediaItems(
                            mediaItems,
                            request.startIndex.coerceIn(0, mediaItems.lastIndex),
                            0L,
                        )
                        prepare()
                        play()
                    }
                }
            }
    }

    private fun cancelPendingQueueBuild() {
        queueGeneration++
        queueBuildJob?.cancel()
        queueBuildJob = null
    }

    private fun mediaItem(
        uri: String,
        title: String?,
        artist: String?,
    ): MediaItem {
        val metadata =
            MediaMetadata
                .Builder()
                .setTitle(title?.takeIf { it.isNotBlank() } ?: "Local audio")
                .setArtist(artist?.takeIf { it.isNotBlank() } ?: "Unknown artist")
                .build()

        return MediaItem
            .Builder()
            .setUri(Uri.parse(uri))
            .setMediaMetadata(metadata)
            .build()
    }

    override fun onDestroy() {
        cancelPendingQueueBuild()
        mainHandler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
        session?.release()
        session = null
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY_URI = "org.sableos.media.PLAY_URI"
        const val ACTION_PAUSE = "org.sableos.media.PAUSE"
        const val ACTION_RESUME = "org.sableos.media.RESUME"
        const val ACTION_STOP = "org.sableos.media.STOP"
        const val ACTION_PREVIOUS = "org.sableos.media.PREVIOUS"
        const val ACTION_NEXT = "org.sableos.media.NEXT"
        const val ACTION_PLAY_QUEUE = "org.sableos.media.PLAY_QUEUE"

        const val EXTRA_TITLE = "org.sableos.media.TITLE"
        const val EXTRA_SOURCE = "org.sableos.media.SOURCE"
        const val EXTRA_QUEUE_TOKEN = "org.sableos.media.QUEUE_TOKEN"
    }
}
