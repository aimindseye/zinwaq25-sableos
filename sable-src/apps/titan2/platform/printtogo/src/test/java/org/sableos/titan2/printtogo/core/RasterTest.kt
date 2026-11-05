package org.sableos.titan2.printtogo.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RasterTest {
    private fun pwgPage(out: DataOutputStream, width: Int, height: Int, bitsPerPixel: Int) {
        val header = ByteArray(RasterReader.PWG_HEADER)
        fun put(at: Int, v: Int) {
            header[at] = (v shr 24).toByte()
            header[at + 1] = (v shr 16).toByte()
            header[at + 2] = (v shr 8).toByte()
            header[at + 3] = v.toByte()
        }
        put(RasterReader.PWG_RESOLUTION, 300)
        put(RasterReader.PWG_WIDTH, width)
        put(RasterReader.PWG_HEIGHT, height)
        put(RasterReader.PWG_BITS_PER_COLOR, 8)
        put(RasterReader.PWG_BITS_PER_PIXEL, bitsPerPixel)
        out.write(header)
    }

    @Test
    fun pwgGreyPagesDecodeRunsLiteralsRepeatsAndWhiteFill() {
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        out.write(RasterReader.PWG_SYNC)
        pwgPage(out, width = 4, height = 3, bitsPerPixel = 8)
        // Line 0, repeated twice: 2 x 0x10, then 2 literal pixels.
        out.write(byteArrayOf(1, 1, 0x10, (257 - 2).toByte(), 0x20, 0x30))
        // Line 2: one black pixel then white to the end.
        out.write(byteArrayOf(0, 0, 0x00, 0x80.toByte()))
        val reader = RasterReader(ByteArrayInputStream(bytes.toByteArray()))
        val page = reader.nextPage()!!
        assertEquals(RasterPage(4, 3, 300, 1), page)
        val rows = mutableListOf<List<Int>>()
        reader.readRows { _, argb -> rows += argb.map { it and 0xFF } }
        assertEquals(listOf(0x10, 0x10, 0x20, 0x30), rows[0])
        assertEquals(rows[0], rows[1])
        assertEquals(listOf(0x00, 0xFF, 0xFF, 0xFF), rows[2])
        assertNull(reader.nextPage())
    }

    @Test
    fun appleRasterRgbPage() {
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        out.write(RasterReader.URF_SYNC)
        out.writeInt(1)
        val header = ByteArray(RasterReader.URF_HEADER)
        header[0] = 24
        fun put(at: Int, v: Int) {
            header[at] = (v shr 24).toByte()
            header[at + 1] = (v shr 16).toByte()
            header[at + 2] = (v shr 8).toByte()
            header[at + 3] = v.toByte()
        }
        put(RasterReader.URF_WIDTH, 2)
        put(RasterReader.URF_HEIGHT, 1)
        put(RasterReader.URF_DPI, 300)
        out.write(header)
        out.write(byteArrayOf(0, 1, 0x11, 0x22, 0x33))
        val reader = RasterReader(ByteArrayInputStream(bytes.toByteArray()))
        assertEquals(RasterPage(2, 1, 300, 3), reader.nextPage())
        val rows = mutableListOf<List<Int>>()
        reader.readRows { _, argb -> rows += argb.map { it and 0xFFFFFF } }
        assertEquals(listOf(listOf(0x112233, 0x112233)), rows)
        assertNull(reader.nextPage())
    }

    @Test(expected = RasterReader.UnsupportedRaster::class)
    fun otherFormatsAreRefused() {
        RasterReader(ByteArrayInputStream("%PDF-1.7 ....".toByteArray()))
    }
}
