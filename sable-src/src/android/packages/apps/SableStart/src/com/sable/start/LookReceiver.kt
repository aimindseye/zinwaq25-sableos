package org.sableos.start

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.sableos.start.model.StartLook
import org.sableos.start.platform.StartStateRepository

/**
 * First-boot setup's Look step (org.sableos.start.action.SET_LOOK, extra "look": bb10, bb7 or
 * plain): saves the home style and the BB10 home's status dials. The manifest lets only senders
 * holding WRITE_SECURE_SETTINGS (setup, the system) reach it; an unknown look changes nothing.
 */
class LookReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_SET_LOOK) return
        val look = StartLook.fromStableValue(intent.getStringExtra(EXTRA_LOOK)) ?: return
        val state = StartStateRepository(context)
        state.saveHomeStyle(look.homeStyle)
        state.saveHiddenHomeSections(look.hiddenSections(state.loadHiddenHomeSections()))
    }

    private companion object {
        const val ACTION_SET_LOOK = "org.sableos.start.action.SET_LOOK"
        const val EXTRA_LOOK = "look"
    }
}
