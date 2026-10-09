package org.sableos.titan2.displaycompat.core

/**
 * Display compatibility model (APP_DISPLAY_COMPATIBILITY_PROFILES_UX).
 * Pure Kotlin: no Android imports so it is unit-testable on a JVM.
 */

enum class AspectProfile(val id: String) {
    Native("native"),
    MiniMode("mini"),
    Ratio16x9Letterbox("16_9_letterbox"),
    Ratio16x9Fill("16_9_fill"),
    Ratio4x3Letterbox("4_3_letterbox"),
    SquareSafe("square_safe"),
    FullscreenMedia("fullscreen_media"),
    KeyboardSafe("keyboard_safe"),
    Custom("custom");

    companion object {
        fun fromId(id: String?): AspectProfile = entries.firstOrNull { it.id == id } ?: Native
    }
}

enum class OrientationPref { Default, Portrait, Landscape, Auto }
enum class ScalingPref { Default, Fit, Fill }
enum class LetterboxBackground { System, Dark, Light, Accent }
enum class Tri { Default, On, Off }
enum class BarsPref { Default, Show, Hide }

data class SizePx(val w: Int, val h: Int) {
    init {
        require(w > 0 && h > 0) { "size must be positive: ${w}x$h" }
    }
    val aspect: Float get() = w.toFloat() / h
}

data class RectPx(val l: Int, val t: Int, val r: Int, val b: Int) {
    val w: Int get() = r - l
    val h: Int get() = b - t
}

data class Ratio(val w: Int, val h: Int) {
    init {
        require(w > 0 && h > 0) { "ratio must be positive: $w:$h" }
    }
    val value: Float get() = w.toFloat() / h
    fun inverse() = Ratio(h, w)
    override fun toString() = "$w:$h"
    companion object {
        val R16_9 = Ratio(16, 9)
        val R4_3 = Ratio(4, 3)
        const val MIN = 0.5f
        const val MAX = 2.4f
    }
}

/** One per-user per-package profile. Default is [AspectProfile.Native], which means "do nothing". */
data class DisplayProfile(
    val profile: AspectProfile = AspectProfile.Native,
    val orientation: OrientationPref = OrientationPref.Default,
    val scaling: ScalingPref = ScalingPref.Default,
    val letterboxBackground: LetterboxBackground = LetterboxBackground.System,
    val keyboardSafeArea: Tri = Tri.Default,
    val statusNav: BarsPref = BarsPref.Default,
    val fullscreenMediaException: Boolean = true,
    val customRatio: Ratio? = null,
    /**
     * Stretching distorts text, maps, camera previews, games and auth screens.
     * Off by default; on requires a warning.
     */
    val stretch: Boolean = false
) {
    val isNative: Boolean get() = this == NATIVE
    companion object {
        val NATIVE = DisplayProfile()
    }
}

/** Facts supplied by the Android glue about an app; the core never queries the platform. */
data class AppTraits(
    val packageName: String,
    val isHome: Boolean = false,
    val isDialer: Boolean = false,
    val isInputMethod: Boolean = false,
    val isAccessibilityService: Boolean = false,
    val isSystemUiOrSettings: Boolean = false,
    val isAuthenticatorOrPayment: Boolean = false,
    val isCamera: Boolean = false,
    val isMedia: Boolean = false
)
