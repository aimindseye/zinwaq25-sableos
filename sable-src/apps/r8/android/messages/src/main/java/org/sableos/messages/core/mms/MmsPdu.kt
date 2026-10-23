package org.sableos.messages.core.mms

/** MMS PDU header field and value codes (OMA-TS-MMS_ENC-V1_3 section 7). */
object MmsHeaders {
    const val BCC = 0x81
    const val CC = 0x82
    const val CONTENT_LOCATION = 0x83
    const val CONTENT_TYPE = 0x84
    const val DATE = 0x85
    const val EXPIRY = 0x88
    const val FROM = 0x89
    const val MESSAGE_CLASS = 0x8A
    const val MESSAGE_ID = 0x8B
    const val MESSAGE_TYPE = 0x8C
    const val MMS_VERSION = 0x8D
    const val MESSAGE_SIZE = 0x8E
    const val PRIORITY = 0x8F
    const val REPORT_ALLOWED = 0x91
    const val STATUS = 0x95
    const val SUBJECT = 0x96
    const val TO = 0x97
    const val TRANSACTION_ID = 0x98

    const val MESSAGE_TYPE_NOTIFICATION_IND = 0x82
    const val MESSAGE_TYPE_NOTIFYRESP_IND = 0x83
    const val MESSAGE_TYPE_RETRIEVE_CONF = 0x84

    const val ADDRESS_PRESENT = 0x80
    const val ABSOLUTE_TOKEN = 0x80
    const val STATUS_RETRIEVED = 0x81
    const val VALUE_NO = 0x81

    /** MMS version 1.2 as a short integer (major 1, minor 2). */
    const val VERSION_1_2 = 0x92

    /** Address types of content://mms/#/addr rows (PduHeaders.FROM/TO/CC/BCC as stored). */
    const val ADDR_TYPE_FROM = 137
    const val ADDR_TYPE_TO = 151
    const val ADDR_TYPE_CC = 130
}

data class ContentType(
    val mimeType: String,
    val charset: Int? = null,
    val name: String? = null,
    val start: String? = null,
) {
    val isMultipart: Boolean
        get() = mimeType.startsWith("multipart/") || mimeType.startsWith("application/vnd.wap.multipart")
}

class PduPart(
    val contentType: ContentType,
    val contentId: String?,
    val contentLocation: String?,
    val fileName: String?,
    val data: ByteArray,
) {
    val mimeType: String get() = contentType.mimeType.lowercase()
    val isText: Boolean get() = mimeType == "text/plain"
    val isSmil: Boolean get() = mimeType == "application/smil"

    /** Name to show and store: filename, Content-Type name, Content-Location, then Content-ID. */
    val displayName: String? get() = fileName ?: contentType.name ?: contentLocation ?: contentId

    fun text(): String = String(data, MmsCharsets.forMib(contentType.charset ?: MmsCharsets.UTF_8))
}

sealed interface MmsPdu {
    val messageType: Int
    val transactionId: String?
    val version: Int
}

/** M-Notification.ind: an MMS waiting at [contentLocation] on the MMSC. */
data class NotificationInd(
    override val transactionId: String?,
    override val version: Int,
    val from: String,
    val subject: String?,
    val messageClass: String?,
    val messageSize: Long,
    /** Expiry: absolute (epoch seconds) or relative (seconds from now). */
    val expirySeconds: Long?,
    val expiryAbsolute: Boolean,
    val contentLocation: String,
) : MmsPdu {
    override val messageType: Int get() = MmsHeaders.MESSAGE_TYPE_NOTIFICATION_IND
}

/** M-Retrieve.conf: the downloaded message. */
data class RetrieveConf(
    override val transactionId: String?,
    override val version: Int,
    val messageId: String?,
    val dateSeconds: Long?,
    val from: String,
    val to: List<String>,
    val cc: List<String>,
    val subject: String?,
    val messageClass: String?,
    val priority: Int?,
    val contentType: ContentType,
    val parts: List<PduPart>,
) : MmsPdu {
    override val messageType: Int get() = MmsHeaders.MESSAGE_TYPE_RETRIEVE_CONF

    /** The message text: its text/plain parts joined in order. */
    val text: String get() = parts.filter { it.isText }.joinToString("\n") { it.text() }.trim()

    /** Parts shown as attachments (not text, not the SMIL layout). */
    val attachments: List<PduPart> get() = parts.filterNot { it.isText || it.isSmil }
}

/** Any other PDU type (delivery report, read report…): recognised, not handled. */
data class OtherPdu(
    override val messageType: Int,
    override val transactionId: String?,
    override val version: Int,
) : MmsPdu
