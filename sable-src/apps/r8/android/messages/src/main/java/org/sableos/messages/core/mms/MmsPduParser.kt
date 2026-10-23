package org.sableos.messages.core.mms

/**
 * Parses M-Notification.ind and M-Retrieve.conf PDUs. Written for Sable Messages rather than
 * copied from AOSP's com.google.android.mms (hidden platform API): it decodes the headers Sable
 * stores and skips every other header generically, since all WSP values are self-delimiting.
 */
object MmsPduParser {
    /** The parsed PDU, or null when [bytes] is not a well-formed MMS PDU. */
    fun parse(bytes: ByteArray): MmsPdu? =
        try {
            parseOrThrow(bytes)
        } catch (error: PduFormatException) {
            lastError = error.message
            null
        }

    /** Why the last [parse] returned null (for logs). */
    @Volatile
    var lastError: String? = null
        private set

    fun parseOrThrow(bytes: ByteArray): MmsPdu {
        val reader = PduReader(bytes)
        val headers = Headers()
        while (reader.hasMore() && headers.contentType == null) {
            readHeader(reader, headers)
        }
        val messageType = headers.messageType ?: throw PduFormatException("no message type")
        return when (messageType) {
            MmsHeaders.MESSAGE_TYPE_NOTIFICATION_IND -> {
                val location = headers.contentLocation ?: throw PduFormatException("notification without location")
                NotificationInd(
                    transactionId = headers.transactionId,
                    version = headers.version,
                    from = headers.from.orEmpty(),
                    subject = headers.subject,
                    messageClass = headers.messageClass,
                    messageSize = headers.messageSize ?: 0L,
                    expirySeconds = headers.expiry,
                    expiryAbsolute = headers.expiryAbsolute,
                    contentLocation = location,
                )
            }

            MmsHeaders.MESSAGE_TYPE_RETRIEVE_CONF -> {
                retrieveConf(reader, headers)
            }

            else -> {
                OtherPdu(messageType, headers.transactionId, headers.version)
            }
        }
    }

    private fun retrieveConf(
        reader: PduReader,
        headers: Headers,
    ): RetrieveConf {
        val type = headers.contentType ?: ContentType("application/octet-stream")
        val parts =
            when {
                !reader.hasMore() -> emptyList()
                type.isMultipart -> MultipartParser.parse(reader)
                else -> listOf(PduPart(type, null, null, null, reader.remaining()))
            }
        return RetrieveConf(
            transactionId = headers.transactionId,
            version = headers.version,
            messageId = headers.messageId,
            dateSeconds = headers.date,
            from = headers.from.orEmpty(),
            to = headers.to,
            cc = headers.cc,
            subject = headers.subject,
            messageClass = headers.messageClass,
            priority = headers.priority,
            contentType = type,
            parts = parts,
        )
    }

    private fun readHeader(
        reader: PduReader,
        headers: Headers,
    ) {
        val field = reader.peek()
        if (field < PduReader.HIGH_BIT) {
            // Application header: Token-text name, then a value Sable does not use.
            reader.readTextString()
            reader.skipValue()
            return
        }
        reader.readByte()
        when (field) {
            MmsHeaders.MESSAGE_TYPE -> headers.messageType = reader.readByte()
            MmsHeaders.TRANSACTION_ID -> headers.transactionId = reader.readTextString()
            MmsHeaders.MMS_VERSION -> headers.version = reader.readIntegerValue().toInt()
            MmsHeaders.MESSAGE_ID -> headers.messageId = reader.readTextString()
            MmsHeaders.CONTENT_LOCATION -> headers.contentLocation = reader.readTextString()
            MmsHeaders.DATE -> headers.date = reader.readLongInteger()
            MmsHeaders.MESSAGE_SIZE -> headers.messageSize = reader.readLongInteger()
            MmsHeaders.PRIORITY -> headers.priority = reader.readByte()
            else -> readAddressingHeader(field, reader, headers)
        }
    }

    private fun readAddressingHeader(
        field: Int,
        reader: PduReader,
        headers: Headers,
    ) {
        when (field) {
            MmsHeaders.FROM -> headers.from = readFrom(reader)
            MmsHeaders.TO -> headers.to += cleanAddress(reader.readEncodedString())
            MmsHeaders.CC -> headers.cc += cleanAddress(reader.readEncodedString())
            MmsHeaders.SUBJECT -> headers.subject = reader.readEncodedString()
            MmsHeaders.MESSAGE_CLASS -> headers.messageClass = readMessageClass(reader)
            MmsHeaders.EXPIRY -> readExpiry(reader, headers)
            MmsHeaders.CONTENT_TYPE -> headers.contentType = WspContentTypes.read(reader)
            else -> reader.skipValue()
        }
    }

    private fun readFrom(reader: PduReader): String {
        val length = reader.readValueLength()
        val spanEnd = reader.pos + length
        val token = reader.readByte()
        val address = if (token == MmsHeaders.ADDRESS_PRESENT) cleanAddress(reader.readEncodedString()) else ""
        reader.pos = spanEnd
        return address
    }

    private fun readExpiry(
        reader: PduReader,
        headers: Headers,
    ) {
        val length = reader.readValueLength()
        val spanEnd = reader.pos + length
        headers.expiryAbsolute = reader.readByte() == MmsHeaders.ABSOLUTE_TOKEN
        headers.expiry = reader.readLongInteger()
        reader.pos = spanEnd
    }

    private fun readMessageClass(reader: PduReader): String {
        if (reader.peek() < PduReader.HIGH_BIT) return reader.readTextString()
        return when (reader.readByte()) {
            CLASS_PERSONAL -> "personal"
            CLASS_ADVERTISEMENT -> "advertisement"
            CLASS_INFORMATIONAL -> "informational"
            else -> "auto"
        }
    }

    /** "+15551234567/TYPE=PLMN" → "+15551234567". */
    fun cleanAddress(raw: String): String = raw.substringBefore("/TYPE=").trim()

    private const val CLASS_PERSONAL = 0x80
    private const val CLASS_ADVERTISEMENT = 0x81
    private const val CLASS_INFORMATIONAL = 0x82

    private class Headers {
        var messageType: Int? = null
        var transactionId: String? = null
        var version: Int = 0
        var messageId: String? = null
        var contentLocation: String? = null
        var date: Long? = null
        var messageSize: Long? = null
        var priority: Int? = null
        var from: String? = null
        val to = mutableListOf<String>()
        val cc = mutableListOf<String>()
        var subject: String? = null
        var messageClass: String? = null
        var expiry: Long? = null
        var expiryAbsolute: Boolean = false
        var contentType: ContentType? = null
    }
}

/** application/vnd.wap.multipart.* body (WAP-230-WSP section 8.5). */
internal object MultipartParser {
    private const val CONTENT_LOCATION = 0x8E
    private const val CONTENT_ID = 0xC0
    private const val CONTENT_DISPOSITION = 0xAE
    private const val CONTENT_DISPOSITION_1_4 = 0xC5
    private const val MAX_PARTS = 256

    fun parse(reader: PduReader): List<PduPart> {
        val count = reader.readUintvar()
        if (count > MAX_PARTS) throw PduFormatException("$count parts")
        return List(count.toInt()) { readPart(reader) }
    }

    private fun readPart(reader: PduReader): PduPart {
        val headersLength = reader.readUintvar().toInt()
        val dataLength = reader.readUintvar().toInt()
        val headersEnd = reader.pos + headersLength
        val type = WspContentTypes.read(reader)
        var contentId: String? = null
        var contentLocation: String? = null
        var fileName: String? = null
        while (reader.pos < headersEnd) {
            when (val field = reader.peek()) {
                CONTENT_LOCATION -> {
                    reader.readByte()
                    contentLocation = reader.readTextString()
                }

                CONTENT_ID -> {
                    reader.readByte()
                    contentId = reader.readTextString().removePrefix("<").removeSuffix(">")
                }

                CONTENT_DISPOSITION, CONTENT_DISPOSITION_1_4 -> {
                    reader.readByte()
                    fileName = readDispositionFileName(reader) ?: fileName
                }

                else -> {
                    if (field < PduReader.HIGH_BIT) reader.readTextString() else reader.readByte()
                    reader.skipValue()
                }
            }
        }
        reader.pos = headersEnd
        return PduPart(type, contentId, contentLocation, fileName, reader.readBytes(dataLength))
    }

    private fun readDispositionFileName(reader: PduReader): String? {
        if (reader.peek() > PduReader.LENGTH_QUOTE) {
            reader.skipValue()
            return null
        }
        val length = reader.readValueLength()
        val spanEnd = reader.pos + length
        reader.skipValue() // form-data / attachment / inline
        val name = WspContentTypes.readParameters(reader, spanEnd).name
        reader.pos = spanEnd
        return name
    }
}
