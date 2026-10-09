package org.sableos.media

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

internal data class QueueItemUiState(
    val index: Int,
    val title: String,
    val source: String,
)

internal enum class PlaybackSkip {
    Previous,
    Next,
}

internal data class PlaybackUiState(
    val connected: Boolean = false,
    val title: String = "Nothing queued",
    val source: String = "Local music or radio",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val currentIndex: Int = -1,
    val queue: List<QueueItemUiState> = emptyList(),
)

internal class PlaybackController(
    context: Context,
    private val onStateChanged: (PlaybackUiState) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var cachedQueue: List<QueueItemUiState> = emptyList()
    private var closed = false
    private var progressUpdatesEnabled = false

    private val listener =
        object : Player.Listener {
            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) {
                publishState(
                    refreshQueue =
                        events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                            cachedQueue.size != player.mediaItemCount,
                )
            }
        }

    private val ticker =
        object : Runnable {
            override fun run() {
                if (closed || !progressUpdatesEnabled) {
                    return
                }

                publishState(refreshQueue = false)
                handler.postDelayed(this, TICK_INTERVAL_MS)
            }
        }

    init {
        val token =
            SessionToken(
                context,
                ComponentName(
                    context,
                    PlaybackService::class.java,
                ),
            )

        val future =
            MediaController
                .Builder(
                    context,
                    token,
                ).buildAsync()

        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess(::attachController)
            },
            { command -> command.run() },
        )
    }

    fun setPlaying(playing: Boolean) {
        if (playing) {
            controller?.play()
        } else {
            controller?.pause()
        }
    }

    fun stop() {
        controller?.stop()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun skip(direction: PlaybackSkip) {
        when (direction) {
            PlaybackSkip.Previous -> controller?.seekToPreviousMediaItem()
            PlaybackSkip.Next -> controller?.seekToNextMediaItem()
        }
    }

    fun playQueueIndex(index: Int) {
        val player = controller ?: return
        if (index !in 0 until player.mediaItemCount) {
            return
        }

        player.seekToDefaultPosition(index)
        player.play()
    }

    fun addToQueue(
        uri: String,
        title: String,
        source: String,
    ): Boolean {
        val player = controller ?: return false
        val item =
            MediaItem
                .Builder()
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setTitle(title)
                        .setArtist(source)
                        .build(),
                ).build()

        player.addMediaItem(item)
        publishState(refreshQueue = true)
        return true
    }

    fun moveQueueItem(
        fromIndex: Int,
        toIndex: Int,
    ) {
        val player = controller ?: return
        val validMove =
            fromIndex in 0 until player.mediaItemCount &&
                toIndex in 0 until player.mediaItemCount &&
                fromIndex != toIndex

        if (!validMove) {
            return
        }

        player.moveMediaItem(fromIndex, toIndex)
    }

    fun removeQueueItem(index: Int) {
        val player = controller ?: return
        if (index !in 0 until player.mediaItemCount) {
            return
        }

        player.removeMediaItem(index)
    }

    fun setProgressUpdatesEnabled(enabled: Boolean) {
        if (closed || progressUpdatesEnabled == enabled) {
            return
        }

        progressUpdatesEnabled = enabled
        handler.removeCallbacks(ticker)
        if (enabled) {
            publishState(refreshQueue = false)
            handler.postDelayed(ticker, TICK_INTERVAL_MS)
        }
    }

    fun close() {
        closed = true
        handler.removeCallbacksAndMessages(null)
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    private fun attachController(resolved: MediaController) {
        handler.post {
            if (closed) {
                resolved.release()
                return@post
            }

            controller = resolved
            resolved.addListener(listener)
            publishState(refreshQueue = true)
            if (progressUpdatesEnabled) {
                handler.postDelayed(ticker, TICK_INTERVAL_MS)
            }
        }
    }

    private fun publishState(refreshQueue: Boolean) {
        val player = controller
        if (player == null) {
            cachedQueue = emptyList()
            onStateChanged(PlaybackUiState())
            return
        }

        if (refreshQueue) {
            cachedQueue = buildQueue(player)
        }

        onStateChanged(buildPlaybackState(player))
    }

    private fun buildQueue(player: Player): List<QueueItemUiState> =
        List(player.mediaItemCount) { index ->
            val metadata = player.getMediaItemAt(index).mediaMetadata
            QueueItemUiState(
                index = index,
                title =
                    metadata.title
                        ?.toString()
                        ?.takeIf(String::isNotBlank)
                        ?: "Untitled audio",
                source =
                    metadata.artist
                        ?.toString()
                        ?.takeIf(String::isNotBlank)
                        ?: "Local audio",
            )
        }

    private fun buildPlaybackState(player: Player): PlaybackUiState {
        val metadata = player.mediaMetadata
        return PlaybackUiState(
            connected = true,
            title =
                metadata.title
                    ?.toString()
                    ?.takeIf(String::isNotBlank)
                    ?: "Nothing queued",
            source =
                metadata.artist
                    ?.toString()
                    ?.takeIf(String::isNotBlank)
                    ?: "Local music or radio",
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = resolvedDuration(player),
            hasPrevious = player.hasPreviousMediaItem(),
            hasNext = player.hasNextMediaItem(),
            currentIndex =
                player.currentMediaItemIndex
                    .takeIf { it != C.INDEX_UNSET }
                    ?: C.INDEX_UNSET,
            queue = cachedQueue,
        )
    }

    private fun resolvedDuration(player: Player): Long =
        player.duration
            .takeIf { it != C.TIME_UNSET && it > 0L }
            ?: 0L

    private companion object {
        const val TICK_INTERVAL_MS = 500L
    }
}
