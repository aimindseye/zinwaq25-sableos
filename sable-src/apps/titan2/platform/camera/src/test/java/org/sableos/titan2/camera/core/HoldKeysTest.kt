package org.sableos.titan2.camera.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldKeysTest {
    private val rear = CapabilityInterpreter.report(
        CameraInfo(
            "0",
            Facing.Back,
            jpegSizes = listOf(Size(4096, 3072)),
            minFocusDistance = 10f,
            aeLockAvailable = true,
            manualSensor = true,
            isoRange = IntBounds(100, 3200),
            exposureNsRange = LongBounds(100_000, 1_000_000_000)
        )
    )
    private val fixedFocusNoLock = CapabilityInterpreter.report(
        CameraInfo("1", Facing.Front, jpegSizes = listOf(Size(1280, 960)))
    )

    private fun ctx(ui: UiMode, cam: CameraReport = rear) = DeckContext(ui, cam, 2)

    // ---- bindings ------------------------------------------------------------------------------

    @Test fun holdFAndHoldELockWhileTheirTapsKeepTheirMeaning() {
        assertEquals(CameraAction.AfLockToggle, CameraKeyMap.resolve("f", false, hold = true))
        assertEquals(CameraAction.AeLockToggle, CameraKeyMap.resolve("e", false, hold = true))
        assertEquals(CameraAction.Autofocus, CameraKeyMap.resolve("f", false))
        assertEquals(CameraAction.ExposureUp, CameraKeyMap.resolve("e", false))
        assertTrue(CameraKeyMap.hasHold("f", false))
        assertTrue(CameraKeyMap.hasHold("E", false))
        // Alt+E keeps its plain, repeatable EV- with no hold meaning.
        assertFalse(CameraKeyMap.hasHold("e", true))
        assertFalse(CameraKeyMap.hasHold("space", false))
    }

    @Test fun legendShowsHeldBindings() {
        assertEquals("Hold F", ControlLegend.keyText(Binding("f", hold = true)))
        val e = ControlLegend.entries(ctx(UiMode.Photo), CameraDeviceProfile.ZinwaQ25)
        assertEquals("Hold F", e.first { it.action == CameraAction.AfLockToggle }.keys)
        assertEquals("Hold E", e.first { it.action == CameraAction.AeLockToggle }.keys)
        assertEquals("F", e.first { it.action == CameraAction.Autofocus }.keys)
    }

    @Test fun reviewKeyIsInTheLegend() {
        val e = ControlLegend.entries(ctx(UiMode.Photo), CameraDeviceProfile.ZinwaQ25)
        val g = e.first { it.action == CameraAction.OpenLast }
        assertEquals("G", g.keys)
        assertEquals("Review last capture", g.label)
    }

    // ---- gates ---------------------------------------------------------------------------------

    @Test fun locksAreOfferedOnlyWhenTheCameraBacksThemAndNotInPro() {
        for (ui in listOf(UiMode.Photo, UiMode.Video)) {
            assertTrue(ControlGate.check(CameraAction.AfLockToggle, ctx(ui)).isOpen)
            assertTrue(ControlGate.check(CameraAction.AeLockToggle, ctx(ui)).isOpen)
        }
        assertFalse(ControlGate.check(CameraAction.AfLockToggle, ctx(UiMode.Pro)).isOpen)
        assertFalse(ControlGate.check(CameraAction.AeLockToggle, ctx(UiMode.Pro)).isOpen)
        val front = ctx(UiMode.Photo, fixedFocusNoLock)
        assertEquals(
            Gate.Closed("Fixed focus"),
            ControlGate.check(CameraAction.AfLockToggle, front)
        )
        assertEquals(
            Gate.Closed("No AE lock on this camera"),
            ControlGate.check(CameraAction.AeLockToggle, front)
        )
    }

    @Test fun reviewIsNotOpenedWhileRecording() {
        assertTrue(ControlGate.check(CameraAction.OpenLast, ctx(UiMode.Video)).isOpen)
        val rec = DeckContext(UiMode.Video, rear, 2, recording = true)
        assertFalse(ControlGate.check(CameraAction.OpenLast, rec).isOpen)
    }

    @Test fun locksRouteToTheCameraUnchanged() {
        for (ui in UiMode.entries) {
            assertEquals(
                Cmd.Act(CameraAction.AfLockToggle),
                ActionRouter.route(ui, CameraAction.AfLockToggle)
            )
            assertEquals(
                Cmd.Act(CameraAction.AeLockToggle),
                ActionRouter.route(ui, CameraAction.AeLockToggle)
            )
        }
    }

    // ---- press versus hold -----------------------------------------------------------------

    @Test fun aQuickPressFiresOnReleaseOnly() {
        val t = HoldTracker()
        assertEquals(HoldStep.Wait, t.down("f", 0))
        assertTrue(t.up("f"))
        assertFalse(t.up("f")) // nothing left over
    }

    @Test fun aHoldFiresOnceAndSwallowsTheRelease() {
        val t = HoldTracker()
        assertEquals(HoldStep.Wait, t.down("e", 0))
        assertEquals(HoldStep.Hold, t.down("e", 1))
        assertEquals(HoldStep.Ignore, t.down("e", 2))
        assertEquals(HoldStep.Ignore, t.down("e", 3))
        assertFalse(t.up("e"))
    }

    @Test fun aRefusedHoldFallsBackToThePressOnRelease() {
        val t = HoldTracker()
        t.down("f", 0)
        assertEquals(HoldStep.Hold, t.down("f", 1))
        t.holdRefused("f")
        assertEquals(HoldStep.Ignore, t.down("f", 2)) // no repeated refusals
        assertTrue(t.up("f"))
    }

    @Test fun keysAreTrackedSeparatelyAndClearForgetsThem() {
        val t = HoldTracker()
        t.down("f", 0)
        t.down("e", 0)
        assertEquals(HoldStep.Hold, t.down("e", 1))
        assertTrue(t.up("f"))
        assertFalse(t.up("e"))
        t.down("f", 0)
        t.clear()
        assertFalse(t.up("f"))
        assertEquals(HoldStep.Ignore, t.down("f", 1)) // a repeat without its first press
    }

    // ---- Q25 volume shutter ----------------------------------------------------------------

    @Test fun q25VolumeKeysAreTheShutter() {
        val q25 = CameraDeviceProfile.ZinwaQ25
        assertEquals(CameraAction.Shutter, CameraKeyMap.resolve("volume_up", false, q25))
        assertEquals(CameraAction.Shutter, CameraKeyMap.resolve("volume_down", false, q25))
        assertNull(CameraKeyMap.resolve("func1", false, q25))
        val shutter = ControlLegend.entries(ctx(UiMode.Photo), q25)
            .first { it.action == CameraAction.Shutter }.keys
        assertTrue(shutter.contains("Vol+") && shutter.contains("Vol-"))
    }
}
