package org.sableos.messages.receive

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import org.sableos.messages.core.IncomingNotice
import org.sableos.messages.core.mms.MmsPduEncoder
import org.sableos.messages.core.mms.MmsPduParser
import org.sableos.messages.core.mms.NotificationInd
import org.sableos.messages.core.mms.RetrieveConf
import org.sableos.messages.data.finishInBackground
import org.sableos.messages.data.smsManagerFor
import java.io.File

/**
 * WAP_PUSH_DELIVER with an M-Notification.ind: store the announcement and ask MmsService to
 * download the message (SmsManager.downloadMultimediaMessage, over the carrier's MMS APN).
 */
class MmsWapPushReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val notification =
            intent
                .getByteArrayExtra(EXTRA_DATA)
                ?.takeIf { intent.action == Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION }
                ?.let { MmsPduParser.parse(it) as? NotificationInd }
        if (notification == null) {
            Log.i(TAG, "WAP push is not an MMS notification: ${MmsPduParser.lastError}")
            return
        }
        val subscriptionId =
            intent.getIntExtra(
                SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                SubscriptionManager.getDefaultSmsSubscriptionId(),
            )
        finishInBackground { announce(context.applicationContext, notification, subscriptionId) }
    }

    private fun announce(
        context: Context,
        notification: NotificationInd,
        subscriptionId: Int,
    ) {
        val store = MmsStore(context)
        if (store.isKnown(notification)) return
        val uri = store.storeNotification(notification, subscriptionId) ?: return
        MmsTransfer.download(context, uri, notification.contentLocation, subscriptionId)
    }

    private companion object {
        const val TAG = "SableMessages"
        const val EXTRA_DATA = "data"
    }
}

/** Starts downloads and acknowledges them to the MMSC. */
object MmsTransfer {
    const val ACTION_DOWNLOADED = "org.sableos.messages.action.MMS_DOWNLOADED"
    const val ACTION_ACK_SENT = "org.sableos.messages.action.MMS_ACK_SENT"
    const val EXTRA_FILE = "file"
    const val EXTRA_SUBSCRIPTION = "subscription"
    private const val TAG = "SableMessages"

    fun download(
        context: Context,
        notificationUri: Uri,
        contentLocation: String,
        subscriptionId: Int,
    ) {
        MmsFileProvider.deleteStale(context)
        val name = "download-${ContentUris.parseId(notificationUri)}.pdu"
        val intent =
            Intent(context, MmsDownloadedReceiver::class.java)
                .setAction(ACTION_DOWNLOADED)
                .setData(notificationUri)
                .putExtra(EXTRA_FILE, name)
                .putExtra(EXTRA_SUBSCRIPTION, subscriptionId)
        val done =
            PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        MmsStore(context).markDownloading(notificationUri)
        runCatching {
            context
                .smsManagerFor(subscriptionId)
                .downloadMultimediaMessage(context, contentLocation, MmsFileProvider.uriFor(name), null, done)
        }.onFailure { error ->
            Log.w(TAG, "MMS download could not start", error)
            MmsStore(context).markDownloadFailed(notificationUri)
        }
    }

    /** Retries a download the user asked for from the conversation. */
    fun retry(
        context: Context,
        messageId: Long,
    ) {
        val uri = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, messageId)
        val row =
            context.contentResolver
                .query(uri, arrayOf(Telephony.Mms.CONTENT_LOCATION, Telephony.Mms.SUBSCRIPTION_ID), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst() || cursor.isNull(0)) return@use null
                    val subscription =
                        if (cursor.isNull(
                                1,
                            )
                        ) {
                            SubscriptionManager.getDefaultSmsSubscriptionId()
                        } else {
                            cursor.getInt(1)
                        }
                    cursor.getString(0) to subscription
                }
                ?: return
        download(context, uri, row.first, row.second)
    }

    /** M-NotifyResp.ind (status "retrieved"), so the MMSC stops announcing the message. */
    fun acknowledge(
        context: Context,
        conf: RetrieveConf,
        subscriptionId: Int,
        id: Long,
    ) {
        val transactionId = conf.transactionId ?: return
        val name = "ack-$id.pdu"
        val file = File(MmsFileProvider.directory(context), name)
        file.writeBytes(MmsPduEncoder.notifyRespInd(transactionId, conf.version))
        val intent =
            Intent(context, MmsDownloadedReceiver::class.java)
                .setAction(ACTION_ACK_SENT)
                .setData(MmsFileProvider.uriFor(name))
                .putExtra(EXTRA_FILE, name)
        val done =
            PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        runCatching {
            context
                .smsManagerFor(
                    subscriptionId,
                ).sendMultimediaMessage(context, MmsFileProvider.uriFor(name), null, null, done)
        }.onFailure { Log.w(TAG, "MMS acknowledgement not sent", it) }
    }
}

/** MmsService finished a download (or sent the acknowledgement). */
class MmsDownloadedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val file =
            intent
                .getStringExtra(MmsTransfer.EXTRA_FILE)
                ?.let { MmsFileProvider.fileFor(context, MmsFileProvider.uriFor(it)) }
                ?: return
        when (intent.action) {
            MmsTransfer.ACTION_ACK_SENT -> file.delete()
            MmsTransfer.ACTION_DOWNLOADED -> downloaded(context.applicationContext, intent, file)
        }
    }

    private fun downloaded(
        context: Context,
        intent: Intent,
        file: File,
    ) {
        val notificationUri = intent.data
        val subscriptionId =
            intent.getIntExtra(
                MmsTransfer.EXTRA_SUBSCRIPTION,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID,
            )
        val ok = resultCode == Activity.RESULT_OK
        finishInBackground {
            try {
                notificationUri?.let { finishDownload(context, file, it, subscriptionId, ok) }
            } finally {
                if (file.exists() && !file.delete()) Log.w(TAG, "MMS download file not deleted")
            }
        }
    }

    private fun finishDownload(
        context: Context,
        file: File,
        notificationUri: Uri,
        subscriptionId: Int,
        ok: Boolean,
    ) {
        val store = MmsStore(context)
        val conf = if (ok && file.exists()) MmsPduParser.parse(file.readBytes()) as? RetrieveConf else null
        if (conf == null) {
            Log.w(TAG, "MMS download failed (ok=$ok, parse=${MmsPduParser.lastError})")
            store.markDownloadFailed(notificationUri)
            return
        }
        val threadId = store.storeRetrieved(conf, notificationUri, subscriptionId)
        MmsTransfer.acknowledge(context, conf, subscriptionId, ContentUris.parseId(notificationUri))
        val body = IncomingNotice.mmsBody(conf.text, conf.subject, conf.attachments.map { it.mimeType })
        MessageNotifier.notifyIncoming(context, threadId, conf.from, body)
    }

    private companion object {
        const val TAG = "SableMessages"
    }
}
