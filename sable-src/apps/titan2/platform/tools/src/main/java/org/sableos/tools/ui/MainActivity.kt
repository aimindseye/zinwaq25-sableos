package org.sableos.tools.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.sableos.tools.R
import org.sableos.tools.android.SecretCodeSync
import org.sableos.tools.android.ToolsEnv
import org.sableos.tools.core.Command
import org.sableos.tools.core.HomeInputs
import org.sableos.tools.core.HomeModel
import org.sableos.tools.core.KeyCommands
import org.sableos.tools.core.KeyContext
import org.sableos.tools.core.KeyPress
import org.sableos.tools.core.Tool
import org.sableos.tools.core.Visibility

/**
 * Sable Tools home: title + device-profile summary, a bounded Recent row, then Utilities / Diagnostics / Reports.
 * Only tools the active profile proves are listed (no grid of unavailable hardware). Keyboard-first: printable keys
 * filter, / starts an explicit search, arrows move, Enter opens, Menu (or Fn+Enter) shows details, Back/Esc clears
 * the filter or leaves.
 */
class MainActivity : Activity() {
    private lateinit var env: ToolsEnv
    private lateinit var list: LinearLayout
    private lateinit var summary: TextView
    private lateinit var filterLine: TextView
    private var filter = ""
    private var searching = false
    private val rowTools = mutableMapOf<View, Tool>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = Ui.column(this)
        col.addView(Ui.title(this, getString(R.string.app_name)))
        summary = Ui.subtitle(this, "")
        col.addView(summary)
        filterLine = Ui.subtitle(this, "").apply { setTextColor(Ui.TEXT) }
        col.addView(filterLine)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(list)
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(Ui.BG)
                addView(col)
            }
        )
    }

    override fun onResume() {
        super.onResume()
        env = ToolsEnv(this)
        SecretCodeSync.sync(this)
        render()
        publishShortcuts()
    }

    private fun inputs() = HomeInputs(
        profile = env.profile,
        probes = env.probes,
        developerMode = env.developerMode,
        granted = env.granted(),
        calibrated = env.prefs.calibrated(),
        recents = env.prefs.recents,
        filter = filter
    )

    private fun render() {
        val inputs = inputs()
        val groups = HomeModel.build(inputs)
        summary.text = HomeModel.profileSummary(inputs, groups)
        val filtering = searching || filter.isNotEmpty()
        filterLine.visibility = if (filtering) View.VISIBLE else View.GONE
        filterLine.text = "Filter: $filter▏  (Backspace deletes, Esc clears)"
        list.removeAllViews()
        rowTools.clear()
        groups.forEach { g ->
            list.addView(Ui.header(this, g.title))
            g.items.forEach { item ->
                val v = Ui.row(this, item.tool.title, item.badge.label) { open(item.tool) }
                rowTools[v] = item.tool
                list.addView(v)
            }
        }
        if (groups.isEmpty()) list.addView(Ui.body(this, "No tool matches \"$filter\"."))
        if (env.systemDeveloperOptions && !filtering) {
            list.addView(Ui.header(this, "Developer"))
            val label = "Developer diagnostics: " + if (env.prefs.developerOptIn) "on" else "off"
            list.addView(
                Ui.row(this, label, null) {
                    env.prefs.developerOptIn = !env.prefs.developerOptIn
                    env = ToolsEnv(this)
                    render()
                }
            )
        }
        rowTools.keys.firstOrNull()?.requestFocus()
    }

    private fun open(tool: Tool) {
        env.prefs.recents = HomeModel.pushRecent(env.prefs.recents, tool)
        startActivity(ToolActivity.intent(this, tool))
    }

    private fun details(tool: Tool) {
        val cap = tool.capability
        val text = buildString {
            appendLine("${tool.section.title} · ${tool.kind.name.lowercase()}")
            if (cap != null) appendLine(env.resolutions.getValue(cap).reason)
            if (tool.permissions.isNotEmpty()) {
                appendLine(
                    "Asks when opened: " + tool.permissions.joinToString {
                        it.substringAfterLast('.')
                    }
                )
            }
            if (tool.developerOnly) appendLine("Developer/service tier")
        }
        AlertDialog.Builder(this).setTitle(tool.title).setMessage(text.trim())
            .setPositiveButton("Open") { _, _ -> open(tool) }
            .setNegativeButton("Close", null).show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val ctx = KeyContext.Home(filterActive = searching || filter.isNotEmpty())
        return when (
            val c = KeyCommands.map(
                ctx,
                KeyPress(keyCode, event.unicodeChar, event.metaState, event.repeatCount)
            )
        ) {
            is Command.FilterAppend -> {
                filter += c.ch
                render()
                true
            }

            Command.FilterDelete -> {
                filter = filter.dropLast(1)
                render()
                true
            }

            Command.Search -> {
                searching = true
                render()
                true
            }

            Command.Actions -> {
                rowTools[currentFocus]?.let { details(it) }
                true
            }

            Command.Back -> {
                if (searching || filter.isNotEmpty()) {
                    filter = ""
                    searching = false
                    render()
                } else {
                    finish()
                }
                true
            }

            // Arrows and Enter use framework focus navigation and the focused row's click.
            else -> super.onKeyDown(keyCode, event)
        }
    }

    /** Pinnable shortcuts (e.g. into Sable Start) for proven, frequently used tools only. */
    private fun publishShortcuts() {
        val sm = getSystemService(ShortcutManager::class.java) ?: return
        val candidates = listOf(Tool.IR_REMOTE, Tool.FLASHLIGHT, Tool.COMPASS, Tool.KEY_VIEWER)
        val shortcuts = candidates.filter { t ->
            val cap = t.capability
            cap == null || env.resolutions.getValue(cap).visibility == Visibility.VISIBLE
        }.take(sm.maxShortcutCountPerActivity).map { t ->
            ShortcutInfo.Builder(this, t.name)
                .setShortLabel(t.title)
                .setIcon(Icon.createWithResource(this, R.drawable.ic_sable_tools))
                .setIntent(ToolActivity.intent(this, t).setAction(ToolActivity.ACTION_OPEN_TOOL))
                .build()
        }
        try {
            sm.dynamicShortcuts = shortcuts
        } catch (_: IllegalStateException) {
            // rate-limited or locked user: shortcuts are a convenience only
        }
    }
}
