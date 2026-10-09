package org.sableos.reader.core.importing

import java.io.InputStream
import java.security.MessageDigest

/** Stable content identity for library items: lowercase hex MD5, matching the legacy EPUB identity. */
object ContentHash {
    private const val BUFFER_SIZE = 8192
    private const val HEX_BYTE_FORMAT = "%02x"

    fun md5(stream: InputStream): String {
        val buffer = ByteArray(BUFFER_SIZE)
        val digest = MessageDigest.getInstance("MD5")
        while (true) {
            val read = stream.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { HEX_BYTE_FORMAT.format(it) }
    }
}
