// Minimal stand-in for org.junit.Assert (see Test.java).
package org.junit;

import java.util.Objects;

public final class Assert {
    private Assert() {}

    public static void assertEquals(Object expected, Object actual) {
        assertEquals(null, expected, actual);
    }

    public static void assertEquals(String message, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError((message == null ? "" : message + ": ")
                    + "expected <" + expected + "> got <" + actual + ">");
        }
    }

    public static void assertTrue(boolean condition) {
        assertTrue("expected true", condition);
    }

    public static void assertTrue(String message, boolean condition) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void assertFalse(boolean condition) {
        assertTrue("expected false", !condition);
    }

    public static void assertFalse(String message, boolean condition) {
        assertTrue(message, !condition);
    }

    public static void assertNull(Object actual) {
        assertTrue("expected null, got " + actual, actual == null);
    }

    public static void assertNotNull(Object actual) {
        assertTrue("expected non-null", actual != null);
    }
}
