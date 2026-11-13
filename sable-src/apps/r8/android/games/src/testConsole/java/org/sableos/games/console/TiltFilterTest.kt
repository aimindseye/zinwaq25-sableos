package org.sableos.games.console

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class TiltFilterTest {
    private fun lean(degrees: Double) = TiltFilter.GRAVITY * sin(Math.toRadians(degrees))

    /** Feeds the same reading until the smoothing settles; returns the steps it made. */
    private fun TiltFilter.hold(
        ax: Double,
        ay: Double,
    ): List<TiltStep> = (1..30).mapNotNull { feed(ax, ay) }

    @Test
    fun oneLeanIsOneStepUntilThePhoneComesBackLevel() {
        val f = TiltFilter()
        f.hold(0.0, 0.0)
        // Right edge down: the accelerometer's x goes negative.
        assertEquals(listOf(TiltStep.Right), f.hold(-lean(30.0), 0.0))
        assertEquals(emptyList<TiltStep>(), f.hold(-lean(30.0), 0.0))
        f.hold(0.0, 0.0)
        assertEquals(listOf(TiltStep.Left), f.hold(lean(30.0), 0.0))
        f.hold(0.0, 0.0)
        assertEquals(listOf(TiltStep.Down), f.hold(0.0, lean(30.0)))
        f.hold(0.0, 0.0)
        assertEquals(listOf(TiltStep.Up), f.hold(0.0, -lean(30.0)))
    }

    @Test
    fun levelMakesTheCurrentAngleZero() {
        val f = TiltFilter()
        f.hold(0.0, lean(40.0))
        f.level()
        assertEquals(0.0, f.y, 1e-9)
        assertNull(f.feed(0.0, lean(40.0)))
        f.hold(0.0, lean(40.0))
        assertEquals(0.0, f.y, 0.01)
    }

    @Test
    fun sensitivitySetsHowFarIsAFullLean() {
        val low = TiltFilter(TiltSensitivity.Low)
        val high = TiltFilter(TiltSensitivity.High)
        low.hold(0.0, 0.0)
        high.hold(0.0, 0.0)
        low.hold(-lean(15.0), 0.0)
        high.hold(-lean(15.0), 0.0)
        assertTrue(high.x > 0.95)
        assertTrue(low.x in 0.4..0.6)
        assertEquals(TiltSensitivity.Low, TiltSensitivity.High.next())
    }
}
