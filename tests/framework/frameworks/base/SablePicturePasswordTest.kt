package org.sableos.test.frameworks.base

import com.android.keyguard.sable.SablePicturePassword
import com.android.keyguard.sable.SablePicturePassword.Attempt
import com.android.keyguard.sable.SablePicturePassword.Number
import com.android.keyguard.sable.SablePicturePassword.Record
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class SablePicturePasswordTest {
    private val seven = Number(7, 4, 6)

    /** Moves the grid until [n]'s digit sits on its spot, as a user would with the trackpad. */
    private fun solve(a: Attempt, n: Number) {
        repeat(10) { dc ->
            if (a.digitAt(n.row, n.col) == n.digit) return
            a.move(0, 1)
        }
        repeat(10) {
            if (a.digitAt(n.row, n.col) == n.digit) return
            a.move(1, 0)
        }
        error("never reached")
    }

    @Test
    fun theDigitOnTheSpotUnlocksAndAnyOtherDoesNot() {
        val record = SablePicturePassword.enrol(10, 0, arrayOf(seven), Random(1))
        val a = Attempt.start(10, Random(2))
        solve(a, seven)
        assertTrue(SablePicturePassword.check(record, 0, a))
        a.move(0, 1)
        assertFalse(SablePicturePassword.check(record, 0, a))
    }

    @Test
    fun everyDigitCanReachEverySpotByMovingTheGrid() {
        val a = Attempt.start(12, Random(3))
        for (d in 0..9) for (r in 0 until 12) for (c in 0 until 12) solve(a, Number(d, r, c))
    }

    @Test
    fun eachRoundStartsDifferentlySoNoTwoUnlocksMoveAlike() {
        val starts = (1..20).map { seed ->
            val a = Attempt.start(10, Random(seed.toLong()))
            (0 until 10).joinToString("") { a.digitAt(0, it).toString() }
        }.toSet()
        assertTrue(starts.size > 15)
    }

    @Test
    fun oneCellEitherWayIsAcceptedOnlyWhenChosen() {
        val loose = SablePicturePassword.enrol(10, 1, arrayOf(seven), Random(4))
        val exact = SablePicturePassword.enrol(10, 0, arrayOf(seven), Random(4))
        val a = Attempt.start(10, Random(5))
        solve(a, Number(7, 4, 7)) // one cell to the right
        assertTrue(SablePicturePassword.check(loose, 0, a))
        assertFalse(SablePicturePassword.check(exact, 0, a))
    }

    @Test
    fun theStrongerSecondNumberIsItsOwnRound() {
        val second = Number(2, 1, 1)
        val record = SablePicturePassword.enrol(10, 0, arrayOf(seven, second), Random(6))
        assertEquals(2, record.numbers())
        val round1 = Attempt.start(10, Random(7)).also { solve(it, seven) }
        val round2 = Attempt.start(10, Random(8)).also { solve(it, second) }
        assertTrue(SablePicturePassword.check(record, 0, round1))
        assertTrue(SablePicturePassword.check(record, 1, round2))
        // The first number on the second round's spot fails.
        val wrong = Attempt.start(10, Random(9)).also { solve(it, seven) }
        assertFalse(SablePicturePassword.check(record, 1, wrong))
    }

    @Test
    fun theRecordHoldsNoDigitOrSpotInClearAndRoundTrips() {
        val record = SablePicturePassword.enrol(9, 0, arrayOf(seven), Random(10))
        val stored = record.encode()
        assertTrue(stored.startsWith("pp1:9:0:"))
        assertFalse(stored.contains("|7|"))
        val back = Record.decode(stored)!!
        val a = Attempt.start(9, Random(11)).also { solve(it, seven) }
        assertTrue(SablePicturePassword.check(back, 0, a))
        assertNull(Record.decode("pp1:20:0:a\$b"))
        assertNull(Record.decode("pp0:9:0:a\$b"))
        assertNull(Record.decode(null))
        assertNotEquals(
            SablePicturePassword.enrol(9, 0, arrayOf(seven), Random(12)).encode(),
            SablePicturePassword.enrol(9, 0, arrayOf(seven), Random(13)).encode(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun theSameNumberTwiceIsRefused() {
        SablePicturePassword.enrol(10, 0, arrayOf(seven, seven), Random(0).let { FixedSalt() })
    }

    private class FixedSalt : Random() {
        override fun nextLong() = 42L
    }

    @Test
    fun thePinTakesOverAfterRebootTimeoutOrFiveWrongPictures() {
        assertTrue(SablePicturePassword.allowed(true, true, 0))
        assertTrue(SablePicturePassword.allowed(true, true, 4))
        assertFalse(SablePicturePassword.allowed(true, true, 5))
        assertFalse(SablePicturePassword.allowed(true, false, 0)) // after reboot or 72 h
        assertFalse(SablePicturePassword.allowed(false, true, 0))
    }

    @Test
    fun strengthGrowsWithTheSecondNumberAndShrinksWithTolerance() {
        assertEquals(1000L, SablePicturePassword.combinations(10, 0, 1))
        assertEquals(1_000_000L, SablePicturePassword.combinations(10, 0, 2))
        assertTrue(SablePicturePassword.combinations(10, 1, 1) < 1000L)
        assertEquals(8, SablePicturePassword.clampSize(3))
        assertEquals(12, SablePicturePassword.clampSize(40))
    }
}
