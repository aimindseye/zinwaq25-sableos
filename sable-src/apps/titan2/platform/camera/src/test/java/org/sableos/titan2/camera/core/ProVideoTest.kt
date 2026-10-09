package org.sableos.titan2.camera.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProVideoTest {
    private val limits =
        ProLimits(
            IntBounds(50, 3200),
            LongBounds(100_000, 1_000_000_000),
            IntBounds(-6, 6),
            0.5f,
            10f,
            setOf(1, 2, 3, 5, 6)
        )
    private val seed = AutoSeed(iso = 400, exposureNs = 16_666_666L, focusDiopters = 2f)

    @Test fun laddersStayInsideReportedRanges() {
        assertEquals(
            listOf(50, 100, 200, 400, 800, 1600, 3200),
            ProControls.isoLadder(limits.iso!!)
        )
        val s = ProControls.shutterLadder(limits.exposureNs!!)
        assertTrue(s.all { it in 100_000L..1_000_000_000L })
        assertEquals(1_000_000_000L, s.last())
        assertEquals(1_000_000_000L / 8000, s.first())
        assertEquals(
            listOf(100, 800),
            ProControls.isoLadder(
                IntBounds(100, 800).let {
                    IntBounds(100, 800)
                }
            ).let { listOf(it.first(), it.last()) }
        )
        // no standard stop inside: fall back to the two ends
        assertEquals(listOf(70, 90), ProControls.isoLadder(IntBounds(70, 90)))
    }

    @Test fun isoFromAutoStartsNearCameraChoiceThenSteps() {
        val s = ProControls.adjust(ProState(), ProField.Iso, +1, limits, seed)
        assertEquals(800, s.iso) // nearest to 400, then one step up
        assertEquals(400, ProControls.adjust(s, ProField.Iso, -1, limits, seed).iso)
        var x = ProState(iso = 3200)
        x = ProControls.adjust(x, ProField.Iso, +1, limits)
        assertEquals(3200, x.iso) // clamped
    }

    @Test fun shutterStepsAndLabels() {
        // dir 0 counts as +1 only for >=0; verify nearest-to-1/60 neighbour
        val s = ProControls.adjust(ProState(), ProField.Shutter, 0, limits, seed)
        assertEquals("1/30", ProControls.shutterLabel(s.shutterNs!!))
        assertEquals("1/125", ProControls.shutterLabel(1_000_000_000L / 125))
        assertEquals("2\"", ProControls.shutterLabel(2_000_000_000L))
        assertEquals("1\"", ProControls.shutterLabel(1_000_000_000L))
    }

    @Test fun evClampsAndIgnoredInManualExposure() {
        var s = ProState()
        repeat(10) { s = ProControls.adjust(s, ProField.Ev, +1, limits) }
        assertEquals(6, s.evSteps)
        assertEquals("+3.0", ProControls.valueLabel(s, ProField.Ev, limits))
        val req = ProControls.toRequest(s, limits, seed)
        assertTrue(req.aeOn)
        assertEquals(6, req.evSteps)
        val m = ProControls.toRequest(s.copy(iso = 800), limits, seed)
        assertFalse(m.aeOn)
        assertEquals(0, m.evSteps)
        assertEquals("–", ProControls.valueLabel(s.copy(iso = 800), ProField.Ev, limits))
    }

    @Test fun oneManualFieldTurnsAeOffAndFillsTheOtherFromSeed() {
        val a = ProControls.toRequest(ProState(iso = 1600), limits, seed)
        assertFalse(a.aeOn)
        assertEquals(1600, a.iso)
        assertEquals(16_666_666L, a.exposureNs)
        val b = ProControls.toRequest(ProState(shutterNs = 1_000_000_000L), limits, seed)
        assertEquals(400, b.iso)
        assertEquals(1_000_000_000L, b.frameDurationNs) // long exposure stretches the frame
        assertEquals(33_333_333L, a.frameDurationNs) // short exposure keeps a 30 fps frame
        val none = ProControls.toRequest(ProState(), limits, seed)
        assertTrue(none.aeOn)
    }

    @Test fun missingRangesMeanControlNotOffered() {
        val bare = ProLimits(null, null, null, 0f, 0f, emptySet())
        for (f in listOf(ProField.Iso, ProField.Shutter, ProField.Ev, ProField.Focus)) {
            assertFalse(bare.offers(f))
            assertEquals(ProState(), ProControls.adjust(ProState(), f, 1, bare))
        }
        assertTrue(bare.offers(ProField.Wb))
        assertEquals(listOf(WbPreset.Auto), ProControls.wbChoices(bare))
        val r = ProControls.toRequest(ProState(iso = 800), bare)
        assertTrue(r.aeOn) // cannot go manual without ranges
    }

    @Test fun whiteBalanceCyclesOnlyAvailablePresets() {
        val l = limits.copy(awbModes = setOf(1, 5))
        var s = ProState()
        val seen = mutableListOf(s.wb)
        repeat(3) {
            s = ProControls.adjust(s, ProField.Wb, +1, l)
            seen += s.wb
        }
        assertEquals(
            listOf(WbPreset.Auto, WbPreset.Daylight, WbPreset.Auto, WbPreset.Daylight),
            seen
        )
        assertEquals(5, ProControls.toRequest(ProState(wb = WbPreset.Daylight), l).awbMode)
        // unavailable preset degrades to Auto
        assertEquals(1, ProControls.toRequest(ProState(wb = WbPreset.Tungsten), l).awbMode)
    }

    @Test fun focusManualAndAuto() {
        val s = ProControls.adjust(ProState(), ProField.Focus, +1, limits, seed)
        assertTrue(s.focusDiopters!! > 0f)
        val r = ProControls.toRequest(s, limits)
        assertFalse(r.afOn)
        assertTrue(r.focusDiopters <= 10f)
        assertTrue(ProControls.toRequest(ProControls.auto(s, ProField.Focus), limits).afOn)
        assertEquals("∞", ProControls.focusLabel(0f))
        assertEquals("0.50 m", ProControls.focusLabel(2f))
        // fixed focus
        assertFalse(ProLimits(null, null, null, 0f, 0f, emptySet()).offers(ProField.Focus))
        assertEquals(emptyList<Float>(), ProControls.focusLadder(0f))
    }

    @Test fun autoResetsEachField() {
        val s = ProState(WbPreset.Cloudy, 800, 1_000_000L, 3, 1f)
        assertNull(ProControls.auto(s, ProField.Iso).iso)
        assertNull(ProControls.auto(s, ProField.Shutter).shutterNs)
        assertEquals(0, ProControls.auto(s, ProField.Ev).evSteps)
        assertEquals(WbPreset.Auto, ProControls.auto(s, ProField.Wb).wb)
        assertNull(ProControls.auto(s, ProField.Focus).focusDiopters)
    }

    // ---- video ----
    private val sizes =
        listOf(
            Size(
                3840,
                2160
            ),
            Size(
                1920,
                1080
            ),
            Size(
                1440,
                1080
            ),
            Size(
                1280,
                720
            ),
            Size(1088, 1088), Size(640, 480), Size(320, 240), Size(1920, 1000), Size(7680, 4320)
        )

    @Test fun videoOptionsSkipOddAndTinyAndHugeSizes() {
        val o = VideoPlanner.options(sizes)
        assertEquals(
            listOf(
                Size(3840, 2160),
                Size(1920, 1080),
                Size(1440, 1080),
                Size(1088, 1088),
                Size(1280, 720),
                Size(640, 480)
            ),
            o.map {
                it.size
            }
        )
        assertEquals("4K 16:9 (3840x2160)", o[0].label)
        assertEquals("1080p 16:9 (1920x1080)", o[1].label)
        assertEquals("1088p 1:1 (1088x1088)", o[3].label)
    }

    @Test fun videoDefaultPrefers1080WideThenStandardThenLargest() {
        assertEquals(Size(1920, 1080), VideoPlanner.default(VideoPlanner.options(sizes))!!.size)
        assertEquals(
            Size(1440, 1080),
            VideoPlanner.default(
                VideoPlanner.options(listOf(Size(3840, 2880), Size(1440, 1080), Size(640, 480)))
            )!!.size
        )
        assertEquals(
            Size(1088, 1088),
            VideoPlanner.default(VideoPlanner.options(listOf(Size(1088, 1088))))!!.size
        )
        assertNull(VideoPlanner.default(emptyList()))
    }

    @Test fun videoQualityCyclesWithWrap() {
        val o = VideoPlanner.options(sizes)
        assertEquals(o[1], VideoPlanner.next(o, o[0], +1))
        assertEquals(o.last(), VideoPlanner.next(o, o[0], -1))
        assertEquals(o[0], VideoPlanner.next(o, o.last(), +1))
        assertNull(VideoPlanner.next(emptyList(), null, 1))
    }

    @Test fun videoBitrateAndNames() {
        assertEquals(6_220_800, VideoPlanner.bitrate(Size(1920, 1080)))
        assertEquals(49_766_400, VideoPlanner.bitrate(Size(3840, 2160), 60))
        assertEquals(50_000_000, VideoPlanner.bitrate(Size(3840, 2160), 90))
        assertEquals(4_000_000, VideoPlanner.bitrate(Size(640, 480)))
        assertEquals(
            "SBV_20261001_115500_cam0_1080p.mp4",
            VideoPlanner.fileName("0", Size(1920, 1080), "20261001", "115500")
        )
        assertEquals(
            "SBV_20261001_115500_cam1_4k_2.mp4",
            VideoPlanner.fileName("1", Size(3840, 2160), "20261001", "115500", 2)
        )
    }

    @Test fun recorderStateMachineNeverDoublesUp() {
        var s = RecState.Idle
        s = RecStateMachine.reduce(s, RecEvent.TogglePause)
        assertEquals(RecState.Idle, s)
        s = RecStateMachine.reduce(s, RecEvent.ToggleRecord)
        assertEquals(RecState.Recording, s)
        s = RecStateMachine.reduce(s, RecEvent.TogglePause)
        assertEquals(RecState.Paused, s)
        s = RecStateMachine.reduce(s, RecEvent.TogglePause)
        assertEquals(RecState.Recording, s)
        s = RecStateMachine.reduce(s, RecEvent.ToggleRecord)
        assertEquals(RecState.Idle, s)
        assertEquals(RecState.Idle, RecStateMachine.reduce(RecState.Paused, RecEvent.Failed))
        assertEquals("00:00", RecStateMachine.clock(-5))
        assertEquals("01:05", RecStateMachine.clock(65_999))
        assertEquals("1:01:01", RecStateMachine.clock(3_661_000))
    }

    // ---- routing ----
    @Test fun arrowsZoomInPhotoButStepFieldsInPro() {
        assertEquals(
            Cmd.Act(CameraAction.ZoomIn),
            ActionRouter.route(UiMode.Photo, CameraAction.StepUp)
        )
        assertEquals(
            Cmd.Act(CameraAction.ZoomOut),
            ActionRouter.route(UiMode.Video, CameraAction.StepDown)
        )
        assertEquals(Cmd.ProStep(1), ActionRouter.route(UiMode.Pro, CameraAction.StepUp))
        assertEquals(Cmd.ProStep(-1), ActionRouter.route(UiMode.Pro, CameraAction.StepDown))
        assertEquals(
            Cmd.Act(CameraAction.ZoomIn),
            ActionRouter.route(UiMode.Pro, CameraAction.ZoomIn)
        ) // plus/minus keep zooming in Pro
    }

    @Test fun spaceRecordsInVideoAndShootsElsewhere() {
        assertEquals(
            Cmd.Act(CameraAction.VideoToggle),
            ActionRouter.route(UiMode.Video, CameraAction.Shutter)
        )
        assertEquals(
            Cmd.Act(CameraAction.Shutter),
            ActionRouter.route(UiMode.Photo, CameraAction.Shutter)
        )
        assertEquals(
            Cmd.Act(CameraAction.Shutter),
            ActionRouter.route(UiMode.Pro, CameraAction.Shutter)
        )
        assertEquals(
            Cmd.SetUi(UiMode.Video),
            ActionRouter.route(UiMode.Photo, CameraAction.VideoToggle)
        )
        assertEquals(Cmd.Nop, ActionRouter.route(UiMode.Photo, CameraAction.PauseToggle))
        assertEquals(
            Cmd.Act(CameraAction.PauseToggle),
            ActionRouter.route(UiMode.Video, CameraAction.PauseToggle)
        )
    }

    @Test fun proSelectorsOnlyActInPro() {
        assertEquals(
            Cmd.ProSelect(ProField.Iso),
            ActionRouter.route(UiMode.Pro, CameraAction.SelectIso)
        )
        assertEquals(Cmd.Nop, ActionRouter.route(UiMode.Photo, CameraAction.SelectIso))
        assertEquals(
            Cmd.ProSelect(ProField.Ev),
            ActionRouter.route(UiMode.Pro, CameraAction.ExposureUp)
        )
        assertEquals(
            Cmd.Act(CameraAction.ExposureUp),
            ActionRouter.route(UiMode.Photo, CameraAction.ExposureUp)
        )
        assertEquals(
            Cmd.ProSelect(ProField.Focus),
            ActionRouter.route(UiMode.Pro, CameraAction.Autofocus)
        )
        assertEquals(
            Cmd.Act(CameraAction.Autofocus),
            ActionRouter.route(UiMode.Photo, CameraAction.Autofocus)
        )
        assertEquals(Cmd.ProAuto, ActionRouter.route(UiMode.Pro, CameraAction.ProAuto))
        assertEquals(Cmd.Nop, ActionRouter.route(UiMode.Video, CameraAction.ProAuto))
        assertEquals(Cmd.Nop, ActionRouter.route(UiMode.Pro, CameraAction.CompareToggle))
    }

    @Test fun modeStripFollowsCapabilitiesAndDoesNotWrap() {
        val rear =
            CameraInfo(
                "0",
                Facing.Back,
                jpegSizes = listOf(Size(4096, 3072)),
                manualSensor = true,
                isoRange = IntBounds(100, 800),
                exposureNsRange = LongBounds(1000, 1_000_000_000),
                videoSizes = listOf(Size(1920, 1080))
            )
        val all = ActionRouter.available(CapabilityInterpreter.report(rear))
        assertEquals(listOf(UiMode.Video, UiMode.Photo, UiMode.Pro), all)
        val noPro = ActionRouter.available(
            CapabilityInterpreter.report(rear.copy(manualSensor = false))
        )
        assertEquals(listOf(UiMode.Video, UiMode.Photo), noPro)
        val noVideo = ActionRouter.available(
            CapabilityInterpreter.report(rear.copy(videoSizes = emptyList()))
        )
        assertEquals(listOf(UiMode.Photo, UiMode.Pro), noVideo)
        assertEquals(UiMode.Pro, ActionRouter.step(UiMode.Photo, +1, all))
        assertEquals(UiMode.Pro, ActionRouter.step(UiMode.Pro, +1, all))
        assertEquals(UiMode.Video, ActionRouter.step(UiMode.Photo, -1, all))
        assertEquals(
            UiMode.Photo,
            ActionRouter.step(
                UiMode.Pro,
                +1,
                noPro.let {
                    listOf(UiMode.Photo)
                }
            )
        )
        assertEquals(CameraAction.StepUp, CameraKeyMap.resolve("up", false))
        assertEquals(CameraAction.ZoomIn, CameraKeyMap.resolve("plus", false))
        assertEquals(CameraAction.PauseToggle, CameraKeyMap.resolve("p", false))
    }
}
