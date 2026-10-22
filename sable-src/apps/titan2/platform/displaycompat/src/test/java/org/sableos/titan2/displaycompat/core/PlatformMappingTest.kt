package org.sableos.titan2.displaycompat.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The platform values the gated backend sends. Constants checked against LineageOS 23.2 frameworks/base
 * (PackageManager.USER_MIN_ASPECT_RATIO_*, ActivityInfo orientation change ids).
 */
class PlatformMappingTest {
    @Test fun aspectConstantsMatchAosp() {
        assertEquals(0, UserMinAspectRatio.UNSET)
        assertEquals(2, UserMinAspectRatio.DISPLAY_SIZE)
        assertEquals(3, UserMinAspectRatio.RATIO_4_3)
        assertEquals(4, UserMinAspectRatio.RATIO_16_9)
        assertEquals(5, UserMinAspectRatio.RATIO_3_2)
        assertEquals(6, UserMinAspectRatio.FULLSCREEN)
    }

    @Test fun orientationChangeIdsMatchAosp() {
        assertEquals(265464455L, OrientationOverrides.OVERRIDE_ANY_ORIENTATION)
        assertEquals(265452344L, OrientationOverrides.OVERRIDE_UNDEFINED_ORIENTATION_TO_PORTRAIT)
        assertEquals(
            266124927L,
            OrientationOverrides.OVERRIDE_LANDSCAPE_ORIENTATION_TO_REVERSE_LANDSCAPE
        )
    }

    @Test fun profilesMapToMatchingRatio() {
        assertEquals(0, UserMinAspectRatio.forProfile(AspectProfile.Native))
        assertEquals(4, UserMinAspectRatio.forProfile(AspectProfile.Ratio16x9Letterbox))
        assertEquals(3, UserMinAspectRatio.forProfile(AspectProfile.Ratio4x3Letterbox))
        assertEquals(6, UserMinAspectRatio.forProfile(AspectProfile.FullscreenMedia))
        assertEquals(2, UserMinAspectRatio.forProfile(AspectProfile.SquareSafe))
        assertEquals(5, UserMinAspectRatio.forProfile(AspectProfile.MiniMode))
    }

    @Test fun customRatioMapsOnlyToPlatformRatios() {
        assertEquals(4, UserMinAspectRatio.forProfile(AspectProfile.Custom, Ratio(9, 16)))
        assertEquals(5, UserMinAspectRatio.forProfile(AspectProfile.Custom, Ratio(3, 2)))
        assertEquals(3, UserMinAspectRatio.forProfile(AspectProfile.Custom, Ratio(4, 3)))
        assertNull(UserMinAspectRatio.forProfile(AspectProfile.Custom, Ratio(21, 9)))
        assertNull(UserMinAspectRatio.forProfile(AspectProfile.Custom, null))
    }

    @Test fun profilesWithoutPlatformEquivalentStayStoredOnly() {
        listOf(AspectProfile.Ratio16x9Fill, AspectProfile.KeyboardSafe).forEach {
            assertNull(it.name, UserMinAspectRatio.forProfile(it))
        }
    }

    @Test fun portraitAndLandscapeUseTheOverrideAnyPairs() {
        val portrait = PlatformMapping.request(
            DisplayProfile(orientation = OrientationPref.Portrait)
        )
        assertEquals(
            setOf(
                OrientationOverrides.OVERRIDE_ANY_ORIENTATION,
                OrientationOverrides.OVERRIDE_UNDEFINED_ORIENTATION_TO_PORTRAIT
            ),
            portrait.enableOrientation
        )
        assertEquals(
            setOf(OrientationOverrides.OVERRIDE_LANDSCAPE_ORIENTATION_TO_REVERSE_LANDSCAPE),
            portrait.disableOrientation
        )
        assertTrue(portrait.hasEffect)
        val landscape = PlatformMapping.request(
            DisplayProfile(orientation = OrientationPref.Landscape)
        )
        assertTrue(
            OrientationOverrides.OVERRIDE_LANDSCAPE_ORIENTATION_TO_REVERSE_LANDSCAPE in
                landscape.enableOrientation
        )
        assertTrue(landscape.describe().contains("orientation landscape"))
    }

    @Test fun autoOrientationIsReportedNotApplied() {
        val r = PlatformMapping.request(DisplayProfile(orientation = OrientationPref.Auto))
        assertTrue(r.enableOrientation.isEmpty())
        assertEquals(OrientationOverrides.ALL, r.disableOrientation)
        assertTrue(r.notApplied.contains("orientation auto"))
        assertFalse(r.hasEffect)
    }

    @Test fun miniModeSendsThreeByTwo() {
        val r = PlatformMapping.request(DisplayProfile(AspectProfile.MiniMode))
        assertEquals(UserMinAspectRatio.RATIO_3_2, r.minAspectRatio)
        assertTrue(r.notApplied.isEmpty())
        assertTrue(r.describe().startsWith("aspect 3:2"))
    }

    @Test fun storedOnlyProfileStillClearsThePlatform() {
        val r = PlatformMapping.request(DisplayProfile(AspectProfile.KeyboardSafe))
        assertEquals(UserMinAspectRatio.UNSET, r.minAspectRatio)
        assertEquals(OrientationOverrides.ALL, r.disableOrientation)
        assertFalse(r.hasEffect)
        assertEquals(listOf("keyboard safe"), r.notApplied)
    }

    @Test fun cosmeticFieldsAreListedAsStoredOnly() {
        val r = PlatformMapping.request(
            DisplayProfile(
                AspectProfile.Ratio16x9Letterbox,
                scaling = ScalingPref.Fit,
                letterboxBackground = LetterboxBackground.Dark
            )
        )
        assertEquals(UserMinAspectRatio.RATIO_16_9, r.minAspectRatio)
        assertEquals(listOf("scaling", "letterbox background"), r.notApplied)
    }

    @Test fun clearRequestUnsetsEverything() {
        assertEquals(UserMinAspectRatio.UNSET, PlatformRequest.CLEAR.minAspectRatio)
        assertEquals(OrientationOverrides.ALL, PlatformRequest.CLEAR.disableOrientation)
    }
}
