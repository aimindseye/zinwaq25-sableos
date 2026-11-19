package io.github.aimindseye.tenseven.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SableDialTest {
    @Test
    fun threeOrMoreDigitsAreANumber() {
        assertTrue(SableDial.isNumber("555"))
        assertTrue(SableDial.isNumber("+15551234"))
        assertTrue(SableDial.isNumber("*123#"))
    }

    @Test
    fun shortOrLetterQueriesAreSearches() {
        assertFalse(SableDial.isNumber(""))
        assertFalse(SableDial.isNumber("12"))
        assertFalse(SableDial.isNumber("5a55"))
        assertFalse(SableDial.isNumber("1 2 3"))
    }
}
