// Host test for the wipe after 10 failed unlock tries rules (patches/framework/frameworks/base/
// 0325, SableUnlockWipe). tests/run.sh extracts the class from the patch, compiles it with this
// file and runs the test with org.junit.Run.

package com.android.server.devicepolicy.sable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SableUnlockWipeTest {
    @Test
    public void offLeavesTheAdminLimit() {
        assertEquals(0, SableUnlockWipe.effectiveMax(0, true, false));
        assertEquals(15, SableUnlockWipe.effectiveMax(15, true, false));
    }

    @Test
    public void onCountsDownFromTenForTheMainUserOnly() {
        assertEquals(10, SableUnlockWipe.effectiveMax(0, true, true));
        assertEquals(10, SableUnlockWipe.effectiveMax(15, true, true));
        assertEquals(4, SableUnlockWipe.effectiveMax(4, true, true));
        assertEquals(0, SableUnlockWipe.effectiveMax(0, false, true));
    }

    @Test
    public void wipesOnTheTenthTry() {
        assertFalse(SableUnlockWipe.shouldWipe(9, false, true, true));
        assertTrue(SableUnlockWipe.shouldWipe(10, false, true, true));
        assertTrue(SableUnlockWipe.shouldWipe(11, false, true, true));
    }

    @Test
    public void neverWipesWhenOffForOtherUsersOrWhenTheAdminAlreadyWipes() {
        assertFalse(SableUnlockWipe.shouldWipe(10, false, true, false));
        assertFalse(SableUnlockWipe.shouldWipe(10, false, false, true));
        assertFalse(SableUnlockWipe.shouldWipe(10, true, true, true));
    }
}
