package org.sableos.phone.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Answer, Decline, Hang up and Call back from the call notifications. Not exported: only Sable Phone's own intents. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ANSWER -> CallBook.ringing()?.let {
                CallControl.answer(it)
                val screen = Intent(
                    context,
                    InCallActivity::class.java
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(screen)
            }

            DECLINE -> CallBook.ringing()?.let(CallControl::decline)

            HANG_UP -> CallBook.primary()?.let(CallControl::hangUp)

            CALL_BACK -> intent.getStringExtra(EXTRA_NUMBER)?.let {
                CallNotifications.missed(context, 0, "", "")
                Dialing.call(context, it)
            }
        }
    }

    companion object {
        const val ANSWER = "org.sableos.phone.action.ANSWER"
        const val DECLINE = "org.sableos.phone.action.DECLINE"
        const val HANG_UP = "org.sableos.phone.action.HANG_UP"
        const val CALL_BACK = "org.sableos.phone.action.CALL_BACK"
        const val EXTRA_NUMBER = "org.sableos.phone.extra.NUMBER"
    }
}
