package org.sableos.titan2.keyboard.android

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.TextView
import org.sableos.titan2.keyboard.core.ChipState
import org.sableos.titan2.keyboard.core.ModChip
import org.sableos.titan2.keyboard.core.StripContent
import org.sableos.titan2.keyboard.core.StripGeometry
import org.sableos.titan2.keyboard.core.StripMode
import org.sableos.titan2.keyboard.core.StripModel

/**
 * The IME candidates view. Renders a [StripModel]: in Compact mode a language chip, Shift/Alt/Sym/Nav
 * indicators and one content area; in CandidatesOnly mode just the candidate chips, as before. It
 * holds no state of its own; the service rebuilds the model and calls [render]. Padding follows the
 * live window insets (display cutout, rounded corners, navigation bar), never a device constant.
 */
class CompactStripView(
    context: Context,
    private val onCandidate: (Int) -> Unit,
    private val onSymbol: (String) -> Unit
) : LinearLayout(context) {
    private val dp = resources.displayMetrics.density
    private val status = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val content = CandidateBar(context, handleInsets = false) { onCandidate(it) }.apply {
        compact = true
    }

    private companion object {
        const val CHIP_TEXT_SP = 13f
        const val CHIP_PAD_H_DP = 8
        const val CHIP_CORNER_DP = 8
        const val CHIP_MARGIN_DP = 2
        val BAR_BG = Color.parseColor("#091621")
        val OFF_BG = Color.parseColor("#14293A")
        val OFF_FG = Color.parseColor("#6F8799")
        val ON_BG = Color.parseColor("#2F7FB8")
        val LOCK_BG = Color.parseColor("#55B9FF")
        val NAV_BG = Color.parseColor("#3FA37A")
        val SUSPENDED_FG = Color.parseColor("#FFB74D")
    }

    private fun px(v: Int): Int = (v * dp).toInt()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(BAR_BG)
        addView(status)
        addView(content, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        applyPadding(0, 0, 0, 0, 0)
        setOnApplyWindowInsetsListener { v, insets ->
            val cut = insets.getInsets(WindowInsets.Type.displayCutout())
            val nav = insets.getInsets(WindowInsets.Type.navigationBars())
            applyPadding(
                cut.left,
                cut.right,
                corner(insets, android.view.RoundedCorner.POSITION_BOTTOM_LEFT),
                corner(insets, android.view.RoundedCorner.POSITION_BOTTOM_RIGHT),
                nav.bottom
            )
            insets
        }
    }

    private fun corner(insets: WindowInsets, position: Int): Int =
        insets.getRoundedCorner(position)?.radius ?: 0

    private fun applyPadding(cutL: Int, cutR: Int, cornerL: Int, cornerR: Int, navBottom: Int) {
        val p = StripGeometry.contentPadding(
            px(StripGeometry.PAD_H_DP),
            StripGeometry.Insets(cutL, cutR, cornerL, cornerR, navBottom)
        )
        setPadding(
            p.left,
            px(StripGeometry.PAD_V_DP),
            p.right,
            px(StripGeometry.PAD_V_DP) + p.bottom
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestApplyInsets()
    }

    fun render(m: StripModel) {
        visibility = if (m.visible) View.VISIBLE else View.GONE
        if (!m.visible) return
        status.removeAllViews()
        status.visibility = if (m.mode == StripMode.Compact) View.VISIBLE else View.GONE
        if (m.mode == StripMode.Compact) {
            status.addView(chip(m.languageLabel, ChipState.Held, m.languageDescription))
            m.mods.forEach { status.addView(modChip(it)) }
        }
        when (val c = m.content) {
            StripContent.None -> content.showItems(emptyList(), 0, null)
            is StripContent.Candidates -> content.show(c.items)
            is StripContent.Variation -> content.showItems(c.options, c.selected, null)
            is StripContent.Symbols -> content.showItems(c.items, -1) { onSymbol(c.items[it]) }
        }
    }

    private fun modChip(m: ModChip): TextView = chip(
        m.label,
        m.state,
        m.label + ": " + m.state.name.lowercase()
    )

    private fun chip(label: String, state: ChipState, description: String): TextView =
        TextView(context).apply {
            text = label
            contentDescription = description
            setTextSize(TypedValue.COMPLEX_UNIT_SP, CHIP_TEXT_SP)
            gravity = Gravity.CENTER
            minHeight = px(StripGeometry.INDICATOR_HEIGHT_DP)
            setPadding(px(CHIP_PAD_H_DP), 0, px(CHIP_PAD_H_DP), 0)
            setTextColor(if (state == ChipState.Off) OFF_FG else Color.WHITE)
            if (state == ChipState.Suspended) setTextColor(SUSPENDED_FG)
            background = GradientDrawable().apply {
                cornerRadius = CHIP_CORNER_DP * dp
                setColor(
                    when (state) {
                        ChipState.Off, ChipState.Suspended -> OFF_BG
                        ChipState.Once, ChipState.Held -> ON_BG
                        ChipState.Locked -> LOCK_BG
                        ChipState.Active -> NAV_BG
                    }
                )
            }
            layoutParams =
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    setMargins(px(CHIP_MARGIN_DP), 0, px(CHIP_MARGIN_DP), 0)
                }
        }
}
