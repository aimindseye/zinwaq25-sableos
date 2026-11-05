package org.sableos.titan2.printtogo.android

import android.os.Handler
import android.os.Looper

/** What the screen shows, shared between the service and the screen in one process. */
object PrintToGoState {
    enum class Phase { Off, Ready, NoWifi, Failed, IdleOff }

    data class Snapshot(
        val phase: Phase = Phase.Off,
        val printerName: String = "",
        /** The last print saved or failed, already worded for the screen. */
        val last: String? = null
    )

    @Volatile
    var snapshot = Snapshot()
        private set

    private val listeners = mutableSetOf<(Snapshot) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun update(change: (Snapshot) -> Snapshot) {
        synchronized(this) { snapshot = change(snapshot) }
        val now = snapshot
        main.post { listeners.toList().forEach { it(now) } }
    }

    fun listen(listener: (Snapshot) -> Unit) {
        listeners += listener
        listener(snapshot)
    }

    fun forget(listener: (Snapshot) -> Unit) {
        listeners -= listener
    }
}
