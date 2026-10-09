package org.sableos.tools.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Minimal view kit for Sable Tools: plain framework views (no Compose), dense rows readable on a 720x720 square
 * screen, a strong visible focus ring (VISIBLE_FOCUS=REQUIRED) and touch as the secondary path.
 */
object Ui {
    val BG = Color.parseColor("#0E1116")
    val SURFACE = Color.parseColor("#161C24")
    val TEXT = Color.parseColor("#F7F9FC")
    val MUTED = Color.parseColor("#A9B4C2")
    val ACCENT = Color.parseColor("#E5534B")
    val FOCUS = Color.parseColor("#2A3A4E")
    val OK = Color.parseColor("#57AB5A")

    const val PAD = 12
    private const val PAD_SMALL = 4
    private const val PAD_ROW = 8
    private const val TITLE_SP = 20f
    private const val HEADER_SP = 13f
    private const val ROW_SP = 16f
    private const val SMALL_SP = 12f
    private const val READING_SP = 40f
    private const val FOCUS_STROKE = 3
    private const val CORNER = 6f

    fun dp(ctx: Context, v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        v.toFloat(),
        ctx.resources.displayMetrics
    ).toInt()

    fun column(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val p = dp(ctx, PAD)
        setPadding(p, p, p, p)
        setBackgroundColor(BG)
    }

    fun title(ctx: Context, text: String) = TextView(ctx).apply {
        this.text = text
        setTextColor(TEXT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_SP)
        typeface = Typeface.DEFAULT_BOLD
    }

    fun subtitle(ctx: Context, text: String) = TextView(ctx).apply {
        this.text = text
        setTextColor(MUTED)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, SMALL_SP)
    }

    fun header(ctx: Context, text: String) = TextView(ctx).apply {
        this.text = text.uppercase()
        setTextColor(ACCENT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, HEADER_SP)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(ctx, PAD), 0, dp(ctx, PAD_SMALL))
    }

    private fun focusBackground(ctx: Context): StateListDrawable {
        val focused = GradientDrawable().apply {
            setColor(FOCUS)
            setStroke(dp(ctx, FOCUS_STROKE), ACCENT)
            cornerRadius = dp(ctx, CORNER.toInt()).toFloat()
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(android.R.attr.state_pressed), focused)
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
    }

    /** A focusable, clickable row: title on the left, optional badge on the right. */
    fun row(ctx: Context, text: String, badge: String? = null, onOpen: () -> Unit): View {
        val l = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isFocusableInTouchMode = false
            isClickable = true
            background = focusBackground(ctx)
            val p = dp(ctx, PAD_ROW)
            setPadding(p, p, p, p)
            setOnClickListener { onOpen() }
        }
        l.addView(
            TextView(ctx).apply {
                this.text = text
                setTextColor(TEXT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, ROW_SP)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        if (badge != null) {
            l.addView(
                TextView(ctx).apply {
                    this.text = badge
                    setTextColor(MUTED)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, SMALL_SP)
                }
            )
        }
        return l
    }

    fun mono(ctx: Context, text: String = "") = TextView(ctx).apply {
        this.text = text
        typeface = Typeface.MONOSPACE
        setTextColor(TEXT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, SMALL_SP)
        setTextIsSelectable(false)
    }

    /** The always-visible current reading (CURRENT_READING_ALWAYS_VISIBLE=YES). */
    fun reading(ctx: Context) = TextView(ctx).apply {
        setTextColor(TEXT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, READING_SP)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
    }

    fun body(ctx: Context, text: String = "") = TextView(ctx).apply {
        this.text = text
        setTextColor(MUTED)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, ROW_SP - 2)
    }

    fun hint(ctx: Context, text: String) = subtitle(ctx, text).apply {
        setPadding(0, dp(ctx, PAD_ROW), 0, 0)
    }
}
