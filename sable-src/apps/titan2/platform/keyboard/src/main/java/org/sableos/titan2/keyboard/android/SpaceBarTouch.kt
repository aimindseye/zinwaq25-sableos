package org.sableos.titan2.keyboard.android

import android.view.MotionEvent
import android.view.View
import org.sableos.titan2.keyboard.core.SpaceDrag

/** Touch handling for the on-screen space bar; the rules live in [SpaceDrag]. */
class SpaceBarTouch(
    density: Float,
    private val onTap: () -> Unit,
    private val onLong: () -> Unit,
    private val onSwipe: (Int) -> Unit,
    private val onCursor: (Int) -> Unit
) : View.OnTouchListener {
    private val drag = SpaceDrag(STEP_DP * density, SWIPE_DP * density)

    override fun onTouch(v: View, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                v.isPressed = true
                drag.down(e.x, e.eventTime)
            }

            MotionEvent.ACTION_MOVE -> drag.move(e.x, e.eventTime).takeIf { it != 0 }?.let(onCursor)

            MotionEvent.ACTION_UP -> {
                v.isPressed = false
                finish(drag.up(e.x, e.eventTime))
            }

            MotionEvent.ACTION_CANCEL -> v.isPressed = false
        }
        return true
    }

    private fun finish(o: SpaceDrag.Outcome) {
        when (o) {
            SpaceDrag.Outcome.Tap -> onTap()
            SpaceDrag.Outcome.LongPress -> onLong()
            SpaceDrag.Outcome.SwipeLeft -> onSwipe(-1)
            SpaceDrag.Outcome.SwipeRight -> onSwipe(1)
            SpaceDrag.Outcome.Done -> Unit
        }
    }

    private companion object {
        const val STEP_DP = 14f
        const val SWIPE_DP = 56f
    }
}
