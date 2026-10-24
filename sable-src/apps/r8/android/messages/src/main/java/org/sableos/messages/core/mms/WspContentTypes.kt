package org.sableos.messages.core.mms

/** WSP Content-type-value decoding and the well-known media table (WAP-230-WSP Table 40). */
internal object WspContentTypes {
    private val wellKnown: Map<Int, String> =
        mapOf(
            0x00 to "*/*",
            0x01 to "text/*",
            0x02 to "text/html",
            0x03 to "text/plain",
            0x06 to "text/x-vcalendar",
            0x07 to "text/x-vcard",
            0x0C to "multipart/mixed",
            0x0F to "multipart/alternative",
            0x10 to "application/*",
            0x1C to "image/*",
            0x1D to "image/gif",
            0x1E to "image/jpeg",
            0x1F to "image/tiff",
            0x20 to "image/png",
            0x21 to "image/vnd.wap.wbmp",
            0x22 to "application/vnd.wap.multipart.*",
            0x23 to "application/vnd.wap.multipart.mixed",
            0x26 to "application/vnd.wap.multipart.alternative",
            0x27 to "application/xml",
            0x28 to "text/xml",
            0x33 to "application/vnd.wap.multipart.related",
            0x3B to "application/xhtml+xml",
            0x3D to "text/css",
            0x3E to "application/vnd.wap.mms-message",
        )

    private const val PARAM_CHARSET = 0x01
    private const val PARAM_NAME = 0x05
    private const val PARAM_FILENAME = 0x06
    private const val PARAM_TYPE = 0x09
    private const val PARAM_START = 0x0A
    private const val PARAM_NAME_1_4 = 0x17
    private const val PARAM_FILENAME_1_4 = 0x18
    private const val PARAM_START_1_4 = 0x19

    fun mediaName(code: Int): String = wellKnown[code] ?: "application/octet-stream"

    fun read(reader: PduReader): ContentType =
        if (reader.peek() > PduReader.LENGTH_QUOTE) ContentType(readMedia(reader)) else readGeneralForm(reader)

    /** Well-known media (short or long integer) or an extension media type (text). */
    private fun readMedia(reader: PduReader): String =
        when {
            reader.peek() >= PduReader.HIGH_BIT -> mediaName(reader.readByte() and PduReader.LOW_SEVEN_BITS)
            reader.peek() <= PduReader.MAX_SHORT_LENGTH -> mediaName(reader.readLongInteger().toInt())
            else -> reader.readTextString().lowercase()
        }

    private fun readGeneralForm(reader: PduReader): ContentType {
        val length = reader.readValueLength()
        val spanEnd = reader.pos + length
        val media = readMedia(reader)
        val params = readParameters(reader, spanEnd)
        reader.pos = spanEnd
        return ContentType(media, params.charset, params.name, params.start)
    }

    class Parameters {
        var charset: Int? = null
        var name: String? = null
        var start: String? = null
    }

    /** Typed and untyped parameters up to [spanEnd]; keeps charset, name/filename and start. */
    fun readParameters(
        reader: PduReader,
        spanEnd: Int,
    ): Parameters {
        val out = Parameters()
        while (reader.pos < spanEnd) {
            if (reader.peek() >= PduReader.HIGH_BIT) {
                readTypedParameter(reader, reader.readByte() and PduReader.LOW_SEVEN_BITS, out)
            } else {
                readUntypedParameter(reader, out)
            }
        }
        return out
    }

    private fun readUntypedParameter(
        reader: PduReader,
        out: Parameters,
    ) {
        val name = reader.readTextString().lowercase()
        val value = readTextOrSkip(reader) ?: return
        when (name) {
            "charset" -> out.charset = MmsCharsetNames.mibFor(value) ?: out.charset
            "name", "filename" -> out.name = value
            "start" -> out.start = value
            else -> Unit
        }
    }

    private fun readTypedParameter(
        reader: PduReader,
        token: Int,
        out: Parameters,
    ) {
        when (token) {
            PARAM_CHARSET -> out.charset = reader.readIntegerValue().toInt()
            PARAM_NAME, PARAM_FILENAME, PARAM_NAME_1_4, PARAM_FILENAME_1_4 -> out.name = readTextOrSkip(reader)
            PARAM_START, PARAM_START_1_4 -> out.start = readTextOrSkip(reader)?.removePrefix("<")?.removeSuffix(">")
            PARAM_TYPE -> readTextOrSkip(reader)
            else -> reader.skipValue()
        }
    }

    private fun readTextOrSkip(reader: PduReader): String? {
        val first = reader.peek()
        return if (first in (PduReader.LENGTH_QUOTE + 1) until PduReader.HIGH_BIT) {
            reader.readTextString()
        } else {
            reader.skipValue()
            null
        }
    }
}

/** Charset names in untyped parameters ("charset=utf-8") to MIBenum. */
internal object MmsCharsetNames {
    private val mibs =
        mapOf(
            "utf-8" to 106,
            "us-ascii" to 3,
            "iso-8859-1" to 4,
            "utf-16" to 1015,
            "shift_jis" to 17,
            "big5" to 2026,
            "gb2312" to 2025,
            "gbk" to 113,
            "euc-kr" to 38,
        )

    fun mibFor(name: String): Int? = mibs[name.lowercase()]
}
