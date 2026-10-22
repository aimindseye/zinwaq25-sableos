package org.sableos.test.lineagesdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lineageos.internal.notification.sable.SableBatteryLightPolicy
import org.lineageos.internal.notification.sable.SableBatteryLightPolicy.Light

// Q4-LED-BB battery light: blinking amber while charging, solid green only at 100 %.
class SableBatteryLightPolicyTest {
    private val charging = SableBatteryLightPolicy.STATUS_CHARGING
    private val discharging = SableBatteryLightPolicy.STATUS_DISCHARGING
    private val notCharging = SableBatteryLightPolicy.STATUS_NOT_CHARGING
    private val full = SableBatteryLightPolicy.STATUS_FULL

    private fun decide(level: Int, status: Int, low: Boolean = level <= 15,
            enabled: Boolean = true, fullOff: Boolean = false, pulse: Boolean = true) =
        SableBatteryLightPolicy.decide(level, status, low, enabled, fullOff, pulse)

    @Test
    fun chargingBelowFullIsTheChargingLightNotGreen() {
        assertEquals(Light.CHARGING, decide(50, charging))
        // LineageOS showed green from 90 %; Sable waits for 100 %.
        assertEquals(Light.CHARGING, decide(90, charging))
        assertEquals(Light.CHARGING, decide(99, charging))
    }

    @Test
    fun lowAndChargingUsesTheLowColorWithTheChargingPattern() {
        assertEquals(Light.LOW_CHARGING, decide(10, charging))
    }

    @Test
    fun greenOnlyWhenCompletelyChargedAndPlugged() {
        assertEquals(Light.FULL, decide(100, charging))
        assertEquals(Light.FULL, decide(100, full))
        assertEquals(Light.FULL, decide(100, notCharging))
        // A FULL status from the charger counts even if the gauge says 99.
        assertEquals(Light.FULL, decide(99, full))
        assertTrue(SableBatteryLightPolicy.isPluggedAndFull(100, charging))
        assertFalse(SableBatteryLightPolicy.isPluggedAndFull(100, discharging))
    }

    @Test
    fun fullStaysOnByDefaultAndTheUserCanTurnItOff() {
        assertEquals(Light.FULL, decide(100, full, fullOff = false))
        assertEquals(Light.OFF, decide(100, full, fullOff = true))
        // Turning off at full never hides the charging light.
        assertEquals(Light.CHARGING, decide(80, charging, fullOff = true))
    }

    @Test
    fun unpluggedIsOffUnlessLow() {
        assertEquals(Light.OFF, decide(100, discharging))
        assertEquals(Light.OFF, decide(60, discharging))
        assertEquals(Light.LOW_FLASH, decide(10, discharging))
        assertEquals(Light.OFF, decide(10, discharging, pulse = false))
        // Plugged in but not charging below full behaves as not charging.
        assertEquals(Light.LOW_FLASH, decide(10, notCharging))
        assertEquals(Light.OFF, decide(60, notCharging))
    }

    @Test
    fun batteryLightSwitchedOffIsAlwaysOff() {
        for (status in listOf(charging, discharging, notCharging, full)) {
            assertEquals(Light.OFF, decide(100, status, enabled = false))
            assertEquals(Light.OFF, decide(10, status, enabled = false))
        }
    }

    @Test
    fun chargingBlinksOnlyWhenTheLightCanAndBothTimesArePositive() {
        assertTrue(SableBatteryLightPolicy.chargingBlinks(true, 1000, 1000))
        assertFalse(SableBatteryLightPolicy.chargingBlinks(false, 1000, 1000))
        assertFalse(SableBatteryLightPolicy.chargingBlinks(true, 0, 1000))
        assertFalse(SableBatteryLightPolicy.chargingBlinks(true, 1000, 0))
    }
}
