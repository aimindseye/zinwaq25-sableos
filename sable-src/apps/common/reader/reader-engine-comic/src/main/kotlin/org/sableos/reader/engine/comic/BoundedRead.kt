package org.sableos.reader.engine.comic

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Reads at most [limit] bytes from [source]. A stream that produces more is hostile (a header that lied about its size,
 * or a decompression bomb) and fails with [PageFailure.TOO_LARGE] after at most `limit + 1` bytes were buffered.
 */
object BoundedRead {
    private const val CHUNK = 16 * 1024

    fun readAll(source: InputStream, limit: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(CHUNK)
        var total = 0L
        try {
            while (true) {
                val n = source.read(buffer)
                if (n < 0) break
                total += n
                if (total > limit) throw ComicPageException(PageFailure.TOO_LARGE, "page exceeds $limit bytes")
                out.write(buffer, 0, n)
            }
        } catch (e: IOException) {
            throw ComicPageException(PageFailure.UNREADABLE, e.message, e)
        }
        return out.toByteArray()
    }
}
