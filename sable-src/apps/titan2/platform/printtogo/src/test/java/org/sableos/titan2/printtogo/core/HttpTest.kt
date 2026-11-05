package org.sableos.titan2.printtogo.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpTest {
    @Test
    fun contentLengthAndChunkedBodiesOnOneConnection() {
        val wire =
            "POST /ipp/print HTTP/1.1\r\nContent-Type: application/ipp\r\nContent-Length: 5\r\n" +
                "Expect: 100-continue\r\n\r\nhello" +
                "POST /ipp/print HTTP/1.1\r\nTransfer-Encoding: chunked\r\n" +
                "Connection: close\r\n\r\n" +
                "4\r\nabcd\r\n3;x=y\r\nefg\r\n0\r\n\r\n"
        val input = ByteArrayInputStream(wire.toByteArray())
        val first = Http.read(input)!!
        assertEquals("POST", first.method)
        assertEquals("/ipp/print", first.path)
        assertEquals("application/ipp", first.header("Content-Type"))
        assertTrue(first.expectsContinue)
        assertTrue(first.keepAlive)
        assertEquals("hello", String(first.body.readBytes()))
        val second = Http.read(input)!!
        assertEquals("abcdefg", String(second.body.readBytes()))
        assertEquals(false, second.keepAlive)
        assertNull(Http.read(input))
    }

    @Test
    fun responsesCarryLengthAndConnection() {
        val out = ByteArrayOutputStream()
        Http.respond(out, 200, "OK", "application/ipp", byteArrayOf(1, 2, 3), keepAlive = true)
        val text = String(out.toByteArray(), Charsets.ISO_8859_1)
        assertTrue(text.startsWith("HTTP/1.1 200 OK\r\n"))
        assertTrue("Content-Length: 3\r\n" in text)
        assertTrue("Connection: keep-alive\r\n" in text)
    }
}
