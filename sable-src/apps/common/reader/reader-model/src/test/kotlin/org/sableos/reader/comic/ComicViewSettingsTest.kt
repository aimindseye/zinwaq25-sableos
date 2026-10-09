package org.sableos.reader.comic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicViewSettingsTest {
    @Test
    fun everyModeRoundTripsThroughTheCodec() {
        ComicReadingMode.entries.forEach { mode ->
            ComicFit.entries.forEach { fit ->
                val settings = ComicViewSettings(mode, fit)
                assertEquals(settings, ComicViewSettingsCodec.decode(ComicViewSettingsCodec.encode(settings)))
            }
        }
    }

    @Test
    fun exactlyFiveModesExistWithStableNames() {
        assertEquals(
            listOf("PAGED_LTR", "PAGED_RTL", "VERTICAL_PAGER", "CONTINUOUS_VERTICAL", "WEBTOON"),
            ComicReadingMode.entries.map { it.stableValue },
        )
    }

    @Test
    fun badInputDecodesToNullInsteadOfThrowing() {
        listOf(null, "", "   ", "not json", "{}", "{\"mode\":\"SPIRAL\"}", "[1,2]").forEach {
            assertNull(it, ComicViewSettingsCodec.decode(it))
        }
    }

    @Test
    fun unknownFitFallsBackButUnknownKeysAreIgnored() {
        val decoded = ComicViewSettingsCodec.decode("{\"v\":9,\"mode\":\"WEBTOON\",\"fit\":\"STRETCH\",\"x\":1}")
        assertEquals(ComicViewSettings(ComicReadingMode.WEBTOON, ComicFit.FIT_PAGE), decoded)
    }

    @Test
    fun modeFlagsDescribeTheirBehaviour() {
        assertTrue(ComicReadingMode.PAGED_RTL.isRightToLeft)
        assertFalse(ComicReadingMode.PAGED_LTR.isRightToLeft)
        assertTrue(ComicReadingMode.PAGED_LTR.isPaged)
        assertFalse(ComicReadingMode.VERTICAL_PAGER.isPaged)
        assertTrue(ComicReadingMode.WEBTOON.isContinuous)
        assertTrue(ComicReadingMode.CONTINUOUS_VERTICAL.isContinuous)
        assertFalse(ComicReadingMode.VERTICAL_PAGER.isContinuous)
    }

    @Test
    fun webtoonIsSeamlessAndContinuousFitsTheWidth() {
        assertEquals(0, ComicViewSettings(ComicReadingMode.WEBTOON).pageGapDp)
        assertEquals(ComicViewSettings.PAGE_GAP_DP, ComicViewSettings(ComicReadingMode.CONTINUOUS_VERTICAL).pageGapDp)
        assertEquals(ComicFit.FIT_WIDTH, ComicViewSettings(ComicReadingMode.WEBTOON, ComicFit.FIT_PAGE).effectiveFit)
        assertEquals(ComicFit.FIT_PAGE, ComicViewSettings(ComicReadingMode.PAGED_LTR, ComicFit.FIT_PAGE).effectiveFit)
    }
}
