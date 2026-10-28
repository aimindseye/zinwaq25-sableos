package org.lineageos.setupwizard.sable

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure tests for the short-window setup layout carried in patches/framework/packages/apps/SetupWizard/0414. */
class SableShortWindowTest {
    @Test fun theQ25SquareScreenIsShort() {
        assertTrue(SableShortWindow.isShort(411))
        assertTrue(SableShortWindow.isShort(599))
    }

    @Test fun tallWindowsAndUnknownHeightsKeepTheUpstreamLayout() {
        assertFalse(SableShortWindow.isShort(600))
        assertFalse(SableShortWindow.isShort(860))
        assertFalse(SableShortWindow.isShort(0))
        assertFalse(SableShortWindow.isShort(-1))
    }
}
