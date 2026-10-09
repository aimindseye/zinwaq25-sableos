package org.sableos.titan2.displaycompat.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayCompatCoreTest {
    private val titan2 = SizePx(1440, 1440)
    private val elite = SizePx(1080, 1200)
    private val app = AppTraits("com.example.app")

    @Test fun nativeIsNoOpAndFullDisplay() {
        val p = CanvasPlanner.plan(titan2, DisplayProfile.NATIVE)
        assertEquals(RectPx(0, 0, 1440, 1440), p.canvas)
        assertTrue(p.bars.isEmpty())
        assertEquals(0f, p.cropFraction, 0f)
    }

    @Test fun letterbox16x9OnSquareIsCentredLandscape() {
        val p = CanvasPlanner.plan(titan2, DisplayProfile(AspectProfile.Ratio16x9Letterbox))
        assertEquals(1440, p.canvas.w)
        assertEquals(810, p.canvas.h)
        assertEquals(315, p.canvas.t)
        assertEquals(2, p.bars.size)
    }

    @Test fun letterbox16x9PortraitIsNarrowColumn() {
        val p = CanvasPlanner.plan(
            titan2,
            DisplayProfile(AspectProfile.Ratio16x9Letterbox, orientation = OrientationPref.Portrait)
        )
        assertEquals(810, p.canvas.w)
        assertEquals(1440, p.canvas.h)
        assertEquals(OrientationPref.Portrait, p.lockedOrientation)
    }

    @Test fun letterbox4x3OnElite() {
        val p = CanvasPlanner.plan(elite, DisplayProfile(AspectProfile.Ratio4x3Letterbox))
        assertEquals(1080, p.canvas.w)
        assertEquals(810, p.canvas.h)
    }

    @Test fun fillCropsAndReportsFraction() {
        val p = CanvasPlanner.plan(titan2, DisplayProfile(AspectProfile.Ratio16x9Fill))
        assertEquals(0.4375f, p.cropFraction, 0.001f) // 1 - 9/16
        assertTrue(p.bars.isEmpty())
    }

    @Test fun miniModeIsNoOpUntilSizeCaptured() {
        val noSize = CanvasPlanner.plan(titan2, DisplayProfile(AspectProfile.MiniMode))
        assertEquals(RectPx(0, 0, 1440, 1440), noSize.canvas)
        assertTrue(noSize.note.contains("not captured"))
        val sized = CanvasPlanner.plan(
            titan2,
            DisplayProfile(AspectProfile.MiniMode),
            miniSize = SizePx(1080, 1080)
        )
        assertEquals(1080, sized.canvas.w)
        assertEquals(180, sized.canvas.l)
    }

    @Test fun keyboardSafeReservesBottom() {
        assertEquals(
            576,
            CanvasPlanner.plan(titan2, DisplayProfile(AspectProfile.KeyboardSafe)).reservedBottomPx
        )
        assertEquals(
            0,
            CanvasPlanner.plan(
                titan2,
                DisplayProfile(AspectProfile.KeyboardSafe, keyboardSafeArea = Tri.Off)
            ).reservedBottomPx
        )
    }

    @Test fun customRatioOutOfRangeIsNoOp() {
        val p = CanvasPlanner.plan(
            titan2,
            DisplayProfile(AspectProfile.Custom, customRatio = Ratio(10, 1))
        )
        assertEquals(RectPx(0, 0, 1440, 1440), p.canvas)
    }

    @Test fun validatorBlocksProtectedApps() {
        val sys = AppTraits("com.android.settings", isSystemUiOrSettings = true)
        assertTrue(
            ProfileValidator.validate(sys, DisplayProfile(AspectProfile.Ratio16x9Letterbox)).blocked
        )
        assertTrue(
            ProfileValidator.validate(
                AppTraits("ime", isInputMethod = true),
                DisplayProfile(AspectProfile.SquareSafe)
            ).blocked
        )
        assertTrue(
            ProfileValidator.validate(
                AppTraits("home", isHome = true),
                DisplayProfile(AspectProfile.MiniMode)
            ).blocked
        )
    }

    @Test fun validatorProtectsAuthPaymentCameraDialer() {
        val auth = AppTraits("a", isAuthenticatorOrPayment = true)
        assertTrue(
            ProfileValidator.validate(auth, DisplayProfile(AspectProfile.Ratio16x9Fill)).blocked
        )
        val letterbox = ProfileValidator.validate(
            auth,
            DisplayProfile(AspectProfile.Ratio16x9Letterbox)
        )
        assertFalse(letterbox.blocked)
        assertTrue(letterbox.ackRequired.isNotEmpty())
        assertTrue(
            ProfileValidator.validate(
                AppTraits("c", isCamera = true),
                DisplayProfile(AspectProfile.Ratio16x9Fill)
            ).blocked
        )
        assertTrue(
            ProfileValidator.validate(
                AppTraits("d", isDialer = true),
                DisplayProfile(AspectProfile.Ratio4x3Letterbox)
            ).blocked
        )
        assertFalse(
            ProfileValidator.validate(
                AppTraits("d", isDialer = true),
                DisplayProfile(AspectProfile.KeyboardSafe)
            ).blocked
        )
    }

    @Test fun fillAndStretchNeedAck() {
        assertTrue(
            ProfileValidator.validate(
                app,
                DisplayProfile(AspectProfile.Ratio16x9Fill)
            ).ackRequired.isNotEmpty()
        )
        assertTrue(
            ProfileValidator.validate(
                app,
                DisplayProfile(AspectProfile.SquareSafe, stretch = true)
            ).ackRequired.isNotEmpty()
        )
        assertTrue(
            ProfileValidator.validate(
                app,
                DisplayProfile(AspectProfile.Ratio16x9Letterbox)
            ).issues.isEmpty()
        )
    }

    @Test fun customIsGatedAndRangeChecked() {
        val c = DisplayProfile(AspectProfile.Custom, customRatio = Ratio(3, 2))
        assertTrue(ProfileValidator.validate(app, c, advancedEnabled = false).blocked)
        assertFalse(ProfileValidator.validate(app, c, advancedEnabled = true).blocked)
        assertTrue(
            ProfileValidator.validate(
                app,
                c.copy(customRatio = Ratio(9, 1)),
                advancedEnabled = true
            ).blocked
        )
        assertTrue(
            ProfileValidator.validate(
                app,
                c.copy(customRatio = null),
                advancedEnabled = true
            ).blocked
        )
    }

    @Test fun codecRoundTripsAndToleratesGarbage() {
        val p =
            DisplayProfile(
                AspectProfile.Custom, OrientationPref.Portrait, ScalingPref.Fit,
                LetterboxBackground.Dark, Tri.On, BarsPref.Hide, false,
                Ratio(
                    5,
                    4
                ),
                true
            )
        assertEquals(p, ProfileCodec.decode(ProfileCodec.encode(p)))
        assertEquals(DisplayProfile.NATIVE, ProfileCodec.decode("garbage"))
        assertEquals(DisplayProfile.NATIVE, ProfileCodec.decode("v=2;p=custom"))
        assertEquals(AspectProfile.Native, ProfileCodec.decode("v=1;p=bogus").profile)
        assertNull(ProfileCodec.decode("v=1;p=custom;r=0:5").customRatio)
    }

    @Test fun controllerRefusesInvalidAndUnacknowledged() {
        val store = InMemoryProfileStore()
        val c = ProfileController(store, NoOpBackend)
        val blocked = c.save(
            AppTraits("x", isSystemUiOrSettings = true),
            DisplayProfile(AspectProfile.SquareSafe),
            titan2,
            false,
            true
        )
        assertFalse(blocked.saved)
        val noAck = c.save(app, DisplayProfile(AspectProfile.Ratio16x9Fill), titan2, false, false)
        assertFalse(noAck.saved)
        assertTrue(store.packages().isEmpty())
        val ok = c.save(app, DisplayProfile(AspectProfile.Ratio16x9Fill), titan2, false, true)
        assertTrue(ok.saved)
        assertEquals(ApplyResult.NoOp, ok.result)
        assertEquals(setOf(app.packageName), store.packages())
    }

    @Test fun resetWorksOutsideAppAndNativeIsNotStored() {
        val store = InMemoryProfileStore()
        val c = ProfileController(store, NoOpBackend)
        c.save(app, DisplayProfile(AspectProfile.SquareSafe), titan2, false, true)
        c.save(AppTraits("b"), DisplayProfile(AspectProfile.Ratio4x3Letterbox), titan2, false, true)
        c.reset(app.packageName)
        assertEquals(setOf("b"), store.packages())
        assertEquals(1, c.resetAll())
        assertTrue(store.packages().isEmpty())
        c.save(app, DisplayProfile.NATIVE, titan2, false, true)
        assertTrue(store.packages().isEmpty())
    }

    @Test fun nameHeuristics() {
        assertTrue(TraitHeuristics.nameLooksSensitive("com.google.android.apps.authenticator2"))
        assertTrue(TraitHeuristics.nameLooksSensitive("com.chase.banking.app"))
        assertFalse(TraitHeuristics.nameLooksSensitive("org.mozilla.firefox"))
        assertTrue(TraitHeuristics.isProtectedSystemPackage("com.android.settings"))
        assertTrue(TraitHeuristics.isProtectedSystemPackage("com.android.systemui.plugin"))
        assertFalse(TraitHeuristics.isProtectedSystemPackage("com.android.settingsfake"))
    }
}
