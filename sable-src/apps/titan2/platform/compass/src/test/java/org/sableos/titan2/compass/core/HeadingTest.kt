package org.sableos.titan2.compass.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadingTest {
    private val delta = 1e-6

    /** Row-major device-to-world matrix from the device's right edge, top edge and screen normal in world terms. */
    private fun matrix(right: DoubleArray, top: DoubleArray, screen: DoubleArray) =
        FloatArray(9) { i ->
            val column = listOf(right, top, screen)[i % 3]
            column[i / 3].toFloat()
        }

    private val flatNorth =
        matrix(
            doubleArrayOf(1.0, 0.0, 0.0),
            doubleArrayOf(0.0, 1.0, 0.0),
            doubleArrayOf(0.0, 0.0, 1.0)
        )

    @Test
    fun flatThePhonesTopEdgeIsTheHeading() {
        assertEquals(0.0, Heading.facing(flatNorth)!!, delta)
        val flatEast =
            matrix(
                doubleArrayOf(0.0, -1.0, 0.0),
                doubleArrayOf(1.0, 0.0, 0.0),
                doubleArrayOf(0.0, 0.0, 1.0)
            )
        assertEquals(90.0, Heading.facing(flatEast)!!, delta)
        assertEquals(0.0, Heading.tilt(flatNorth), delta)
        assertEquals(1.0, Heading.squash(0.0), delta)
    }

    @Test
    fun uprightTheBackOfThePhoneIsTheHeading() {
        // Standing up, screen towards the user, back looking east.
        val uprightEast =
            matrix(
                doubleArrayOf(0.0, -1.0, 0.0),
                doubleArrayOf(0.0, 0.0, 1.0),
                doubleArrayOf(-1.0, 0.0, 0.0)
            )
        assertEquals(90.0, Heading.facing(uprightEast)!!, delta)
        assertEquals(90.0, Heading.tilt(uprightEast), delta)
        assertEquals(Heading.MIN_SQUASH, Heading.squash(90.0), delta)
    }

    @Test
    fun tiltingInBetweenKeepsTheSameHeading() {
        for (deg in listOf(10.0, 45.0, 80.0)) {
            val a = Math.toRadians(deg)
            val m =
                matrix(
                    doubleArrayOf(1.0, 0.0, 0.0),
                    doubleArrayOf(0.0, Math.cos(a), Math.sin(a)),
                    doubleArrayOf(0.0, -Math.sin(a), Math.cos(a))
                )
            assertEquals(0.0, Heading.facing(m)!!, delta)
            assertEquals(deg, Heading.tilt(m), 1e-4)
        }
        assertEquals(Math.cos(Math.toRadians(30.0)), Heading.squash(150.0), delta)
    }

    @Test
    fun anEdgeOnPhoneHasNoHeading() {
        // Top edge straight down, screen facing up-turned... both horizontal parts cancel.
        val m =
            matrix(
                doubleArrayOf(1.0, 0.0, 0.0),
                doubleArrayOf(0.0, 0.0, -1.0),
                doubleArrayOf(0.0, 0.0, 0.0)
            )
        assertNull(Heading.facing(m))
    }

    @Test
    fun smoothingGoesTheShortWayRound() {
        assertEquals(1.0, Heading.smooth(359.0, 3.0, 0.5), delta)
        assertEquals(358.0, Heading.smooth(2.0, 354.0, 0.5), delta)
        assertEquals(10.0, Heading.smooth(null, 370.0, 0.1), delta)
        assertEquals(-2.0, Heading.turn(1.0, 359.0), delta)
        assertTrue(Heading.moved(350.0, 10.0, 15.0))
        assertFalse(Heading.moved(355.0, 5.0, 15.0))
    }

    @Test
    fun wordsDegreesAndTrueNorth() {
        assertEquals("n", Heading.word(359.0))
        assertEquals("n", Heading.word(22.0))
        assertEquals("ne", Heading.word(23.0))
        assertEquals("w", Heading.word(270.0))
        assertEquals("nw", Heading.word(-40.0))
        assertEquals(0, Heading.degrees(359.6))
        assertEquals(45, Heading.degrees(45.2))
        assertEquals(355.0, Heading.toTrue(5.0, -10.0), delta)
        assertEquals(12.5, Heading.toTrue(10.0, 2.5), delta)
    }

    @Test
    fun accuracyBelowMediumAsksForCalibration() {
        assertTrue(Accuracy.of(0).needsCalibration)
        assertTrue(Accuracy.of(1).needsCalibration)
        assertFalse(Accuracy.of(2).needsCalibration)
        assertEquals(Accuracy.HIGH, Accuracy.of(3))
        assertEquals(Accuracy.UNRELIABLE, Accuracy.of(-1))
    }

    @Test
    fun theFigure8StaysInsideItsBox() {
        for (i in 0..100) {
            val (x, y) = Figure8.point(i / 100.0)
            assertTrue(x in -1.0..1.0 && y in -1.0..1.0)
        }
        assertEquals(1.0, Figure8.point(0.0).first, delta)
        assertEquals(-1.0, Figure8.point(0.5).first, delta)
    }
}
