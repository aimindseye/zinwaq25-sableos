package org.sableos.messages.receive

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import android.util.Log
import org.sableos.messages.core.Addresses
import org.sableos.messages.core.MessageStatus
import org.sableos.messages.data.finishInBackground

/**
 * SMS_DELIVER: only the default SMS app receives it, and it must write the message to the inbox
 * itself. A long text arrives as several PDUs in one intent; they are joined in order.
 */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts =
            Telephony.Sms.Intents
                .getMessagesFromIntent(intent)
                ?.filterNotNull()
                .orEmpty()
        if (parts.isEmpty()) return
        val subscriptionId =
            intent.getIntExtra(
                SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID,
            )
        finishInBackground { store(context.applicationContext, parts, subscriptionId) }
    }

    private fun store(
        context: Context,
        parts: List<SmsMessage>,
        subscriptionId: Int,
    ) {
        val first = parts.first()
        val address = Addresses.normalize(first.displayOriginatingAddress.orEmpty())
        val body = parts.joinToString("") { it.displayMessageBody.orEmpty() }
        val threadId = Telephony.Threads.getOrCreateThreadId(context, address.ifEmpty { UNKNOWN_SENDER })
        val values =
            ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.DATE_SENT, first.timestampMillis)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.SEEN, 0)
                put(Telephony.Sms.TYPE, MessageStatus.SMS_TYPE_INBOX)
                put(Telephony.Sms.THREAD_ID, threadId)
                put(Telephony.Sms.PROTOCOL, first.protocolIdentifier)
                put(Telephony.Sms.REPLY_PATH_PRESENT, if (first.isReplyPathPresent) 1 else 0)
                put(Telephony.Sms.SERVICE_CENTER, first.serviceCenterAddress)
                if (SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
                    put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
                }
            }
        runCatching { context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) }
            .onFailure { Log.e(TAG, "could not store an incoming SMS", it) }
        // Notify even when the provider refused the row, so a text is never lost silently.
        MessageNotifier.notifyIncoming(context, threadId, address, body)
    }

    private companion object {
        const val TAG = "SableMessages"
        const val UNKNOWN_SENDER = "Unknown"
    }
}
