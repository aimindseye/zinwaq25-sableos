package org.sableos.reader.core.audio

import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import org.sableos.reader.engine.audio.RandomAccessSource

/** Positional reads over a file channel (a content URI opened as a file descriptor); closes the channel with it. */
class ChannelSource(private val channel: FileChannel, private val onClose: () -> Unit = {}) :
    RandomAccessSource,
    Closeable {
    override val length: Long = channel.size()

    override fun read(offset: Long, length: Int): ByteArray? {
        val valid = offset >= 0 && length >= 0 && offset + length <= this.length
        return if (valid) readFully(offset, length) else null
    }

    private fun readFully(offset: Long, length: Int): ByteArray? {
        val buffer = ByteBuffer.allocate(length)
        return try {
            while (buffer.hasRemaining()) {
                if (channel.read(buffer, offset + buffer.position()) < 0) break
            }
            buffer.array().takeIf { !buffer.hasRemaining() }
        } catch (e: IOException) {
            timber.log.Timber.d(e, "Audio source read failed")
            null
        }
    }

    override fun close() {
        channel.close()
        onClose()
    }
}
