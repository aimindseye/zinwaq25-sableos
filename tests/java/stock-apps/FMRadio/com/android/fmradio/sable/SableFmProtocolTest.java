// Host tests for the Sable FM bridge protocol (FMRadio patch 1061). Run by tests/run.sh.
package com.android.fmradio.sable;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SableFmProtocolTest {
    private static final int ME = 1000;
    private static final int SYSTEM = 1000;
    private static final int MEDIA = 10123;

    @Test
    public void onlySableMediaOnTheSystemImageIsObeyed() {
        String[] media = { "org.sableos.media" };
        assertTrue(SableFmProtocol.callerAllowed(MEDIA, ME, SYSTEM, media, new boolean[] { true }));
        // A user-installed app (no FLAG_SYSTEM) is refused even with the right name.
        assertFalse(SableFmProtocol.callerAllowed(MEDIA, ME, SYSTEM, media, new boolean[] { false }));
        assertFalse(SableFmProtocol.callerAllowed(MEDIA, ME, SYSTEM,
                new String[] { "com.example.radio" }, new boolean[] { true }));
        assertFalse(SableFmProtocol.callerAllowed(MEDIA, ME, SYSTEM, null, null));
        // Message.sendingUid is -1 for a message that did not come through a Messenger.
        assertFalse(SableFmProtocol.callerAllowed(-1, ME, SYSTEM, media, new boolean[] { true }));
        assertTrue(SableFmProtocol.callerAllowed(SYSTEM, ME, SYSTEM, null, null));
    }

    @Test
    public void commandsAndBand() {
        assertTrue(SableFmProtocol.isKnownCommand(SableFmProtocol.MSG_REGISTER));
        assertTrue(SableFmProtocol.isKnownCommand(SableFmProtocol.MSG_GET_STATE));
        assertFalse(SableFmProtocol.isKnownCommand(SableFmProtocol.MSG_STATE));
        assertFalse(SableFmProtocol.isKnownCommand(0));
        assertTrue(SableFmProtocol.isValidStation(875));
        assertTrue(SableFmProtocol.isValidStation(1080));
        assertFalse(SableFmProtocol.isValidStation(874));
        assertFalse(SableFmProtocol.isValidStation(0));
    }
}
