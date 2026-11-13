package org.sableos.textreader.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyDarknessTest {
    @Test
    fun `a missing or unknown choice is the Sable 2_0 darkness`() {
        assertEquals(PrivacyDarkness.Level.Dark, PrivacyDarkness.parse(null))
        assertEquals(PrivacyDarkness.Level.Dark, PrivacyDarkness.parse("Darker"))
        assertEquals(240, PrivacyDarkness.parse(null).alpha)
    }

    @Test
    fun `each level is darker than the one before and Black hides everything`() {
        val alphas = PrivacyDarkness.Level.entries.map { it.alpha }
        assertTrue(alphas.zipWithNext().all { (a, b) -> a < b })
        assertEquals(255, PrivacyDarkness.Level.Black.alpha)
        PrivacyDarkness.Level.entries.forEach { assertEquals(it, PrivacyDarkness.parse(it.name)) }
    }
}
