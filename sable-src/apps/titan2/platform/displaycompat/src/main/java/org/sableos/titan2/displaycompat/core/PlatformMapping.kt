package org.sableos.titan2.displaycompat.core

/**
 * Values of the hidden `PackageManager.USER_MIN_ASPECT_RATIO_*` constants, checked against LineageOS 23.2
 * (Android 16) `frameworks/base` at f4ed08a0: UNSET 0, SPLIT_SCREEN 1, DISPLAY_SIZE 2, 4_3 3, 16_9 4, 3_2 5,
 * FULLSCREEN 6, APP_DEFAULT 7. The public SDK does not expose them, so they are literals here.
 * The platform call is `IPackageManager.setUserMinAspectRatio(String packageName, int userId, int aspectRatio)`.
 */
object UserMinAspectRatio {
    const val UNSET = 0
    const val DISPLAY_SIZE = 2
    const val RATIO_4_3 = 3
    const val RATIO_16_9 = 4
    const val RATIO_3_2 = 5
    const val FULLSCREEN = 6

    /** Platform value for a profile with a real platform equivalent, else null (stored only). */
    fun forProfile(profile: AspectProfile, custom: Ratio? = null): Int? = when (profile) {
        AspectProfile.Native -> UNSET
        AspectProfile.Ratio16x9Letterbox -> RATIO_16_9
        AspectProfile.Ratio4x3Letterbox -> RATIO_4_3
        AspectProfile.FullscreenMedia -> FULLSCREEN
        AspectProfile.SquareSafe -> DISPLAY_SIZE
        // Mini mode on a square display: the app gets a 3:2 phone-shaped window (480x720 on the Q25).
        AspectProfile.MiniMode -> RATIO_3_2
        AspectProfile.Custom -> forRatio(custom)
        AspectProfile.Ratio16x9Fill, AspectProfile.KeyboardSafe -> null
    }

    /** A custom ratio maps only when it is one the platform offers, in either orientation. */
    private fun forRatio(r: Ratio?): Int? {
        if (r == null) return null
        val wide = if (r.w >= r.h) r else r.inverse()
        return when {
            wide.sameAs(Ratio.R16_9) -> RATIO_16_9
            wide.sameAs(Ratio.R4_3) -> RATIO_4_3
            wide.sameAs(R3_2) -> RATIO_3_2
            else -> null
        }
    }

    private val R3_2 = Ratio(3, 2)

    private fun Ratio.sameAs(o: Ratio): Boolean = w.toLong() * o.h == h.toLong() * o.w

    fun label(value: Int): String = when (value) {
        UNSET -> "app default"
        DISPLAY_SIZE -> "display size"
        RATIO_4_3 -> "4:3"
        RATIO_16_9 -> "16:9"
        RATIO_3_2 -> "3:2"
        FULLSCREEN -> "fullscreen"
        else -> "value $value"
    }
}

/**
 * Per-app orientation through the platform's overridable app-compat changes (`android.content.pm.ActivityInfo`,
 * all `@Overridable`, set with `IPlatformCompat.putOverridesOnReleaseBuilds`). They are per package, for every
 * user, and take effect the next time the app starts.
 */
object OrientationOverrides {
    const val OVERRIDE_ANY_ORIENTATION = 265464455L
    const val OVERRIDE_UNDEFINED_ORIENTATION_TO_PORTRAIT = 265452344L
    const val OVERRIDE_LANDSCAPE_ORIENTATION_TO_REVERSE_LANDSCAPE = 266124927L

    /** Every change this app may set; the ones a profile does not enable are removed. */
    val ALL: Set<Long> = setOf(
        OVERRIDE_ANY_ORIENTATION,
        OVERRIDE_UNDEFINED_ORIENTATION_TO_PORTRAIT,
        OVERRIDE_LANDSCAPE_ORIENTATION_TO_REVERSE_LANDSCAPE
    )

    /** Changes to enable for an orientation, or null when the platform has no per-app override for it. */
    fun forOrientation(o: OrientationPref): Set<Long>? = when (o) {
        OrientationPref.Default -> emptySet()

        OrientationPref.Portrait -> setOf(OVERRIDE_ANY_ORIENTATION, OVERRIDE_UNDEFINED_ORIENTATION_TO_PORTRAIT)

        // The platform's only landscape override is reverse landscape (270 degrees).
        OrientationPref.Landscape ->
            setOf(OVERRIDE_ANY_ORIENTATION, OVERRIDE_LANDSCAPE_ORIENTATION_TO_REVERSE_LANDSCAPE)

        // OVERRIDE_ANY_ORIENTATION_TO_USER only acts on displays that ignore orientation requests; a phone does not.
        OrientationPref.Auto -> null
    }
}

/**
 * Everything one save asks of the platform. It always carries a full state (an aspect value, the orientation
 * changes to enable and the ones to remove), so switching from a ratio to a stored-only profile clears the ratio.
 */
data class PlatformRequest(
    val minAspectRatio: Int,
    val enableOrientation: Set<Long>,
    val notApplied: List<String>
) {
    val disableOrientation: Set<Long> get() = OrientationOverrides.ALL - enableOrientation

    /** True when the platform is asked to change something this profile wants. */
    val hasEffect: Boolean
        get() = minAspectRatio != UserMinAspectRatio.UNSET || enableOrientation.isNotEmpty()

    fun describe(): String = buildString {
        append("aspect ").append(UserMinAspectRatio.label(minAspectRatio))
        append(", orientation ").append(orientationLabel())
        if (notApplied.isNotEmpty()) append("; stored only: ").append(notApplied.joinToString(", "))
    }

    private fun orientationLabel(): String = when {
        OrientationOverrides.OVERRIDE_UNDEFINED_ORIENTATION_TO_PORTRAIT in enableOrientation -> "portrait"
        OrientationOverrides.OVERRIDE_LANDSCAPE_ORIENTATION_TO_REVERSE_LANDSCAPE in enableOrientation ->
            "landscape"
        else -> "app default"
    }

    companion object {
        /** Clears both platform settings for a package. */
        val CLEAR = PlatformRequest(UserMinAspectRatio.UNSET, emptySet(), emptyList())
    }
}

object PlatformMapping {
    fun request(p: DisplayProfile): PlatformRequest {
        val notApplied = mutableListOf<String>()
        val aspect = UserMinAspectRatio.forProfile(p.profile, p.customRatio)
        if (aspect == null) notApplied += p.profile.id.replace('_', ' ')
        val orientation = OrientationOverrides.forOrientation(p.orientation)
        if (orientation == null) notApplied += "orientation " + p.orientation.name.lowercase()
        if (p.scaling != ScalingPref.Default) notApplied += "scaling"
        if (p.letterboxBackground != LetterboxBackground.System) notApplied += "letterbox background"
        if (p.keyboardSafeArea != Tri.Default) notApplied += "keyboard-safe area"
        if (p.statusNav != BarsPref.Default) notApplied += "status/nav bars"
        if (p.stretch) notApplied += "stretch"
        return PlatformRequest(aspect ?: UserMinAspectRatio.UNSET, orientation.orEmpty(), notApplied)
    }
}
