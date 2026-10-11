package org.sableos.start.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Header status rings: the labels and arc fill shown on the Start header. */
class DeviceStatsFormatTest {
    @Test
    fun rateIsShortEnoughForARing() {
        assertEquals("–", DeviceStatsFormat.rate(null))
        assertEquals("0", DeviceStatsFormat.rate(512))
        assertEquals("12K", DeviceStatsFormat.rate(12L * 1024))
        assertEquals("1.5M", DeviceStatsFormat.rate(1536L * 1024))
        assertEquals("24M", DeviceStatsFormat.rate(24L * 1024 * 1024))
    }

    @Test
    fun heatIsLogScaledAndClamped() {
        assertEquals(0f, DeviceStatsFormat.heat(null))
        assertEquals(0f, DeviceStatsFormat.heat(100))
        val slow = DeviceStatsFormat.heat(10L * 1024)
        val fast = DeviceStatsFormat.heat(5L * 1024 * 1024)
        assertTrue(slow in 0f..fast)
        assertEquals(1f, DeviceStatsFormat.heat(Long.MAX_VALUE))
    }

    @Test
    fun hoursLeftReadsAsMinutesHoursOrDays() {
        assertEquals("–", DeviceStatsFormat.hours(null))
        assertEquals("30m", DeviceStatsFormat.hours(0.5f))
        assertEquals("4h", DeviceStatsFormat.hours(4.2f))
        assertEquals("3d", DeviceStatsFormat.hours(72f))
    }
}
