package org.sableos.titan2.setup.android

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.sableos.titan2.setup.core.Item
import org.sableos.titan2.setup.core.LaunchReport
import org.sableos.titan2.setup.core.Readiness
import org.sableos.titan2.setup.core.SetupModel
import org.sableos.titan2.setup.core.SetupRows

/**
 * Post-provisioning readiness checklist. This is not the Android Setup Wizard (that is a privileged system
 * package and is left alone); it is a normal app that shows what it can observe and opens the matching screen.
 * Nothing here changes a setting by itself: every action opens a screen the user confirms, and a screen that
 * cannot be opened is shown as unavailable instead of silently doing nothing.
 */
class SetupActivity : Activity() {
    private lateinit var body: LinearLayout
    private lateinit var launcher: Launcher
    private val dp by lazy { resources.displayMetrics.density }
    private fun px(v: Int) = (v * dp).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launcher = Launcher(this)
        body =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(px(PAD_H), px(PAD_V), px(PAD_H), px(PAD_V))
            }
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(BG)
                addView(body)
            }
        )
    }

    override fun onResume() {
        super.onResume()
        render(null)
    }

    private fun render(failure: String?) {
        body.removeAllViews()
        val items = SetupRows.build(SetupReader(this).snapshot(), launcher::resolve)
        body.addView(text(SetupModel.TITLE, TITLE_SP, Color.WHITE, true))
        body.addView(text(SetupModel.SELF_DESCRIPTION, DETAIL_SP, DIM, false))
        body.addView(
            text(SetupModel.profileLine(SysProps.get("ro.sable.profile.id")), DETAIL_SP, DIM, false)
        )
        val ready = if (SetupModel.isReady(items)) " · no action required" else ""
        body.addView(
            text(SetupModel.summary(items) + ready, SUMMARY_SP, Color.WHITE, false)
                .apply { setPadding(0, px(SPACING), 0, px(SPACING)) }
        )
        if (failure != null) body.addView(text(failure, DETAIL_SP, WARN, true))
        items.forEach { addRow(it) }
        body.addView(
            text(SetupModel.FOOTNOTE, DETAIL_SP, DIM, false).apply {
                setPadding(0, px(SPACING), 0, 0)
            }
        )
    }

    private fun addRow(item: Item) {
        body.addView(
            text(marker(item.state) + item.title, ROW_TITLE_SP, Color.WHITE, true)
                .apply { setPadding(0, px(SPACING), 0, 0) }
        )
        body.addView(text(item.detail, DETAIL_SP, DIM, false))
        val label = SetupRows.buttonLabel(item) ?: return
        body.addView(
            Button(this).apply {
                text = label
                isAllCaps = false
                setTextColor(Color.WHITE)
                setBackgroundColor(KEY)
                setOnClickListener {
                    render(LaunchReport.message(item.action.label, launcher.run(item.action)))
                }
            }
        )
    }

    private fun marker(state: Readiness): String = when (state) {
        Readiness.Satisfied -> "✓ "
        Readiness.ActionRequired -> "○ "
        Readiness.Informational -> "› "
        Readiness.Unavailable -> "✕ "
        Readiness.Unknown -> "? "
    }

    private fun text(s: String, sp: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = s
        textSize = sp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private companion object {
        const val PAD_H = 16
        const val PAD_V = 18
        const val SPACING = 10
        const val TITLE_SP = 26f
        const val SUMMARY_SP = 15f
        const val ROW_TITLE_SP = 17f
        const val DETAIL_SP = 13f
        val BG = Color.parseColor("#0C1E2D")
        val KEY = Color.parseColor("#1E3A52")
        val DIM = Color.parseColor("#8FA9BF")
        val WARN = Color.parseColor("#FFB347")
    }
}
