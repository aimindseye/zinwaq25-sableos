package org.sableos.test.frameworks.base

import com.android.keyguard.sable.SableLockSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SableLockSizeTest {
    private val q25 = 411
    private val statusBar = 24

    @Test
    fun levelsScaleAndBadLevelsAreDefault() {
        assertTrue(abs(SableLockSize.scale(0) - 0.85f) < 0.001f)
        assertTrue(abs(SableLockSize.scale(1) - 1.0f) < 0.001f)
        assertTrue(abs(SableLockSize.scale(3) - 1.3f) < 0.001f)
        assertEquals(SableLockSize.DEFAULT_LEVEL, SableLockSize.clampLevel(-1))
        assertEquals(SableLockSize.DEFAULT_LEVEL, SableLockSize.clampLevel(4))
    }

    @Test
    fun tallWindowsKeepAndroidsPad() {
        assertNull(SableLockSize.compactPad(1, 411, 600, statusBar, 1f, true))
        assertNull(SableLockSize.compactPad(1, 411, 0, statusBar, 1f, true))
    }

    @Test
    fun defaultOnTheQ25IsWideShortKeys() {
        val pad = SableLockSize.compactPad(1, q25, q25, statusBar, 1f, true)!!
        assertEquals(44, pad.keyHeight)
        assertEquals(97, pad.keyWidth)
        assertEquals(22, pad.digitSize)
        assertEquals(40, pad.entryHeight)
        assertEquals(37, SableLockSize.compactPad(0, q25, q25, statusBar, 1f, true)!!.keyHeight)
    }

    @Test
    fun largerSizesStopWhereTheScreenIsFull() {
        val large = SableLockSize.compactPad(2, q25, q25, statusBar, 1f, true)!!
        val larger = SableLockSize.compactPad(3, q25, q25, statusBar, 1f, true)!!
        assertEquals(48, large.keyHeight)
        assertEquals(48, larger.keyHeight)
        assertTrue(larger.digitSize > large.digitSize)
        assertEquals(44, SableLockSize.compactPad(3, q25, q25, statusBar, 1.3f, true)!!.keyHeight)
    }

    @Test
    fun everyLevelAndTextSizeFitsTheQ25() {
        for (level in 0..3) {
            for (font in floatArrayOf(0.9f, 1.0f, 1.15f, 1.3f)) {
                for (fallback in booleanArrayOf(true, false)) {
                    val pad = SableLockSize.compactPad(level, q25, q25, statusBar, font, fallback)!!
                    assertTrue(
                        "level $level font $font",
                        pad.totalHeight(font, fallback) <= q25 - statusBar,
                    )
                    assertTrue(pad.keyWidth * 3 + pad.gap * 2 <= q25)
                    assertTrue(pad.digitSize < pad.keyHeight)
                }
            }
        }
    }

    @Test
    fun aVeryShortWindowNeverGoesUnderTheMinimum() {
        val pad = SableLockSize.compactPad(3, 300, 300, statusBar, 1.3f, true)!!
        assertEquals(32, pad.keyHeight)
        assertEquals(32, pad.entryHeight)
    }
}
