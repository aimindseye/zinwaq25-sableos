package org.sableos.phone.android

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.TextView
import org.sableos.titan2.theme.SableFonts
import org.sableos.titan2.theme.SablePalette

/**
 * The small view kit Sable Phone draws with: Sable palette text, rows and buttons with the 2 dp accent focus
 * outline every Sable screen shows for the trackpad and keyboard, sized for the Q25's 720x720 display.
 */
class Ui(private val context: Context, val palette: SablePalette) {
    private val density = context.resources.displayMetrics.density

    fun dp(value: Int): Int = (value * density).toInt()

    fun text(
        sp: Float,
        color: Int = palette.text,
        bold: Boolean = false,
        s: CharSequence = ""
    ): TextView = TextView(context).apply {
        text = s
        textSize = sp
        setTextColor(color)
        typeface = SableFonts.of(bold)
    }

    /** A focusable list row or tile: [fill] at rest, the focus surface with an accent outline when focused. */
    fun focusBackground(fill: Int = palette.window, corner: Int = CORNER): StateListDrawable =
        StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_focused),
                box(palette.focus, palette.accent, corner)
            )
            addState(
                intArrayOf(android.R.attr.state_pressed),
                box(palette.focus, palette.focus, corner)
            )
            addState(intArrayOf(), box(fill, fill, corner))
        }

    fun box(fill: Int, stroke: Int, corner: Int = CORNER): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(corner).toFloat()
            setStroke(dp(STROKE), stroke)
        }

    /** A round call-screen button with a label; [fill] carries its meaning (green answer, red end). */
    fun button(label: String, fill: Int, textColor: Int, onClick: () -> Unit): TextView =
        text(BUTTON_SP, textColor, true, label).apply {
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            minHeight = dp(BUTTON_HEIGHT)
            setPadding(dp(PAD), 0, dp(PAD), 0)
            val states = StateListDrawable().apply {
                addState(
                    intArrayOf(android.R.attr.state_focused),
                    box(fill, palette.text, BUTTON_CORNER)
                )
                addState(intArrayOf(), box(fill, fill, BUTTON_CORNER))
            }
            background = RippleDrawable(ColorStateList.valueOf(palette.focus), states, null)
            setOnClickListener { onClick() }
        }

    /**
     * Keeps [root] clear of the status bar, the navigation bar and the on-screen keyboard: Android 15+
     * draws apps edge to edge, so without this the top of the screen sits under the status bar.
     */
    fun fitSystemBars(root: View) {
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsets.CONSUMED
        }
    }

    companion object {
        const val PAD = 12
        const val STROKE = 2
        const val CORNER = 8
        const val BUTTON_CORNER = 24
        const val BUTTON_HEIGHT = 48
        const val TITLE_SP = 20f
        const val BODY_SP = 16f
        const val SMALL_SP = 13f
        const val BUTTON_SP = 15f

        /** BlackBerry call green and end red, readable in dark and light. */
        const val GREEN = 0xFF1FA855.toInt()
        const val RED = 0xFFD93025.toInt()
        const val ON_COLOR = 0xFFFFFFFF.toInt()
    }
}
