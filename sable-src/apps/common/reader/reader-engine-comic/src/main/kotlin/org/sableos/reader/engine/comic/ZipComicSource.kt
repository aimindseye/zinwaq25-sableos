package org.sableos.reader.engine.comic

import java.io.File
import java.io.IOException
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * A CBZ read with `java.util.zip.ZipFile`. Pages are inflated on demand into memory, never extracted to disk, and
 * every read is capped on the bytes actually produced. Structural limits fail the open; per-page limits fail only
 * that page.
 */
class ZipComicSource private constructor(
    private val zip: ZipFile,
    override val limits: ComicLimits,
    override val catalog: ComicCatalog,
    override val info: ComicInfo?,
) : ComicSource {
    override fun readPage(index: Int): ByteArray {
        val page = catalog.pages.getOrNull(index) ?: throw ComicPageException(PageFailure.UNREADABLE, "no page $index")
        ComicCatalogBuilder.checkReadable(page, limits)
        val entry = zip.getEntry(page.handle) ?: throw ComicPageException(PageFailure.UNREADABLE, "entry vanished")
        return try {
            zip.getInputStream(entry).use { BoundedRead.readAll(it, limits.maxPageBytes) }
        } catch (e: IOException) {
            val failure = if (isEncrypted(e)) PageFailure.ENCRYPTED else PageFailure.UNREADABLE
            throw ComicPageException(failure, e.message, e)
        }
    }

    override fun close() = zip.close()

    companion object {
        fun open(file: File, limits: ComicLimits = ComicLimits()): ComicSource {
            val zip = openZip(file)
            try {
                val raw = zip.entries().asSequence().map { RawEntry(it.name, it.size, it.compressedSize) }
                val catalog = ComicCatalogBuilder.build(raw, limits)
                val info = catalog.comicInfoHandle?.let { readInfo(zip, it, limits) }
                return ZipComicSource(zip, limits, catalog, info)
            } catch (e: ComicOpenException) {
                zip.close()
                throw e
            } catch (e: IllegalStateException) {
                zip.close()
                throw ComicOpenException(ComicOpenFailure.CORRUPT, e.message, e)
            }
        }

        private fun openZip(file: File): ZipFile {
            if (!file.isFile) throw ComicOpenException(ComicOpenFailure.NOT_FOUND)
            try {
                return ZipFile(file)
            } catch (e: IOException) {
                throw ComicOpenException(openFailureOf(e), e.message, e)
            }
        }

        private fun readInfo(zip: ZipFile, name: String, limits: ComicLimits): ComicInfo? {
            val entry = zip.getEntry(name)?.takeIf { it.size <= limits.maxComicInfoBytes }
            val bytes = entry?.let {
                runCatching {
                    val cap = limits.maxComicInfoBytes.toLong()
                    zip.getInputStream(it).use { stream -> BoundedRead.readAll(stream, cap) }
                }.getOrNull()
            }
            return bytes?.let { ComicInfoParser.parse(it, limits) }
        }

        private fun openFailureOf(e: IOException): ComicOpenFailure = when {
            isEncrypted(e) -> ComicOpenFailure.ENCRYPTED
            e is ZipException -> ComicOpenFailure.CORRUPT
            else -> ComicOpenFailure.UNREADABLE
        }

        /** The JDK reports "encrypted entry"; Android's libcore reports an unsupported general-purpose bit flag. */
        private fun isEncrypted(e: IOException): Boolean {
            val message = e.message.orEmpty()
            return message.contains("encrypt", ignoreCase = true) ||
                message.contains("General Purpose Bit Flag", ignoreCase = true)
        }
    }
}
