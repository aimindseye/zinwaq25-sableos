package org.sableos.messages.core.mms

import java.nio.charset.Charset

/** A malformed or truncated WSP/MMS PDU. */
class PduFormatException(
    message: String,
) : IllegalArgumentException(message)

/**
 * WSP primitive decoding (WAP-230-WSP section 8.4) used by MMS PDUs (OMA-TS-MMS_ENC). Every value
 * is self-delimiting, so unknown headers are skipped without knowing their meaning.
 */
internal class PduReader(
    private val data: ByteArray,
    var pos: Int = 0,
    val end: Int = data.size,
) {
    fun hasMore(): Boolean = pos < end

    fun peek(): Int {
        ensure(1)
        return data[pos].toInt() and BYTE_MASK
    }

    fun readByte(): Int {
        ensure(1)
        return data[pos++].toInt() and BYTE_MASK
    }

    fun readBytes(count: Int): ByteArray {
        if (count < 0) throw PduFormatException("negative length $count")
        ensure(count)
        val out = data.copyOfRange(pos, pos + count)
        pos += count
        return out
    }

    /** Unsigned variable-length integer: 7 bits per octet, high bit set on all but the last. */
    fun readUintvar(): Long {
        var value = 0L
        repeat(MAX_UINTVAR_OCTETS) {
            val octet = readByte()
            value = (value shl SEVEN_BITS) or (octet and LOW_SEVEN_BITS).toLong()
            if (octet and HIGH_BIT == 0) return value
        }
        throw PduFormatException("uintvar longer than $MAX_UINTVAR_OCTETS octets")
    }

    /** Value-length: a short length (0-30) or LENGTH_QUOTE followed by a uintvar. */
    fun readValueLength(): Int {
        val first = readByte()
        val length =
            when {
                first <= MAX_SHORT_LENGTH -> first.toLong()
                first == LENGTH_QUOTE -> readUintvar()
                else -> throw PduFormatException("not a value length: $first")
            }
        if (length > end - pos) throw PduFormatException("length $length past the end")
        return length.toInt()
    }

    /** Long-integer: short length then that many big-endian octets. */
    fun readLongInteger(): Long {
        val count = readByte()
        if (count > MAX_SHORT_LENGTH ||
            count > Long.SIZE_BYTES
        ) {
            throw PduFormatException("long integer of $count octets")
        }
        var value = 0L
        repeat(count) { value = (value shl Byte.SIZE_BITS) or readByte().toLong() }
        return value
    }

    /** Integer-value: a short integer (one octet with the high bit set) or a long integer. */
    fun readIntegerValue(): Long =
        if (peek() and HIGH_BIT !=
            0
        ) {
            (readByte() and LOW_SEVEN_BITS).toLong()
        } else {
            readLongInteger()
        }

    /** Text-string, Token-text or Quoted-string: optional quote octet, then octets up to NUL. */
    fun readTextString(): String {
        if (peek() == TEXT_QUOTE || peek() == QUOTED_STRING) pos++
        val start = pos
        while (pos < end && data[pos].toInt() != 0) pos++
        if (pos >= end) throw PduFormatException("unterminated text string")
        val text = String(data, start, pos - start, Charsets.UTF_8)
        pos++
        return text
    }

    /** Skips any WSP value: short-integer, value-length-prefixed data or text. */
    fun skipValue() {
        val first = peek()
        when {
            first <= MAX_SHORT_LENGTH || first == LENGTH_QUOTE -> pos += readValueLength()
            first < HIGH_BIT -> readTextString()
            else -> pos++
        }
    }

    private fun ensure(count: Int) {
        if (count > end - pos) throw PduFormatException("truncated PDU at $pos")
    }

    companion object {
        const val BYTE_MASK = 0xFF
        const val HIGH_BIT = 0x80
        const val LOW_SEVEN_BITS = 0x7F
        const val SEVEN_BITS = 7
        const val MAX_UINTVAR_OCTETS = 5
        const val MAX_SHORT_LENGTH = 30
        const val LENGTH_QUOTE = 31
        const val TEXT_QUOTE = 0x7F
        const val QUOTED_STRING = 0x22
    }
}

/** The rest of the PDU (a single-part body). */
internal fun PduReader.remaining(): ByteArray = readBytes(end - pos)

/**
 * Encoded-string-value: a Text-string, or a value length, a charset (MIBenum) and the text. The
 * length bounds the text, so UTF-16 text with NUL octets decodes too.
 */
internal fun PduReader.readEncodedString(): String {
    if (peek() > PduReader.LENGTH_QUOTE) return readTextString()
    val length = readValueLength()
    val spanEnd = pos + length
    val charset = readIntegerValue().toInt()
    val raw = readBytes(spanEnd - pos)
    val quoted = raw.isNotEmpty() && (raw[0].toInt() and PduReader.BYTE_MASK) == PduReader.TEXT_QUOTE
    val text = if (quoted) raw.copyOfRange(1, raw.size) else raw
    return String(text.dropLastWhile { it.toInt() == 0 }.toByteArray(), MmsCharsets.forMib(charset))
}

/** IANA MIBenum charsets that MMS uses (WAP-230-WSP Table 42 and OMA MMS conformance). */
object MmsCharsets {
    const val UTF_8 = 106
    private val names =
        mapOf(
            3 to "US-ASCII",
            4 to "ISO-8859-1",
            5 to "ISO-8859-2",
            6 to "ISO-8859-3",
            7 to "ISO-8859-4",
            8 to "ISO-8859-5",
            9 to "ISO-8859-6",
            10 to "ISO-8859-7",
            11 to "ISO-8859-8",
            12 to "ISO-8859-9",
            17 to "Shift_JIS",
            18 to "EUC-JP",
            38 to "EUC-KR",
            106 to "UTF-8",
            113 to "GBK",
            1000 to "UTF-16BE",
            1013 to "UTF-16BE",
            1014 to "UTF-16LE",
            1015 to "UTF-16",
            2025 to "GB2312",
            2026 to "Big5",
        )

    /** The charset for an MIBenum, UTF-8 when unknown or unsupported (0 = "any charset"). */
    fun forMib(mib: Int): Charset =
        names[mib]
            ?.takeIf { Charset.isSupported(it) }
            ?.let { Charset.forName(it) }
            ?: Charsets.UTF_8
}
