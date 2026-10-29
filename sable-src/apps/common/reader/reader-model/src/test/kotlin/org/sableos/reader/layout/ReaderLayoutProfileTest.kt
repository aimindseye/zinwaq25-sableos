package org.sableos.reader.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderLayoutProfileTest {
    @Test
    fun squareDisplayUsesTransientChromeAndMaximizedContent() {
        val p = ReaderLayoutProfile(widthDp = 400f, heightDp = 400f)
        assertTrue(p.isSquareish)
        assertTrue(p.transientChrome)
        assertFalse(p.chromeInitiallyVisible)
        assertTrue(p.contentMaximized)
        assertFalse(p.persistentSideNavigation)
    }

    @Test
    fun compactPortraitPhoneUsesTransientChrome() {
        val p = ReaderLayoutProfile(widthDp = 360f, heightDp = 800f)
        assertFalse(p.isSquareish)
        assertTrue(p.isCompact)
        assertTrue(p.transientChrome)
    }

    @Test
    fun largeTabletKeepsChromeVisibleButStillHasNoSideNavigation() {
        val p = ReaderLayoutProfile(widthDp = 900f, heightDp = 1280f)
        assertFalse(p.isCompact)
        assertFalse(p.isSquareish)
        assertFalse(p.transientChrome)
        assertTrue(p.chromeInitiallyVisible)
        assertFalse(p.persistentSideNavigation)
    }

    @Test
    fun orientationDoesNotChangeTheClassification() {
        assertEquals(ReaderLayoutProfile(360f, 800f).isSquareish, ReaderLayoutProfile(800f, 360f).isSquareish)
        assertEquals(ReaderLayoutProfile(360f, 800f).isCompact, ReaderLayoutProfile(800f, 360f).isCompact)
    }

    @Test
    fun degenerateSizeIsNotSquare() {
        assertFalse(ReaderLayoutProfile(0f, 0f).isSquareish)
    }

    @Test
    fun squareThresholdIsInclusive() {
        assertTrue(ReaderLayoutProfile(750f, 1000f).isSquareish)
        assertFalse(ReaderLayoutProfile(749f, 1000f).isSquareish)
    }

    @Test
    fun keyboardSquarePhoneIsShortAndFitsFourCoversAcross() {
        // 720x720 at density 280, minus the status bar.
        val q25 = ReaderLayoutProfile(411f, 387f)
        assertTrue(q25.isShort)
        assertEquals(4, q25.libraryColumns(411f))
    }

    @Test
    fun tallWindowsKeepFullSizeCovers() {
        val phone = ReaderLayoutProfile(411f, 860f)
        assertFalse(phone.isShort)
        assertEquals(3, phone.libraryColumns(411f))
        assertFalse(ReaderLayoutProfile(0f, 0f).isShort)
    }
}
