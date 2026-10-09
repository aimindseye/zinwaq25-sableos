package org.sableos.reader.engine.comic

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/**
 * Generates comic containers, well-formed and hostile, entirely in code so no binary fixture is ever committed.
 * Every hostile shape named in the P5C archive-security contract has a builder here.
 */
object ComicFixtures {
    /** A real, decodable PNG. */
    fun png(width: Int = 4, height: Int = 6, rgb: Int = DEFAULT_RGB): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until width) for (y in 0 until height) image.setRGB(x, y, rgb)
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    private const val DEFAULT_RGB = 0x336699

    class Entry(val name: String, val data: ByteArray, val stored: Boolean = false)

    fun zipBytes(entries: List<Entry>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { e ->
                val entry = ZipEntry(e.name)
                if (e.stored) {
                    entry.method = ZipEntry.STORED
                    entry.size = e.data.size.toLong()
                    entry.compressedSize = e.data.size.toLong()
                    entry.crc = java.util.zip.CRC32().also { it.update(e.data) }.value
                }
                zip.putNextEntry(entry)
                zip.write(e.data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    fun write(dir: File, name: String, bytes: ByteArray): File = File(dir, name).also { it.writeBytes(bytes) }

    fun cbz(dir: File, name: String, vararg pages: String): File =
        write(dir, name, zipBytes(pages.map { Entry(it, png()) }))

    fun comicInfoXml(body: String): ByteArray =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?><ComicInfo>$body</ComicInfo>".toByteArray()

    /** One highly compressible entry: [size] zero bytes inflate from a few kilobytes (ratio ~1000:1). */
    fun compressionBomb(size: Int = BOMB_SIZE): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.setLevel(Deflater.BEST_COMPRESSION)
            zip.putNextEntry(ZipEntry("001.png"))
            val chunk = ByteArray(MEGABYTE)
            repeat(size / chunk.size) { zip.write(chunk) }
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    /** Overwrites the uncompressed size recorded in the central directory entry called [name]. */
    fun lieAboutSize(zip: ByteArray, name: String, declared: Int): ByteArray {
        val copy = zip.copyOf()
        val buffer = ByteBuffer.wrap(copy).order(ByteOrder.LITTLE_ENDIAN)
        val target = name.toByteArray()
        for (at in centralEntries(buffer)) {
            if (nameAt(copy, buffer, at).contentEquals(target)) {
                buffer.putInt(at + CEN_SIZE_OFFSET, declared)
                return copy
            }
        }
        error("central directory entry $name not found")
    }

    /** Sets the "encrypted" general-purpose flag on every entry, as a password-protected archive would. */
    fun markEncrypted(zip: ByteArray): ByteArray {
        val copy = zip.copyOf()
        val buffer = ByteBuffer.wrap(copy).order(ByteOrder.LITTLE_ENDIAN)
        for (at in 0..copy.size - SIGNATURE_BYTES) {
            val flagAt = when (buffer.getInt(at)) {
                CEN_SIGNATURE -> at + CEN_FLAG_OFFSET
                LOC_SIGNATURE -> at + LOC_FLAG_OFFSET
                else -> continue
            }
            buffer.putShort(flagAt, (buffer.getShort(flagAt).toInt() or ENCRYPTED_FLAG).toShort())
        }
        return copy
    }

    private fun centralEntries(buffer: ByteBuffer): List<Int> =
        (0..buffer.capacity() - CEN_FIXED).filter { buffer.getInt(it) == CEN_SIGNATURE }

    private fun nameAt(bytes: ByteArray, buffer: ByteBuffer, cen: Int): ByteArray {
        val length = buffer.getShort(cen + CEN_NAME_LEN_OFFSET).toInt() and SHORT_MASK
        return bytes.copyOfRange(cen + CEN_FIXED, cen + CEN_FIXED + length)
    }

    private const val BOMB_SIZE = 100 * 1024 * 1024
    private const val MEGABYTE = 1024 * 1024
    private const val SIGNATURE_BYTES = 4
    private const val SHORT_MASK = 0xffff
    private const val ENCRYPTED_FLAG = 1
    private const val CEN_SIGNATURE = 0x02014b50
    private const val LOC_SIGNATURE = 0x04034b50
    private const val CEN_FIXED = 46
    private const val CEN_FLAG_OFFSET = 8
    private const val CEN_SIZE_OFFSET = 24
    private const val CEN_NAME_LEN_OFFSET = 28
    private const val LOC_FLAG_OFFSET = 6
}
