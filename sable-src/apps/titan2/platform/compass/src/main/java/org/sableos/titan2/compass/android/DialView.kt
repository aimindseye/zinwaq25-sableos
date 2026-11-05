package org.sableos.titan2.compass.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.min
import kotlin.math.sqrt
import org.sableos.titan2.compass.core.CompassText.t
import org.sableos.titan2.compass.core.Heading
import org.sableos.titan2.theme.SablePalette

/**
 * The compass card: a disc that stays fixed to the world while the phone turns, with the direction faced
 * under the mark at the top. Tilting the phone leans the disc, so held upright it is seen nearly edge-on with
 * its rim showing, like a card in a liquid compass.
 */
class DialView(context: Context, private val palette: SablePalette) : View(context) {
    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val oval = RectF()
    private val mark = Path()

    /** Degrees clockwise from north that the user faces; the card turns the other way. */
    var heading = 0.0
        set(value) {
            field = value
            invalidate()
        }

    /** Height against width, from [Heading.squash]. */
    var squash = 1.0
        set(value) {
            field = value
            invalidate()
        }

    /** True north on: the north letter becomes the true-north mark. */
    var trueNorth = false
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f * FACE
        if (r <= 0f) return
        val sq = squash.toFloat()
        rim(canvas, cx, cy, r, sq)
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(1f, sq)
        fill.color = palette.surface
        canvas.drawCircle(0f, 0f, r, fill)
        line.color = palette.muted
        line.strokeWidth = density
        canvas.drawCircle(0f, 0f, r, line)
        canvas.rotate(-heading.toFloat())
        ticks(canvas, r)
        labels(canvas, r)
        canvas.restore()
        lubber(canvas, cx, cy - r * sq, r)
    }

    /** The disc's edge below its face, deeper the more it leans. */
    private fun rim(canvas: Canvas, cx: Float, cy: Float, r: Float, sq: Float) {
        val depth = r * RIM * sqrt(1f - sq * sq)
        if (depth < 1f) return
        fill.color = palette.focus
        oval.set(cx - r, cy - r * sq + depth, cx + r, cy + r * sq + depth)
        canvas.drawOval(oval, fill)
        canvas.drawRect(cx - r, cy, cx + r, cy + depth, fill)
    }

    private fun ticks(canvas: Canvas, r: Float) {
        for (deg in 0 until FULL step STEP) {
            val major = deg % MAJOR == 0
            val mid = deg % MID == 0
            val len = r * if (major) {
                LONG
            } else if (mid) {
                MEDIUM
            } else {
                SHORT
            }
            line.color = if (major) palette.text else palette.muted
            line.strokeWidth = density * if (major) 2f else 1f
            canvas.save()
            canvas.rotate(deg.toFloat())
            canvas.drawLine(0f, -r * INSET, 0f, -r * INSET + len, line)
            canvas.restore()
        }
    }

    private fun labels(canvas: Canvas, r: Float) {
        for (deg in 0 until FULL step MAJOR) {
            val cardinal = deg % QUARTER == 0
            val text =
                when (deg) {
                    0 -> if (trueNorth) t("true_mark") else t("dial_n")
                    QUARTER -> t("dial_e")
                    2 * QUARTER -> t("dial_s")
                    3 * QUARTER -> t("dial_w")
                    else -> deg.toString()
                }
            label.textSize = r * if (cardinal) CARDINAL_TEXT else NUMBER_TEXT
            label.color =
                when {
                    deg == 0 -> palette.danger
                    cardinal -> palette.text
                    else -> palette.muted
                }
            canvas.save()
            canvas.rotate(deg.toFloat())
            canvas.drawText(text, 0f, -r * LABEL_AT + label.textSize / 2, label)
            canvas.restore()
        }
    }

    /** The fixed mark at the top of the card that the heading is read against. */
    private fun lubber(canvas: Canvas, x: Float, top: Float, r: Float) {
        val w = r * MARK
        mark.reset()
        mark.moveTo(x - w, top - w * 2)
        mark.lineTo(x + w, top - w * 2)
        mark.lineTo(x, top + w / 2)
        mark.close()
        fill.color = palette.accent
        canvas.drawPath(mark, fill)
    }

    private companion object {
        const val FACE = 0.86f
        const val RIM = 0.12f
        const val FULL = 360
        const val STEP = 5
        const val MID = 10
        const val MAJOR = 30
        const val QUARTER = 90
        const val INSET = 0.98f
        const val LONG = 0.14f
        const val MEDIUM = 0.09f
        const val SHORT = 0.05f
        const val LABEL_AT = 0.68f
        const val CARDINAL_TEXT = 0.2f
        const val NUMBER_TEXT = 0.11f
        const val MARK = 0.06f
    }
}
