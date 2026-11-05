package org.sableos.titan2.printtogo.core

import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream

/** One raster page's size and pixel layout. */
data class RasterPage(
    val width: Int,
    val height: Int,
    val dpi: Int,
    /** 1 (8-bit grey) or 3 (8-bit sRGB). */
    val bytesPerPixel: Int
)

/**
 * Reads PWG Raster (image/pwg-raster, IPP Everywhere) and Apple Raster (image/urf, AirPrint), the two raster
 * formats driverless clients send, page by page. Both compress each line the same way: a line-repeat count,
 * then runs of one repeated pixel or of literal pixels. Only 8-bit grey and 8-bit sRGB are taken, which is what
 * the printer advertises; anything else is refused with [UnsupportedRaster].
 */
class RasterReader(input: InputStream) {
    private val data = DataInputStream(input.buffered())
    private val kind: Kind
    private var current: RasterPage? = null
    private var pagesLeft = Int.MAX_VALUE

    class UnsupportedRaster(message: String) : Exception(message)

    private enum class Kind { Pwg, Urf }

    init {
        val sync = ByteArray(SYNC_BYTES).also(data::readFully)
        kind =
            when {
                sync.contentEquals(PWG_SYNC) -> Kind.Pwg

                sync.contentEquals(URF_SYNC.copyOf(SYNC_BYTES)) -> {
                    data.readFully(ByteArray(URF_SYNC.size - SYNC_BYTES))
                    pagesLeft = data.readInt().takeIf { it > 0 } ?: Int.MAX_VALUE
                    Kind.Urf
                }

                else -> throw UnsupportedRaster("not PWG or Apple raster")
            }
    }

    /** The next page's header, or null after the last page. Call [readRows] before asking again. */
    fun nextPage(): RasterPage? {
        if (pagesLeft <= 0) return null
        val page =
            try {
                if (kind == Kind.Pwg) pwgHeader() else urfHeader()
            } catch (_: EOFException) {
                return null
            }
        pagesLeft--
        current = page
        return page
    }

    /** Decodes the current page, giving each row as ARGB pixels (the same array, reused). */
    fun readRows(row: (index: Int, argb: IntArray) -> Unit) {
        val page = checkNotNull(current) { "no page" }
        val bpp = page.bytesPerPixel
        val line = ByteArray(page.width * bpp)
        val pixels = IntArray(page.width)
        var y = 0
        while (y < page.height) {
            val repeat = data.readUnsignedByte() + 1
            readLine(line, bpp)
            toArgb(line, bpp, pixels)
            repeat(minOf(repeat, page.height - y)) { row(y++, pixels) }
        }
        current = null
    }

    private fun readLine(line: ByteArray, bpp: Int) {
        var x = 0
        val end = line.size
        while (x < end) {
            val n = data.readUnsignedByte()
            when {
                n == FILL_WHITE -> {
                    line.fill(WHITE, x, end)
                    x = end
                }

                n < FILL_WHITE -> {
                    val pixel = ByteArray(bpp).also(data::readFully)
                    repeat(n + 1) {
                        if (x < end) pixel.copyInto(line, x)
                        x += bpp
                    }
                }

                else -> {
                    val bytes = minOf((LITERAL_BASE - n) * bpp, end - x)
                    data.readFully(line, x, bytes)
                    x += bytes
                }
            }
        }
    }

    private fun pwgHeader(): RasterPage {
        val header = ByteArray(PWG_HEADER).also(data::readFully)
        val dpi = header.int(PWG_RESOLUTION)
        val width = header.int(PWG_WIDTH)
        val height = header.int(PWG_HEIGHT)
        val bitsPerColor = header.int(PWG_BITS_PER_COLOR)
        val bitsPerPixel = header.int(PWG_BITS_PER_PIXEL)
        if (bitsPerColor != BITS || (bitsPerPixel != BITS && bitsPerPixel != BITS * RGB)) {
            throw UnsupportedRaster("PWG $bitsPerColor/$bitsPerPixel bits")
        }
        return page(width, height, dpi, bitsPerPixel / BITS)
    }

    private fun urfHeader(): RasterPage {
        val header = ByteArray(URF_HEADER).also(data::readFully)
        val bitsPerPixel = header[0].toInt() and BYTE
        if (bitsPerPixel != BITS &&
            bitsPerPixel != BITS * RGB
        ) {
            throw UnsupportedRaster("URF $bitsPerPixel bits")
        }
        return page(
            header.int(URF_WIDTH),
            header.int(URF_HEIGHT),
            header.int(URF_DPI),
            bitsPerPixel / BITS
        )
    }

    private fun page(width: Int, height: Int, dpi: Int, bpp: Int): RasterPage {
        if (width !in 1..MAX_SIDE ||
            height !in 1..MAX_SIDE
        ) {
            throw UnsupportedRaster("page $width x $height")
        }
        return RasterPage(width, height, dpi.coerceIn(MIN_DPI, MAX_DPI), bpp)
    }

    private fun toArgb(line: ByteArray, bpp: Int, out: IntArray) {
        for (x in out.indices) {
            val i = x * bpp
            out[x] =
                if (bpp == 1) {
                    val g = line[i].toInt() and BYTE
                    OPAQUE or (g shl 16) or (g shl 8) or g
                } else {
                    OPAQUE or (line[i].toInt() and BYTE shl 16) or
                        (line[i + 1].toInt() and BYTE shl 8) or
                        (line[i + 2].toInt() and BYTE)
                }
        }
    }

    private fun ByteArray.int(at: Int): Int =
        (this[at].toInt() and BYTE shl 24) or (this[at + 1].toInt() and BYTE shl 16) or
            (this[at + 2].toInt() and BYTE shl 8) or (this[at + 3].toInt() and BYTE)

    companion object {
        val PWG_SYNC = "RaS2".toByteArray(Charsets.US_ASCII)
        val URF_SYNC = "UNIRAST\u0000".toByteArray(Charsets.US_ASCII)
        private const val SYNC_BYTES = 4

        /** The CUPS/PWG page header after the sync word, with the byte offsets of the fields read. */
        const val PWG_HEADER = 1796
        const val PWG_RESOLUTION = 276
        const val PWG_WIDTH = 372
        const val PWG_HEIGHT = 376
        const val PWG_BITS_PER_COLOR = 384
        const val PWG_BITS_PER_PIXEL = 388

        const val URF_HEADER = 32
        const val URF_WIDTH = 12
        const val URF_HEIGHT = 16
        const val URF_DPI = 20

        private const val FILL_WHITE = 0x80
        private const val LITERAL_BASE = 257
        private const val WHITE = 0xFF.toByte()
        private const val BITS = 8
        private const val RGB = 3
        private const val BYTE = 0xFF
        private const val OPAQUE = -0x1000000
        private const val MAX_SIDE = 20_000
        private const val MIN_DPI = 72
        private const val MAX_DPI = 1200
    }
}
