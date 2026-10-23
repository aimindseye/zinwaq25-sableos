package org.sableos.messages.core.mms

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class MmsPduParserTest {
    private class Pdu {
        val out = ByteArrayOutputStream()

        fun octets(vararg values: Int) = apply { values.forEach { out.write(it) } }

        fun text(value: String) =
            apply {
                out.write(value.toByteArray(Charsets.UTF_8))
                out.write(0)
            }

        fun bytes(value: ByteArray) = apply { out.write(value) }

        fun uintvar(value: Int) =
            apply {
                val groups = mutableListOf<Int>()
                var rest = value
                do {
                    groups.add(0, rest and 0x7F)
                    rest = rest shr 7
                } while (rest > 0)
                groups.forEachIndexed { i, g -> out.write(if (i < groups.lastIndex) g or 0x80 else g) }
            }

        fun build(): ByteArray = out.toByteArray()
    }

    private fun encodedFrom(address: String): ByteArray {
        val value = address.toByteArray() + 0
        return byteArrayOf((value.size + 1).toByte(), 0x80.toByte()) + value
    }

    private val notification: ByteArray =
        Pdu()
            .octets(0x8C, 0x82)
            .octets(0x98)
            .text("T123")
            .octets(0x8D, 0x92)
            .octets(0x89)
            .bytes(encodedFrom("+15550101010/TYPE=PLMN"))
            // Application header the parser must skip.
            .text("X-Carrier")
            .text("value")
            // X-Mms-Delivery-Report (unknown to Sable): skipped as a short integer.
            .octets(0x86, 0x81)
            .octets(0x8A, 0x80)
            .octets(0x8E, 0x02, 0x30, 0x39)
            .octets(0x88, 0x05, 0x81, 0x03, 0x09, 0x3A, 0x80)
            .octets(0x83)
            .text("http://mmsc.example/abc")
            .build()

    @Test
    fun parsesNotificationInd() {
        val pdu = MmsPduParser.parse(notification) as NotificationInd
        assertEquals("T123", pdu.transactionId)
        assertEquals(0x12, pdu.version)
        assertEquals("+15550101010", pdu.from)
        assertEquals("personal", pdu.messageClass)
        assertEquals(12345L, pdu.messageSize)
        assertEquals(604800L, pdu.expirySeconds)
        assertEquals(false, pdu.expiryAbsolute)
        assertEquals("http://mmsc.example/abc", pdu.contentLocation)
    }

    private fun retrieveConf(): ByteArray {
        val textPart = "Train arrives 7:18".toByteArray()
        val image = byteArrayOf(1, 2, 3, 4, 5)
        // Text part: general-form content type text/plain; charset=utf-8, Content-ID <t1>.
        val textHeaders =
            Pdu()
                .octets(0x03, 0x83, 0x81, 0xEA)
                .octets(0xC0, 0x22)
                .text("<t1>")
                .build()
        // Image part: image/jpeg; name="station.jpg", Content-Location.
        val imageType = Pdu().octets(0x9E, 0x85).text("station.jpg").build()
        val imageHeaders =
            Pdu()
                .octets(imageType.size)
                .bytes(imageType)
                .octets(0x8E)
                .text("station.jpg")
                .build()
        val smilHeaders = Pdu().text("application/smil").build()
        val smil = "<smil/>".toByteArray()
        val subject = Pdu().octets(0x0B, 0xEA).text("Hello é!").build()
        return Pdu()
            .octets(0x8C, 0x84)
            .octets(0x8B)
            .text("MID-1")
            .octets(0x8D, 0x92)
            .octets(0x85, 0x04, 0x65, 0x00, 0x00, 0x00)
            .octets(0x89)
            .bytes(encodedFrom("+15550101010/TYPE=PLMN"))
            .octets(0x97)
            .text("+15559999999/TYPE=PLMN")
            .octets(0x97)
            .text("+15550202020/TYPE=PLMN")
            .octets(0x96)
            .bytes(subject)
            .octets(0x84, 0xB3)
            .uintvar(3)
            .uintvar(smilHeaders.size)
            .uintvar(smil.size)
            .bytes(smilHeaders)
            .bytes(smil)
            .uintvar(textHeaders.size)
            .uintvar(textPart.size)
            .bytes(textHeaders)
            .bytes(textPart)
            .uintvar(imageHeaders.size)
            .uintvar(image.size)
            .bytes(imageHeaders)
            .bytes(image)
            .build()
    }

    @Test
    fun parsesRetrieveConfWithParts() {
        val pdu = MmsPduParser.parse(retrieveConf()) as RetrieveConf
        assertEquals("MID-1", pdu.messageId)
        assertEquals(0x65000000L, pdu.dateSeconds)
        assertEquals("+15550101010", pdu.from)
        assertEquals(listOf("+15559999999", "+15550202020"), pdu.to)
        assertEquals("Hello é!", pdu.subject)
        assertEquals("application/vnd.wap.multipart.related", pdu.contentType.mimeType)
        assertEquals(3, pdu.parts.size)
        assertTrue(pdu.parts[0].isSmil)
        assertEquals("t1", pdu.parts[1].contentId)
        assertEquals(106, pdu.parts[1].contentType.charset)
        assertEquals("Train arrives 7:18", pdu.text)
        val image = pdu.attachments.single()
        assertEquals("image/jpeg", image.mimeType)
        assertEquals("station.jpg", image.displayName)
        assertEquals("station.jpg", image.contentLocation)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), image.data)
    }

    @Test
    fun singlePartBody() {
        val pdu =
            Pdu()
                .octets(0x8C, 0x84, 0x8D, 0x90)
                .octets(0x89, 0x01, 0x81)
                .octets(0x84, 0x83)
                .bytes("plain".toByteArray())
                .build()
        val parsed = MmsPduParser.parse(pdu) as RetrieveConf
        assertEquals("", parsed.from)
        assertEquals("plain", parsed.text)
    }

    @Test
    fun utf16EncodedStringWithNulOctets() {
        val text = "Hé".toByteArray(Charsets.UTF_16BE)
        val subject =
            Pdu()
                .octets(1 + 2 + text.size + 2, 0x02, 0x03, 0xF7)
                .bytes(text)
                .octets(0, 0)
                .build()
        val pdu =
            Pdu()
                .octets(0x8C, 0x84, 0x96)
                .bytes(subject)
                .octets(0x84, 0x83)
                .build()
        assertEquals("Hé", (MmsPduParser.parse(pdu) as RetrieveConf).subject)
    }

    @Test
    fun otherTypesAreRecognised() {
        val pdu =
            Pdu()
                .octets(0x8C, 0x86, 0x98)
                .text("T9")
                .octets(0x8D, 0x92)
                .build()
        val parsed = MmsPduParser.parse(pdu) as OtherPdu
        assertEquals(0x86, parsed.messageType)
        assertEquals("T9", parsed.transactionId)
    }

    @Test
    fun malformedPdusGiveNull() {
        assertNull(MmsPduParser.parse(ByteArray(0)))
        assertNull(MmsPduParser.parse(notification.copyOf(notification.size - 4)))
        // Notification without a content location.
        assertNull(MmsPduParser.parse(Pdu().octets(0x8C, 0x82, 0x98).text("T").build()))
        // A part longer than the PDU.
        val truncated =
            Pdu()
                .octets(0x8C, 0x84, 0x84, 0xB3)
                .uintvar(1)
                .uintvar(1)
                .uintvar(500)
                .octets(0x83)
                .build()
        assertNull(MmsPduParser.parse(truncated))
        assertNull(MmsPduParser.parse(Pdu().octets(0x8C, 0x84, 0x84, 0xB3).uintvar(5000).build()))
    }

    @Test
    fun notifyRespIndRoundTrips() {
        val bytes = MmsPduEncoder.notifyRespInd("T123", 0x12)
        assertArrayEquals(
            Pdu()
                .octets(0x8C, 0x83, 0x98)
                .text("T123")
                .octets(0x8D, 0x92, 0x95, 0x81, 0x91, 0x81)
                .build(),
            bytes,
        )
        val parsed = MmsPduParser.parse(bytes) as OtherPdu
        assertEquals(MmsHeaders.MESSAGE_TYPE_NOTIFYRESP_IND, parsed.messageType)
        assertEquals("T123", parsed.transactionId)
        assertEquals(MmsHeaders.VERSION_1_2, MmsPduEncoder.notifyRespInd("T", 0)[6].toInt() and 0xFF)
    }

    @Test
    fun charsetsFallBackToUtf8() {
        assertEquals(Charsets.UTF_8, MmsCharsets.forMib(0))
        assertEquals(Charsets.ISO_8859_1, MmsCharsets.forMib(4))
    }
}
