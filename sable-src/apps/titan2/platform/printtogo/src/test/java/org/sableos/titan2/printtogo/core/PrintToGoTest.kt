package org.sableos.titan2.printtogo.core

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrintToGoTest {
    private val utc = ZoneId.of("UTC")
    private val at = 1_793_885_460_000L // 2026-11-05 13:31 UTC

    @Test
    fun savedNamesAreSafeAndStamped() {
        assertEquals(
            "Boarding pass 2026-11-05 1331.pdf",
            PrintToGo.fileName("Boarding pass.pdf", at, utc)
        )
        assertEquals(
            "report 2026-11-05 1331.pdf",
            PrintToGo.fileName("C:\\Users\\me\\report.docx", at, utc)
        )
        assertEquals("a b 2026-11-05 1331.pdf", PrintToGo.fileName("a:b", at, utc))
        assertEquals("Print 2026-11-05 1331.pdf", PrintToGo.fileName("  ", at, utc))
        assertEquals(
            60 + " 2026-11-05 1331.pdf".length,
            PrintToGo.fileName("x".repeat(200), at, utc).length
        )
    }

    @Test
    fun theFirstBytesDecideTheFormat() {
        assertEquals(
            IppPrinter.PDF,
            PrintToGo.sniff("%PDF-1.4".toByteArray(), IppPrinter.OCTET_STREAM)
        )
        assertEquals(
            IppPrinter.PWG_RASTER,
            PrintToGo.sniff("RaS2xxxx".toByteArray(), IppPrinter.PDF)
        )
        assertEquals(
            IppPrinter.URF_RASTER,
            PrintToGo.sniff("UNIRAST\u0000".toByteArray(), IppPrinter.OCTET_STREAM)
        )
        assertNull(PrintToGo.sniff("PK\u0003\u0004".toByteArray(), IppPrinter.OCTET_STREAM))
        assertEquals(IppPrinter.PDF, PrintToGo.sniff(ByteArray(0), IppPrinter.PDF))
    }

    @Test
    fun announcementAndIdleTimeout() {
        val txt = PrintToGo.txtRecord("Sable Print To Go (Q25)", "urn:uuid:abc")
        assertEquals("ipp/print", txt["rp"])
        assertEquals("abc", txt["UUID"])
        assertEquals("application/pdf,image/pwg-raster,image/urf", txt["pdl"])
        assertEquals(PrintToGo.IDLE_MS, PrintToGo.remaining(1000, 1000))
        assertEquals(0L, PrintToGo.remaining(0, PrintToGo.IDLE_MS + 5))
    }
}
