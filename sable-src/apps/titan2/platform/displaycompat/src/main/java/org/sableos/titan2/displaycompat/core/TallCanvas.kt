package org.sableos.titan2.displaycompat.core

import kotlin.math.min
import kotlin.math.roundToInt
import org.sableos.titan2.displaycompat.core.DisplayCompatText.t

/**
 * Tall canvas sizes: the display keeps its width and gets a taller logical height, so an app laid
 * out for a phone sees a phone-shaped screen. The panel shows it scaled down with bars at the
 * sides. Heights are a ratio of the native width, so on the Q25 (720x720) they are 720x772,
 * 720x960, 720x1280 and 720x1440.
 */
enum class TallCanvas(
    val id: String,
    private val num: Int,
    private val den: Int,
    val shape: String
) {
    Native("native", 1, 1, "native"),
    Taller("tall_772", TALLER_NUM, TALLER_DEN, "a bit taller"),
    Tall3x4("tall_3_4", FOUR, THREE, "3:4"),
    Tall9x16("tall_9_16", SIXTEEN, NINE, "9:16"),
    Tall1x2("tall_1_2", 2, 1, "1:2");

    /** Logical size on a display of [native] size, or null for Native or a size no taller. */
    fun size(native: SizePx): SizePx? {
        if (this == Native) return null
        val h = (native.w.toLong() * num / den.toDouble()).roundToInt()
        return if (h > native.h) SizePx(native.w, h) else null
    }

    /** "720x1280 (9:16)" on the given display, "Native 720x720", or "not taller here". */
    fun label(native: SizePx): String {
        val s = size(native)
        return when {
            this == Native -> t("native_x", native.w, native.h)
            s == null -> t("not_taller_here", shape)
            else -> "${s.w}x${s.h} ($shape)"
        }
    }

    companion object {
        fun fromId(id: String?): TallCanvas = entries.firstOrNull { it.id == id } ?: Native

        /** The sizes that change something on [native], in cycle order (shortest first). */
        fun usable(native: SizePx): List<TallCanvas> = entries.filter { it.size(native) != null }
    }
}

// 772 / 720 = 193 / 180: the "a bit taller" preset of the community Q25 resolution tools.
private const val TALLER_NUM = 193
private const val TALLER_DEN = 180
private const val THREE = 3
private const val FOUR = 4
private const val NINE = 9
private const val SIXTEEN = 16

/**
 * Smallest-width arithmetic for the large-screen threshold. At 600 dp Android treats the display as
 * a tablet (Launcher3 shows its taskbar). LineageOS Launcher3 has no setting to keep the taskbar
 * stashed, so a canvas that would cross the threshold also gets a density that keeps it under.
 */
object CanvasMath {
    const val LARGE_SCREEN_DP = 600
    private const val MDPI = 160

    fun smallestWidthDp(size: SizePx, densityDpi: Int): Int =
        min(size.w, size.h) * MDPI / densityDpi

    /**
     * Density to force with [size] so that its smallest width stays below [LARGE_SCREEN_DP], or null
     * when [densityDpi] already keeps it there. Every Tall canvas keeps the native width, so on a
     * square display this only matters if the user's density is already near the threshold.
     */
    fun compactDensity(size: SizePx, densityDpi: Int): Int? {
        if (densityDpi <= 0 || smallestWidthDp(size, densityDpi) < LARGE_SCREEN_DP) return null
        // smallest * 160 / d < 600  <=>  d > smallest * 160 / 600
        return min(size.w, size.h) * MDPI / LARGE_SCREEN_DP + 1
    }
}

/** Resolution hotkey order: Native, then the favourites shortest first, then Native again. */
object CanvasCycle {
    /** Favourites when the user has not chosen any: 3:4 and 9:16. */
    val DEFAULT_FAVOURITES: Set<TallCanvas> = setOf(TallCanvas.Tall3x4, TallCanvas.Tall9x16)

    fun order(favourites: Set<TallCanvas>, native: SizePx): List<TallCanvas> =
        listOf(TallCanvas.Native) + TallCanvas.usable(native).filter { it in favourites }

    /**
     * The size after [current]; null when there is no favourite to cycle to. A [current] that is not
     * in the cycle (favourites changed since) starts it again at the first favourite.
     */
    fun next(favourites: Set<TallCanvas>, current: TallCanvas, native: SizePx): TallCanvas? {
        val order = order(favourites, native)
        if (order.size < 2) return null
        val i = order.indexOf(current)
        return if (i < 0) order[1] else order[(i + 1) % order.size]
    }

    fun encode(favourites: Set<TallCanvas>): String =
        TallCanvas.entries.filter { it in favourites && it != TallCanvas.Native }
            .joinToString(",") { it.id }

    /** Null (never saved) gives the defaults; an empty string is a saved empty choice. */
    fun decode(raw: String?): Set<TallCanvas> = when (raw) {
        null -> DEFAULT_FAVOURITES

        else -> raw.split(',').mapNotNull { id -> TallCanvas.entries.firstOrNull { it.id == id } }
            .filter { it != TallCanvas.Native }.toSet()
    }
}
