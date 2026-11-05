package org.sableos.start.platform

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.sableos.start.model.FrameMedia
import org.sableos.start.model.FrameMediaCommand

/**
 * Media controls on Active Frames: the active media sessions, newest first, and their own
 * transport controls. SableLauncher is platform-signed, so it holds MEDIA_CONTENT_CONTROL, the
 * permission Android's own media controls in the shade use; without it every call is empty.
 * Start never reads what a session plays beyond its title, and never starts playback by itself.
 */
class FrameMediaRepository(
    context: Context,
) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private fun controllers(): List<MediaController> =
        try {
            manager?.getActiveSessions(null).orEmpty()
        } catch (e: SecurityException) {
            Log.i(TAG, "no media control permission", e)
            emptyList()
        }

    fun sessions(): List<FrameMedia> =
        controllers().map { controller ->
            val state = controller.playbackState
            val actions = state?.actions ?: 0L
            FrameMedia(
                packageName = controller.packageName,
                title = controller.metadata?.description?.title?.toString(),
                playing = state?.state == PlaybackState.STATE_PLAYING,
                canPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
                canNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            )
        }

    fun control(
        packageName: String,
        command: FrameMediaCommand,
    ) {
        val controller = controllers().firstOrNull { it.packageName == packageName } ?: return
        val controls = controller.transportControls
        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        when (command) {
            FrameMediaCommand.Previous -> controls.skipToPrevious()
            FrameMediaCommand.Next -> controls.skipToNext()
            FrameMediaCommand.PlayPause -> if (playing) controls.pause() else controls.play()
        }
    }

    /**
     * Calls [onChange] on the main thread when sessions come or go, or one starts, pauses or
     * changes track. Returns the call that stops listening.
     */
    fun watch(onChange: () -> Unit): () -> Unit {
        val watched = mutableListOf<Pair<MediaController, MediaController.Callback>>()
        fun follow(list: List<MediaController>?) {
            watched.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
            watched.clear()
            list.orEmpty().forEach { controller ->
                val callback =
                    object : MediaController.Callback() {
                        override fun onPlaybackStateChanged(state: PlaybackState?) = onChange()

                        override fun onMetadataChanged(metadata: MediaMetadata?) = onChange()
                    }
                controller.registerCallback(callback, main)
                watched += controller to callback
            }
            onChange()
        }
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { follow(it) }
        try {
            manager?.addOnActiveSessionsChangedListener(listener, null, main)
            follow(controllers())
        } catch (e: SecurityException) {
            Log.i(TAG, "no media control permission", e)
        }
        return {
            runCatching { manager?.removeOnActiveSessionsChangedListener(listener) }
            watched.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
            watched.clear()
        }
    }

    private companion object {
        const val TAG = "FrameMedia"
    }
}
