package io.github.aimindseye.tenseven.platform

import io.github.aimindseye.tenseven.platform.SableExtraDim.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SableExtraDimTest {
    @Test
    fun theBottomOfTheSliderTurnsExtraDimOn() {
        assertEquals(Action.TurnOn, SableExtraDim.decide(enabled = true, manual = true, brightness = 1, active = false, ours = false))
        assertEquals(Action.TurnOn, SableExtraDim.decide(enabled = true, manual = true, brightness = 0, active = false, ours = false))
    }

    @Test
    fun movingUpTurnsOffOnlyWhatTheHomeTurnedOn() {
        assertEquals(Action.TurnOff, SableExtraDim.decide(enabled = true, manual = true, brightness = 40, active = true, ours = true))
        assertNull(SableExtraDim.decide(enabled = true, manual = true, brightness = 40, active = true, ours = false))
    }

    @Test
    fun autoBrightnessAndTheSwitchLeaveItOff() {
        assertNull(SableExtraDim.decide(enabled = true, manual = false, brightness = 1, active = false, ours = false))
        assertNull(SableExtraDim.decide(enabled = false, manual = true, brightness = 1, active = false, ours = false))
        assertEquals(Action.TurnOff, SableExtraDim.decide(enabled = false, manual = true, brightness = 1, active = true, ours = true))
    }

    @Test
    fun aboveTheBottomNothingChanges() {
        assertNull(SableExtraDim.decide(enabled = true, manual = true, brightness = SableExtraDim.BOTTOM + 1, active = false, ours = false))
    }
}
