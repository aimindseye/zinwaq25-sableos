package org.sableos.titan2.displaycompat.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCompatTest {
    private val native = SizePx(720, 720)
    private val facts = DisplayFacts(native, 280)

    private fun situation(p: DisplayProfile, landscape: Boolean = false) = Situation(
        gateOpen = true,
        setupComplete = true,
        interactive = true,
        frontPackage = "com.example",
        frontProfile = p,
        systemRotation = SystemRotation.FollowApps,
        anyAspectProfile = false,
        landscape = landscape
    )

    @Test
    fun layoutsAreSquareTallPhoneOrCustom() {
        assertEquals(AppLayout.Square, AppLayout.of(DisplayProfile.NATIVE))
        assertEquals(AppLayout.TallPhone, AppLayout.of(DisplayProfile(canvas = TallCanvas.Tall1x2)))
        assertEquals(AppLayout.Custom, AppLayout.of(DisplayProfile(canvas = TallCanvas.Tall9x16)))
        assertEquals(
            AppLayout.Custom,
            AppLayout.of(DisplayProfile(profile = AspectProfile.Ratio4x3Letterbox))
        )
        // App size and the video choice do not change the layout.
        assertEquals(
            AppLayout.TallPhone,
            AppLayout.of(
                DisplayProfile(
                    canvas = TallCanvas.Tall1x2,
                    appSize = AppSize.Smaller,
                    fullscreenMediaException = false
                )
            )
        )
    }

    @Test
    fun choosingALayoutKeepsSizeAndVideo() {
        val p = DisplayProfile(appSize = AppSize.Smallest, fullscreenMediaException = false)
        val tall = AppLayout.apply(p, AppLayout.TallPhone)
        assertEquals(TallCanvas.Tall1x2, tall.canvas)
        assertEquals(AppSize.Smallest, tall.appSize)
        assertFalse(tall.fullscreenMediaException)
        assertEquals(TallCanvas.Native, AppLayout.apply(tall, AppLayout.Square).canvas)
        val custom = DisplayProfile(profile = AspectProfile.Ratio4x3Letterbox)
        assertEquals(custom, AppLayout.apply(custom, AppLayout.Custom))
    }

    @Test
    fun appSizeScalesTheDensityOnlyWhileTheAppIsInFront() {
        assertEquals(
            238,
            DisplayPolicy.target(
                situation(DisplayProfile(appSize = AppSize.Smaller)),
                facts
            ).density
        )
        assertEquals(
            210,
            DisplayPolicy.target(
                situation(DisplayProfile(appSize = AppSize.Smallest)),
                facts
            ).density
        )
        assertNull(DisplayPolicy.target(situation(DisplayProfile.NATIVE), facts).density)
        val away = situation(DisplayProfile(appSize = AppSize.Smallest)).copy(frontPackage = null)
        assertNull(DisplayPolicy.target(away, facts).density)
        assertTrue(
            DisplayPolicy.needsForegroundTracking(listOf(DisplayProfile(appSize = AppSize.Smaller)))
        )
    }

    @Test
    fun sidewaysVideoFitDropsTheTallCanvas() {
        val tall = DisplayProfile(canvas = TallCanvas.Tall1x2)
        assertEquals(SizePx(720, 1440), DisplayPolicy.target(situation(tall), facts).size)
        assertNull(DisplayPolicy.target(situation(tall, landscape = true), facts).size)
        val keep = tall.copy(fullscreenMediaException = false)
        assertEquals(
            SizePx(720, 1440),
            DisplayPolicy.target(situation(keep, landscape = true), facts).size
        )
    }

    @Test
    fun theCodecKeepsAppSize() {
        val p = DisplayProfile(canvas = TallCanvas.Tall1x2, appSize = AppSize.Smaller)
        assertEquals(p, ProfileCodec.decode(ProfileCodec.encode(p)))
        assertEquals(
            AppSize.Full,
            ProfileCodec.decode(
                ProfileCodec.encode(DisplayProfile(canvas = TallCanvas.Tall1x2))
            ).appSize
        )
    }

    @Test
    fun presetsParseAndAreGivenOnce() {
        val presets =
            Presets.parse(
                """
                # comment
                com.google.android.youtube	tall	fit
                com.google.android.apps.maps	square
                com.example.small	square	keep	85
                not a package	tall
                com.example.bad	round
                """.trimIndent()
            )
        assertEquals(
            setOf(
                "com.google.android.youtube",
                "com.google.android.apps.maps",
                "com.example.small"
            ),
            presets.keys
        )
        assertEquals(
            AppLayout.TallPhone,
            AppLayout.of(presets.getValue("com.google.android.youtube"))
        )
        assertTrue(presets.getValue("com.google.android.apps.maps").isNative)
        assertEquals(AppSize.Smaller, presets.getValue("com.example.small").appSize)
        assertFalse(presets.getValue("com.example.small").fullscreenMediaException)
        val installed = listOf("com.google.android.youtube", "com.example.small", "com.other")
        assertEquals(
            listOf("com.google.android.youtube", "com.example.small"),
            Presets.toSeed(installed, presets, emptySet(), emptySet())
        )
        assertEquals(
            listOf("com.example.small"),
            Presets.toSeed(installed, presets, setOf("com.google.android.youtube"), emptySet())
        )
        assertEquals(
            emptyList<String>(),
            Presets.toSeed(
                installed,
                presets,
                setOf("com.google.android.youtube"),
                setOf("com.example.small")
            )
        )
    }

    @Test
    fun theShippedPresetsMatchThePlan() {
        val text = java.io.File("src/main/res/raw/app_compat_presets.tsv").readText()
        val presets = Presets.parse(text)
        listOf(
            "com.google.android.youtube",
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.facebook.katana",
            "com.tubitv",
            "com.google.android.videos",
            "com.google.android.apps.youtube.music"
        ).forEach {
            assertEquals(it, AppLayout.TallPhone, AppLayout.of(presets.getValue(it)))
            assertEquals(it, LandscapeVideo.Fit, LandscapeVideo.of(presets.getValue(it)))
        }
        assertEquals(
            AppLayout.Square,
            AppLayout.of(presets.getValue("com.google.android.apps.maps"))
        )
        assertEquals(
            AppLayout.Square,
            AppLayout.of(presets.getValue("com.google.android.projection.gearhead"))
        )
    }

    @Test
    fun thePageStepsBetweenChoices() {
        val square = DisplayProfile.NATIVE
        assertEquals(AppLayout.TallPhone, AppLayout.of(AppPage.layoutStep(square, 1)))
        assertNull(AppPage.step(square, AppPage.Row.Layout, -1))
        assertEquals(AppSize.Smaller, AppPage.sizeStep(square, 1).appSize)
        assertNull(AppPage.step(square, AppPage.Row.Size, -1))
        assertFalse(AppPage.videoStep(square).fullscreenMediaException)
        assertNull(AppPage.step(square, AppPage.Row.Reset, 1))
        val custom = DisplayProfile(canvas = TallCanvas.Tall9x16)
        assertEquals(AppLayout.Square, AppLayout.of(AppPage.layoutStep(custom, -1)))
    }

    @Test
    fun olderCodeLoadingListMatchesTheFramework() {
        assertEquals("persist.sys.sable.legacy_dcl", CodeLoading.PROPERTY)
        assertTrue(CodeLoading.allows("", "tv.pluto.android"))
        assertFalse(CodeLoading.allows("-", "tv.pluto.android"))
        assertEquals("com.example,tv.pluto.android", CodeLoading.with("", "com.example", true))
        assertEquals("-", CodeLoading.with("", "tv.pluto.android", false))
        assertEquals("tv.pluto.android", CodeLoading.with("-", "tv.pluto.android", true))
        assertNull(CodeLoading.with("", "a".repeat(100), true))
    }
}
