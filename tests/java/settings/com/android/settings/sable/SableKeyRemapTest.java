// Host test for Key remapping's pure rules (patches/framework/packages/apps/Settings/1617,
// SableKeyRemap). tests/run.sh extracts the class from the patch, compiles it with this file and
// runs the test with org.junit.Run.

package com.android.settings.sable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SableKeyRemapTest {
    private static final int CTRL_RIGHT = 114;
    private static final int ESCAPE = 111;

    @Test
    public void unmappedOrSelfMappedKeysShowDefault() {
        assertEquals(0, SableKeyRemap.targetIndex(SableKeyRemap.KEY_CURRENCY, 0));
        assertEquals(0, SableKeyRemap.targetIndex(SableKeyRemap.KEY_CURRENCY,
                SableKeyRemap.KEY_CURRENCY));
        assertEquals(0, SableKeyRemap.targetIndex(SableKeyRemap.KEY_CURRENCY, 999));
    }

    @Test
    public void mappedKeysShowTheirTarget() {
        assertEquals(1, SableKeyRemap.targetIndex(SableKeyRemap.KEY_SHIFT_RIGHT, CTRL_RIGHT));
        assertEquals(4, SableKeyRemap.targetIndex(SableKeyRemap.KEY_RECENTS, ESCAPE));
    }

    @Test
    public void defaultMapsTheKeyToItselfWhichClearsIt() {
        assertEquals(SableKeyRemap.KEY_RECENTS, SableKeyRemap.remapTo(SableKeyRemap.KEY_RECENTS, 0));
        assertEquals(CTRL_RIGHT, SableKeyRemap.remapTo(SableKeyRemap.KEY_CURRENCY, 1));
        assertEquals(SableKeyRemap.KEY_CURRENCY, SableKeyRemap.remapTo(SableKeyRemap.KEY_CURRENCY, 99));
    }

    @Test
    public void onlyTheQ25SpareKeysAreOffered() {
        assertTrue(SableKeyRemap.isOffered(SableKeyRemap.KEY_SHIFT_RIGHT));
        assertFalse(SableKeyRemap.isOffered(62));
    }
}
