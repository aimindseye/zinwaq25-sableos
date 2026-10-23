package org.sableos.titan2.displaycompat.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.sableos.titan2.displaycompat.core.PlanRunner

/**
 * Boot: whatever Display Compat still held when the device went down (a crash or a reboot while
 * a Tall canvas or a forced rotation was on) is given back first, before the lock screen is used
 * (LOCKED_BOOT_COMPLETED, device-protected storage only). After the unlock (BOOT_COMPLETED) the
 * profiles are applied again and the service starts if a profile needs it.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> restoreHeld(context)

            Intent.ACTION_BOOT_COMPLETED -> {
                restoreHeld(context)
                val app = context.displayCompat
                if (context.packageManager.isSafeMode) app.controller.resetAll()
                app.refreshDisplay()
            }
        }
    }

    private fun restoreHeld(context: Context) {
        val held = HeldStore(context)
        val failed = runCatching {
            PlanRunner.restoreAll(WmDisplayPort(context.contentResolver), held.read(), held::write)
        }.getOrElse {
            Log.w(TAG, "restore at boot failed", it)
            return
        }
        if (failed.isNotEmpty()) Log.w(TAG, "restore at boot left $failed")
    }

    private companion object {
        const val TAG = "SableDisplayTools"
    }
}
