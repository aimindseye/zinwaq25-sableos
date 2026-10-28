package org.sableos.tools.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

private const val FOOTER_PAD = 8
private const val FOOTER_SP = 16f
private const val FOOTER_CORNER = 6
private const val FOOTER_FOCUS_STROKE = 3

/**
 * The bottom line of a tool: its keys on the left and, when [button] is set, a focusable button
 * on the right (the trackpad reaches it like any row; a tap works too).
 */
fun Ui.footer(ctx: Context, keys: String, button: String?, onButton: () -> Unit): View {
    val l = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(ctx, FOOTER_PAD), 0, 0)
    }
    l.addView(
        subtitle(ctx, keys),
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    )
    if (button != null) {
        l.addView(
            TextView(ctx).apply {
                text = button
                setTextColor(TEXT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, FOOTER_SP)
                isFocusable = true
                isClickable = true
                // A thin accent outline, thicker when the trackpad focuses it (the Sable focus ring).
                background = StateListDrawable().apply {
                    addState(
                        intArrayOf(android.R.attr.state_focused),
                        pill(ctx, FOOTER_FOCUS_STROKE)
                    )
                    addState(
                        intArrayOf(android.R.attr.state_pressed),
                        pill(ctx, FOOTER_FOCUS_STROKE)
                    )
                    addState(intArrayOf(), pill(ctx, 1))
                }
                val p = dp(ctx, FOOTER_PAD)
                setPadding(p * 2, p, p * 2, p)
                setOnClickListener { onButton() }
            }
        )
    }
    return l
}

private fun Ui.pill(ctx: Context, strokeDp: Int) = GradientDrawable().apply {
    setColor(FOCUS)
    setStroke(dp(ctx, strokeDp), ACCENT)
    cornerRadius = dp(ctx, FOOTER_CORNER).toFloat()
}
