// Host test for Sable Audio's pure rules (patches/framework/packages/apps/Settings/1616,
// SableAudioCodecs). tests/run.sh extracts the class from the patch, compiles it with this file
// and runs the test with org.junit.Run.

package com.android.settings.sable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public final class SableAudioCodecsTest {
    @Test
    public void codecsAreListedBestFirstAndOnce() {
        assertTrue(Arrays.equals(
                new int[] {SableAudioCodecs.LDAC, SableAudioCodecs.AAC, SableAudioCodecs.SBC},
                SableAudioCodecs.sortCodecs(new int[] {0, 1, 4, 1, 99})));
        assertEquals("aptX HD", SableAudioCodecs.codecName(SableAudioCodecs.APTX_HD));
    }

    @Test
    public void ratesAndDepthsComeFromTheCapabilityMask() {
        // 44.1, 48 and 96 kHz.
        assertTrue(Arrays.equals(new int[] {8, 2, 1},
                SableAudioCodecs.flagsIn(1 | 2 | 8, SableAudioCodecs.RATE_FLAGS)));
        assertEquals(8, SableAudioCodecs.highest(1 | 2 | 8, SableAudioCodecs.RATE_FLAGS));
        assertEquals("96 kHz", SableAudioCodecs.rateLabel(8));
        assertEquals("24-bit", SableAudioCodecs.bitsLabel(2));
        assertEquals(0, SableAudioCodecs.highest(0, SableAudioCodecs.BITS_FLAGS));
    }

    @Test
    public void unknownLdacQualityCountsAsAdaptive() {
        assertEquals(0, SableAudioCodecs.ldacIndex(1000L));
        assertEquals(3, SableAudioCodecs.ldacIndex(0L));
    }
}
