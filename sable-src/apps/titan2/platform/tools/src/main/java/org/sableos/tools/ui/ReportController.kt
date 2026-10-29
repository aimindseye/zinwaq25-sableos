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
import org.sableos.tools.core.ReportFile
import org.sableos.tools.core.ReportPolicy
import org.sableos.tools.core.Sensitivity
import org.sableos.tools.core.SettingsLink
import org.sableos.tools.core.TextEntryTest
import org.sableos.tools.core.Tool
import org.sableos.tools.core.ToolsText.t

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
                    t("full_system_bug_reports_contain")
                )
            )
            root.addView(
                Ui.row(ctx, t("open_developer_options"), null) {
                    host.openLink(SettingsLink.DEVELOPER)
                }.also { it.post { it.requestFocus() } }
            )
            return
        }
        root.addView(Ui.header(ctx, t("sections")))
        offered().forEach { c ->
            val label =
                (if (c in selected) "[x] " else "[ ] ") + c.title +
                    if (c.sensitivity == Sensitivity.SENSITIVE) " (sensitive)" else ""
            val v = Ui.row(ctx, label, null) { toggle(c) }
            rows[v] = c
            root.addView(v)
        }
        root.addView(Ui.header(ctx, t("before_sharing")))
        root.addView(planView)
        root.addView(Ui.row(ctx, t("preview"), null) { preview() })
        root.addView(Ui.row(ctx, t("copy_report"), null) { copy() })
        root.addView(Ui.row(ctx, t("save_to_phone"), ReportFile.FOLDER) { save() })
        root.addView(Ui.row(ctx, t("share_report"), null) { share() })
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
                t("included") + p.included.joinToString {
                    it.title
                }.ifEmpty { t("nothing_selected") }
            )
            p.warnings.forEach { appendLine(t("warning", it)) }
            if (p.notSelected.isNotEmpty()) {
                appendLine(
                    t("not_included_unless_selected") + p.notSelected.joinToString {
                        it.title
                    }
                )
            }
            append(t("never_included") + p.neverIncluded.joinToString())
        }
    }

    private fun render(): String {
        val sections = selected.sortedBy { it.ordinal }.flatMap { c ->
            host.collectors.collect(
                c,
                sessionLog = Session.keyLog.toList(),
                textEntry = TextEntryTest.summary(
                    Session.textEntry,
                    TextEntryTest.casesFor(host.env.profile.keyboard)
                )
            )
        }
        lastText =
            ReportPolicy.render(
                t("sable_tools", tool.title),
                host.env.profileLine(),
                selected,
                sections
            )
        return lastText
    }

    private fun preview() {
        if (selected.isEmpty()) return host.toast(NOTHING_SELECTED)
        android.app.AlertDialog.Builder(
            ctx
        ).setTitle(t("preview")).setMessage(render()).setPositiveButton(t("close"), null).show()
    }

    private fun copy() {
        if (selected.isEmpty()) return host.toast(NOTHING_SELECTED)
        ctx.getSystemService(
            ClipboardManager::class.java
        )?.setPrimaryClip(ClipData.newPlainText(t("sable_tools_report"), render()))
        host.toast(t("copied_identifiers_masked"))
    }

    private fun save() {
        if (selected.isEmpty()) return host.toast(NOTHING_SELECTED)
        host.saveToFile(render())
    }

    private fun share() {
        if (selected.isEmpty()) return host.toast(NOTHING_SELECTED)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, t("sable_tools", tool.title))
            .putExtra(Intent.EXTRA_TEXT, render())
        ctx.startActivity(Intent.createChooser(send, t("share_report_2")))
    }

    override fun onCommand(c: Command): Boolean {
        val cat = if (c == Command.Toggle) rows[ctx.currentFocus] else null
        cat?.let { toggle(it) }
        return cat != null
    }

    override fun info() = t("reports_are_built_on_this")
    override fun copyText(): String? = if (selected.isEmpty()) null else render()
}

private val NOTHING_SELECTED: String get() = t("select_at_least_one_section")
