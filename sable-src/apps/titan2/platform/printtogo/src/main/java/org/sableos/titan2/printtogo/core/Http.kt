package org.sableos.titan2.printtogo.core

import java.io.EOFException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** One HTTP/1.1 request head; the body is read from [HttpRequest.body]. */
class HttpRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: InputStream
) {
    fun header(name: String): String? = headers[name.lowercase()]

    val keepAlive: Boolean get() = header("connection")?.lowercase() != "close"

    val expectsContinue: Boolean get() = header("expect")?.lowercase() == "100-continue"
}

/** An HTTP status line's code and reason. */
class HttpStatus(val code: Int, val reason: String) {
    companion object {
        val OK = HttpStatus(200, "OK")
        val BAD_REQUEST = HttpStatus(400, "Bad Request")
        val NOT_ALLOWED = HttpStatus(405, "Method Not Allowed")
    }
}

/**
 * The little HTTP/1.1 that IPP rides on: request heads, Content-Length and chunked bodies (CUPS and macOS send
 * chunked jobs), "Expect: 100-continue", and plain responses. Nothing else is served.
 */
object Http {
    private const val MAX_LINE = 8192
    private const val MAX_HEADERS = 100
    private const val CR = '\r'.code
    private const val LF = '\n'.code
    private const val HEX = 16
    private const val REQUEST_LINE_PARTS = 3
    private const val BYTE_MASK = 0xFF

    /** Reads the next request head from [input], or null when the client closed the connection first. */
    fun read(input: InputStream): HttpRequest? {
        val parts = requestLine(input) ?: return null
        val headers = headers(input)
        val chunked = headers["transfer-encoding"]?.lowercase()?.contains("chunked") == true
        val body =
            if (chunked) {
                ChunkedInput(input)
            } else {
                LimitedInput(input, headers["content-length"]?.toLongOrNull() ?: 0L)
            }
        return HttpRequest(parts[0], parts[1], headers, body)
    }

    /** Method, target and version; blank lines before it are skipped. Null when the client closed first. */
    private fun requestLine(input: InputStream): List<String>? {
        var text = line(input)
        while (text != null && text.isEmpty()) text = line(input)
        val parts = text?.split(' ')
        if (parts != null && parts.size < REQUEST_LINE_PARTS) throw IOException("bad request line")
        return parts
    }

    private fun headers(input: InputStream): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        while (true) {
            val header = line(input) ?: throw EOFException()
            if (header.isEmpty()) break
            if (headers.size >= MAX_HEADERS) throw IOException("too many headers")
            val colon = header.indexOf(':')
            if (colon >
                0
            ) {
                headers[header.substring(0, colon).trim().lowercase()] =
                    header.substring(colon + 1).trim()
            }
        }
        return headers
    }

    fun writeContinue(output: OutputStream) {
        output.write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray(Charsets.US_ASCII))
        output.flush()
    }

    fun respond(
        output: OutputStream,
        status: HttpStatus,
        type: String,
        body: ByteArray,
        keepAlive: Boolean
    ) {
        val head =
            "HTTP/1.1 ${status.code} ${status.reason}\r\n" +
                "Content-Type: $type\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: ${if (keepAlive) "keep-alive" else "close"}\r\n" +
                "Server: Sable Print To Go\r\n\r\n"
        output.write(head.toByteArray(Charsets.US_ASCII))
        output.write(body)
        output.flush()
    }

    /** Reads what is left of a request body so the next request on the connection starts in the right place. */
    fun drain(body: InputStream) {
        val buffer = ByteArray(MAX_LINE)
        while (body.read(buffer) >= 0) Unit
    }

    internal fun line(input: InputStream): String? {
        val bytes = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (bytes.isEmpty()) null else throw EOFException()
            if (b == LF) break
            if (b != CR) bytes.append(b.toChar())
            if (bytes.length > MAX_LINE) throw IOException("line too long")
        }
        return bytes.toString()
    }

    private class LimitedInput(input: InputStream, private var left: Long) :
        FilterInputStream(input) {
        override fun read(): Int {
            if (left <= 0) return -1
            val b = super.read()
            if (b < 0) throw EOFException()
            left--
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (left <= 0) return -1
            val n = super.read(b, off, minOf(len.toLong(), left).toInt())
            if (n < 0) throw EOFException()
            left -= n
            return n
        }

        override fun available(): Int = minOf(super.available().toLong(), left).toInt()

        override fun close() = Unit
    }

    private class ChunkedInput(input: InputStream) : FilterInputStream(input) {
        private var left = 0L
        private var done = false

        /** Moves to the next chunk when this one is used up; false once the last (empty) chunk is read. */
        private fun nextChunk(): Boolean {
            if (!done && left == 0L) {
                val size = line(`in`) ?: throw EOFException()
                left =
                    size.substringBefore(';').trim().toLongOrNull(HEX)
                        ?: throw IOException("bad chunk")
                if (left == 0L) {
                    // Trailers, then the blank line that ends the body.
                    while ((line(`in`) ?: "").isNotEmpty()) Unit
                    done = true
                }
            }
            return !done
        }

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and BYTE_MASK
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int = when {
            len == 0 -> 0
            !nextChunk() -> -1
            else -> readChunk(b, off, len)
        }

        private fun readChunk(b: ByteArray, off: Int, len: Int): Int {
            val n = `in`.read(b, off, minOf(len.toLong(), left).toInt())
            if (n < 0) throw EOFException()
            left -= n
            if (left == 0L) line(`in`)
            return n
        }

        override fun available(): Int = 0

        override fun close() = Unit
    }
}
