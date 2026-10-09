package org.sableos.titan2.radiodiag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.titan2.radiodiag.core.RadioReport
import org.sableos.titan2.radiodiag.core.Redact
import org.sableos.titan2.radiodiag.core.Row
import org.sableos.titan2.radiodiag.core.Section

class RadioReportTest {
    @Test fun idKeepsLastFour() {
        assertEquals("***********3456", Redact.id("89014103211123456".takeLast(15)))
        assertEquals("-", Redact.id(null))
        assertEquals("***", Redact.id("123"))
    }

    @Test fun textMasksLongDigitRuns() {
        assertEquals("imsi ********2345 ok 12345", Redact.text("imsi 310410012345 ok 12345"))
    }

    @Test fun reportListsSectionsAndDashesBlanks() {
        val r = RadioReport.render(
            "Titan 2 · N0",
            listOf(Section("SIM", listOf(Row("state", "READY"), Row("operator", ""))))
        )
        assertTrue(r.contains("## SIM"))
        assertTrue(r.contains("state: READY"))
        assertTrue(r.contains("operator: -"))
        assertFalse(r.contains("IMEI"))
    }
}
