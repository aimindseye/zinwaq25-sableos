package io.github.aimindseye.tenseven.frames

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SableBackToFramesTest {
    private val home = "org.sableos.launcher"

    @Test
    fun anotherAppInFrontMeansFramesOnReturn() {
        assertTrue(SableBackToFrames.leftForAnotherApp(home, "org.sableos.messages", false))
    }

    @Test
    fun ownScreensScreenOffAndRotationChangeNothing() {
        assertFalse(SableBackToFrames.leftForAnotherApp(home, home, false))
        assertFalse(SableBackToFrames.leftForAnotherApp(home, null, false))
        assertFalse(SableBackToFrames.leftForAnotherApp(home, "org.sableos.messages", true))
    }
}
