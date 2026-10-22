package org.sableos.titan2.displaycompat.core

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Result of planning a profile on a physical display.
 * Used for the live preview and as the input a backend would apply.
 */
data class CanvasPlan(
    val display: SizePx,
    val canvas: RectPx,
    val bars: List<RectPx>,
    /** 0..1 share of the app's canvas that is cropped away (Fill only). */
    val cropFraction: Float,
    val lockedOrientation: OrientationPref?,
    val reservedBottomPx: Int,
    val note: String
)

object CanvasPlanner {
    private const val PERCENT = 100

    /** Mini mode window: a 3:2 phone shape, the platform's USER_MIN_ASPECT_RATIO_3_2 (480x720 on the Q25). */
    private const val MINI_RATIO = 1.5f

    /**
     * @param softKeyboardFraction share of the height reserved by [AspectProfile.KeyboardSafe] when the soft
     *   keyboard may show.
     */
    fun plan(display: SizePx, p: DisplayProfile, softKeyboardFraction: Float = 0.40f): CanvasPlan =
        when (p.profile) {
            AspectProfile.Native -> plain(display, p, "Native: app and platform defaults.")

            AspectProfile.FullscreenMedia -> plain(
                display,
                p,
                "Fullscreen media: no forced letterboxing."
            )

            AspectProfile.SquareSafe -> plain(
                display,
                p,
                "Square-safe: full display with a 1:1 minimum-aspect hint; no bars."
            )

            AspectProfile.KeyboardSafe -> keyboardSafe(display, p, softKeyboardFraction)

            AspectProfile.MiniMode -> miniMode(display, p)

            AspectProfile.Ratio16x9Letterbox -> letterbox(
                display,
                Ratio.R16_9.value,
                p,
                "16:9 letterbox"
            )

            AspectProfile.Ratio4x3Letterbox -> letterbox(
                display,
                Ratio.R4_3.value,
                p,
                "4:3 letterbox"
            )

            AspectProfile.Custom -> custom(display, p)

            AspectProfile.Ratio16x9Fill ->
                fill(display, Ratio.R16_9.value, p, RectPx(0, 0, display.w, display.h))
        }

    private fun plain(display: SizePx, p: DisplayProfile, note: String, reserve: Int = 0) =
        CanvasPlan(
            display,
            RectPx(0, 0, display.w, display.h - reserve),
            emptyList(),
            0f,
            orientationLock(p),
            reserve,
            note
        )

    private fun keyboardSafe(
        display: SizePx,
        p: DisplayProfile,
        softKeyboardFraction: Float
    ): CanvasPlan {
        val reserve = if (p.keyboardSafeArea ==
            Tri.Off
        ) {
            0
        } else {
            (display.h * softKeyboardFraction).roundToInt()
        }
        return plain(display, p, "Keyboard-safe: reserves ${reserve}px for text entry.", reserve)
    }

    /** A phone-shaped window: portrait unless the profile asks for landscape (the platform's own behaviour). */
    private fun miniMode(display: SizePx, p: DisplayProfile): CanvasPlan {
        val phone = if (p.orientation == OrientationPref.Landscape) MINI_RATIO else 1f / MINI_RATIO
        val unturned = p.copy(orientation = OrientationPref.Default)
        return letterbox(display, phone, unturned, "Mini mode (3:2 phone window)")
            .copy(lockedOrientation = orientationLock(p))
    }

    private fun custom(display: SizePx, p: DisplayProfile): CanvasPlan {
        val r = p.customRatio
        return when {
            r == null -> plain(display, p, "Custom ratio missing: no-op.")

            r.value < Ratio.MIN || r.value > Ratio.MAX -> plain(
                display,
                p,
                "Custom ratio $r out of range: no-op."
            )

            else -> letterbox(display, r.value, p, "Custom $r letterbox")
        }
    }

    /** Fill scales the canvas to cover the display and crops what overflows. */
    private fun fill(display: SizePx, ratio: Float, p: DisplayProfile, full: RectPx): CanvasPlan {
        val r = effectiveRatio(display, ratio, p)
        val da = display.aspect
        val visible = min(r / da, da / r)
        return CanvasPlan(
            display,
            full,
            emptyList(),
            1f - visible,
            orientationLock(p),
            0,
            "16:9 fill: ${((1f - visible) * PERCENT).roundToInt()}% of the app canvas " +
                "is cropped. Use letterbox if controls go missing."
        )
    }

    private fun letterbox(
        display: SizePx,
        ratio: Float,
        p: DisplayProfile,
        label: String
    ): CanvasPlan {
        val r = effectiveRatio(display, ratio, p)
        val w: Int
        val h: Int
        if (display.aspect > r) {
            h = display.h
            w = (h * r).roundToInt().coerceAtMost(display.w)
        } else {
            w = display.w
            h = (w / r).roundToInt().coerceAtMost(display.h)
        }
        val l = (display.w - w) / 2
        val t = (display.h - h) / 2
        val canvas = RectPx(l, t, l + w, t + h)
        val bars = buildList {
            if (canvas.t > 0) add(RectPx(0, 0, display.w, canvas.t))
            if (canvas.b < display.h) add(RectPx(0, canvas.b, display.w, display.h))
            if (canvas.l > 0) add(RectPx(0, canvas.t, canvas.l, canvas.b))
            if (canvas.r < display.w) add(RectPx(canvas.r, canvas.t, display.w, canvas.b))
        }
        return CanvasPlan(
            display,
            canvas,
            bars,
            0f,
            orientationLock(p),
            0,
            "$label: ${canvas.w}x${canvas.h} centred, background ${p.letterboxBackground.name.lowercase()}."
        )
    }

    /** Wide profiles default to landscape (video/game assumption); Portrait inverts; Auto follows the display. */
    private fun effectiveRatio(display: SizePx, ratio: Float, p: DisplayProfile): Float =
        when (p.orientation) {
            OrientationPref.Portrait -> 1f / ratio
            OrientationPref.Landscape, OrientationPref.Default -> ratio
            OrientationPref.Auto -> if (display.aspect < 1f) 1f / ratio else ratio
        }

    private fun orientationLock(p: DisplayProfile): OrientationPref? = when (p.orientation) {
        OrientationPref.Default, OrientationPref.Auto -> null
        else -> p.orientation
    }
}
