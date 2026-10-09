package org.sableos.titan2.keyboard.android

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowInsets
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import org.sableos.titan2.keyboard.core.Cand
import org.sableos.titan2.keyboard.core.StripGeometry

/**
 * Scrollable strip of candidates (pinyin words, kana forms). Used as the IME candidates view so it works with or
 * without the soft keyboard.
 */
class CandidateBar(
    context: Context,
    private val handleInsets: Boolean = true,
    private val onPick: (Int) -> Unit
) : HorizontalScrollView(context) {
    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val dp = resources.displayMetrics.density

    /** Compact strip mode uses the strip chip height instead of the larger candidate height. */
    var compact = false

    private companion object {
        const val BAR_PAD_DP = 4
        const val CAND_PAD_H_DP = 14
        const val CAND_MIN_DP = 44
        const val CAND_CORNER_DP = 8
        const val CAND_MARGIN_DP = 2
        const val CAND_TEXT_SP = 22f
    }

    private fun px(v: Int): Int = (v * dp).toInt()

    init {
        isHorizontalScrollBarEnabled = false
        setBackgroundColor(Color.parseColor("#091621"))
        addView(row)
        setPadding(px(BAR_PAD_DP), px(BAR_PAD_DP), px(BAR_PAD_DP), px(BAR_PAD_DP))
        if (handleInsets) {
            setOnApplyWindowInsetsListener { v, insets ->
                val i = insets.getInsets(WindowInsets.Type.displayCutout())
                v.setPadding(
                    px(BAR_PAD_DP) + i.left,
                    px(BAR_PAD_DP),
                    px(BAR_PAD_DP) + i.right,
                    px(BAR_PAD_DP)
                )
                insets
            }
        }
    }

    fun show(cands: List<Cand>) = showItems(cands.map { it.text }, 0, onPick)

    /** Shows [items] as chips; [selected] is highlighted. A null [pick] makes them indicators only. */
    fun showItems(items: List<String>, selected: Int, pick: ((Int) -> Unit)?) {
        row.removeAllViews()
        items.forEachIndexed { i, itemText ->
            row.addView(
                TextView(context).apply {
                    text = itemText
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, CAND_TEXT_SP)
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(px(CAND_PAD_H_DP), 0, px(CAND_PAD_H_DP), 0)
                    minHeight = px(if (compact) StripGeometry.CHIP_HEIGHT_DP else CAND_MIN_DP)
                    minWidth = px(CAND_MIN_DP)
                    background =
                        GradientDrawable().apply {
                            cornerRadius = CAND_CORNER_DP * dp
                            setColor(
                                if (i == selected) {
                                    Color.parseColor("#2F7FB8")
                                } else {
                                    Color.parseColor("#1E3A52")
                                }
                            )
                        }
                    layoutParams =
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(px(CAND_MARGIN_DP), 0, px(CAND_MARGIN_DP), 0)
                        }
                    if (pick != null) setOnClickListener { pick(i) }
                }
            )
        }
        scrollTo(0, 0)
    }
}
