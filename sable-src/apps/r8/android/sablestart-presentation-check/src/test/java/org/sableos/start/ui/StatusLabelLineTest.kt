package org.sableos.start.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.sableos.start.platform.DeviceStats

class StatusLabelLineTest {
    @Test fun statLabelsInOrderWithNoReadingLeftOut() {
        val st = DeviceStats(cpu = 0.234f, ram = 0.61f, battery = 0.82f, charging = true, signal = 0.75f)
        assertEquals(
            "CPU 23% · RAM 61% · BAT 82%+ · Cell 3/4",
            statusLabelLine(st, mobile = "Mobile", cell = "Cell"),
        )
    }

    @Test fun noCpuAndNoSimLeaveThoseOut() {
        val st = DeviceStats(cpu = null, ram = 0.5f, battery = 0.15f)
        assertEquals("RAM 50% · BAT 15%", statusLabelLine(st, mobile = "Mobile", cell = "Cell"))
    }
}
