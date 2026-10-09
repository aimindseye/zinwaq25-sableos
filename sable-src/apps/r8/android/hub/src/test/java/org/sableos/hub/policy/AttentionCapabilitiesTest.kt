package org.sableos.hub.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ATTENTION_CAPABILITY_GATING=PASS. */
class AttentionCapabilitiesTest {
    private val phone = PlatformAttentionFacts(hasAudioOutput = true, hasVibrator = true)

    @Test
    fun missingDeclarationFailsClosedToAndroidAudioAndHaptic() {
        val profile = AttentionDeviceProfile.parse("zinwa-q25", null, phone)
        assertEquals(listOf(AttentionOutput.Audio, AttentionOutput.Haptic), profile.visibleOutputs())
        assertTrue(profile.selectableOutputs().isEmpty())
    }

    @Test
    fun noVibratorHidesHaptic() {
        val profile = AttentionDeviceProfile.parse("x", null, phone.copy(hasVibrator = false))
        assertFalse(profile.isSupported(AttentionOutput.Haptic))
    }

    @Test
    fun validatedSableOutputsBecomeSelectable() {
        val profile =
            AttentionDeviceProfile.parse(
                "titan2",
                "titan2:secondary_display=validated,keyboard_backlight=validated,aod=absent",
                phone,
            )
        assertEquals(
            listOf(AttentionOutput.KeyboardBacklight, AttentionOutput.SecondaryDisplay),
            profile.selectableOutputs(),
        )
        assertFalse(profile.isSupported(AttentionOutput.AlwaysOnDisplay))
    }

    @Test
    fun candidatesAreHiddenNotShownDisabled() {
        val profile = AttentionDeviceProfile.parse("titan2-elite", "titan2-elite:aod=candidate", phone)
        assertEquals(CapabilityEvidence.Candidate, profile.evidenceFor(AttentionOutput.AlwaysOnDisplay))
        assertFalse(AttentionOutput.AlwaysOnDisplay in profile.visibleOutputs())
    }

    @Test
    fun deviceFamiliesDoNotInheritEachOthersPass() {
        // A Titan 2 declaration on an Elite build (or any other profile) is ignored entirely.
        val profile = AttentionDeviceProfile.parse("titan2-elite", "titan2:secondary_display=validated", phone)
        assertFalse(profile.isSupported(AttentionOutput.SecondaryDisplay))
        val q27 = AttentionDeviceProfile.parse("zinwa-q27", "zinwa-q25:status_led=validated", phone)
        assertFalse(q27.isSupported(AttentionOutput.StatusLed))
    }

    @Test
    fun malformedDeclarationFailsClosed() {
        listOf(
            "titan2:secondary_display=validated,bogus=validated",
            "titan2:secondary_display",
            "titan2:secondary_display=yes",
            ":secondary_display=validated",
            "titan2",
            "titan2:" + "keyboard_backlight=validated,".repeat(40),
        ).forEach { declaration ->
            val profile = AttentionDeviceProfile.parse("titan2", declaration, phone)
            assertTrue(declaration, profile.selectableOutputs().isEmpty())
        }
    }

    @Test
    fun declarationCannotClaimOrRemoveAndroidAudioAndHaptic() {
        val profile =
            AttentionDeviceProfile.parse(
                "p",
                "p:audio=absent,haptic=validated",
                phone.copy(hasVibrator = false),
            )
        assertTrue(profile.isSupported(AttentionOutput.Audio))
        assertFalse(profile.isSupported(AttentionOutput.Haptic))
    }

    @Test
    fun unsafeProfileIdsAreIgnored() {
        val profile = AttentionDeviceProfile.parse("bad id!", "bad id!:status_led=validated", phone)
        assertFalse(profile.isSupported(AttentionOutput.StatusLed))
    }

    @Test
    fun conservativeDefaults() {
        assertEquals(AttentionDefault.Off, AttentionDefaults.defaultFor(AttentionOutput.KeyboardBacklight))
        assertEquals(
            AttentionDefault.ProfileAndPrivacyGated,
            AttentionDefaults.defaultFor(AttentionOutput.SecondaryDisplay),
        )
        assertEquals(AttentionDefault.PlatformDefault, AttentionDefaults.defaultFor(AttentionOutput.StatusLed))
        assertEquals(AttentionDefault.PlatformDefault, AttentionDefaults.defaultFor(AttentionOutput.AlwaysOnDisplay))

        val both =
            AttentionDeviceProfile.parse(
                "titan2",
                "titan2:keyboard_backlight=validated,secondary_display=validated",
                phone,
            )
        assertEquals(setOf(AttentionOutput.SecondaryDisplay), AttentionDefaults.defaultSelection(both))
        val none = AttentionDeviceProfile.parse("zinwa-q25", "zinwa-q25:keyboard_backlight=candidate", phone)
        assertTrue(AttentionDefaults.defaultSelection(none).isEmpty())
    }
}
