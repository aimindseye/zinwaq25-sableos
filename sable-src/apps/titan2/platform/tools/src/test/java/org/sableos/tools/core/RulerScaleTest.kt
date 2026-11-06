package org.sableos.tools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RulerScaleTest {
    @Test fun metricTicksEveryMillimetreNumberedEveryCentimetre() {
        val ticks = RulerScale.metric(lengthPx = 254f, pxPerInch = 254f)
        assertEquals(26, ticks.size)
        assertEquals("0", ticks[0].label)
        assertEquals("1", ticks[10].label)
        assertEquals(100f, ticks[10].px, 0.01f)
        assertEquals(RulerTick.Size.MEDIUM, ticks[5].size)
        assertEquals(RulerTick.Size.SHORT, ticks[3].size)
    }

    @Test fun imperialTicksEveryEighthNumberedEveryInch() {
        val ticks = RulerScale.imperial(lengthPx = 400f, pxPerInch = 160f)
        assertEquals(21, ticks.size)
        assertEquals("2", ticks[16].label)
        assertEquals(320f, ticks[16].px, 0.01f)
        assertEquals(RulerTick.Size.MEDIUM, ticks[4].size)
    }

    @Test fun nothingWithoutADensity() {
        assertTrue(RulerScale.metric(100f, 0f).isEmpty())
        assertTrue(RulerScale.imperial(0f, 160f).isEmpty())
    }
}
