// Host tests for album type-to-jump (Glimpse patch 1021). Run by tests/run.sh.
package org.lineageos.glimpse.sable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class SableTypeAheadTest {

    private static final List<String> ALBUMS =
            Arrays.asList("Camera", "Été 2025", "Downloads", "camping", null, "Screenshots", "2024");

    @Test
    public void lettersBuildAPrefixWithinTheTimeout() {
        final SableTypeAhead t = new SableTypeAhead();
        assertEquals("c", t.type('C', 0));
        assertEquals("ca", t.type('a', 500));
        assertEquals("cam", t.type('m', 1400));
        assertFalse(t.isCycling());
        // A pause starts again.
        assertEquals("d", t.type('d', 1400 + SableTypeAhead.TIMEOUT_MS + 1));
    }

    @Test
    public void repeatingOneLetterCycles() {
        final SableTypeAhead t = new SableTypeAhead();
        assertEquals("c", t.type('c', 0));
        assertFalse(t.isCycling());
        assertEquals("c", t.type('c', 100));
        assertTrue(t.isCycling());
        // From "Camera" (0), cycling searches after it and lands on "camping" (3).
        assertEquals(3, SableTypeAhead.find(ALBUMS, "c", 0 + 1));
        // And wraps back to "Camera".
        assertEquals(0, SableTypeAhead.find(ALBUMS, "c", 3 + 1));
    }

    @Test
    public void caseAndAccentsAreIgnored() {
        assertEquals(1, SableTypeAhead.find(ALBUMS, SableTypeAhead.fold("ete"), 0));
        assertEquals(1, SableTypeAhead.find(ALBUMS, SableTypeAhead.fold("ÉT"), 0));
        assertEquals("ete 2025", SableTypeAhead.fold("Été 2025"));
    }

    @Test
    public void digitsJumpAndNullNamesNeverMatch() {
        final SableTypeAhead t = new SableTypeAhead();
        assertEquals(6, SableTypeAhead.find(ALBUMS, t.type('2', 0), 0));
        assertEquals(-1, SableTypeAhead.find(ALBUMS, "x", 0));
        assertEquals(-1, SableTypeAhead.find(ALBUMS, "", 0));
        assertEquals(-1, SableTypeAhead.find(Arrays.<String>asList(), "a", 0));
    }

    @Test
    public void otherCharactersResetAndAreNotTyped() {
        final SableTypeAhead t = new SableTypeAhead();
        t.type('c', 0);
        assertNull(t.type(' ', 10));
        assertNull(t.type('/', 20));
        assertEquals("d", t.type('d', 30));
    }

    @Test
    public void aClockGoingBackwardsStartsAgain() {
        final SableTypeAhead t = new SableTypeAhead();
        t.type('c', 5000);
        assertEquals("d", t.type('d', 100));
    }

    @Test
    public void startOutsideTheListWraps() {
        assertEquals(5, SableTypeAhead.find(ALBUMS, "s", 7));
        assertEquals(5, SableTypeAhead.find(ALBUMS, "s", -2));
    }
}
