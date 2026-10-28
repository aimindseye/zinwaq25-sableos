package org.sableos.messages.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import org.sableos.messages.core.Addresses
import org.sableos.messages.core.MessageStatus

/**
 * Whether this app holds the SMS role: then it, not the framework, writes sent and received texts.
 * RoleManager answers for the role itself; Telephony.Sms.getDefaultSmsPackage can return null for
 * a holder the role was given at first boot (seen on the Q25), so it is only the fallback.
 */
fun Context.isDefaultSmsApp(): Boolean {
    val roles = getSystemService(RoleManager::class.java)
    val held =
        runCatching {
            roles != null && roles.isRoleAvailable(RoleManager.ROLE_SMS) && roles.isRoleHeld(RoleManager.ROLE_SMS)
        }.getOrDefault(false)
    return held || Telephony.Sms.getDefaultSmsPackage(this) == packageName
}

/** SmsManager for a subscription, or the default one when the id is invalid (as Sable Hub does). */
fun Context.smsManagerFor(subscriptionId: Int): SmsManager =
    when {
        !SubscriptionManager.isValidSubscriptionId(subscriptionId) -> {
            getSystemService(SmsManager::class.java)
        }

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
        }

        else -> {
            legacySmsManager(subscriptionId)
        }
    }

@Suppress("DEPRECATION")
private fun legacySmsManager(subscriptionId: Int): SmsManager =
    SmsManager.getSmsManagerForSubscriptionId(subscriptionId)

/**
 * Sends texts with SmsManager on the default SMS subscription (as Sable Hub does), split into
 * parts when long. As the default SMS app it records the message in the outbox first and
 * [SmsSentReceiver] moves it to sent or failed from the sent PendingIntents; otherwise the
 * framework records it and Sable Messages must not.
 */
class SmsSender(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun canSend(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    /** Sends [body] to [address]; returns the thread it belongs to. Call off the main thread. */
    @SuppressLint("MissingPermission")
    fun send(
        address: String,
        body: String,
    ): Long {
        val recipient = Addresses.normalize(address)
        require(recipient.isNotEmpty()) { "Recipient is required." }
        require(body.isNotBlank()) { "Message is empty." }
        check(canSend()) { "SMS sending permission is not available." }
        val threadId = Telephony.Threads.getOrCreateThreadId(appContext, setOf(recipient))
        val subscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId()
        val messageUri =
            if (appContext.isDefaultSmsApp()) {
                recordOutbox(
                    recipient,
                    body,
                    threadId,
                    subscriptionId,
                )
            } else {
                null
            }
        transmit(recipient, body, subscriptionId, messageUri)
        return threadId
    }

    /** Sends a failed message again (default app: same provider row, back to the outbox). */
    @SuppressLint("MissingPermission")
    fun retry(
        messageId: Long,
        address: String,
        body: String,
    ) {
        check(canSend()) { "SMS sending permission is not available." }
        val subscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId()
        if (!appContext.isDefaultSmsApp()) {
            transmit(address, body, subscriptionId, null)
            return
        }
        val uri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, messageId.toString())
        val values =
            ContentValues().apply {
                put(Telephony.Sms.TYPE, MessageStatus.SMS_TYPE_OUTBOX)
                put(Telephony.Sms.ERROR_CODE, 0)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
            }
        appContext.contentResolver.update(uri, values, null, null)
        transmit(address, body, subscriptionId, uri)
    }

    private fun recordOutbox(
        address: String,
        body: String,
        threadId: Long,
        subscriptionId: Int,
    ): Uri? {
        val values =
            ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
                put(Telephony.Sms.TYPE, MessageStatus.SMS_TYPE_OUTBOX)
                put(Telephony.Sms.THREAD_ID, threadId)
                put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
            }
        return runCatching { appContext.contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values) }
            .onFailure { Log.w(TAG, "could not record the outgoing message", it) }
            .getOrNull()
    }

    @SuppressLint("MissingPermission")
    private fun transmit(
        address: String,
        body: String,
        subscriptionId: Int,
        messageUri: Uri?,
    ) {
        val manager = appContext.smsManagerFor(subscriptionId)
        val parts = manager.divideMessage(body)
        // Without a provider row of our own (not the default app) there is nothing to update.
        val sentIntents = messageUri?.let { uri -> ArrayList(parts.indices.map { sentIntent(uri, it, parts.size) }) }
        runCatching {
            if (parts.size <= 1) {
                manager.sendTextMessage(address, null, body, sentIntents?.first(), null)
            } else {
                manager.sendMultipartTextMessage(address, null, parts, sentIntents, null)
            }
        }.onFailure { error ->
            Log.w(TAG, "SmsManager refused the message", error)
            messageUri?.let { SmsSentReceiver.markFailed(appContext, it, MessageStatus.ERROR_GENERIC) }
        }
    }

    private fun sentIntent(
        messageUri: Uri,
        part: Int,
        parts: Int,
    ): PendingIntent {
        val intent =
            Intent(appContext, SmsSentReceiver::class.java)
                .setAction(SmsSentReceiver.ACTION_SENT)
                .setData(messageUri)
                .putExtra(SmsSentReceiver.EXTRA_PART, part)
                .putExtra(SmsSentReceiver.EXTRA_PARTS, parts)
        return PendingIntent.getBroadcast(
            appContext,
            part,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val TAG = "SableMessages"
    }
}

/** Moves an outgoing message from the outbox to sent or failed as SmsManager reports each part. */
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_SENT) return
        val uri = intent.data ?: return
        val part = intent.getIntExtra(EXTRA_PART, 0)
        val parts = intent.getIntExtra(EXTRA_PARTS, 1)
        val code = resultCode
        finishInBackground { update(context, uri, code, isLastPart = part >= parts - 1) }
    }

    companion object {
        const val ACTION_SENT = "org.sableos.messages.action.SMS_SENT"
        const val EXTRA_PART = "part"
        const val EXTRA_PARTS = "parts"
        private const val TAG = "SableMessages"

        fun update(
            context: Context,
            uri: Uri,
            resultCode: Int,
            isLastPart: Boolean,
        ) {
            val resolver = context.contentResolver
            val current =
                runCatching {
                    resolver.query(uri, arrayOf(Telephony.Sms.TYPE), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getInt(0) else null
                    }
                }.getOrNull() ?: return
            val next = MessageStatus.afterPartResult(current, resultCode, isLastPart)
            if (next == current) return
            val values =
                ContentValues().apply {
                    put(Telephony.Sms.TYPE, next)
                    if (next == MessageStatus.SMS_TYPE_FAILED) put(Telephony.Sms.ERROR_CODE, resultCode)
                }
            runCatching { resolver.update(uri, values, null, null) }
                .onFailure { Log.w(TAG, "could not update $uri", it) }
        }

        fun markFailed(
            context: Context,
            uri: Uri,
            errorCode: Int,
        ) = update(context, uri, errorCode, isLastPart = true)
    }
}
