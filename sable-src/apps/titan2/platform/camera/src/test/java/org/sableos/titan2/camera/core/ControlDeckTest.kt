package org.sableos.titan2.camera.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlDeckTest {
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

    private fun ctx(
        ui: UiMode,
        cam: CameraReport = rear,
        count: Int = 2,
        rec: Boolean = false,
        f: ProField = ProField.Iso
    ) = DeckContext(ui, cam, count, rec, f)

    // ---- raw key layer is separate from semantic actions -------------------------------------

    @Test fun rawKeyCodesMapToCoreNamesOnly() {
        assertEquals("space", RawKeys.nameOf(62))
        assertEquals("enter", RawKeys.nameOf(66))
        assertEquals("enter", RawKeys.nameOf(23))
        assertEquals("a", RawKeys.nameOf(29))
        assertEquals("z", RawKeys.nameOf(54))
        assertEquals("slash", RawKeys.nameOf(76))
        assertEquals("plus", RawKeys.nameOf(70))
        assertEquals("volume_up", RawKeys.nameOf(24))
        assertNull(RawKeys.nameOf(131)) // F1 is not a camera key
        assertNull(RawKeys.nameOf(0))
    }

    @Test fun everyBoundKeyNameIsReachableFromSomeRawKeyCode() {
        val reachable = RawKeys.allNames()
        val bound = CameraKeyMap.standard.keys.map { it.key }.toSet()
        assertTrue("unreachable: ${bound - reachable}", reachable.containsAll(bound))
    }

    @Test fun coreKeyMapHoldsNoAndroidKeyCodes() {
        // Bindings are names, never ints; a raw code can only enter through RawKeys.
        assertTrue(CameraKeyMap.standard.keys.all { it.key.all { c -> !c.isDigit() } })
    }

    // ---- one fixed map -----------------------------------------------------------------------

    @Test fun fixedMapMatchesTheControlDeckDocument() {
        val m = CameraKeyMap
        assertEquals(CameraAction.Shutter, m.resolve("space", false))
        assertEquals(CameraAction.Shutter, m.resolve("enter", false))
        assertEquals(CameraAction.Shutter, m.resolve("camera", false))
        assertEquals(CameraAction.Autofocus, m.resolve("f", false))
        assertEquals(CameraAction.ExposureUp, m.resolve("e", false))
        assertEquals(CameraAction.SelectIso, m.resolve("i", false))
        assertEquals(CameraAction.SelectWb, m.resolve("w", false))
        assertEquals(CameraAction.SelectShutter, m.resolve("s", false))
        assertEquals(CameraAction.ZoomIn, m.resolve("plus", false))
        assertEquals(CameraAction.ZoomOut, m.resolve("minus", false))
        assertEquals(CameraAction.PrevMode, m.resolve("left", false))
        assertEquals(CameraAction.NextMode, m.resolve("right", false))
        assertEquals(CameraAction.StepUp, m.resolve("up", false))
        assertEquals(CameraAction.StepDown, m.resolve("down", false))
        assertEquals(CameraAction.SwitchCamera, m.resolve("c", false))
        assertEquals(CameraAction.CompareToggle, m.resolve("b", false))
        assertEquals(CameraAction.OpenLast, m.resolve("g", false))
        assertEquals(CameraAction.ProAuto, m.resolve("a", false))
        assertEquals(CameraAction.ToggleLegend, m.resolve("h", false))
        assertEquals(CameraAction.ToggleLegend, m.resolve("slash", false))
    }

    @Test fun func1AndFunc2StayUnboundEverywhere() {
        for (p in listOf(
            CameraDeviceProfile.Unknown,
            CameraDeviceProfile.Titan2,
            CameraDeviceProfile.Titan2Elite,
            CameraDeviceProfile.ZinwaQ27
        )) {
            assertNull(CameraKeyMap.resolve("func1", false, p))
            assertNull(CameraKeyMap.resolve("func2", false, p))
        }
    }

    @Test fun everyActionHasAtLeastOneBinding() {
        val bound = CameraKeyMap.boundActions()
        assertEquals(emptySet<CameraAction>(), CameraAction.entries.toSet() - bound)
    }

    @Test fun deckAddsNoCaptureModes() {
        assertEquals(listOf("Video", "Photo", "Pro"), UiMode.entries.map { it.label })
    }

    // ---- touch and keys share one semantic path ---------------------------------------------

    @Test fun touchCoversEveryActionExceptSystemBack() {
        val missing = CameraAction.entries.toSet() - TouchFallback.covered()
        assertEquals(TouchFallback.systemProvided, missing)
    }

    @Test fun touchControlsUseTheSameActionsAsKeys() {
        val keyed = CameraKeyMap.boundActions()
        assertTrue(TouchFallback.controls(ctx(UiMode.Photo)).all { it.action in keyed })
    }

    @Test fun touchAndKeyRouteIdentically() {
        for (ui in UiMode.entries) {
            for (t in TouchFallback.controls(ctx(ui))) {
                val key = CameraKeyMap.keys(t.action).first()
                val viaKey = ActionRouter.route(ui, CameraKeyMap.resolve(key.key, key.alt)!!)
                assertEquals(viaKey, ActionRouter.route(ui, t.action))
            }
        }
    }

    @Test fun touchChromeIsAlwaysThereInPortraitAndOptionalInTheDeck() {
        assertTrue(DeckOrientation.touchChromeVisible(DeckPresentation.Portrait, false))
        assertFalse(DeckOrientation.touchChromeVisible(DeckPresentation.LandscapeDeck, false))
        assertTrue(DeckOrientation.touchChromeVisible(DeckPresentation.LandscapeDeck, true))
    }

    @Test fun touchCanRevealChromeInTheDeck() {
        val s = ControlDeck.toggleTouchChrome(DeckState())
        assertTrue(s.touchChromeRevealed)
        assertFalse(ControlDeck.toggleTouchChrome(s).touchChromeRevealed)
    }

    // ---- orientation -------------------------------------------------------------------------

    @Test fun orientationPolicyIsAutoAndNeverLocked() {
        assertEquals("AUTO", DeckPolicy.ORIENTATION_POLICY)
        assertFalse(DeckPolicy.GLOBAL_LANDSCAPE_LOCK)
        assertTrue(DeckPolicy.PORTRAIT_SUPPORTED)
    }

    @Test fun presentationFollowsTheWindowShape() {
        assertEquals(DeckPresentation.LandscapeDeck, DeckOrientation.presentation(2000, 1000))
        assertEquals(DeckPresentation.Portrait, DeckOrientation.presentation(1000, 2000))
        assertEquals(DeckPresentation.Portrait, DeckOrientation.presentation(1000, 1000))
    }

    // ---- capability gating -------------------------------------------------------------------

    @Test fun proControlsAreOpenOnlyInProAndOnlyWhenOffered() {
        assertTrue(ControlGate.check(CameraAction.SelectIso, ctx(UiMode.Pro)).isOpen)
        assertFalse(ControlGate.check(CameraAction.SelectIso, ctx(UiMode.Photo)).isOpen)
        assertFalse(ControlGate.check(CameraAction.ProAuto, ctx(UiMode.Photo)).isOpen)
        val noManual = CapabilityInterpreter.report(rearInfo.copy(isoRange = null))
        assertFalse(ControlGate.check(CameraAction.SelectIso, ctx(UiMode.Pro, noManual)).isOpen)
        assertFalse(ControlGate.check(CameraAction.SelectShutter, ctx(UiMode.Pro, noManual)).isOpen)
    }

    @Test fun zoomNeedsMoreThanOneRealStop() {
        assertTrue(ControlGate.check(CameraAction.ZoomIn, ctx(UiMode.Photo)).isOpen)
        assertFalse(ControlGate.check(CameraAction.ZoomIn, ctx(UiMode.Photo, front)).isOpen)
        assertFalse(ControlGate.check(CameraAction.StepUp, ctx(UiMode.Photo, front)).isOpen)
    }

    @Test fun fixedFocusAndMissingEvAreClosed() {
        val fixed = CapabilityInterpreter.report(rearInfo.copy(minFocusDistance = 0f))
        assertFalse(ControlGate.check(CameraAction.Autofocus, ctx(UiMode.Photo, fixed)).isOpen)
        assertFalse(ControlGate.check(CameraAction.Autofocus, ctx(UiMode.Pro, fixed)).isOpen)
        val noEv = CapabilityInterpreter.report(rearInfo.copy(aeCompensationSteps = null))
        assertFalse(ControlGate.check(CameraAction.ExposureUp, ctx(UiMode.Photo, noEv)).isOpen)
        assertTrue(ControlGate.check(CameraAction.ExposureUp, ctx(UiMode.Photo)).isOpen)
    }

    @Test fun cameraSwitchAndModeStepsNeedSomethingToSwitchTo() {
        assertFalse(
            ControlGate.check(CameraAction.SwitchCamera, ctx(UiMode.Photo, count = 1)).isOpen
        )
        assertFalse(
            ControlGate.check(CameraAction.SwitchCamera, ctx(UiMode.Photo, rec = true)).isOpen
        )
        assertTrue(ControlGate.check(CameraAction.NextMode, ctx(UiMode.Photo)).isOpen)
        assertFalse(ControlGate.check(CameraAction.NextMode, ctx(UiMode.Photo, front)).isOpen)
    }

    @Test fun videoOnlyControlsFollowVideoState() {
        assertFalse(ControlGate.check(CameraAction.PauseToggle, ctx(UiMode.Video)).isOpen)
        assertTrue(
            ControlGate.check(CameraAction.PauseToggle, ctx(UiMode.Video, rec = true)).isOpen
        )
        assertTrue(ControlGate.check(CameraAction.QualityNext, ctx(UiMode.Video)).isOpen)
        assertFalse(ControlGate.check(CameraAction.QualityNext, ctx(UiMode.Photo)).isOpen)
        assertFalse(ControlGate.check(CameraAction.CompareToggle, ctx(UiMode.Video)).isOpen)
    }

    @Test fun gateAnswersEveryActionInEveryMode() {
        for (ui in UiMode.entries) {
            for (a in CameraAction.entries) {
                assertNotNull(ControlGate.check(a, ctx(ui)))
            }
        }
    }

    @Test fun helpAndExitAreNeverGatedOff() {
        for (ui in UiMode.entries) {
            for (cam in listOf(rear, front)) {
                assertTrue(ControlGate.check(CameraAction.ToggleLegend, ctx(ui, cam)).isOpen)
                assertTrue(ControlGate.check(CameraAction.Back, ctx(ui, cam)).isOpen)
            }
        }
    }

    // ---- transient parameter strips ----------------------------------------------------------

    private val limits = ProLimits.of(rearInfo)

    @Test fun isoStripShowsAutoThenTheRealLadder() {
        val s = ParameterStrips.forField(ProField.Iso, ProState(), limits)!!
        assertEquals("AUTO", s.labels.first())
        assertEquals(0, s.selected)
        assertTrue(s.isAuto)
        val m = ParameterStrips.forField(ProField.Iso, ProState(iso = 400), limits)!!
        assertEquals("400", m.labels[m.selected])
        assertFalse(m.isAuto)
    }

    @Test fun isoStripNeverListsValuesOutsideTheDeviceRange() {
        val s = ParameterStrips.forField(ProField.Iso, ProState(), limits)!!
        assertEquals(listOf("AUTO", "100", "200", "400", "800", "1600", "3200"), s.labels)
    }

    @Test fun stripIsNullWhenTheFieldIsNotOffered() {
        val l = ProLimits.of(rearInfo.copy(isoRange = null, minFocusDistance = 0f))
        assertNull(ParameterStrips.forField(ProField.Iso, ProState(), l))
        assertNull(ParameterStrips.forField(ProField.Shutter, ProState(), l))
        assertNull(ParameterStrips.forField(ProField.Focus, ProState(), l))
    }

    @Test fun whiteBalanceStripOffersOnlyReportedPresets() {
        val s = ParameterStrips.forField(ProField.Wb, ProState(), limits)!!
        assertEquals(listOf("Auto", "Tungsten", "Daylight"), s.labels)
        assertEquals(0, s.selected)
    }

    @Test fun evStripCoversTheReportedRangeAndMarksZero() {
        val s = ParameterStrips.ev(0, IntBounds(-6, 6), 0.5f)
        assertEquals(13, s.labels.size)
        assertEquals("0", s.labels[s.selected])
        assertTrue(s.isAuto)
        assertEquals(
            "+1.0",
            ParameterStrips.ev(2, IntBounds(-6, 6), 0.5f).let {
                it.labels[it.selected]
            }
        )
    }

    @Test fun zoomStripUsesTheCameraStopsAndIsNullForFixedZoom() {
        val s = ParameterStrips.zoom(2f, rear)!!
        assertEquals("2x", s.labels[s.selected])
        assertNull(ParameterStrips.zoom(1f, front))
    }

    @Test fun windowKeepsLongLadderToASmallCenteredStrip() {
        val s = ParameterStrips.ev(0, IntBounds(-12, 12), 0.5f)
        val (cells, sel) = s.window()
        assertEquals(DeckPolicy.STRIP_MAX_CELLS, cells.size)
        assertEquals("0", cells[sel])
        val edge = ParameterStrips.ev(-12, IntBounds(-12, 12), 0.5f).window()
        assertEquals(0, edge.second)
    }

    @Test fun shortStripWindowIsUnchanged() {
        val s = ParameterStrips.forField(ProField.Wb, ProState(), limits)!!
        assertEquals(s.labels to s.selected, s.window())
    }

    private fun stripCtx(pro: ProState = ProState(), field: ProField = ProField.Iso) =
        StripContext(rear, pro, field, AutoSeed(), 2f, 0)

    @Test fun commandsShowTheStripTheyAdjust() {
        val c = stripCtx(ProState(iso = 800))
        assertEquals("ISO", ParameterStrips.after(Cmd.ProSelect(ProField.Iso), c)!!.title)
        assertEquals("ISO", ParameterStrips.after(Cmd.ProStep(1), c)!!.title)
        assertEquals("ISO", ParameterStrips.after(Cmd.ProAuto, c)!!.title)
        assertEquals("Zoom", ParameterStrips.after(Cmd.Act(CameraAction.ZoomIn), c)!!.title)
        assertEquals("EV", ParameterStrips.after(Cmd.Act(CameraAction.ExposureUp), c)!!.title)
        assertNull(ParameterStrips.after(Cmd.Act(CameraAction.Shutter), c))
        assertNull(ParameterStrips.after(Cmd.Nop, c))
    }

    @Test fun stripIsTransientAndExpires() {
        val strip = ParameterStrips.zoom(2f, rear)
        val shown = ControlDeck.showStrip(DeckState(), strip, 1_000L)
        assertNotNull(shown.strip)
        assertNotNull(ControlDeck.tick(shown, 1_000L + DeckPolicy.STRIP_TIMEOUT_MS - 1).strip)
        assertNull(ControlDeck.tick(shown, 1_000L + DeckPolicy.STRIP_TIMEOUT_MS).strip)
    }

    @Test fun interactionRestartsTheStripTimer() {
        val strip = ParameterStrips.zoom(2f, rear)
        val a = ControlDeck.showStrip(DeckState(), strip, 0L)
        val b = ControlDeck.showStrip(a, strip, 2_000L)
        assertNotNull(ControlDeck.tick(b, 4_000L).strip)
        assertNull(ControlDeck.tick(b, 4_500L).strip)
    }

    @Test fun nullStripLeavesStateAlone() {
        val s = ControlDeck.showStrip(DeckState(legendVisible = true), null, 5L)
        assertEquals(DeckState(legendVisible = true), s)
    }

    // ---- legend ------------------------------------------------------------------------------

    @Test fun legendOpensAndClosesWithTheHelpAction() {
        val open = ControlDeck.onAction(DeckState(), CameraAction.ToggleLegend)
        assertTrue(open.legendVisible)
        assertFalse(ControlDeck.onAction(open, CameraAction.ToggleLegend).legendVisible)
    }

    @Test fun anyOtherActionClosesTheLegendWithoutSwallowingIt() {
        val open = DeckState(legendVisible = true)
        assertFalse(ControlDeck.onAction(open, CameraAction.Shutter).legendVisible)
        assertEquals(DeckState(), ControlDeck.onAction(DeckState(), CameraAction.Shutter))
    }

    @Test fun backClosesLegendThenStripThenLeaves() {
        val strip = ParameterStrips.zoom(2f, rear)
        val s = DeckState(strip = strip, legendVisible = true)
        val r1 = ControlDeck.onBack(s) as BackResult.Consumed
        assertFalse(r1.state.legendVisible)
        assertNotNull(r1.state.strip)
        val r2 = ControlDeck.onBack(r1.state) as BackResult.Consumed
        assertNull(r2.state.strip)
        assertEquals(BackResult.PassThrough, ControlDeck.onBack(r2.state))
    }

    @Test fun touchCanDismissTheLegendAndStrip() {
        val s = DeckState(strip = ParameterStrips.zoom(2f, rear), legendVisible = true)
        assertFalse(ControlDeck.dismissLegend(s).legendVisible)
        assertNull(ControlDeck.dismissStrip(s).strip)
    }

    @Test fun legendKeysAllResolveBackToTheirAction() {
        for (ui in UiMode.entries) {
            for (e in ControlLegend.entries(ctx(ui), CameraDeviceProfile.Titan2)) {
                for (b in ControlLegend.keysFor(e.action, CameraDeviceProfile.Titan2)) {
                    assertEquals(
                        e.action,
                        CameraKeyMap.resolve(b.key, b.alt, CameraDeviceProfile.Titan2)
                    )
                }
            }
        }
    }

    @Test fun legendCoversEveryBoundActionOnce() {
        val e = ControlLegend.entries(ctx(UiMode.Photo), CameraDeviceProfile.Unknown)
        assertEquals(CameraKeyMap.boundActions(), e.map { it.action }.toSet())
        assertEquals(e.size, e.map { it.action }.toSet().size)
    }

    @Test fun legendShowsClosedControlsWithAReasonInsteadOfHidingThem() {
        val e = ControlLegend.entries(
            ctx(UiMode.Photo, front, count = 1),
            CameraDeviceProfile.Unknown
        )
        val zoom = e.first { it.action == CameraAction.ZoomIn }
        assertTrue(zoom.gate is Gate.Closed)
        assertTrue((zoom.gate as Gate.Closed).reason.isNotBlank())
    }

    @Test fun legendIsProfileTruthfulAboutVolumeKeys() {
        fun keys(p: CameraDeviceProfile) = ControlLegend
            .entries(ctx(UiMode.Photo), p).first { it.action == CameraAction.Shutter }.keys
        assertTrue(keys(CameraDeviceProfile.Titan2).contains("Vol+"))
        assertFalse(keys(CameraDeviceProfile.Titan2Elite).contains("Vol+"))
        assertFalse(keys(CameraDeviceProfile.Unknown).contains("Func"))
    }

    @Test fun legendLabelsFollowTheMode() {
        assertEquals("Record / stop", ControlLegend.label(CameraAction.Shutter, UiMode.Video))
        assertEquals("Shutter", ControlLegend.label(CameraAction.Shutter, UiMode.Photo))
        assertEquals("Adjust +", ControlLegend.label(CameraAction.StepUp, UiMode.Pro))
        assertEquals("Zoom in", ControlLegend.label(CameraAction.StepUp, UiMode.Photo))
    }

    @Test fun legendFormatsAltChords() {
        assertEquals("Alt+E", ControlLegend.keyText(Binding("e", alt = true)))
        assertEquals("Space", ControlLegend.keyText(Binding("space")))
    }

    // ---- one dispatch path for keys and touch ------------------------------------------------

    @Test fun dispatchRoutesAnOpenAction() {
        val d = ControlDeck.dispatch(DeckState(), CameraAction.Shutter, ctx(UiMode.Photo))
        assertTrue(d.route)
        assertNull(d.status)
    }

    @Test fun dispatchRefusesAClosedActionAndSaysWhy() {
        val d = ControlDeck.dispatch(DeckState(), CameraAction.ZoomIn, ctx(UiMode.Photo, front))
        assertFalse(d.route)
        assertEquals("Zoom in: No zoom range", d.status)
    }

    @Test fun dispatchHelpTogglesLegendWithoutRouting() {
        val d = ControlDeck.dispatch(DeckState(), CameraAction.ToggleLegend, ctx(UiMode.Photo))
        assertTrue(d.deck.legendVisible)
        assertFalse(d.route)
    }

    @Test fun dispatchBackClosesOverlaysBeforeExit() {
        val s = DeckState(legendVisible = true)
        val first = ControlDeck.dispatch(s, CameraAction.Back, ctx(UiMode.Photo))
        assertFalse(first.deck.legendVisible)
        assertFalse(first.route)
        val second = ControlDeck.dispatch(first.deck, CameraAction.Back, ctx(UiMode.Photo))
        assertTrue(second.route) // nothing left to close: Back reaches the router
    }

    @Test fun dispatchLegendNeverTrapsAKeyPress() {
        val d = ControlDeck.dispatch(
            DeckState(legendVisible = true),
            CameraAction.Shutter,
            ctx(UiMode.Photo)
        )
        assertFalse(d.deck.legendVisible)
        assertTrue(d.route)
    }

    @Test fun everyActionHasALegendLabelInEveryMode() {
        assertEquals(CameraAction.entries.toSet(), ControlLegend.labelled())
        for (ui in UiMode.entries) {
            for (a in CameraAction.entries) {
                assertTrue(ControlLegend.label(a, ui).isNotBlank())
            }
        }
    }

    @Test fun evLabelsAreSharedWithTheProHud() {
        assertEquals("0", EvLabels.of(0, 0.5f))
        assertEquals("+1.5", EvLabels.of(3, 0.5f))
        assertEquals("-1.0", EvLabels.of(-2, 0.5f))
        assertEquals("+1.5", ProControls.valueLabel(ProState(evSteps = 3), ProField.Ev, limits))
    }
}
