package org.sableos.titan2.camera.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Q25 keyboard: letter keys for zoom/RAW/legend, the trackpad guard, the hint dock, capture intents. */
class Q25KeysTest {
    private val rearInfo = CameraInfo(
        "0", Facing.Back,
        activeArray = Size(4096, 3072),
        jpegSizes = listOf(Size(4096, 3072), Size(1920, 1080), Size(8192, 6144)),
        rawSizes = listOf(Size(4096, 3072)), hasRaw = true, manualSensor = true,
        isoRange = IntBounds(100, 3200),
        exposureNsRange = LongBounds(100_000, 1_000_000_000),
        zoomRatio = FloatRange(1f, 10f), minFocusDistance = 10f,
        aeCompensationSteps = IntBounds(-6, 6), aeCompensationStep = 0.5f,
        awbModes = setOf(1, 2, 5),
        videoSizes = listOf(Size(1920, 1080), Size(1280, 720))
    )
    private val frontInfo = CameraInfo(
        "1",
        Facing.Front,
        activeArray = Size(3280, 2464),
        jpegSizes = listOf(Size(3264, 2448), Size(1280, 960)),
        zoomRatio = FloatRange(1f, 1f)
    )
    private val rear = CapabilityInterpreter.report(rearInfo)
    private val front = CapabilityInterpreter.report(frontInfo)
    private val q25 = CameraDeviceProfile.ZinwaQ25

    private fun ctx(ui: UiMode, cam: CameraReport = rear) = DeckContext(ui, cam, 2)

    // ---- letters replace + - / (Alt or Sym characters on the Q25) -----------------------------

    @Test fun zAndXZoomAndAltZZoomsOut() {
        assertEquals(CameraAction.ZoomIn, CameraKeyMap.resolve("z", false, q25))
        assertEquals(CameraAction.ZoomOut, CameraKeyMap.resolve("x", false, q25))
        assertEquals(CameraAction.ZoomOut, CameraKeyMap.resolve("z", true, q25))
    }

    @Test fun everyActionOfPlusMinusAndSlashHasAPlainLetter() {
        for (key in listOf("plus", "minus", "slash")) {
            val action = CameraKeyMap.resolve(key, false)!!
            val letters = CameraKeyMap.keys(action).filter { it.key.length == 1 && !it.alt }
            assertTrue("$key -> $action has no letter", letters.isNotEmpty())
        }
    }

    @Test fun rTogglesRawInPhotoOnly() {
        assertEquals(CameraAction.RawToggle, CameraKeyMap.resolve("r", false, q25))
        assertEquals(
            Cmd.Act(CameraAction.RawToggle),
            ActionRouter.route(UiMode.Photo, CameraAction.RawToggle)
        )
        assertEquals(Cmd.Nop, ActionRouter.route(UiMode.Video, CameraAction.RawToggle))
        assertEquals(Cmd.Nop, ActionRouter.route(UiMode.Pro, CameraAction.RawToggle))
        assertTrue(ControlGate.check(CameraAction.RawToggle, ctx(UiMode.Photo)).isOpen)
        assertFalse(ControlGate.check(CameraAction.RawToggle, ctx(UiMode.Video)).isOpen)
        assertFalse(ControlGate.check(CameraAction.RawToggle, ctx(UiMode.Photo, front)).isOpen)
    }

    @Test fun rawToggleGoesAutoRawAutoOrHighResWithoutRaw() {
        assertEquals(CaptureMode.Raw, PhotoPath.rawToggle(CaptureMode.Auto, rear))
        assertEquals(CaptureMode.Auto, PhotoPath.rawToggle(CaptureMode.Raw, rear))
        assertEquals(CaptureMode.Auto, PhotoPath.rawToggle(CaptureMode.HighRes, rear))
        assertNull(PhotoPath.rawToggle(CaptureMode.Auto, front))
        assertEquals("HIGH-RES", PhotoPath.label(CaptureMode.HighRes))
        assertEquals("RAW", PhotoPath.label(CaptureMode.Raw))
    }

    // ---- trackpad guard ---------------------------------------------------------------------

    @Test fun plainTrackpadArrowsAreGuardedOnTheQ25Only() {
        for (k in listOf("left", "right", "up", "down")) {
            assertTrue(k, ArrowGuard.blocks(Binding(k), q25, stripFocused = false))
            assertFalse(k, ArrowGuard.blocks(Binding(k, alt = true), q25, stripFocused = false))
            assertFalse(k, ArrowGuard.blocks(Binding(k), CameraDeviceProfile.Titan2, false))
        }
        assertFalse(ArrowGuard.blocks(Binding("z"), q25, stripFocused = false))
    }

    @Test fun aFocusedStripTakesUpAndDownButNeverModeChanges() {
        assertFalse(ArrowGuard.blocks(Binding("up"), q25, stripFocused = true))
        assertFalse(ArrowGuard.blocks(Binding("down"), q25, stripFocused = true))
        assertTrue(ArrowGuard.blocks(Binding("left"), q25, stripFocused = true))
        assertTrue(ArrowGuard.blocks(Binding("right"), q25, stripFocused = true))
    }

    @Test fun altArrowsCarryTheArrowActionsEverywhere() {
        assertEquals(CameraAction.PrevMode, CameraKeyMap.resolve("left", true))
        assertEquals(CameraAction.NextMode, CameraKeyMap.resolve("right", true))
        assertEquals(CameraAction.StepUp, CameraKeyMap.resolve("up", true))
        assertEquals(CameraAction.StepDown, CameraKeyMap.resolve("down", true))
    }

    @Test fun q25LegendShowsAltArrowsAndNotThePlainOnes() {
        val e = ControlLegend.entries(ctx(UiMode.Photo), q25)
        val next = e.first { it.action == CameraAction.NextMode }.keys
        assertEquals("Alt+→", next)
        assertEquals("Z", e.first { it.action == CameraAction.ZoomIn }.keys.split(" / ").last())
        val titan = ControlLegend.entries(ctx(UiMode.Photo), CameraDeviceProfile.Titan2)
        assertTrue(titan.first { it.action == CameraAction.NextMode }.keys.startsWith("→"))
    }

    // ---- hint dock ------------------------------------------------------------------------------

    @Test fun hintDockKeysResolveToTheirActionsInEveryMode() {
        for (ui in UiMode.entries) {
            for ((b, a) in HintDock.bindings(ui)) {
                assertEquals("$ui ${b.key}", a, CameraKeyMap.resolve(b, q25))
            }
            assertEquals(HintDock.bindings(ui).size, HintDock.hints(ctx(ui), q25).size)
        }
    }

    @Test fun hintDockReadsLikeTheDesign() {
        val photo = HintDock.hints(ctx(UiMode.Photo), q25).map { "[${it.keyText}] ${it.label}" }
        assertEquals(listOf("[Space] Shutter", "[Z] Zoom", "[R] RAW/HiRes", "[H] Keys"), photo)
        val video = HintDock.hints(ctx(UiMode.Video), q25).first()
        assertEquals("Record", video.label)
        val pro = HintDock.hints(ctx(UiMode.Pro), q25).map { it.label }
        assertTrue(pro.containsAll(listOf("Exposure", "Focus")))
    }

    // ---- default still camera ------------------------------------------------------------------

    @Test fun launchKindFollowsTheIntentAction() {
        assertEquals(
            LaunchKind.ImageCapture,
            LaunchIntent.kind("android.media.action.IMAGE_CAPTURE")
        )
        assertEquals(
            LaunchKind.StillCamera,
            LaunchIntent.kind("android.media.action.STILL_IMAGE_CAMERA")
        )
        assertEquals(LaunchKind.Normal, LaunchIntent.kind("android.intent.action.MAIN"))
        assertEquals(LaunchKind.Normal, LaunchIntent.kind(null))
    }

    @Test fun imageCaptureKeepsToOneAutoJpeg() {
        val k = LaunchKind.ImageCapture
        assertTrue(LaunchIntent.allows(k, CameraAction.Shutter))
        assertTrue(LaunchIntent.allows(k, CameraAction.ZoomIn))
        assertTrue(LaunchIntent.allows(k, CameraAction.SwitchCamera))
        for (a in listOf(
            CameraAction.VideoToggle,
            CameraAction.NextMode,
            CameraAction.RawToggle,
            CameraAction.CompareToggle,
            CameraAction.OpenLast,
            CameraAction.NextCaptureMode
        )) {
            assertFalse("$a", LaunchIntent.allows(k, a))
            assertTrue("$a", LaunchIntent.allows(LaunchKind.StillCamera, a))
        }
    }

    @Test fun thumbnailIsAtMost512OnItsLongEdge() {
        assertEquals(1, LaunchIntent.thumbnailSample(400, 300))
        assertEquals(8, LaunchIntent.thumbnailSample(4000, 3000))
        assertTrue(
            8160 / LaunchIntent.thumbnailSample(8160, 6144) <= LaunchIntent.THUMBNAIL_EDGE_PX
        )
    }
}
