package org.sableos.design

import org.junit.Assert.assertEquals
import org.junit.Test

/** A profile's stored value round-trips, and a missing or unknown value is the default BB10 profile. */
class SableProfileTest {
    @Test
    fun storedValuesRoundTrip() {
        SableProfile.entries.forEach { assertEquals(it, SableProfile.fromStableValue(it.stableValue)) }
    }

    @Test
    fun missingOrUnknownIsBb10() {
        assertEquals(SableProfile.BB10, SableProfile.fromStableValue(null))
        assertEquals(SableProfile.BB10, SableProfile.fromStableValue(""))
        assertEquals(SableProfile.BB10, SableProfile.fromStableValue("bb5"))
    }

    @Test
    fun settingValuesMatchTheLauncherAndSetup() {
        assertEquals("sable_profile", SableProfile.SETTING_KEY)
        assertEquals(listOf("bb10", "bb7"), SableProfile.entries.map { it.stableValue })
    }
}
