// Host test for the Recents key hold rule (patches/framework/frameworks/base/0326,
// SableCommandKeys.isHubPeekAllowed). tests/run.sh extracts SableCommandKeys from the frameworks/base
// patches, compiles it with this file and runs the test with org.junit.Run.

package com.android.server.policy.sable;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SableHubPeekKeysTest {
    @Test
    public void holdOpensTheHubOverAnAppAfterSetup() {
        assertTrue(SableCommandKeys.isHubPeekAllowed(true, false, true));
    }

    @Test
    public void neverOnTheKeyguardBeforeSetupOrWithoutTheHome() {
        assertFalse(SableCommandKeys.isHubPeekAllowed(true, true, true));
        assertFalse(SableCommandKeys.isHubPeekAllowed(true, false, false));
        assertFalse(SableCommandKeys.isHubPeekAllowed(false, false, true));
    }
}
