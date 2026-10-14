package org.sableos.titan2.displaycompat.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.sableos.titan2.displaycompat.core.AspectProfile

/** The gated backend must hand the platform the AOSP USER_MIN_ASPECT_RATIO_* value that matches each profile. */
class UserMinAspectRatioTest {
    @Test fun constantsMatchAosp() {
        assertEquals(0, UserMinAspectRatio.UNSET)
        assertEquals(2, UserMinAspectRatio.DISPLAY_SIZE)
        assertEquals(3, UserMinAspectRatio.RATIO_4_3)
        assertEquals(4, UserMinAspectRatio.RATIO_16_9)
        assertEquals(6, UserMinAspectRatio.FULLSCREEN)
    }

    @Test fun profilesMapToMatchingRatio() {
        assertEquals(4, UserMinAspectRatio.forProfile(AspectProfile.Ratio16x9Letterbox))
        assertEquals(3, UserMinAspectRatio.forProfile(AspectProfile.Ratio4x3Letterbox))
        assertEquals(6, UserMinAspectRatio.forProfile(AspectProfile.FullscreenMedia))
        assertEquals(2, UserMinAspectRatio.forProfile(AspectProfile.SquareSafe))
    }

    @Test fun profilesWithoutPlatformEquivalentStayStoredOnly() {
        listOf(
            AspectProfile.Native,
            AspectProfile.MiniMode,
            AspectProfile.Ratio16x9Fill,
            AspectProfile.KeyboardSafe,
            AspectProfile.Custom
        ).forEach { assertNull(it.name, UserMinAspectRatio.forProfile(it)) }
    }
}
