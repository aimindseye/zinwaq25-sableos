package org.sableos.tools.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.sableos.tools.core.Command
import org.sableos.tools.core.ReportCategory
import org.sableos.tools.core.ReportPolicy
import org.sableos.tools.core.Sensitivity
import org.sableos.tools.core.SettingsLink
import org.sableos.tools.core.TextEntryTest
import org.sableos.tools.core.Tool

/**
 * User-authorized report export. The screen lists what will be included, the warnings for sensitive sections and
 * what is never included, before anything leaves the app. Sensitive sections start unselected. Sharing goes through
 * the Android share sheet only when the user chooses it (SILENT_UPLOAD_OR_EXPORT=NO).
 */
class ReportController(private val host: ToolHost) : ToolController {
    private val ctx = host.activity
    private val tool = host.tool
    private val selected = ReportPolicy.preset(tool).toMutableSet()
    private val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val planView: TextView = Ui.body(ctx)
    private val rows = mutableMapOf<View, ReportCategory>()
    private var lastText = ""

    private fun offered(): List<ReportCategory> {
        val base = if (tool == Tool.REPORT_CUSTOM) {
            ReportCategory.entries.toList()
        } else {
            (ReportPolicy.preset(tool) + ReportPolicy.offered(tool)).toList()
        }
        return base.filter { it != ReportCategory.CAPABILITIES || host.env.developerMode }
    }

    override fun view(): View = ScrollView(ctx).apply { addView(root) }.also { build() }

    private fun build() {
        root.removeAllViews()
        rows.clear()
        if (tool == Tool.BUGREPORT) {
            root.addView(
                Ui.body(
                    ctx,
                    "Full system bug reports contain private logs, so Sable Tools does " +
                        "not collect them. Android's own bug report flow lives in Developer " +
                        "options and asks before sharing."
                )
            )
            root.addView(
                Ui.row(ctx, "Open Developer options", null) {
                    host.openLink(SettingsLink.DEVELOPER)
                }.also { it.post { it.requestFocus() } }
            )
            return
        }
        root.addView(Ui.header(ctx, "Sections"))
        offered().forEach { c ->
            val label =
                (if (c in selected) "[x] " else "[ ] ") + c.title +
                    if (c.sensitivity == Sensitivity.SENSITIVE) " (sensitive)" else ""
            val v = Ui.row(ctx, label, null) { toggle(c) }
            rows[v] = c
            root.addView(v)
        }
        root.addView(Ui.header(ctx, "Before sharing"))
        root.addView(planView)
        root.addView(Ui.row(ctx, "Preview", null) { preview() })
        root.addView(Ui.row(ctx, "Copy report", null) { copy() })
        root.addView(Ui.row(ctx, "Share report…", null) { share() })
        updatePlan()
        rows.keys.firstOrNull()?.post { rows.keys.firstOrNull()?.requestFocus() }
    }

    private fun toggle(c: ReportCategory) {
        if (!selected.remove(c)) selected.add(c)
        val focusedCategory = rows[ctx.currentFocus]
        build()
        rows.entries.firstOrNull { it.value == (focusedCategory ?: c) }?.key?.requestFocus()
    }

    private fun updatePlan() {
        val p = ReportPolicy.plan(selected)
        planView.text = buildString {
            appendLine(
                "Included: " + p.included.joinToString {
                    it.title
                }.ifEmpty { "nothing selected" }
            )
            p.warnings.forEach { appendLine("Warning: $it") }
            if (p.notSelected.isNotEmpty()) {
                appendLine(
                    "Not included unless selected: " + p.notSelected.joinToString {
                        it.title
                    }
                )
            }
            append("Never included: " + p.neverIncluded.joinToString())
        }
    }

    private fun render(): String {
        val sections = selected.sortedBy { it.ordinal }.flatMap { c ->
            host.collectors.collect(
                c,
                sessionLog = Session.keyLog.toList(),
                textEntry = TextEntryTest.summary(Session.textEntry)
            )
        }
        lastText =
            ReportPolicy.render(
                "Sable Tools · ${tool.title}",
                host.env.profileLine(),
                selected,
                sections
            )
        return lastText
    }

    private fun preview() {
        if (selected.isEmpty()) return host.toast("Select at least one section")
        android.app.AlertDialog.Builder(
            ctx
        ).setTitle("Preview").setMessage(render()).setPositiveButton("Close", null).show()
    }

    private fun copy() {
        if (selected.isEmpty()) return host.toast("Select at least one section")
        ctx.getSystemService(
            ClipboardManager::class.java
        )?.setPrimaryClip(ClipData.newPlainText("Sable Tools report", render()))
        host.toast("Copied (identifiers masked)")
    }

    private fun share() {
        if (selected.isEmpty()) return host.toast("Select at least one section")
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Sable Tools · ${tool.title}")
            .putExtra(Intent.EXTRA_TEXT, render())
        ctx.startActivity(Intent.createChooser(send, "Share report"))
    }

    override fun onCommand(c: Command): Boolean {
        val cat = if (c == Command.Toggle) rows[ctx.currentFocus] else null
        cat?.let { toggle(it) }
        return cat != null
    }

    override fun info() =
        "Reports are built on this phone from read-only state. Identifiers are masked; " +
            "sensitive sections need your selection."
    override fun copyText(): String? = if (selected.isEmpty()) null else render()
}
