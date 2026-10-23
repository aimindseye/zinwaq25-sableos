package org.sableos.titan2.displaycompat.android

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.util.Log

/**
 * Small started service (no UI, no notification) that follows the app in front of the default
 * display for Tall canvas and per-app rotation. It runs only while some profile needs it, starts at
 * boot ([BootReceiver]) and is sticky, so after a crash it comes back and reconciles from the held
 * originals. The system UID is exempt from background start limits.
 *
 * Foreground changes come from `IActivityTaskManager.registerTaskStackListener` (MANAGE_ACTIVITY_TASKS);
 * screen off, screen on and unlock from broadcasts. Every event schedules one debounced reconcile,
 * which reads the top task itself, so the listener never depends on which callback fired.
 */
class DisplayToolsService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val reconcile = Runnable { if (!displayCompat.reconcileNow()) stopSelf() }
    private val listener = StackListener { schedule() }
    private var registered = false

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = schedule()
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED)
        registered = TaskStack.register(listener)
        if (!registered) Log.w(TAG, "task stack listener unavailable; following screen events only")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        schedule()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(reconcile)
        unregisterReceiver(screen)
        if (registered) TaskStack.unregister(listener)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun schedule() {
        handler.removeCallbacks(reconcile)
        handler.postDelayed(reconcile, DEBOUNCE_MS)
    }

    /**
     * An `ITaskStackListener` without the hidden stub class: every one-way call is just "the task
     * stack changed". Anything else (interface or dump transactions) goes to [Binder].
     */
    private class StackListener(private val onChange: () -> Unit) : Binder(DESCRIPTOR) {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code in FIRST_CALL_TRANSACTION..LAST_CALL_TRANSACTION) {
                onChange()
                return true
            }
            return super.onTransact(code, data, reply, flags)
        }
    }

    private object TaskStack {
        private val atm: Any? by lazy {
            runCatching {
                Class.forName(
                    "android.app.ActivityTaskManager"
                ).getMethod("getService").invoke(null)
            }.getOrNull()
        }

        private fun call(name: String, binder: IBinder): Boolean = runCatching {
            val listenerClass = Class.forName(DESCRIPTOR)
            val listener = Class.forName("$DESCRIPTOR\$Stub")
                .getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            Class.forName("android.app.IActivityTaskManager").getMethod(name, listenerClass)
                .invoke(requireNotNull(atm), listener)
            true
        }.onFailure { Log.w(TAG, "$name failed", it) }.getOrDefault(false)

        fun register(binder: IBinder) = call("registerTaskStackListener", binder)

        fun unregister(binder: IBinder) = call("unregisterTaskStackListener", binder)
    }

    private companion object {
        const val TAG = "SableDisplayTools"
        const val DESCRIPTOR = "android.app.ITaskStackListener"
        const val DEBOUNCE_MS = 150L
    }
}
