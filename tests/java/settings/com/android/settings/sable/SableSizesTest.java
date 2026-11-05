// Host test for the pure size classes added to LineageOS Settings by
// patches/framework/packages/apps/Settings/1601 (SableTextSize) and 1603
// (SableSizes). tests/run.sh extracts them from the patches, compiles them with
// this file and runs the test with org.junit.Run.

package com.android.settings.sable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SableSizesTest {
    private static final int Q25 = 280;

    @Test
    public void displayStepsCenterOnThePanelDensity() {
        assertEquals(5, SableSizes.displayStepCount());
        assertEquals(241, SableSizes.displayDensity(Q25, 0));
        assertEquals(260, SableSizes.displayDensity(Q25, 1));
        assertEquals(Q25, SableSizes.displayDensity(Q25, SableSizes.DISPLAY_DEFAULT_INDEX));
        assertEquals(300, SableSizes.displayDensity(Q25, 3));
        assertEquals(319, SableSizes.displayDensity(Q25, 4));
        assertEquals(Q25, SableSizes.displayDensity(Q25, 9));
    }

    @Test
    public void densitiesMapBackToTheirStep() {
        for (int i = 0; i < SableSizes.displayStepCount(); i++) {
            assertEquals(i, SableSizes.displayIndexOf(Q25, SableSizes.displayDensity(Q25, i)));
        }
        assertEquals(3, SableSizes.displayIndexOf(Q25, 301));
        assertEquals(-1, SableSizes.displayIndexOf(Q25, 400));
        assertEquals(-1, SableSizes.displayIndexOf(0, 280));
    }

    @Test
    public void everyDisplayStepKeepsAPhoneWidth() {
        for (int i = 0; i < SableSizes.displayStepCount(); i++) {
            int widthDp = 720 * 160 / SableSizes.displayDensity(Q25, i);
            assertTrue("step " + i + " is " + widthDp + " dp", widthDp >= 360 && widthDp <= 480);
        }
    }

    @Test
    public void lockLevelsFallBackToDefault() {
        assertEquals(0, SableSizes.lockLevel(0));
        assertEquals(3, SableSizes.lockLevel(3));
        assertEquals(SableSizes.LOCK_DEFAULT_LEVEL, SableSizes.lockLevel(4));
        assertEquals(SableSizes.LOCK_DEFAULT_LEVEL, SableSizes.lockLevel(-1));
        assertEquals("sable_lock_screen_size", SableSizes.LOCK_SETTING);
    }

    @Test
    public void tilesGrowFromSmallToLarge() {
        assertTrue(SableSizes.tileTextSp(0, 5) == 12f);
        assertTrue(SableSizes.tileTextSp(4, 5) == 26f);
        for (int i = 1; i < 4; i++) {
            assertTrue(SableSizes.tileTextSp(i, 4) > SableSizes.tileTextSp(i - 1, 4));
        }
    }

    @Test
    public void textSizesMatchTheTiles() {
        assertEquals(4, SableTextSize.SCALES.length);
        assertEquals(2, SableTextSize.indexOf(1.15f));
        assertEquals(-1, SableTextSize.indexOf(1.5f));
    }
}
