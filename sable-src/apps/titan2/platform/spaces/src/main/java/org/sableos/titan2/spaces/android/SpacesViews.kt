package org.sableos.titan2.spaces.android

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.TextView
import org.sableos.titan2.theme.SableFonts
import org.sableos.titan2.theme.SablePalette

/** The few views Sable Spaces draws, in the Sable theme. Keys go to the screen, not to a focused button. */
class SpacesViews(private val context: Context, val palette: SablePalette) {
    private val dp = context.resources.displayMetrics.density

    fun text(s: String, sp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(context).apply {
            text = s
            textSize = sp
            setTextColor(color)
            typeface = SableFonts.of(bold)
            setPadding(0, px(GAP), 0, 0)
        }

    fun button(label: String, onClick: () -> Unit): Button = Button(context).apply {
        text = label
        isAllCaps = false
        isFocusable = false
        setOnClickListener { onClick() }
    }

    fun box(fill: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = CORNER * dp
        setStroke(px(STROKE), stroke)
    }

    fun px(v: Int): Int = (v * dp).toInt()

    companion object {
        const val PAD = 12
        const val GAP = 6
        const val TITLE_SP = 18f
        const val BODY_SP = 14f
        const val SMALL_SP = 11f
        private const val STROKE = 2
        private const val CORNER = 6f
    }
}
