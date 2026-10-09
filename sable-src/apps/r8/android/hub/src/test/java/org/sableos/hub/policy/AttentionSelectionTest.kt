package org.sableos.hub.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.hub.ConnectedAppKey
import org.sableos.hub.ConnectedAppPolicy
import org.sableos.hub.ConnectedAppPolicyCodec
import org.sableos.hub.HubPreviewPolicy

class AttentionSelectionTest {
    private val key = ConnectedAppKey("org.example.chat", 10L)
    private val facts = PlatformAttentionFacts(hasAudioOutput = true, hasVibrator = true)

    @Test
    fun codecRoundTripsPerProfileSelection() {
        val selection = AttentionSelection(key, setOf(AttentionOutput.KeyboardBacklight))
        assertEquals(selection, AttentionSelectionCodec.decode(AttentionSelectionCodec.encode(selection)))
        assertNull(AttentionSelectionCodec.decode("1|10|b3Jn|audio"))
        assertNull(AttentionSelectionCodec.decode("garbage"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun androidOwnedOutputsCannotBeSelected() {
        AttentionSelection(key, setOf(AttentionOutput.Haptic))
    }

    @Test
    fun selectionForAHiddenOutputIsInert() {
        val profile = AttentionDeviceProfile.parse("zinwa-q25", "zinwa-q25:keyboard_backlight=candidate", facts)
        val stored = mapOf(key to AttentionSelection(key, setOf(AttentionOutput.KeyboardBacklight)))
        assertTrue(AttentionSelections.effectiveFor(key, stored, profile).isEmpty())
    }

    @Test
    fun unconfiguredAppsGetTheConservativeDefault() {
        val profile =
            AttentionDeviceProfile.parse(
                "titan2",
                "titan2:keyboard_backlight=validated,secondary_display=validated",
                facts,
            )
        assertEquals(
            setOf(AttentionOutput.SecondaryDisplay),
            AttentionSelections.effectiveFor(key, emptyMap(), profile),
        )
    }

    @Test
    fun hubPolicyCodecV3CarriesPreviewPolicyAndReadsV2() {
        val policy = ConnectedAppPolicy(key, includeInMessages = true, previewPolicy = HubPreviewPolicy.SenderOnly)
        assertEquals(policy, ConnectedAppPolicyCodec.decode(ConnectedAppPolicyCodec.encode(policy)))
        val v2 = "2|10|b3JnLmV4YW1wbGUuY2hhdA|1|1|0|SevenDays|1"
        val decoded = ConnectedAppPolicyCodec.decode(v2)
        assertEquals(HubPreviewPolicy.ShowContent, decoded?.previewPolicy)
        assertEquals(true, decoded?.favorite)
    }
}
