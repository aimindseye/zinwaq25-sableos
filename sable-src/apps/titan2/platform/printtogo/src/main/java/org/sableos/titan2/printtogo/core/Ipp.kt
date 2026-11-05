package org.sableos.titan2.printtogo.core

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.InputStream

/** One IPP attribute value (RFC 8010 section 3.5). */
sealed interface IppValue {
    data class Integer(val value: Int, val tag: Int = Ipp.TAG_INTEGER) : IppValue

    data class Bool(val value: Boolean) : IppValue

    /** Text-like values: keyword, uri, name, text, charset, language, MIME type. */
    data class Text(val value: String, val tag: Int) : IppValue

    data class Resolution(val x: Int, val y: Int, val units: Int = Ipp.DPI) : IppValue

    data class Range(val low: Int, val high: Int) : IppValue

    /** A collection: its members in order (a member may hold several values). */
    data class Collection(val members: List<Pair<String, List<IppValue>>>) : IppValue

    /** Anything else, kept as received. */
    data class Raw(val tag: Int, val bytes: ByteArray) : IppValue {
        override fun equals(other: Any?): Boolean =
            other is Raw && other.tag == tag && other.bytes.contentEquals(bytes)

        override fun hashCode(): Int = 31 * tag + bytes.contentHashCode()
    }
}

data class IppAttribute(val name: String, val values: List<IppValue>)

data class IppGroup(val tag: Int, val attributes: List<IppAttribute>) {
    fun find(name: String): IppAttribute? = attributes.firstOrNull { it.name == name }
}

/** A request (code = operation) or response (code = status). */
data class IppMessage(
    val code: Int,
    val requestId: Int,
    val groups: List<IppGroup>,
    val version: Int = Ipp.VERSION_2_0
) {
    fun group(tag: Int): IppGroup? = groups.firstOrNull { it.tag == tag }

    fun text(groupTag: Int, name: String): String? =
        (group(groupTag)?.find(name)?.values?.firstOrNull() as? IppValue.Text)?.value

    fun int(groupTag: Int, name: String): Int? =
        (group(groupTag)?.find(name)?.values?.firstOrNull() as? IppValue.Integer)?.value
}

/** IPP/1.1 and 2.0 encoding and decoding, the subset a driverless printer needs. */
object Ipp {
    const val VERSION_1_1 = 0x0101
    const val VERSION_2_0 = 0x0200

    const val GROUP_OPERATION = 0x01
    const val GROUP_JOB = 0x02
    const val GROUP_END = 0x03
    const val GROUP_PRINTER = 0x04
    const val GROUP_UNSUPPORTED = 0x05

    // RFC 8010 3.5.2 out-of-band: the attribute has no value yet.
    const val TAG_NO_VALUE = 0x13
    const val TAG_INTEGER = 0x21
    const val TAG_BOOLEAN = 0x22
    const val TAG_ENUM = 0x23
    const val TAG_OCTET_STRING = 0x30
    const val TAG_RESOLUTION = 0x32
    const val TAG_RANGE = 0x33
    const val TAG_BEGIN_COLLECTION = 0x34
    const val TAG_END_COLLECTION = 0x37
    const val TAG_TEXT = 0x41
    const val TAG_NAME = 0x42
    const val TAG_KEYWORD = 0x44
    const val TAG_URI = 0x45
    const val TAG_CHARSET = 0x47
    const val TAG_LANGUAGE = 0x48
    const val TAG_MIME = 0x49
    const val TAG_MEMBER_NAME = 0x4A

    const val DPI = 3

    private const val TEXT_TAGS_FIRST = 0x40
    private const val TEXT_TAGS_LAST = 0x5F
    private const val RESOLUTION_BYTES = 9
    private const val RANGE_BYTES = 8
    private const val INT_BYTES = 4

    /**
     * Reads one IPP message from [input] and stops at the end-of-attributes tag, so the document data that
     * follows a Print-Job or Send-Document is left unread in [input].
     */
    fun decode(input: InputStream): IppMessage {
        val data = DataInputStream(input)
        val version = data.readUnsignedShort()
        val code = data.readUnsignedShort()
        val requestId = data.readInt()
        val groups = mutableListOf<IppGroup>()
        var tag = data.readUnsignedByte()
        while (tag != GROUP_END) {
            require(tag in GROUP_OPERATION..0x0F) { "bad group tag $tag" }
            val group = tag
            val entries = mutableListOf<Entry>()
            tag = data.readUnsignedByte()
            while (tag > 0x0F) {
                entries += readEntry(data, tag)
                tag = data.readUnsignedByte()
            }
            groups += IppGroup(group, fold(entries))
        }
        return IppMessage(code, requestId, groups, version)
    }

    fun encode(message: IppMessage): ByteArray {
        val out = ByteArrayOutputStream()
        out.short(message.version)
        out.short(message.code)
        out.int(message.requestId)
        message.groups.forEach { group ->
            out.write(group.tag)
            group.attributes.forEach { attribute ->
                attribute.values.forEachIndexed { i, value ->
                    writeValue(out, if (i == 0) attribute.name else "", value)
                }
            }
        }
        out.write(GROUP_END)
        return out.toByteArray()
    }

    private class Entry(val tag: Int, val name: String, val bytes: ByteArray)

    private fun readEntry(data: DataInputStream, tag: Int): Entry {
        val name = ByteArray(data.readUnsignedShort()).also(data::readFully)
        val value = ByteArray(data.readUnsignedShort()).also(data::readFully)
        return Entry(tag, String(name, Charsets.UTF_8), value)
    }

    /** Turns the flat entries of one group into attributes, rebuilding collections. */
    private fun fold(entries: List<Entry>): List<IppAttribute> {
        val result = mutableListOf<IppAttribute>()
        var i = 0
        while (i < entries.size) {
            val entry = entries[i]
            val (value, next) = valueAt(entries, i)
            i = next
            if (entry.name.isEmpty() && result.isNotEmpty()) {
                val last = result.removeAt(result.lastIndex)
                result += last.copy(values = last.values + value)
            } else {
                result += IppAttribute(entry.name, listOf(value))
            }
        }
        return result
    }

    /** The value starting at [index] and the index after it (a collection spans many entries). */
    private fun valueAt(entries: List<Entry>, index: Int): Pair<IppValue, Int> {
        val entry = entries[index]
        if (entry.tag != TAG_BEGIN_COLLECTION) return parse(entry.tag, entry.bytes) to index + 1
        val members = mutableListOf<Pair<String, List<IppValue>>>()
        var i = index + 1
        while (i < entries.size && entries[i].tag != TAG_END_COLLECTION) {
            val member = entries[i]
            if (member.tag == TAG_MEMBER_NAME) {
                members += String(member.bytes, Charsets.UTF_8) to emptyList()
                i++
            } else {
                val (value, next) = valueAt(entries, i)
                if (members.isNotEmpty()) {
                    val (name, values) = members.removeAt(members.lastIndex)
                    members += name to values + value
                }
                i = next
            }
        }
        return IppValue.Collection(members) to minOf(i + 1, entries.size)
    }

    private fun parse(tag: Int, bytes: ByteArray): IppValue = when {
        (tag == TAG_INTEGER || tag == TAG_ENUM) && bytes.size == INT_BYTES ->
            IppValue.Integer(bytes.int(0), tag)

        tag == TAG_BOOLEAN && bytes.size == 1 -> IppValue.Bool(bytes[0].toInt() != 0)

        tag == TAG_RESOLUTION && bytes.size == RESOLUTION_BYTES ->
            IppValue.Resolution(bytes.int(0), bytes.int(INT_BYTES), bytes[RANGE_BYTES].toInt())

        tag == TAG_RANGE && bytes.size == RANGE_BYTES ->
            IppValue.Range(bytes.int(0), bytes.int(INT_BYTES))

        tag in TEXT_TAGS_FIRST..TEXT_TAGS_LAST -> IppValue.Text(
            String(bytes, Charsets.UTF_8),
            tag
        )

        else -> IppValue.Raw(tag, bytes)
    }

    private fun writeValue(out: ByteArrayOutputStream, name: String, value: IppValue) {
        when (value) {
            is IppValue.Integer -> out.entry(value.tag, name, intBytes(value.value))

            is IppValue.Bool -> out.entry(TAG_BOOLEAN, name, byteArrayOf(if (value.value) 1 else 0))

            is IppValue.Text -> out.entry(value.tag, name, value.value.toByteArray(Charsets.UTF_8))

            is IppValue.Resolution ->
                out.entry(
                    TAG_RESOLUTION,
                    name,
                    intBytes(value.x) + intBytes(value.y) + value.units.toByte()
                )

            is IppValue.Range -> out.entry(
                TAG_RANGE,
                name,
                intBytes(value.low) + intBytes(value.high)
            )

            is IppValue.Raw -> out.entry(value.tag, name, value.bytes)

            is IppValue.Collection -> {
                out.entry(TAG_BEGIN_COLLECTION, name, ByteArray(0))
                value.members.forEach { (member, values) ->
                    out.entry(TAG_MEMBER_NAME, "", member.toByteArray(Charsets.UTF_8))
                    values.forEach { writeValue(out, "", it) }
                }
                out.entry(TAG_END_COLLECTION, "", ByteArray(0))
            }
        }
    }

    private fun ByteArrayOutputStream.entry(tag: Int, name: String, value: ByteArray) {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        write(tag)
        short(nameBytes.size)
        write(nameBytes)
        short(value.size)
        write(value)
    }

    private fun ByteArrayOutputStream.short(v: Int) {
        write(v shr 8 and 0xFF)
        write(v and 0xFF)
    }

    private fun ByteArrayOutputStream.int(v: Int) = write(intBytes(v))

    private fun intBytes(v: Int) =
        byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())

    private fun ByteArray.int(at: Int): Int =
        (this[at].toInt() and 0xFF shl 24) or (this[at + 1].toInt() and 0xFF shl 16) or
            (this[at + 2].toInt() and 0xFF shl 8) or (this[at + 3].toInt() and 0xFF)
}
