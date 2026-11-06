package org.sableos.tools.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import org.sableos.tools.core.RulerScale
import org.sableos.tools.core.RulerTick
import org.sableos.tools.core.ToolsText.t

/**
 * An on-screen ruler: centimetres down the left edge and inches down the right, drawn from the
 * panel's own density (DisplayMetrics.ydpi), so it is only as true as the panel reports. Lay the
 * object along the left or right edge of the glass, starting at the top. No sensors, nothing kept.
 */
class RulerController(private val host: ToolHost) : ToolController {
    private val ctx = host.activity

    override fun view(): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        addView(Ui.body(ctx, t("ruler_hint")))
        addView(
            RulerView(ctx),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
    }

    override fun info() = t("ruler_info")
}

private class RulerView(ctx: Context) : View(ctx) {
    private val density = ctx.resources.displayMetrics
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = density.density
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, LABEL_SP, density)
    }

    init {
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val top = topOnScreen()
        val length = height.toFloat()
        // Ticks are placed from the top of the glass, so the scale starts at the screen edge.
        RulerScale.metric(length + top, density.ydpi).forEach {
            drawTick(canvas, it, top, onLeft = true)
        }
        RulerScale.imperial(length + top, density.ydpi).forEach {
            drawTick(canvas, it, top, onLeft = false)
        }
    }

    private fun topOnScreen(): Float {
        val at = IntArray(2)
        getLocationOnScreen(at)
        return at[1].toFloat()
    }

    private fun drawTick(canvas: Canvas, tick: RulerTick, top: Float, onLeft: Boolean) {
        val y = tick.px - top
        if (y < 0f || y > height) return
        val size = when (tick.size) {
            RulerTick.Size.SHORT -> SHORT_MM
            RulerTick.Size.MEDIUM -> MEDIUM_MM
            RulerTick.Size.LONG -> LONG_MM
        } * density.xdpi / MM_PER_INCH
        val x0 = if (onLeft) 0f else width.toFloat()
        val x1 = if (onLeft) size else width - size
        canvas.drawLine(x0, y, x1, y, line)
        tick.label?.let {
            val gap = GAP_MM * density.xdpi / MM_PER_INCH
            val tx = if (onLeft) x1 + gap else x1 - gap - text.measureText(it)
            canvas.drawText(it, tx, y + text.textSize / 2, text)
        }
    }

    private companion object {
        const val LABEL_SP = 12f
        const val MM_PER_INCH = 25.4f
        const val SHORT_MM = 2f
        const val MEDIUM_MM = 3.5f
        const val LONG_MM = 5f
        const val GAP_MM = 1f
    }
}
