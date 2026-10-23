package org.sableos.messages.data

import android.content.BroadcastReceiver
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** One worker thread for provider I/O, so writes from receivers and the UI stay ordered. */
object Background {
    private const val TAG = "SableMessages"
    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "sable-messages-io")
        }
    private val main = Handler(Looper.getMainLooper())

    fun run(work: () -> Unit) {
        executor.execute {
            runCatching(work).onFailure { Log.e(TAG, "background work failed", it) }
        }
    }

    /** Runs [work] on the worker and hands its result to [onResult] on the main thread. */
    fun <T> load(
        work: () -> T,
        onResult: (T) -> Unit,
    ) {
        run {
            val result = work()
            main.post { onResult(result) }
        }
    }

    fun postDelayed(
        token: Any,
        delayMillis: Long,
        action: () -> Unit,
    ) {
        main.removeCallbacksAndMessages(token)
        main.postAtTime(action, token, android.os.SystemClock.uptimeMillis() + delayMillis)
    }

    fun cancel(token: Any) = main.removeCallbacksAndMessages(token)
}

/** Runs a receiver's provider work on [Background], keeping the broadcast alive until it ends. */
fun BroadcastReceiver.finishInBackground(work: () -> Unit) {
    val pending = goAsync()
    Background.run {
        try {
            work()
        } finally {
            pending.finish()
        }
    }
}
