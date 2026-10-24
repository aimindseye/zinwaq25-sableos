package org.sableos.messages.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import org.sableos.messages.core.Attachment
import org.sableos.messages.core.ConversationMessage
import org.sableos.messages.core.MessageKind
import org.sableos.messages.core.MessageStatus
import org.sableos.messages.core.ThreadGrouping
import org.sableos.messages.core.ThreadRow
import org.sableos.messages.core.ThreadSummary
import org.sableos.messages.core.mms.MmsCharsets
import org.sableos.messages.core.mms.MmsHeaders

/** Reads threads and messages from the SMS/MMS provider. Call off the main thread. */
class MessagesRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    val contacts = ContactNames(appContext)

    fun threads(): List<ThreadSummary> {
        val rows = threadRows()
        return ThreadGrouping.summaries(rows, canonicalAddresses(), unreadCounts(), contacts::nameFor)
    }

    /** The thread for these numbers, created by the provider when it does not exist yet. */
    fun threadIdFor(addresses: Collection<String>): Long =
        Telephony.Threads.getOrCreateThreadId(appContext, addresses.toSet())

    fun conversation(threadId: Long): List<ConversationMessage> =
        ThreadGrouping.timeline(smsMessages(threadId) + mmsMessages(threadId))

    /** Marks a thread read; only the default SMS app may write, so failures are logged only. */
    fun markRead(threadId: Long) {
        val values =
            ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }
        val where = "${Telephony.Sms.THREAD_ID} = ? AND (${Telephony.Sms.READ} = 0 OR ${Telephony.Sms.SEEN} = 0)"
        runCatching {
            resolver.update(Telephony.Sms.CONTENT_URI, values, where, arrayOf(threadId.toString()))
            resolver.update(Telephony.Mms.CONTENT_URI, values, where, arrayOf(threadId.toString()))
        }.onFailure { Log.w(TAG, "mark read failed for thread $threadId", it) }
    }

    /** An MMS image part decoded at most [maxPixels] on its longer side, or null. */
    fun attachmentBitmap(
        partId: Long,
        maxPixels: Int,
    ): Bitmap? =
        runCatching {
            val uri = ProviderColumns.mmsPart(partId)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPixels) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.onFailure { Log.w(TAG, "could not decode part $partId", it) }
            .getOrNull()

    private fun threadRows(): List<ThreadRow> =
        query(
            ProviderColumns.THREADS_URI,
            arrayOf(
                ProviderColumns.ID,
                Telephony.Threads.DATE,
                Telephony.Threads.SNIPPET,
                ProviderColumns.THREAD_RECIPIENT_IDS,
                Telephony.Threads.MESSAGE_COUNT,
                Telephony.Threads.READ,
                Telephony.Threads.ERROR,
            ),
            sortOrder = "${Telephony.Threads.DATE} DESC",
        ) { cursor ->
            ThreadRow(
                threadId = cursor.long(ProviderColumns.ID),
                dateMillis = cursor.long(Telephony.Threads.DATE),
                snippet = cursor.string(Telephony.Threads.SNIPPET).orEmpty(),
                recipientIds =
                    cursor
                        .string(ProviderColumns.THREAD_RECIPIENT_IDS)
                        .orEmpty()
                        .split(' ')
                        .mapNotNull { it.toLongOrNull() },
                messageCount = cursor.int(Telephony.Threads.MESSAGE_COUNT),
                read = cursor.int(Telephony.Threads.READ) != 0,
                hasError = cursor.int(Telephony.Threads.ERROR) != 0,
            )
        }

    private fun canonicalAddresses(): Map<Long, String> =
        query(ProviderColumns.CANONICAL_ADDRESSES_URI, arrayOf(ProviderColumns.ID, ProviderColumns.ADDR_ADDRESS)) {
            it.long(ProviderColumns.ID) to it.string(ProviderColumns.ADDR_ADDRESS).orEmpty()
        }.toMap()

    private fun unreadCounts(): Map<Long, Int> {
        val where = Where("${Telephony.Sms.READ} = 0")
        val sms =
            query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.THREAD_ID),
                where,
            ) { it.long(Telephony.Sms.THREAD_ID) }
        val mms =
            query(
                Telephony.Mms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Mms.THREAD_ID),
                where,
            ) { it.long(Telephony.Mms.THREAD_ID) }
        return (sms + mms).groupingBy { it }.eachCount()
    }

    private fun smsMessages(threadId: Long): List<ConversationMessage> =
        query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
                Telephony.Sms.ERROR_CODE,
            ),
            Where("${Telephony.Sms.THREAD_ID} = ?", threadId.toString()),
            "${Telephony.Sms.DATE} DESC LIMIT $MAX_MESSAGES",
        ) { cursor ->
            val type = cursor.int(Telephony.Sms.TYPE)
            ConversationMessage(
                id = cursor.long(Telephony.Sms._ID),
                kind = MessageKind.Sms,
                threadId = threadId,
                address = cursor.string(Telephony.Sms.ADDRESS).orEmpty(),
                body = cursor.string(Telephony.Sms.BODY).orEmpty(),
                dateMillis = cursor.long(Telephony.Sms.DATE),
                direction = MessageStatus.smsDirection(type),
                state = MessageStatus.smsState(type),
                read = cursor.int(Telephony.Sms.READ) != 0,
                errorCode = cursor.int(Telephony.Sms.ERROR_CODE),
            )
        }

    private fun mmsMessages(threadId: Long): List<ConversationMessage> {
        val rows =
            query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(
                    Telephony.Mms._ID,
                    Telephony.Mms.DATE,
                    Telephony.Mms.MESSAGE_BOX,
                    Telephony.Mms.READ,
                    Telephony.Mms.MESSAGE_TYPE,
                    Telephony.Mms.SUBJECT,
                    Telephony.Mms.SUBJECT_CHARSET,
                    Telephony.Mms.RETRIEVE_STATUS,
                ),
                Where("${Telephony.Mms.THREAD_ID} = ?", threadId.toString()),
                "${Telephony.Mms.DATE} DESC LIMIT $MAX_MESSAGES",
            ) { cursor -> MmsRow.from(cursor) }
        return rows.map { row -> mmsMessage(threadId, row) }
    }

    private fun mmsMessage(
        threadId: Long,
        row: MmsRow,
    ): ConversationMessage {
        val parts = if (row.messageType == MmsHeaders.MESSAGE_TYPE_NOTIFICATION_IND) emptyList() else mmsParts(row.id)
        val text = parts.filter { it.contentType == "text/plain" }.joinToString("\n") { it.text }.trim()
        val box = row.box
        return ConversationMessage(
            id = row.id,
            kind =
                if (row.messageType ==
                    MmsHeaders.MESSAGE_TYPE_NOTIFICATION_IND
                ) {
                    MessageKind.MmsNotification
                } else {
                    MessageKind.Mms
                },
            threadId = threadId,
            address = mmsAddress(row.id, incoming = box == MessageStatus.MMS_BOX_INBOX),
            body = text,
            dateMillis = row.dateSeconds * MILLIS_PER_SECOND,
            direction = MessageStatus.mmsDirection(box),
            state = MessageStatus.mmsState(box, row.messageType, row.retrieveStatus != 0),
            read = row.read,
            subject = row.subject,
            attachments =
                parts
                    .filterNot { it.contentType == "text/plain" || it.contentType == "application/smil" }
                    .map { Attachment(it.id, it.contentType, it.name) },
        )
    }

    private class PartRow(
        val id: Long,
        val contentType: String,
        val name: String?,
        val text: String,
    )

    private fun mmsParts(messageId: Long): List<PartRow> =
        query(
            ProviderColumns.MMS_PART_URI,
            arrayOf(
                ProviderColumns.ID,
                ProviderColumns.PART_CONTENT_TYPE,
                ProviderColumns.PART_NAME,
                ProviderColumns.PART_CONTENT_LOCATION,
                ProviderColumns.PART_TEXT,
            ),
            Where("${ProviderColumns.PART_MESSAGE_ID} = ?", messageId.toString()),
            "${ProviderColumns.ID} ASC",
        ) { cursor ->
            PartRow(
                id = cursor.long(ProviderColumns.ID),
                contentType = cursor.string(ProviderColumns.PART_CONTENT_TYPE).orEmpty().lowercase(),
                name = cursor.string(ProviderColumns.PART_NAME) ?: cursor.string(ProviderColumns.PART_CONTENT_LOCATION),
                text = cursor.string(ProviderColumns.PART_TEXT).orEmpty(),
            )
        }

    private fun mmsAddress(
        messageId: Long,
        incoming: Boolean,
    ): String {
        val type = if (incoming) MmsHeaders.ADDR_TYPE_FROM else MmsHeaders.ADDR_TYPE_TO
        return query(
            ProviderColumns.mmsAddressesOf(messageId),
            arrayOf(ProviderColumns.ADDR_ADDRESS),
            Where("${ProviderColumns.ADDR_TYPE} = ?", type.toString()),
        ) { it.string(ProviderColumns.ADDR_ADDRESS).orEmpty() }
            .firstOrNull { it.isNotBlank() && it != INSERT_ADDRESS_TOKEN }
            .orEmpty()
    }

    private fun <T> query(
        uri: Uri,
        projection: Array<String>,
        where: Where? = null,
        sortOrder: String? = null,
        map: (Cursor) -> T,
    ): List<T> =
        runCatching {
            resolver.query(uri, projection, where?.clause, where?.args, sortOrder)?.use { cursor ->
                buildList { while (cursor.moveToNext()) add(map(cursor)) }
            } ?: emptyList()
        }.onFailure { Log.w(TAG, "query $uri failed", it) }
            .getOrDefault(emptyList())

    private class Where(
        val clause: String,
        vararg values: String,
    ) {
        val args: Array<String> = Array(values.size) { values[it] }
    }

    private data class MmsRow(
        val id: Long,
        val dateSeconds: Long,
        val box: Int,
        val read: Boolean,
        val messageType: Int,
        val subject: String?,
        val retrieveStatus: Int,
    ) {
        companion object {
            fun from(cursor: Cursor) =
                MmsRow(
                    id = cursor.long(Telephony.Mms._ID),
                    dateSeconds = cursor.long(Telephony.Mms.DATE),
                    box = cursor.int(Telephony.Mms.MESSAGE_BOX),
                    read = cursor.int(Telephony.Mms.READ) != 0,
                    messageType = cursor.int(Telephony.Mms.MESSAGE_TYPE),
                    subject =
                        decodeSubject(
                            cursor.string(Telephony.Mms.SUBJECT),
                            cursor.int(Telephony.Mms.SUBJECT_CHARSET),
                        ),
                    retrieveStatus = cursor.int(Telephony.Mms.RETRIEVE_STATUS),
                )
        }
    }

    companion object {
        private const val TAG = "SableMessages"
        private const val MAX_MESSAGES = 500
        private const val MILLIS_PER_SECOND = 1000L

        /** "insert-address-token": the MMSC fills in the sender. */
        private const val INSERT_ADDRESS_TOKEN = "insert-address-token"

        /**
         * Subjects are stored as ISO-8859-1 strings of the encoded bytes (AOSP PduPersister), with
         * the charset in sub_cs. Sable stores them the same way.
         */
        fun decodeSubject(
            raw: String?,
            charset: Int,
        ): String? =
            when {
                raw.isNullOrBlank() -> null
                charset == 0 -> raw
                else -> String(raw.toByteArray(Charsets.ISO_8859_1), MmsCharsets.forMib(charset))
            }

        fun encodeSubject(subject: String): String = String(subject.toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1)
    }
}

internal fun Cursor.long(column: String): Long =
    getColumnIndex(column).let {
        if (it < 0 ||
            isNull(it)
        ) {
            0L
        } else {
            getLong(it)
        }
    }

internal fun Cursor.int(column: String): Int =
    getColumnIndex(column).let {
        if (it < 0 ||
            isNull(it)
        ) {
            0
        } else {
            getInt(it)
        }
    }

internal fun Cursor.string(column: String): String? =
    getColumnIndex(column).let {
        if (it < 0 ||
            isNull(it)
        ) {
            null
        } else {
            getString(it)
        }
    }
