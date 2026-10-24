package org.sableos.messages.receive

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log
import org.sableos.messages.core.ComposeRequest
import org.sableos.messages.core.SmsLinks
import org.sableos.messages.data.Background
import org.sableos.messages.data.SmsSender

/** "Reply with a message" when declining a call: the dialer hands over the number and text. */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val request =
            intent
                ?.takeIf { it.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE }
                ?.let { SmsLinks.parse(it.data?.schemeSpecificPart, null, it.getStringExtra(Intent.EXTRA_TEXT)) }
        Background.run {
            request?.takeIf { it.body.isNotBlank() }?.let(::reply)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun reply(request: ComposeRequest) {
        request.recipients.forEach { recipient ->
            runCatching { SmsSender(this).send(recipient, request.body) }
                .onFailure { Log.w(TAG, "quick reply not sent", it) }
        }
    }

    private companion object {
        const val TAG = "SableMessages"
    }
}
