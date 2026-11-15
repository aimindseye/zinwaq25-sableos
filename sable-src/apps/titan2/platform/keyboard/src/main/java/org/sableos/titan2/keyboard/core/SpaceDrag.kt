package org.sableos.titan2.keyboard.core

/**
 * The on-screen space bar: a tap types a space, a quick swipe sideways switches language, and a press that is held
 * and then dragged moves the cursor. Pure; the view feeds it x positions and times.
 */
class SpaceDrag(private val stepPx: Float, private val swipePx: Float) {
    enum class Outcome { Tap, LongPress, SwipeLeft, SwipeRight, Done }

    private var startX = 0f
    private var anchorX = 0f
    private var startAt = 0L
    private var cursorMode = false
    private var moved = false

    fun down(x: Float, atMs: Long) {
        startX = x
        anchorX = x
        startAt = atMs
        cursorMode = false
        moved = false
    }

    /** Cursor steps to make now (negative = left); 0 until the press has been held for [HOLD_MS]. */
    fun move(x: Float, atMs: Long): Int {
        if (!cursorMode && atMs - startAt >= HOLD_MS) cursorMode = true
        if (abs(x - startX) > SLOP_PX) moved = true
        if (!cursorMode) return 0
        val steps = ((x - anchorX) / stepPx).toInt()
        anchorX += steps * stepPx
        return steps
    }

    fun up(x: Float, atMs: Long): Outcome = when {
        cursorMode && moved -> Outcome.Done
        moved && x - startX <= -swipePx -> Outcome.SwipeLeft
        moved && x - startX >= swipePx -> Outcome.SwipeRight
        moved -> Outcome.Done
        atMs - startAt >= HOLD_MS -> Outcome.LongPress
        else -> Outcome.Tap
    }

    private fun abs(v: Float) = if (v < 0) -v else v

    companion object {
        const val HOLD_MS = 350L
        const val SLOP_PX = 12f
    }
}
