package org.sableos.tools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The SOS pattern and siren, and the pedometer's daily ledger and estimates. */
class SosAndStepsTest {
    @Test fun sosIsThreeShortThreeLongThreeShort() {
        val steps = SosPattern.STEPS
        assertEquals(18, steps.size)
        val on = steps.filterIndexed { i, _ -> SosPattern.isOn(i) }
        assertEquals(listOf(1L, 1L, 1L, 3L, 3L, 3L, 1L, 1L, 1L).map { it * SosPattern.UNIT_MS }, on)
        assertTrue(SosPattern.isOn(0))
        assertFalse(SosPattern.isOn(1))
        assertEquals("the pattern repeats", 0, SosPattern.next(steps.size - 1))
    }

    @Test fun sirenIsOneLoopOfRealAudio() {
        val wave = SirenWave.samples()
        assertEquals(SirenWave.RATE * SirenWave.SECONDS, wave.size)
        assertTrue(wave.any { it > 10_000 })
        assertTrue(wave.any { it < -10_000 })
    }

    @Test fun ledgerCountsGainsPerDayAndSurvivesAReboot() {
        val ledger = StepLedger()
        ledger.sample(1000, day = 100)
        assertEquals("the first sample only sets the baseline", 0L, ledger.steps(100))
        ledger.sample(1300, day = 100)
        ledger.sample(1500, day = 101)
        assertEquals(300L, ledger.steps(100))
        assertEquals(200L, ledger.steps(101))
        ledger.sample(40, day = 101)
        assertEquals("a lower value is a restart: all of it is new", 240L, ledger.steps(101))
        assertEquals(listOf(101L to 240L, 100L to 300L, 99L to 0L), ledger.recent(101, 3))
    }

    @Test fun ledgerRoundTripsAndIgnoresDamage() {
        val ledger = StepLedger()
        ledger.sample(10, 5)
        ledger.sample(60, 5)
        val back = StepLedger.decode(ledger.encode())
        assertEquals(50L, back.steps(5))
        back.sample(70, 5)
        assertEquals("the baseline came back too", 60L, back.steps(5))
        val broken = StepLedger.decode("x|abc,7:9,8:-2,:3,9:")
        assertEquals(9L, broken.steps(7))
        assertEquals(0L, broken.steps(8))
        assertEquals(0L, StepLedger.decode(null).steps(1))
    }

    @Test fun ledgerForgetsOldDays() {
        val ledger = StepLedger()
        ledger.sample(0, 1)
        ledger.sample(10, 1)
        ledger.sample(20, 1 + StepLedger.KEEP_DAYS)
        assertEquals(0L, ledger.steps(1))
    }

    @Test fun estimatesAreSensibleAndProfileIsClamped() {
        val km = StepMath.distanceKm(8000, 170)
        assertTrue(km in 5.0..6.5)
        assertTrue(StepMath.kcal(km, 70) in 150..250)
        val p = StepProfile(heightCm = 5, weightKg = 9000, goal = 1).safe
        assertEquals(100, p.heightCm)
        assertEquals(250, p.weightKg)
        assertEquals(1000, p.goal)
    }

    @Test fun sosToolIsListedWithItsKeywords() {
        assertTrue(HomeModel.matches(Tool.SOS_ALERT, "siren"))
        assertTrue(HomeModel.matches(Tool.SOS_ALERT, "sos"))
        assertEquals(Capability.SOS_ALERT, Tool.SOS_ALERT.capability)
    }
}
