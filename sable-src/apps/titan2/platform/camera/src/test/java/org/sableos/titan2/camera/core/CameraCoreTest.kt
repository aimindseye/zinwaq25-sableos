package org.sableos.titan2.camera.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCoreTest {
    // Mirrors the researched Titan 2 public path: rear 0 with high-res JPEG + RAW, front 1 with
    // high-res JPEG.
    // Real Titan 2 shape (unihertz-titan2 CAMERA_RESEARCH): the 8192x6144 JPEG is in the ORDINARY
    // jpeg map, above the 4096x3072 active array.
    private val rear =
        CameraInfo(
            "0", Facing.Back,
            activeArray = Size(
                4096,
                3072
            ),
            jpegSizes = listOf(Size(4096, 3072), Size(1920, 1080), Size(8192, 6144)),
            rawSizes = listOf(Size(4096, 3072)), hasRaw = true, manualSensor = true,
            isoRange = IntBounds(
                100,
                3200
            ),
            exposureNsRange = LongBounds(
                100_000,
                1_000_000_000
            ),
            zoomRatio = FloatRange(1f, 10f), minFocusDistance = 10f
        )
    private val front =
        CameraInfo(
            "1",
            Facing.Front,
            activeArray = Size(3280, 2464),
            jpegSizes = listOf(Size(3264, 2448), Size(1280, 960), Size(6560, 4928)),
            zoomRatio = FloatRange(1f, 1f)
        )

    @Test fun modesComeFromWhatTheDeviceReports() {
        val r = CapabilityInterpreter.report(rear)
        assertTrue(r.modes.getValue(CaptureMode.Auto).ok)
        assertTrue(r.modes.getValue(CaptureMode.HighRes).ok)
        assertTrue(r.modes.getValue(CaptureMode.Raw).ok)
        assertTrue(r.modes.getValue(CaptureMode.Pro).ok)
        assertEquals(Size(8192, 6144), r.bestHighRes)
        assertEquals(Size(4096, 3072), r.bestRaw)
        assertEquals(Size(4096, 3072), r.bestJpeg) // Auto must NOT default to the 50 MP path
        val f = CapabilityInterpreter.report(front)
        assertFalse(f.modes.getValue(CaptureMode.Raw).ok)
        assertFalse(f.modes.getValue(CaptureMode.Pro).ok)
        assertTrue(f.modes.getValue(CaptureMode.HighRes).ok)
    }

    @Test fun teleIsNeverAvailableFromOrdinaryPath() {
        val m = CapabilityInterpreter.report(
            rear.copy(physicalIdCount = 2)
        ).modes.getValue(CaptureMode.Tele)
        assertTrue(m is ModeAvailability.Unavailable && m.reason.contains("SYSTEM_CAMERA"))
    }

    @Test fun noSizeAboveActiveArrayMeansNoHighRes() {
        val r = CapabilityInterpreter.report(
            rear.copy(jpegSizes = listOf(Size(4096, 3072), Size(1920, 1080)))
        )
        assertNull(r.bestHighRes)
        assertFalse(r.modes.getValue(CaptureMode.HighRes).ok)
    }

    @Test fun frontHighResIsAboveItsActiveArray() {
        val r = CapabilityInterpreter.report(front)
        assertEquals(Size(3264, 2448), r.bestJpeg)
        assertEquals(Size(6560, 4928), r.bestHighRes)
    }

    @Test fun withoutActiveArrayFallsBackToExplicitHighResLists() {
        val cam = rear.copy(
            activeArray = null,
            jpegSizes = listOf(Size(4096, 3072)),
            jpegHighResSizes = listOf(Size(8192, 6144))
        )
        val r = CapabilityInterpreter.report(cam)
        assertEquals(Size(4096, 3072), r.bestJpeg)
        assertEquals(Size(8192, 6144), r.bestHighRes)
    }

    @Test fun rawFlagWithoutSizesIsUnavailable() {
        assertFalse(
            CapabilityInterpreter.report(
                rear.copy(rawSizes = emptyList())
            ).modes.getValue(CaptureMode.Raw).ok
        )
        assertFalse(
            CapabilityInterpreter.report(
                rear.copy(hasRaw = false)
            ).modes.getValue(CaptureMode.Raw).ok
        )
    }

    @Test fun titan2ProfileValidatesMatchingDevice() {
        val rep = CapabilityInterpreter.interpret(listOf(rear, front), CameraDeviceProfile.Titan2)
        assertTrue(rep.mismatches.none { it.kind != MismatchKind.Note })
        assertTrue(
            rep.mismatches.any {
                it.kind == MismatchKind.Note
            }
        ) // research-only banner is always shown
        assertEquals("titan2", rep.profileId)
    }

    @Test fun titan2ProfileFlagsMissingAndExtraCameras() {
        val rep = CapabilityInterpreter.interpret(
            listOf(rear.copy(rawSizes = listOf(Size(1000, 1000)))),
            CameraDeviceProfile.Titan2
        )
        val kinds = rep.mismatches.map { it.kind }
        assertTrue(MismatchKind.MissingCamera in kinds)
        assertTrue(MismatchKind.MissingRaw in kinds)
        assertTrue(MismatchKind.UnexpectedCameraCount in kinds)
    }

    @Test fun eliteAndQ27InheritNothingFromTitan2() {
        val rep = CapabilityInterpreter.interpret(
            listOf(rear, front),
            CameraDeviceProfile.Titan2Elite
        )
        assertNull(rep.profileId)
        assertEquals(listOf(MismatchKind.Note), rep.mismatches.map { it.kind })
        assertTrue(
            CameraDeviceProfile.Titan2Elite.expectedHighResJpeg.isEmpty() &&
                CameraDeviceProfile.ZinwaQ27.expectedRaw.isEmpty()
        )
        assertEquals("unknown", CameraDeviceProfile.byId("bogus").id)
    }

    @Test fun zoomPlannerStepsAndClamps() {
        val range = FloatRange(1f, 10f)
        assertEquals(listOf(1f, 2f, 3f, 5f, 8f, 10f), ZoomPlanner.steps(range))
        assertEquals(2f, ZoomPlanner.step(1f, 1, range))
        assertEquals(5f, ZoomPlanner.step(3f, 1, range))
        assertEquals(10f, ZoomPlanner.step(10f, 1, range))
        assertEquals(1f, ZoomPlanner.step(1f, -1, range))
        assertEquals(3f, ZoomPlanner.step(5f, -1, range))
        assertEquals(listOf(1f), ZoomPlanner.steps(null))
        assertEquals(1f, ZoomPlanner.step(1f, 1, FloatRange(1f, 1f)))
        assertEquals(10f, ZoomPlanner.clamp(30f, range))
        assertEquals(1f, ZoomPlanner.clamp(0.2f, range))
    }

    @Test fun capturePlansFallBackToAutoOnSameCamera() {
        val rep = CapabilityInterpreter.report(front)
        val p = CapturePlanner.single(rep, CaptureMode.Raw)
        assertEquals(StillKind.Jpeg, p.kind)
        assertEquals("1", p.cameraId)
        assertEquals(
            StillKind.HighResJpeg,
            CapturePlanner.single(CapabilityInterpreter.report(rear), CaptureMode.HighRes).kind
        )
        assertEquals(
            StillKind.RawDng,
            CapturePlanner.single(CapabilityInterpreter.report(rear), CaptureMode.Raw).kind
        )
    }

    @Test fun bracketCoversEveryAvailablePath() {
        assertEquals(
            listOf(StillKind.Jpeg, StillKind.HighResJpeg, StillKind.RawDng),
            CapturePlanner.bracket(CapabilityInterpreter.report(rear)).map {
                it.kind
            }
        )
        assertEquals(
            listOf(StillKind.Jpeg, StillKind.HighResJpeg),
            CapturePlanner.bracket(CapabilityInterpreter.report(front)).map {
                it.kind
            }
        )
    }

    @Test fun fileNames() {
        val p = StillPlan(StillKind.RawDng, Size(4096, 3072), "0")
        assertEquals(
            "SBL_20261001_104500_cam0_raw.dng",
            CapturePlanner.fileName(p, "20261001", "104500")
        )
        assertEquals(
            "SBL_20261001_104500_cam0_raw_2.dng",
            CapturePlanner.fileName(p, "20261001", "104500", 2)
        )
    }

    @Test fun keyMapRequiredActionsAreBoundWithoutGuessingDeviceKeys() {
        val need =
            setOf(
                CameraAction.Shutter,
                CameraAction.VideoToggle,
                CameraAction.ZoomIn,
                CameraAction.ZoomOut,
                CameraAction.SwitchCamera,
                CameraAction.ExposureUp,
                CameraAction.ExposureDown,
                CameraAction.OpenLast,
                CameraAction.NextMode,
                CameraAction.PrevMode
            )
        assertTrue(CameraKeyMap.boundActions().containsAll(need))
        assertEquals(CameraAction.Shutter, CameraKeyMap.resolve("Space", false))
        assertEquals(CameraAction.ExposureDown, CameraKeyMap.resolve("e", true))
        assertNull(CameraKeyMap.resolve("func1", false)) // device key unmapped until captured
        val p = CameraDeviceProfile.Titan2.copy(
            keyOverrides = mapOf(Binding("func1") to CameraAction.Shutter)
        )
        assertEquals(CameraAction.Shutter, CameraKeyMap.resolve("func1", false, p))
        assertEquals(
            CameraAction.Shutter,
            CameraKeyMap.resolve("volume_up", false, CameraDeviceProfile.Titan2)
        ) // probe-confirmed shutter candidates
        assertEquals(CameraAction.Shutter, CameraKeyMap.resolve("camera", false))
        // nothing inherited by Elite
        assertNull(CameraKeyMap.resolve("volume_up", false, CameraDeviceProfile.Titan2Elite))
        // vendor-intercepted: never bound
        assertNull(CameraKeyMap.resolve("func1", false, CameraDeviceProfile.Titan2))
    }

    @Test fun maxResolutionSizesNeedPixelMode() {
        val cam = rear.copy(
            jpegSizes = listOf(Size(4096, 3072)),
            jpegMaxResSizes = listOf(Size(8192, 6144))
        )
        val r = CapabilityInterpreter.report(cam)
        assertTrue(r.modes.getValue(CaptureMode.HighRes).ok)
        assertTrue(CapturePlanner.single(r, CaptureMode.HighRes).maxResMode)
        assertFalse(
            CapturePlanner.single(
                CapabilityInterpreter.report(rear),
                CaptureMode.HighRes
            ).maxResMode
        ) // Titan 2 path needs no pixel mode
        assertTrue(
            CapturePlanner.bracket(r).any {
                it.kind == StillKind.HighResJpeg && it.maxResMode
            }
        )
    }

    @Test fun previewChooserMatchesAspect() {
        val sizes =
            listOf(
                Size(1920, 1080),
                Size(1440, 1080),
                Size(1280, 960),
                Size(640, 480),
                Size(4000, 3000)
            )
        // 4:3, capped at 1920
        assertEquals(Size(1440, 1080), PreviewChooser.choose(sizes, Size(8192, 6144)))
        assertEquals(Size(1920, 1080), PreviewChooser.choose(sizes, Size(3840, 2160)))
        assertEquals(
            Size(1920, 1080),
            PreviewChooser.choose(listOf(Size(1920, 1080), Size(1280, 720)), Size(4000, 3000))
        ) // closest aspect fallback
        assertNull(PreviewChooser.choose(emptyList(), Size(4, 3)))
    }

    @Test fun compareToggleIsBound() =
        assertEquals(CameraAction.CompareToggle, CameraKeyMap.resolve("b", false))

    @Test fun jpegOrientation() {
        assertEquals(90, CaptureOrientation.jpeg(90, 0, false))
        assertEquals(0, CaptureOrientation.jpeg(90, 90, false))
        assertEquals(180, CaptureOrientation.jpeg(90, 270, false))
        assertEquals(270, CaptureOrientation.jpeg(270, 0, true))
        assertEquals(0, CaptureOrientation.jpeg(270, 90, true))
        assertEquals(270, CaptureOrientation.jpeg(90, 180, true))
    }
}
