package org.sableos.titan2.displaycompat.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tall canvas sizes, the hotkey cycle, the restore decisions and the held-originals bookkeeping. */
class DisplayToolsTest {
    private val q25 = SizePx(720, 720)
    private val facts = DisplayFacts(q25, 280)
    private val tall = DisplayProfile(canvas = TallCanvas.Tall9x16)

    private fun moment(front: String? = "com.example.app", interactive: Boolean = true) = Situation(
        gateOpen = true,
        setupComplete = true,
        interactive = interactive,
        frontPackage = front,
        frontProfile = DisplayProfile.NATIVE,
        systemRotation = SystemRotation.FollowApps,
        anyAspectProfile = false
    )

    // --- sizes ---

    @Test fun q25PresetsMatchTheCommunityResolutions() {
        assertEquals(
            listOf(SizePx(720, 772), SizePx(720, 960), SizePx(720, 1280), SizePx(720, 1440)),
            TallCanvas.usable(q25).map { it.size(q25) }
        )
        assertNull(TallCanvas.Native.size(q25))
        assertEquals("720x1280 (9:16)", TallCanvas.Tall9x16.label(q25))
        assertEquals("Native 720x720", TallCanvas.Native.label(q25))
    }

    @Test fun aSizeNoTallerThanTheScreenIsNotOffered() {
        val phone = SizePx(720, 1280)
        assertEquals(listOf(TallCanvas.Tall1x2), TallCanvas.usable(phone))
        assertEquals("9:16 (not taller here)", TallCanvas.Tall9x16.label(phone))
    }

    @Test fun tallCanvasNeverCrossesTheLargeScreenThresholdAtDensity280() {
        TallCanvas.usable(q25).forEach {
            val s = requireNotNull(it.size(q25))
            assertEquals(411, CanvasMath.smallestWidthDp(s, 280))
            assertNull(it.name, CanvasMath.compactDensity(s, 280))
        }
    }

    @Test fun aLowDensityGetsAGuardThatKeepsItUnder600dp() {
        val s = SizePx(720, 1280)
        assertEquals(640, CanvasMath.smallestWidthDp(s, 180))
        val d = requireNotNull(CanvasMath.compactDensity(s, 180))
        assertEquals(193, d)
        assertTrue(CanvasMath.smallestWidthDp(s, d) < CanvasMath.LARGE_SCREEN_DP)
        assertEquals(600, CanvasMath.smallestWidthDp(s, d - 1))
    }

    // --- hotkey cycle ---

    @Test fun cycleGoesNativeThenFavouritesShortestFirstThenNative() {
        val fav = setOf(TallCanvas.Tall9x16, TallCanvas.Tall3x4)
        assertEquals(TallCanvas.Tall3x4, CanvasCycle.next(fav, TallCanvas.Native, q25))
        assertEquals(TallCanvas.Tall9x16, CanvasCycle.next(fav, TallCanvas.Tall3x4, q25))
        assertEquals(TallCanvas.Native, CanvasCycle.next(fav, TallCanvas.Tall9x16, q25))
    }

    @Test fun aSizeOutsideTheCycleRestartsIt() {
        val fav = setOf(TallCanvas.Tall9x16)
        assertEquals(TallCanvas.Tall9x16, CanvasCycle.next(fav, TallCanvas.Tall1x2, q25))
    }

    @Test fun noFavouritesMeansNothingToCycle() {
        assertNull(CanvasCycle.next(emptySet(), TallCanvas.Native, q25))
        assertNull(
            CanvasCycle.next(setOf(TallCanvas.Tall9x16), TallCanvas.Native, SizePx(720, 1440))
        )
    }

    @Test fun favouritesRoundTripAndDefault() {
        assertEquals(CanvasCycle.DEFAULT_FAVOURITES, CanvasCycle.decode(null))
        assertEquals(emptySet<TallCanvas>(), CanvasCycle.decode(""))
        val fav = setOf(TallCanvas.Taller, TallCanvas.Tall1x2)
        assertEquals("tall_772,tall_1_2", CanvasCycle.encode(fav))
        assertEquals(fav, CanvasCycle.decode("tall_1_2,bogus,native,tall_772"))
    }

    @Test fun canvasSurvivesTheProfileCodecAndOldPayloadsStayNative() {
        val p = DisplayProfile(AspectProfile.Native, canvas = TallCanvas.Tall3x4)
        assertEquals(p, ProfileCodec.decode(ProfileCodec.encode(p)))
        assertFalse(p.isNative)
        assertEquals(TallCanvas.Native, ProfileCodec.decode("v=1;p=mini;o=Default").canvas)
        assertEquals(TallCanvas.Native, ProfileCodec.decode("v=1;p=native;c=nope").canvas)
    }

    // --- restore decisions ---

    @Test fun canvasOnlyWhileItsAppIsInFront() {
        val t = DisplayPolicy.target(moment().copy(frontProfile = tall), facts)
        assertEquals(SizePx(720, 1280), t.size)
        assertNull(t.density)
        assertNull(t.rotation)
        assertNull(t.ignoreOrientationRequest)
        // Another app in front: its own (native) profile, so the size is given back.
        assertNull(DisplayPolicy.target(moment(front = "com.other"), facts).size)
    }

    @Test fun lockScreenSetupAndClosedGateRestore() {
        val s = moment().copy(frontProfile = tall)
        assertNull(DisplayPolicy.target(s.copy(interactive = false), facts).size)
        assertEquals(DisplayTarget.NONE, DisplayPolicy.target(s.copy(setupComplete = false), facts))
        assertEquals(DisplayTarget.NONE, DisplayPolicy.target(s.copy(gateOpen = false), facts))
        assertNull(DisplayPolicy.target(s.copy(frontPackage = null), facts).size)
    }

    @Test fun systemRotationLocksAndIgnoresAppRequests() {
        val portrait = DisplayPolicy.target(
            moment().copy(systemRotation = SystemRotation.Portrait),
            facts
        )
        assertEquals(RotationTarget.PORTRAIT, portrait.rotation)
        assertEquals(true, portrait.ignoreOrientationRequest)
        val landscape =
            DisplayPolicy.target(moment().copy(systemRotation = SystemRotation.Landscape), facts)
        assertEquals(RotationTarget(false, Rotations.ROTATION_270), landscape.rotation)
        val auto = DisplayPolicy.target(moment().copy(systemRotation = SystemRotation.Auto), facts)
        assertEquals(RotationTarget.AUTO, auto.rotation)
        assertNull(auto.ignoreOrientationRequest)
        val follow = DisplayPolicy.target(moment(), facts)
        assertNull(follow.rotation)
        assertNull(follow.ignoreOrientationRequest)
    }

    @Test fun perAppOrientationWinsWhileInFrontAndStaysOffTheLockScreen() {
        val s = moment().copy(
            systemRotation = SystemRotation.Portrait,
            frontProfile = DisplayProfile(orientation = OrientationPref.Auto)
        )
        assertEquals(RotationTarget.AUTO, DisplayPolicy.target(s, facts).rotation)
        assertEquals(
            RotationTarget.PORTRAIT,
            DisplayPolicy.target(s.copy(interactive = false), facts).rotation
        )
        val landscape = s.copy(
            frontProfile = DisplayProfile(orientation = OrientationPref.Landscape)
        )
        assertEquals(RotationTarget.LANDSCAPE, DisplayPolicy.target(landscape, facts).rotation)
    }

    @Test fun anAspectProfileKeepsTheDisplayIgnoringOrientationRequests() {
        assertEquals(
            true,
            DisplayPolicy.target(
                moment().copy(anyAspectProfile = true),
                facts
            ).ignoreOrientationRequest
        )
        assertTrue(DisplayPolicy.usesAspectRatio(DisplayProfile(AspectProfile.MiniMode)))
        assertFalse(DisplayPolicy.usesAspectRatio(DisplayProfile(AspectProfile.KeyboardSafe)))
        assertFalse(DisplayPolicy.usesAspectRatio(tall))
    }

    @Test fun onlyCanvasAndOrientationProfilesNeedTheService() {
        assertFalse(
            DisplayPolicy.needsForegroundTracking(listOf(DisplayProfile(AspectProfile.MiniMode)))
        )
        assertTrue(DisplayPolicy.needsForegroundTracking(listOf(tall)))
        assertTrue(
            DisplayPolicy.needsForegroundTracking(
                listOf(DisplayProfile(orientation = OrientationPref.Portrait))
            )
        )
    }

    // --- held originals ---

    private val plain =
        CurrentDisplay(
            null,
            null,
            autoRotate = true,
            userRotation = 1,
            ignoreOrientationRequest = false
        )

    @Test fun firstChangeSavesTheOriginalAndRestoreGivesItBack() {
        val on = Reconciler.plan(DisplayTarget(size = SizePx(720, 1280)), HeldState.NONE, plain)
        assertEquals(listOf(DisplayStep.SetSize(SizePx(720, 1280))), on.steps)
        assertEquals(HeldState.Held<SizePx>(null), on.heldAfter.size)
        assertEquals(on.heldAfter, on.heldDuring)
        val shown = plain.copy(forcedSize = SizePx(720, 1280))
        val off = Reconciler.plan(DisplayTarget.NONE, on.heldAfter, shown)
        assertEquals(listOf(DisplayStep.ClearSize), off.steps)
        assertTrue(off.heldAfter.isEmpty)
        // While the restore runs, the original is still on record.
        assertEquals(on.heldAfter, off.heldDuring)
    }

    @Test fun switchingSizesKeepsTheFirstOriginal() {
        val held = HeldState(size = HeldState.Held(SizePx(700, 700)))
        val now = plain.copy(forcedSize = SizePx(720, 960))
        val p = Reconciler.plan(DisplayTarget(size = SizePx(720, 1280)), held, now)
        assertEquals(listOf(DisplayStep.SetSize(SizePx(720, 1280))), p.steps)
        assertEquals(held.size, p.heldAfter.size)
        val back = Reconciler.plan(
            DisplayTarget.NONE,
            p.heldAfter,
            now.copy(forcedSize = SizePx(720, 1280))
        )
        assertEquals(listOf(DisplayStep.SetSize(SizePx(700, 700))), back.steps)
    }

    @Test fun rotationIsHeldAndGivenBack() {
        val lock = Reconciler.plan(
            DisplayTarget(rotation = RotationTarget.PORTRAIT),
            HeldState.NONE,
            plain
        )
        assertEquals(listOf(DisplayStep.SetRotation(RotationTarget.PORTRAIT)), lock.steps)
        assertEquals(RotationTarget(true, 1), lock.heldAfter.rotation?.value)
        val locked = plain.copy(autoRotate = false, userRotation = 0)
        val back = Reconciler.plan(DisplayTarget.NONE, lock.heldAfter, locked)
        assertEquals(listOf(DisplayStep.SetRotation(RotationTarget(true, 1))), back.steps)
    }

    @Test fun autoRotateDoesNotRewriteTheStoredRotation() {
        val p = Reconciler.plan(
            DisplayTarget(rotation = RotationTarget.AUTO),
            HeldState.NONE,
            plain
        )
        assertTrue(p.steps.isEmpty())
    }

    @Test fun nothingHeldAndNothingWantedDoesNothing() {
        val p = Reconciler.plan(DisplayTarget.NONE, HeldState.NONE, plain)
        assertTrue(p.steps.isEmpty())
        assertTrue(p.heldAfter.isEmpty)
    }

    @Test fun heldCodecRoundTrips() {
        val h = HeldState(
            size = HeldState.Held(null),
            density = HeldState.Held(320),
            rotation = HeldState.Held(RotationTarget(true, 1)),
            ignoreOrientationRequest = HeldState.Held(false)
        )
        assertEquals("s=-;d=320;r=1:1;i=0", HeldCodec.encode(h))
        assertEquals(h, HeldCodec.decode(HeldCodec.encode(h)))
        assertEquals(HeldState.NONE, HeldCodec.decode(null))
        assertEquals(
            HeldState(size = HeldState.Held(SizePx(720, 720))),
            HeldCodec.decode("s=720x720;r=9:9;i=x;d=0")
        )
    }

    // --- DisplayTools with fakes ---

    private class FakePort(var now: CurrentDisplay) : DisplayPort {
        val ran = mutableListOf<DisplayStep>()
        var fail: (DisplayStep) -> Boolean = { false }
        override fun initialSize() = SizePx(720, 720)
        override fun initialDensity() = 280
        override fun current() = now
        override fun run(step: DisplayStep) {
            check(!fail(step)) { "refused" }
            ran += step
            now = when (step) {
                is DisplayStep.SetSize -> now.copy(forcedSize = step.size)

                DisplayStep.ClearSize -> now.copy(forcedSize = null)

                is DisplayStep.SetDensity -> now.copy(forcedDensity = step.density)

                DisplayStep.ClearDensity -> now.copy(forcedDensity = null)

                is DisplayStep.SetRotation ->
                    now.copy(
                        autoRotate = step.rotation.autoRotate,
                        userRotation = step.rotation.userRotation
                    )

                is DisplayStep.SetIgnoreOrientationRequest -> now.copy(
                    ignoreOrientationRequest = step.ignore
                )
            }
        }
    }

    private class FakeSettings : ToolsSettings {
        override var systemRotation = SystemRotation.FollowApps
        override var favourites = CanvasCycle.DEFAULT_FAVOURITES
        val writes = mutableListOf<HeldState>()
        override var held: HeldState = HeldState.NONE
            set(value) {
                writes += value
                field = value
            }
    }

    private object OpenBackend : DisplayBackend {
        override val name = "test"
        override val availability = BackendAvailability.OPEN_UNVERIFIED
        override fun apply(pkg: String, plan: CanvasPlan, p: DisplayProfile) =
            ApplyResult.Applied("ok")
        override fun clear(pkg: String) = ApplyResult.Applied("cleared")
    }

    private val protected = mapOf(
        "org.sableos.launcher" to AppTraits("org.sableos.launcher", isHome = true),
        "org.sableos.titan2.keyboard" to
            AppTraits("org.sableos.titan2.keyboard", isInputMethod = true),
        "com.android.settings" to AppTraits("com.android.settings", isSystemUiOrSettings = true),
        "com.android.dialer" to AppTraits("com.android.dialer", isDialer = true)
    )

    private fun tools(
        port: FakePort,
        settings: FakeSettings = FakeSettings(),
        store: ProfileStore = InMemoryProfileStore()
    ) = DisplayTools(port, settings, store, ProfileController(store, OpenBackend)) { pkg ->
        protected[pkg] ?: AppTraits(
            pkg,
            isSystemUiOrSettings = TraitHeuristics.isProtectedSystemPackage(pkg),
            isAuthenticatorOrPayment = TraitHeuristics.nameLooksSensitive(pkg)
        )
    }

    private fun m(front: String?, interactive: Boolean = true) =
        Moment(true, true, interactive, front)

    @Test fun hotkeyCyclesSavesAndAppliesForTheAppInFront() {
        val port = FakePort(plain)
        val store = InMemoryProfileStore()
        val t = tools(port, store = store)
        val first = t.cycle(m("com.example.app"))
        assertEquals(CycleOutcome.Changed(TallCanvas.Tall3x4, "720x960 (3:4)"), first)
        assertEquals(TallCanvas.Tall3x4, store.get("com.example.app").canvas)
        assertEquals(SizePx(720, 960), port.now.forcedSize)
        t.cycle(m("com.example.app"))
        assertEquals(SizePx(720, 1280), port.now.forcedSize)
        val back = t.cycle(m("com.example.app"))
        assertEquals(CycleOutcome.Changed(TallCanvas.Native, "Native 720x720"), back)
        assertTrue(store.get("com.example.app").isNative)
        assertNull(port.now.forcedSize)
        assertEquals(
            "720x960 (3:4) · Example",
            CycleText.message(first, "Example")
        )
    }

    @Test fun hotkeyNeverReshapesStartKeyboardsSettingsSetupOrThePhone() {
        val port = FakePort(plain)
        val t = tools(port)
        listOf(
            "org.sableos.launcher",
            "org.sableos.titan2.keyboard",
            "com.android.settings",
            "com.android.systemui",
            "org.sableos.titan2.setup",
            "org.lineageos.setupwizard",
            "org.sableos.titan2.displaycompat",
            "com.android.dialer"
        ).forEach { pkg ->
            assertTrue(pkg, t.cycle(m(pkg)) is CycleOutcome.Blocked)
        }
        assertTrue(port.ran.isEmpty())
    }

    @Test fun hotkeyNeedsAnUnlockedAppGateAndFavourites() {
        val port = FakePort(plain)
        val settings = FakeSettings()
        val t = tools(port, settings)
        assertEquals(CycleOutcome.NoApp, t.cycle(m(null)))
        assertEquals(CycleOutcome.NoApp, t.cycle(m("com.example.app", interactive = false)))
        assertEquals(CycleOutcome.GateClosed, t.cycle(Moment(false, true, true, "com.example.app")))
        settings.favourites = emptySet()
        assertEquals(CycleOutcome.NoFavourites, t.cycle(m("com.example.app")))
        assertEquals(CycleOutcome.NeedsConfirmation, tools(port).cycle(m("com.example.wallet")))
    }

    @Test fun leavingTheAppOrLockingRestoresTheDisplay() {
        val port = FakePort(plain)
        val store = InMemoryProfileStore()
        store.put("com.example.app", tall)
        val settings = FakeSettings()
        val t = tools(port, settings, store)
        t.reconcile(m("com.example.app"))
        assertEquals(SizePx(720, 1280), port.now.forcedSize)
        t.reconcile(m("com.example.app", interactive = false))
        assertNull(port.now.forcedSize)
        t.reconcile(m("com.example.app"))
        t.reconcile(m("org.sableos.launcher"))
        assertNull(port.now.forcedSize)
        assertTrue(settings.held.isEmpty)
    }

    @Test fun aRefusedRestoreKeepsTheOriginalsForTheNextTry() {
        val port = FakePort(plain)
        val store = InMemoryProfileStore()
        store.put("com.example.app", tall)
        val settings = FakeSettings()
        val t = tools(port, settings, store)
        t.reconcile(m("com.example.app"))
        port.fail = { it == DisplayStep.ClearSize }
        assertEquals(listOf<DisplayStep>(DisplayStep.ClearSize), t.reconcile(m("com.other")))
        assertEquals(HeldState.Held<SizePx>(null), settings.held.size)
        port.fail = { false }
        assertTrue(t.restoreAll().isEmpty())
        assertNull(port.now.forcedSize)
        assertTrue(settings.held.isEmpty)
    }

    @Test fun originalsAreWrittenBeforeTheFirstChange() {
        val port = FakePort(plain)
        val settings = FakeSettings()
        val store = InMemoryProfileStore()
        store.put("com.example.app", tall)
        port.fail = { true } // the process "dies" at the first step
        tools(port, settings, store).reconcile(m("com.example.app"))
        assertEquals(HeldState.Held<SizePx>(null), settings.writes.first().size)
        assertEquals(HeldState.Held<SizePx>(null), settings.held.size)
    }

    @Test fun bootRestoreGivesBackWhateverWasHeld() {
        val port =
            FakePort(
                plain.copy(forcedSize = SizePx(720, 1440), autoRotate = false, userRotation = 0)
            )
        var held =
            HeldState(
                size = HeldState.Held(null),
                rotation = HeldState.Held(RotationTarget(true, 1))
            )
        val failed = PlanRunner.restoreAll(port, held) { held = it }
        assertTrue(failed.isEmpty())
        assertNull(port.now.forcedSize)
        assertTrue(port.now.autoRotate)
        assertEquals(1, port.now.userRotation)
        assertTrue(held.isEmpty)
        assertTrue(PlanRunner.restoreAll(port, held) { held = it }.isEmpty())
    }

    @Test fun anAspectProfileTurnsIgnoreOrientationOnAndResetGivesItBack() {
        val port = FakePort(plain)
        val store = InMemoryProfileStore()
        val settings = FakeSettings()
        val t = tools(port, settings, store)
        store.put("com.example.video", DisplayProfile(AspectProfile.Ratio16x9Letterbox))
        t.reconcile(m("org.sableos.launcher"))
        assertTrue(port.now.ignoreOrientationRequest)
        store.reset("com.example.video")
        t.reconcile(m("org.sableos.launcher"))
        assertFalse(port.now.ignoreOrientationRequest)
        assertTrue(settings.held.isEmpty)
    }
    // --- validation ---

    private fun codes(t: AppTraits, p: DisplayProfile) =
        ProfileValidator.validate(t, p).issues.map { it.code }

    @Test fun canvasRulesFollowTheProtectedAppRules() {
        assertTrue(ProfileValidator.validate(AppTraits("x", isDialer = true), tall).blocked)
        assertTrue(ProfileValidator.validate(AppTraits("x", isInputMethod = true), tall).blocked)
        assertTrue(ProfileValidator.validate(AppTraits("x", isHome = true), tall).blocked)
        val auth = ProfileValidator.validate(AppTraits("x", isAuthenticatorOrPayment = true), tall)
        assertFalse(auth.blocked)
        assertEquals(listOf("AUTH_PAYMENT_CANVAS"), auth.ackRequired.map { it.code })
        assertTrue("CAMERA_CANVAS" in codes(AppTraits("x", isCamera = true), tall))
        assertTrue(
            "CANVAS_WITH_ASPECT" in
                codes(AppTraits("x"), tall.copy(profile = AspectProfile.MiniMode))
        )
        assertTrue("CANVAS_LIVE" in codes(AppTraits("x"), tall))
    }

    @Test fun autoIsLiveAndLandscapeWithAnAspectRatioIsFlagged() {
        val auto = codes(AppTraits("x"), DisplayProfile(orientation = OrientationPref.Auto))
        assertEquals(listOf("AUTO_LIVE"), auto)
        val mini = DisplayProfile(AspectProfile.MiniMode, orientation = OrientationPref.Landscape)
        assertTrue("ASPECT_KEEPS_PORTRAIT" in codes(AppTraits("x"), mini))
    }

    @Test fun setupAndThisAppAreProtectedPackages() {
        listOf(
            "org.sableos.titan2.setup",
            "org.lineageos.setupwizard",
            "com.android.provision",
            "com.android.launcher3",
            "org.sableos.titan2.displaycompat"
        ).forEach { assertTrue(it, TraitHeuristics.isProtectedSystemPackage(it)) }
    }
}
