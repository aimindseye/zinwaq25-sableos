package org.sableos.tools.core

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportFileTest {
    @Test fun nameIsTheToolTitleAndLocalTime() {
        // 2026-10-28 15:53:15 UTC
        val at = 1_793_202_795_000L
        assertEquals(
            "sable-tools-keyboard-and-input-report-20261028-155315.txt",
            ReportFile.name("Keyboard and input report", at, ZoneOffset.UTC)
        )
    }

    @Test fun oddTitlesStillGiveAFileName() {
        assertEquals(
            "sable-tools-report-19700101-000000.txt",
            ReportFile.name("···", 0L, ZoneOffset.UTC)
        )
        assertEquals(
            "sable-tools-radio-ims-and-fm-status-19700101-000000.txt",
            ReportFile.name("Radio, IMS and FM status", 0L, ZoneOffset.UTC)
        )
    }
}
