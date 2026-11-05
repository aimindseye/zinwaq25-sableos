package org.sableos.browser.core

import kotlin.math.max
import kotlin.math.min

/**
 * The trackpad pointer. The Q25's trackpad sends an arrow key for each stroke, so the pointer
 * moves a step per key: short when keys come slowly (fine aiming), up to [MAX_STEP_DP] when they
 * come fast (crossing the page). Over a link or button the step is cut ([LINK_FRICTION]) so the
 * pointer settles on it. Pushing past an edge scrolls the page instead. Pure.
 */
object PointerPhysics {
    const val BASE_STEP_DP = 6f
    const val MAX_STEP_DP = 48f
    const val LINK_FRICTION = 0.4f

    /** Keys closer together than this are one fast stroke; further apart start slow again. */
    const val STREAK_GAP_MS = 140L

    /** Each key of a fast stroke grows the step by this factor, up to [MAX_STEP_DP]. */
    const val GROWTH = 1.35f

    /** How far past an edge the page scrolls for a step that hits it, as a multiple of the step. */
    const val EDGE_SCROLL = 4f

    /** A run of arrow keys. */
    data class Streak(
        val count: Int,
        val lastMillis: Long,
    )

    /** The streak after a key at [nowMillis]: one more when it follows within [STREAK_GAP_MS]. */
    fun nextStreak(
        streak: Streak?,
        nowMillis: Long,
    ): Streak =
        if (streak != null && nowMillis - streak.lastMillis <= STREAK_GAP_MS) {
            Streak(streak.count + 1, nowMillis)
        } else {
            Streak(1, nowMillis)
        }

    /** The step in dp for the [streak]'s latest key. */
    fun stepDp(
        streak: Streak,
        overLink: Boolean,
    ): Float {
        var step = BASE_STEP_DP
        repeat(streak.count - 1) { step = min(MAX_STEP_DP, step * GROWTH) }
        return if (overLink) max(BASE_STEP_DP * LINK_FRICTION, step * LINK_FRICTION) else step
    }

    /** Where a move lands: the pointer, kept inside the page, and how far the page scrolls. */
    data class Move(
        val x: Float,
        val y: Float,
        val scrollX: Int,
        val scrollY: Int,
    )

    fun move(
        x: Float,
        y: Float,
        dx: Float,
        dy: Float,
        width: Float,
        height: Float,
    ): Move {
        val nx = x + dx
        val ny = y + dy
        val cx = nx.coerceIn(0f, max(0f, width - 1f))
        val cy = ny.coerceIn(0f, max(0f, height - 1f))
        return Move(cx, cy, edgeScroll(nx - cx, dx), edgeScroll(ny - cy, dy))
    }

    private fun edgeScroll(
        overflow: Float,
        step: Float,
    ): Int = if (overflow == 0f) 0 else (step * EDGE_SCROLL).toInt()
}
