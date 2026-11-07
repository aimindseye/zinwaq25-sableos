package org.sableos.textreader.privacy

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Lets Sable Tools' My Sable screen switch the Privacy Screen on and off. Only a sender holding the
 * signature permission [PERMISSION] (declared by this app, so only an app signed alike gets it) can reach
 * this receiver. It answers with RESULT_OK and "on" or "off" when the Privacy Screen service is running,
 * and with RESULT_CANCELED when it is not, so the sender can send the user to Accessibility instead.
 */
class PrivacyScreenSwitchReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION || !isOrderedBroadcast) return
        if (PrivacyScreenService.toggle()) {
            resultCode = Activity.RESULT_OK
            resultData = if (PrivacyScreenService.on) STATE_ON else STATE_OFF
        } else {
            resultCode = Activity.RESULT_CANCELED
        }
    }

    companion object {
        const val ACTION = "org.sableos.textreader.action.TOGGLE_PRIVACY_SCREEN"
        const val PERMISSION = "org.sableos.permission.PRIVACY_SCREEN"
        const val STATE_ON = "on"
        const val STATE_OFF = "off"
    }
}
