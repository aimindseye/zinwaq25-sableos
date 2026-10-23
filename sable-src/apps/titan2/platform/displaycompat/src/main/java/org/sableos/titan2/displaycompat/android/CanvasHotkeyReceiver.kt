package org.sableos.titan2.displaycompat.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import org.sableos.titan2.displaycompat.core.CycleText

/**
 * The resolution hotkey (Alt+Shift+R, or Sym+Shift+R). PhoneWindowManager (frameworks/base 0313)
 * sends [ACTION_CYCLE_CANVAS] to this package when the chord is pressed on a hardware keyboard
 * after setup and off the keyguard. The receiver is not exported: only the system UID (the system
 * server, and this app) can reach it. The app in front moves to its next favourite size, the
 * choice is saved to its profile and a short toast names the new size.
 */
class CanvasHotkeyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CYCLE_CANVAS) return
        val app = context.displayCompat
        if (!app.unlocked()) return
        val moment = DeviceMoment.read(context)
        val outcome = app.tools.cycle(moment)
        app.refreshDisplay()
        val label = moment.frontPackage?.let { labelOf(context.packageManager, it) } ?: "this app"
        Toast.makeText(context, CycleText.message(outcome, label), Toast.LENGTH_SHORT).show()
    }

    private fun labelOf(pm: PackageManager, pkg: String): String = runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    companion object {
        const val ACTION_CYCLE_CANVAS = "org.sableos.titan2.displaycompat.action.CYCLE_CANVAS"
    }
}
