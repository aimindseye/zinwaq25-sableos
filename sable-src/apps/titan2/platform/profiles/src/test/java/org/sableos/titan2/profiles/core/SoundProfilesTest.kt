package org.sableos.titan2.profiles.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundProfilesTest {
    @Test
    fun eachProfileSetsWhatTheBlackBerryOneDid() {
        val normal = SoundProfiles.plan(SoundProfile.NORMAL)
        assertEquals(Ringer.NORMAL, normal.ringer)
        assertEquals(Dnd.OFF, normal.dnd)
        assertEquals(SoundProfiles.NORMAL_VOLUME, normal.volumePercent)
        assertEquals(100, SoundProfiles.plan(SoundProfile.LOUD).volumePercent)
        val vibrate = SoundProfiles.plan(SoundProfile.VIBRATE_ONLY)
        assertEquals(Ringer.VIBRATE, vibrate.ringer)
        assertNull("no volume to set", vibrate.volumePercent)
        val calls = SoundProfiles.plan(SoundProfile.PHONE_CALLS_ONLY)
        assertEquals(Dnd.CALLS_ONLY, calls.dnd)
        assertEquals(Ringer.NORMAL, calls.ringer)
        val off = SoundProfiles.plan(SoundProfile.ALL_ALERTS_OFF)
        assertEquals(Ringer.SILENT, off.ringer)
        assertEquals(Dnd.ALARMS_ONLY, off.dnd)
        assertFalse(off.vibrateWhenRinging)
        assertFalse(off.light)
    }

    @Test
    fun theUsersSettingsWinWhereTheyMakeSense() {
        val s =
            ProfileSettings(
                volumePercent = 140,
                vibrate = false,
                light = false,
                ringtone = "content://r/1"
            )
        val loud = SoundProfiles.plan(SoundProfile.LOUD, s)
        assertEquals("clamped", 100, loud.volumePercent)
        assertFalse(loud.vibrateWhenRinging)
        assertFalse(loud.light)
        assertEquals("content://r/1", loud.ringtone)
        assertNull(
            "vibrate only has no volume",
            SoundProfiles.plan(SoundProfile.VIBRATE_ONLY, s).volumePercent
        )
        val off = SoundProfiles.plan(SoundProfile.ALL_ALERTS_OFF, ProfileSettings(vibrate = true))
        assertFalse("all alerts off never vibrates", off.vibrateWhenRinging)
    }

    @Test
    fun settingsRoundTrip() {
        val s =
            ProfileSettings(40, true, false, "content://media/ring/5", "content://media/notif/2")
        assertEquals(s, SoundProfiles.parse(SoundProfiles.format(s)))
        assertEquals(ProfileSettings(), SoundProfiles.parse(null))
        assertEquals(
            ProfileSettings(volumePercent = 100),
            SoundProfiles.parse("volume=250\nbogus\nlight=maybe")
        )
    }

    @Test
    fun profilesStepAndResolveByName() {
        assertEquals(SoundProfile.NORMAL, SoundProfiles.next(null))
        assertEquals(SoundProfile.LOUD, SoundProfiles.next(SoundProfile.NORMAL))
        assertEquals(SoundProfile.NORMAL, SoundProfiles.next(SoundProfile.ALL_ALERTS_OFF))
        assertEquals(SoundProfile.PHONE_CALLS_ONLY, SoundProfile.fromName("PHONE_CALLS_ONLY"))
        assertNull(SoundProfile.fromName("nope"))
        assertTrue(SoundProfile.entries.size == 5)
    }
}
