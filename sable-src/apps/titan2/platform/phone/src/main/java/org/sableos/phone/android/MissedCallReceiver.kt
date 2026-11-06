package org.sableos.phone.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager

/**
 * Telecom hands the missed call notification to the default Phone app (ACTION_SHOW_MISSED_CALLS_NOTIFICATION),
 * so it comes from Sable Phone and Sable Start's Hub bar opens the Calls tab for it. Only Telecom can send it:
 * the receiver requires MODIFY_PHONE_STATE.
 */
class MissedCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return
        val count = intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0)
        val number = intent.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER).orEmpty()
        val pending = goAsync()
        Thread {
            try {
                val who = PhoneData(context).nameFor(number) ?: CallInfo.formatted(context, number)
                CallNotifications.missed(context, count, who, number)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
