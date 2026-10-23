package org.sableos.messages.receive

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import org.sableos.messages.core.MmsRecipients
import org.sableos.messages.core.mms.MmsCharsets
import org.sableos.messages.core.mms.MmsHeaders
import org.sableos.messages.core.mms.NotificationInd
import org.sableos.messages.core.mms.PduPart
import org.sableos.messages.core.mms.RetrieveConf
import org.sableos.messages.data.MessagesRepository
import org.sableos.messages.data.ProviderColumns

/** Writes received MMS to the provider, the way AOSP Messaging's PduPersister lays them out. */
class MmsStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    /** Whether this announcement was already stored (MMSCs resend notifications). */
    fun isKnown(notification: NotificationInd): Boolean =
        runCatching {
            resolver
                .query(
                    Telephony.Mms.CONTENT_URI,
                    arrayOf(Telephony.Mms._ID),
                    "${Telephony.Mms.CONTENT_LOCATION} = ? OR " +
                        "(${Telephony.Mms.TRANSACTION_ID} = ? AND ${Telephony.Mms.MESSAGE_TYPE} = ?)",
                    arrayOf(
                        notification.contentLocation,
                        notification.transactionId.orEmpty(),
                        MESSAGE_TYPE_RETRIEVE_CONF.toString(),
                    ),
                    null,
                )?.use { it.count > 0 } ?: false
        }.getOrDefault(false)

    /** Stores the announcement so the thread shows "downloading" until the message arrives. */
    fun storeNotification(
        notification: NotificationInd,
        subscriptionId: Int,
    ): Uri? {
        val threadId = Telephony.Threads.getOrCreateThreadId(appContext, notification.from.ifEmpty { UNKNOWN })
        val values =
            ContentValues().apply {
                put(Telephony.Mms.THREAD_ID, threadId)
                put(Telephony.Mms.DATE, nowSeconds())
                put(Telephony.Mms.READ, 0)
                put(Telephony.Mms.SEEN, 0)
                put(Telephony.Mms.MESSAGE_TYPE, MmsHeaders.MESSAGE_TYPE_NOTIFICATION_IND)
                put(Telephony.Mms.CONTENT_LOCATION, notification.contentLocation)
                put(Telephony.Mms.TRANSACTION_ID, notification.transactionId)
                put(Telephony.Mms.MMS_VERSION, notification.version)
                put(Telephony.Mms.MESSAGE_SIZE, notification.messageSize)
                put(Telephony.Mms.MESSAGE_CLASS, notification.messageClass)
                notification.subject?.let { putSubject(this, it) }
                notification.expirySeconds?.let { put(Telephony.Mms.EXPIRY, it) }
                putSubscription(this, subscriptionId)
            }
        val uri = insert(Telephony.Mms.Inbox.CONTENT_URI, values) ?: return null
        insertAddress(ContentUris.parseId(uri), notification.from, MmsHeaders.ADDR_TYPE_FROM)
        return uri
    }

    fun markDownloadFailed(notificationUri: Uri) {
        val values =
            ContentValues().apply {
                put(
                    Telephony.Mms.RETRIEVE_STATUS,
                    ProviderColumns.RETRIEVE_STATUS_FAILED,
                )
            }
        runCatching { resolver.update(notificationUri, values, null, null) }
    }

    fun markDownloading(notificationUri: Uri) {
        val values = ContentValues().apply { putNull(Telephony.Mms.RETRIEVE_STATUS) }
        runCatching { resolver.update(notificationUri, values, null, null) }
    }

    /** Stores the downloaded message and removes its announcement; returns its thread. */
    fun storeRetrieved(
        conf: RetrieveConf,
        notificationUri: Uri?,
        subscriptionId: Int,
    ): Long {
        val recipients = MmsRecipients.threadRecipients(conf.from, conf.to, conf.cc, selfNumbers(subscriptionId))
        val threadId = Telephony.Threads.getOrCreateThreadId(appContext, recipients.ifEmpty { listOf(UNKNOWN) }.toSet())
        val values = retrievedValues(conf, threadId, subscriptionId)
        val uri = insert(Telephony.Mms.Inbox.CONTENT_URI, values) ?: return threadId
        val messageId = ContentUris.parseId(uri)
        conf.parts.forEach { insertPart(messageId, it) }
        insertAddress(messageId, conf.from, MmsHeaders.ADDR_TYPE_FROM)
        conf.to.forEach { insertAddress(messageId, it, MmsHeaders.ADDR_TYPE_TO) }
        conf.cc.forEach { insertAddress(messageId, it, MmsHeaders.ADDR_TYPE_CC) }
        notificationUri?.let { runCatching { resolver.delete(it, null, null) } }
        return threadId
    }

    private fun retrievedValues(
        conf: RetrieveConf,
        threadId: Long,
        subscriptionId: Int,
    ) = ContentValues().apply {
        put(Telephony.Mms.THREAD_ID, threadId)
        put(Telephony.Mms.DATE, nowSeconds())
        conf.dateSeconds?.let { put(Telephony.Mms.DATE_SENT, it) }
        put(Telephony.Mms.READ, 0)
        put(Telephony.Mms.SEEN, 0)
        put(Telephony.Mms.MESSAGE_TYPE, MESSAGE_TYPE_RETRIEVE_CONF)
        put(Telephony.Mms.MESSAGE_ID, conf.messageId)
        put(Telephony.Mms.TRANSACTION_ID, conf.transactionId)
        put(Telephony.Mms.MMS_VERSION, conf.version)
        put(Telephony.Mms.CONTENT_TYPE, conf.contentType.mimeType)
        put(Telephony.Mms.MESSAGE_CLASS, conf.messageClass)
        conf.priority?.let { put(Telephony.Mms.PRIORITY, it) }
        conf.subject?.let { putSubject(this, it) }
        put(Telephony.Mms.MESSAGE_SIZE, conf.parts.sumOf { it.data.size })
        put(Telephony.Mms.TEXT_ONLY, if (conf.attachments.isEmpty()) 1 else 0)
        putSubscription(this, subscriptionId)
    }

    private fun insertPart(
        messageId: Long,
        part: PduPart,
    ) {
        val values =
            ContentValues().apply {
                put(ProviderColumns.PART_CONTENT_TYPE, part.mimeType)
                put(ProviderColumns.PART_CONTENT_ID, part.contentId?.let { "<$it>" })
                put(ProviderColumns.PART_CONTENT_LOCATION, part.contentLocation)
                put(ProviderColumns.PART_NAME, part.displayName)
                put(ProviderColumns.PART_FILENAME, part.fileName)
                if (part.isText || part.isSmil) {
                    put(ProviderColumns.PART_CHARSET, MmsCharsets.UTF_8)
                    put(ProviderColumns.PART_TEXT, part.text())
                }
            }
        val uri = insert(ProviderColumns.mmsPartsOf(messageId), values) ?: return
        if (part.isText || part.isSmil) return
        runCatching { resolver.openOutputStream(uri)?.use { it.write(part.data) } }
            .onFailure { Log.w(TAG, "could not store an MMS part", it) }
    }

    private fun insertAddress(
        messageId: Long,
        address: String,
        type: Int,
    ) {
        if (address.isBlank()) return
        val values =
            ContentValues().apply {
                put(ProviderColumns.ADDR_ADDRESS, address)
                put(ProviderColumns.ADDR_TYPE, type)
                put(ProviderColumns.ADDR_CHARSET, MmsCharsets.UTF_8)
            }
        insert(ProviderColumns.mmsAddressesOf(messageId), values)
    }

    private fun putSubject(
        values: ContentValues,
        subject: String,
    ) {
        values.put(Telephony.Mms.SUBJECT, MessagesRepository.encodeSubject(subject))
        values.put(Telephony.Mms.SUBJECT_CHARSET, MmsCharsets.UTF_8)
    }

    private fun putSubscription(
        values: ContentValues,
        subscriptionId: Int,
    ) {
        if (SubscriptionManager.isValidSubscriptionId(
                subscriptionId,
            )
        ) {
            values.put(Telephony.Mms.SUBSCRIPTION_ID, subscriptionId)
        }
    }

    /** Our own numbers, to keep them out of group threads; empty when the SIM does not say. */
    private fun selfNumbers(subscriptionId: Int): List<String> {
        val granted =
            appContext.checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
        val manager = appContext.getSystemService(SubscriptionManager::class.java)
        if (!granted || manager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return emptyList()
        return runCatching {
            val id =
                if (SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
                    subscriptionId
                } else {
                    SubscriptionManager.getDefaultSmsSubscriptionId()
                }
            val ids = listOf(id).filter { SubscriptionManager.isValidSubscriptionId(it) }
            ids.map { manager.getPhoneNumber(it) }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun insert(
        uri: Uri,
        values: ContentValues,
    ): Uri? =
        runCatching { resolver.insert(uri, values) }
            .onFailure { Log.e(TAG, "insert into $uri failed", it) }
            .getOrNull()

    private fun nowSeconds(): Long = System.currentTimeMillis() / MILLIS_PER_SECOND

    private companion object {
        const val TAG = "SableMessages"
        const val UNKNOWN = "Unknown"
        const val MILLIS_PER_SECOND = 1000L
        val MESSAGE_TYPE_RETRIEVE_CONF = MmsHeaders.MESSAGE_TYPE_RETRIEVE_CONF
    }
}
