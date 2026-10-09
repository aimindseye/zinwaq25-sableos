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

    /**
     * @param miniSize Titan "Mini mode" resolution. The manuals only say "another normal size", so this is
     *   device evidence that must be captured; null keeps Mini mode a no-op rather than guessing a number.
     * @param softKeyboardFraction share of the height reserved by [AspectProfile.KeyboardSafe] when the soft
     *   keyboard may show.
     */
    fun plan(
        display: SizePx,
        p: DisplayProfile,
        miniSize: SizePx? = null,
        softKeyboardFraction: Float = 0.40f
    ): CanvasPlan = when (p.profile) {
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

        AspectProfile.MiniMode -> miniMode(display, p, miniSize)

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

    private fun miniMode(display: SizePx, p: DisplayProfile, miniSize: SizePx?): CanvasPlan =
        if (miniSize == null) {
            plain(display, p, "Mini mode size not captured on this device: no-op.")
        } else {
            letterbox(
                display,
                miniSize.aspect,
                p,
                "Mini mode ${miniSize.w}x${miniSize.h}",
                exactSize = miniSize
            )
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
        label: String,
        exactSize: SizePx? = null
    ): CanvasPlan {
        val r = effectiveRatio(display, ratio, p)
        val w: Int
        val h: Int
        if (exactSize != null && p.orientation == OrientationPref.Default) {
            w = min(exactSize.w, display.w)
            h = min(exactSize.h, display.h)
        } else if (display.aspect > r) {
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
