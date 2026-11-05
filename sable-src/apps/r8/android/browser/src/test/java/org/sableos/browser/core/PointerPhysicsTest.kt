package org.sableos.browser.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerPhysicsTest {
    @Test
    fun slowStrokesAimFastStrokesCross() {
        var streak = PointerPhysics.nextStreak(null, 0)
        assertEquals(PointerPhysics.BASE_STEP_DP, PointerPhysics.stepDp(streak, false), 0.01f)
        repeat(20) { streak = PointerPhysics.nextStreak(streak, streak.lastMillis + 30) }
        assertEquals(PointerPhysics.MAX_STEP_DP, PointerPhysics.stepDp(streak, false), 0.01f)
        // A pause starts slow again.
        val after = PointerPhysics.nextStreak(streak, streak.lastMillis + 500)
        assertEquals(1, after.count)
    }

    @Test
    fun linksSlowThePointerDown() {
        val fast = PointerPhysics.Streak(10, 0)
        assertTrue(PointerPhysics.stepDp(fast, true) < PointerPhysics.stepDp(fast, false))
        assertTrue(PointerPhysics.stepDp(PointerPhysics.Streak(1, 0), true) > 0f)
    }

    @Test
    fun theEdgeScrollsThePage() {
        val page = PointerPhysics.Area(400f, 400f)
        val inside = PointerPhysics.move(100f, 100f, 10f, 0f, page)
        assertEquals(PointerPhysics.Move(110f, 100f, 0, 0), inside)
        val bottom = PointerPhysics.move(100f, 395f, 0f, 20f, page)
        assertEquals(399f, bottom.y, 0.01f)
        assertEquals((20f * PointerPhysics.EDGE_SCROLL).toInt(), bottom.scrollY)
        val left = PointerPhysics.move(2f, 50f, -10f, 0f, page)
        assertEquals(0f, left.x, 0.01f)
        assertTrue(left.scrollX < 0)
    }
}
