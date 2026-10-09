package org.sableos.reader.ui.audio

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.sableos.reader.audio.AudiobookPlaybackService

/** Suspends until a [ListenableFuture] completes; cancelling the coroutine cancels the future. */
internal suspend fun <T> ListenableFuture<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addListener(
        {
            try {
                continuation.resume(get())
            } catch (e: ExecutionException) {
                continuation.resumeWithException(e.cause ?: e)
            }
        },
        MoreExecutors.directExecutor(),
    )
    continuation.invokeOnCancellation { cancel(false) }
}

/** The screen's connection to the playback service (a Media3 `MediaController`). */
internal class PlaybackConnection(private val context: Context) {
    private var future: ListenableFuture<MediaController>? = null

    suspend fun connect(listener: MediaController.Listener): MediaController {
        val token = SessionToken(context, ComponentName(context, AudiobookPlaybackService::class.java))
        val pending = MediaController.Builder(context, token).setListener(listener).buildAsync()
        future = pending
        return pending.awaitResult()
    }

    fun release() {
        future?.let { MediaController.releaseFuture(it) }
        future = null
    }
}
