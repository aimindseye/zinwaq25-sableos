package io.github.aimindseye.tenseven

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.aimindseye.tenseven.model.HomeStyle
import io.github.aimindseye.tenseven.platform.StartStateRepository

/**
 * SableOS first-boot setup's Look step (org.sableos.start.action.SET_LOOK, extra "look": bb10 or bb7):
 * saves the home style. The manifest lets only senders holding WRITE_SECURE_SETTINGS (setup, the
 * system) reach it; an unknown look changes nothing. Added by the SableOS flavor.
 */
class SableLookReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_SET_LOOK) return
        val style = HomeStyle.entries.firstOrNull { it.stableValue == intent.getStringExtra(EXTRA_LOOK) } ?: return
        StartStateRepository(context).saveHomeStyle(style)
    }

    private companion object {
        const val ACTION_SET_LOOK = "org.sableos.start.action.SET_LOOK"
        const val EXTRA_LOOK = "look"
    }
}
