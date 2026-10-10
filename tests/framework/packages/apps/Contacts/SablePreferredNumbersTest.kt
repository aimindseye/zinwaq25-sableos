package com.android.contacts.sable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure tests for the bounded read-only subtitle number map (phone-contacts patches 0701/0751). */
class SablePreferredNumbersTest {
    @Test fun theDefaultNumberWinsThenPrimaryThenTheFirstRow() {
        val b = SablePreferredNumbers.Builder()
        b.offer(1, "111", "Home", false, false)
        b.offer(1, "222", "Work", false, true)
        b.offer(1, "333", "Mobile", true, true)
        b.offer(1, "444", "Other", false, true)
        b.offer(2, "555", "Home", false, false)
        b.offer(2, "666", "Work", false, false)
        val n = b.build()
        assertEquals("333", n.numberFor(1))
        assertEquals("Mobile", n.get(1).label)
        assertEquals("555", n.numberFor(2))
        assertNull(n.numberFor(3))
        assertEquals(2, n.size())
        assertFalse(n.isTruncated())
    }

    @Test fun blankAndOverlongNumbersAreSkipped() {
        val b = SablePreferredNumbers.Builder()
        b.offer(1, "   ", null, true, true)
        b.offer(1, "-- ()", null, true, true)
        b.offer(1, "  +1  555\t0101 ", null, false, false)
        b.offer(2, "9".repeat(SablePreferredNumbers.MAX_NUMBER_LENGTH + 1), null, true, true)
        b.offer(3, null, null, true, true)
        val n = b.build()
        assertEquals("+1 555 0101", n.numberFor(1))
        assertEquals("", n.get(1).label)
        assertNull(n.numberFor(2))
        assertNull(n.numberFor(3))
    }

    @Test fun theContactBoundCapsTheMapAndIsReported() {
        val b = SablePreferredNumbers.Builder(2, 100)
        assertTrue(b.offer(1, "1", null, false, false))
        assertTrue(b.offer(2, "2", null, false, false))
        assertTrue(b.offer(3, "3", null, false, false))
        assertTrue(b.offer(1, "9", null, true, false))
        val n = b.build()
        assertEquals(2, n.size())
        assertNull(n.numberFor(3))
        assertEquals("9", n.numberFor(1))
        assertTrue(n.isTruncated())
    }

    @Test fun theRowBoundStopsReading() {
        val b = SablePreferredNumbers.Builder(100, 2)
        assertTrue(b.offer(1, "1", null, false, false))
        assertTrue(b.offer(2, "2", null, false, false))
        assertFalse(b.offer(3, "3", null, false, false))
        val n = b.build()
        assertEquals(2, n.size())
        assertTrue(n.isTruncated())
    }

    @Test fun defaultsAreBoundedAndEmptyIsShared() {
        assertEquals(5000, SablePreferredNumbers.MAX_CONTACTS)
        assertEquals(20000, SablePreferredNumbers.MAX_ROWS)
        assertTrue(SablePreferredNumbers.Builder().build() === SablePreferredNumbers.empty())
        assertEquals(0, SablePreferredNumbers.empty().size())
    }

    @Test fun aSnapshotDoesNotChangeWhenTheBuilderGoesOn() {
        val b = SablePreferredNumbers.Builder()
        b.offer(1, "1", null, false, false)
        val first = b.build()
        b.offer(2, "2", null, false, false)
        assertEquals(1, first.size())
        assertEquals(2, b.build().size())
    }
}
