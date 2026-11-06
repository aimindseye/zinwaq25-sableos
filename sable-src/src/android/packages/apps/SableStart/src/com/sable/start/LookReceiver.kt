package org.sableos.start

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.sableos.start.model.StartLook
import org.sableos.start.platform.StartStateRepository

/**
 * First-boot setup's Look step (org.sableos.start.action.SET_LOOK, extra "look": bb10 or bb7):
 * saves the home style. The manifest lets only senders
 * holding WRITE_SECURE_SETTINGS (setup, the system) reach it; an unknown look changes nothing.
 */
class LookReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_SET_LOOK) return
        val look = StartLook.fromStableValue(intent.getStringExtra(EXTRA_LOOK)) ?: return
        StartStateRepository(context).saveHomeStyle(look.homeStyle)
    }

    private companion object {
        const val ACTION_SET_LOOK = "org.sableos.start.action.SET_LOOK"
        const val EXTRA_LOOK = "look"
    }
}
