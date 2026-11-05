package org.sableos.titan2.compass.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.View
import kotlin.math.min
import org.sableos.titan2.compass.core.Figure8
import org.sableos.titan2.theme.SablePalette

/** The calibration guide: a figure 8 with a dot running along it, one loop every few seconds. */
class Figure8View(context: Context, private val palette: SablePalette) : View(context) {
    private val density = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = TRACK_DP * density
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val half = min(width / 2f, height.toFloat()) * SIZE
        path.reset()
        for (i in 0..STEPS) {
            val (x, y) = Figure8.point(i.toDouble() / STEPS)
            val px = cx + (x * half).toFloat()
            val py = cy + (y * half).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        track.color = palette.muted
        canvas.drawPath(path, track)
        val phase = (SystemClock.uptimeMillis() % LOOP_MS).toDouble() / LOOP_MS
        val (x, y) = Figure8.point(phase)
        dot.color = palette.accent
        canvas.drawCircle(
            cx + (x * half).toFloat(),
            cy + (y * half).toFloat(),
            DOT_DP * density,
            dot
        )
        if (isShown) postInvalidateOnAnimation()
    }

    private companion object {
        const val SIZE = 0.9f
        const val STEPS = 96
        const val LOOP_MS = 3000L
        const val TRACK_DP = 3f
        const val DOT_DP = 8f
    }
}
