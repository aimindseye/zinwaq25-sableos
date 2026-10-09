package org.sableos.tools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityGateTest {
    private val allPresent = Hardware.entries.associateWith { Probe.PRESENT }

    @Test fun unknownProfileHidesEverythingEvenWhenHardwareReportsItself() {
        val res = CapabilityGate.resolveAll(ToolsDeviceProfile.Unknown, allPresent)
        res.values.forEach { assertEquals(it.capability.name, Visibility.HIDDEN, it.visibility) }
        assertTrue(res.values.none { CapabilityGate.listed(it, developerMode = true) })
    }

    @Test fun supportedNeedsRuntimeToAgree() {
        val p = ToolsDeviceProfile.Titan2
        val ok = CapabilityGate.resolve(p, allPresent, Capability.COMPASS)
        assertEquals(Visibility.VISIBLE, ok.visibility)
        val stale = CapabilityGate.resolve(
            p,
            allPresent + (Hardware.MAGNETOMETER to Probe.ABSENT),
            Capability.COMPASS
        )
        assertEquals(Visibility.HIDDEN, stale.visibility)
        assertTrue(stale.mismatch)
    }

    @Test fun diagnosticOnlyIsDeveloperModeOnly() {
        val r = CapabilityGate.resolve(
            ToolsDeviceProfile.ZinwaQ25,
            allPresent,
            Capability.BUBBLE_LEVEL
        )
        assertEquals(Visibility.DEVELOPER_ONLY, r.visibility)
        assertFalse(CapabilityGate.listed(r, developerMode = false))
        assertTrue(CapabilityGate.listed(r, developerMode = true))
        assertEquals(
            Badge.DEVELOPER_ONLY,
            CapabilityGate.badge(r, missingPermissions = false, calibrated = true)
        )
    }

    @Test fun q25HasNoIrRemoteOrSubscreenEvenInDeveloperMode() {
        val res = CapabilityGate.resolveAll(ToolsDeviceProfile.ZinwaQ25, allPresent)
        listOf(
            Capability.IR_REMOTE,
            Capability.IR_LEARNING,
            Capability.SUBSCREEN_COMPANION
        ).forEach {
            assertEquals(it.name, Visibility.HIDDEN, res.getValue(it).visibility)
            assertFalse(CapabilityGate.listed(res.getValue(it), developerMode = true))
        }
    }

    @Test fun profilesDoNotInheritEvidence() {
        listOf(ToolsDeviceProfile.Titan2Elite, ToolsDeviceProfile.ZinwaQ27).forEach { p ->
            Capability.entries.forEach {
                assertEquals("${p.id} ${it.name}", Declared.UNKNOWN, p.declaration(it).state)
            }
        }
    }

    @Test fun profileLookupIsByIdOnly() {
        assertEquals("zinwa-q25", ToolsDeviceProfile.byId("zinwa-q25").id)
        assertEquals("zinwa-q25", ToolsDeviceProfile.byId(" zinwa-q25 ").id)
        assertEquals("unknown", ToolsDeviceProfile.byId("Q25").id)
        assertEquals("unknown", ToolsDeviceProfile.byId(null).id)
    }

    @Test fun pedometerNeedsAnyOfStepCounterOrAccelerometer() {
        val c = Capability.PEDOMETER
        assertEquals(
            Probe.PRESENT,
            c.runtime(
                mapOf(
                    Hardware.STEP_COUNTER to Probe.ABSENT,
                    Hardware.ACCELEROMETER to Probe.PRESENT
                )
            )
        )
        assertEquals(
            Probe.ABSENT,
            c.runtime(
                mapOf(
                    Hardware.STEP_COUNTER to Probe.ABSENT,
                    Hardware.ACCELEROMETER to Probe.ABSENT
                )
            )
        )
        assertEquals(Probe.NOT_PROBED, c.runtime(emptyMap()))
    }

    @Test fun badgeOrderPermissionThenCalibration() {
        val r = CapabilityGate.resolve(ToolsDeviceProfile.Titan2, allPresent, Capability.COMPASS)
        assertEquals(
            Badge.NEEDS_PERMISSION,
            CapabilityGate.badge(r, missingPermissions = true, calibrated = false)
        )
        assertEquals(
            Badge.NEEDS_CALIBRATION,
            CapabilityGate.badge(r, missingPermissions = false, calibrated = false)
        )
        assertEquals(
            Badge.AVAILABLE,
            CapabilityGate.badge(r, missingPermissions = false, calibrated = true)
        )
        val flash = CapabilityGate.resolve(
            ToolsDeviceProfile.Titan2,
            allPresent,
            Capability.FLASHLIGHT
        )
        assertEquals(
            Badge.AVAILABLE,
            CapabilityGate.badge(flash, missingPermissions = false, calibrated = false)
        )
    }

    @Test fun developerModeNeedsBothSwitches() {
        assertFalse(DeveloperMode.enabled(systemDeveloperOptions = true, userOptIn = false))
        assertFalse(DeveloperMode.enabled(systemDeveloperOptions = false, userOptIn = true))
        assertTrue(DeveloperMode.enabled(systemDeveloperOptions = true, userOptIn = true))
    }

    @Test fun fmIsStatusOnlyAndOwnedByMedia() {
        val r = CapabilityGate.resolve(ToolsDeviceProfile.ZinwaQ25, allPresent, Capability.FM_RADIO)
        val s = FmStatus.of(r, mediaFmInstalled = true)
        assertEquals("PRESENT_NOT_PROVEN", s.state)
        assertFalse(s.handoff)
        assertTrue(s.detail.contains("owner Sable Media"))
        assertEquals(
            "UNKNOWN",
            FmStatus.of(
                CapabilityGate.resolve(ToolsDeviceProfile.Unknown, allPresent, Capability.FM_RADIO),
                true
            ).state
        )
        // No Utilities entry plays FM.
        assertTrue(Tool.entries.none { it.capability == Capability.FM_RADIO })
    }
}
