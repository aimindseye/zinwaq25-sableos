package org.sableos.tools.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import org.sableos.tools.android.Collectors
import org.sableos.tools.android.Links
import org.sableos.tools.android.ToolsEnv
import org.sableos.tools.core.Capability
import org.sableos.tools.core.Command
import org.sableos.tools.core.DialerCodes
import org.sableos.tools.core.Handoffs
import org.sableos.tools.core.HomeModel
import org.sableos.tools.core.KeyCommands
import org.sableos.tools.core.KeyPress
import org.sableos.tools.core.PermissionPlan
import org.sableos.tools.core.SettingsLink
import org.sableos.tools.core.Tool
import org.sableos.tools.core.ToolKind

/** In-memory only, cleared when the process dies; the report includes it only if the user selects it. */
object Session {
    val keyLog = ArrayDeque<String>()
    val textEntry = mutableMapOf<String, org.sableos.tools.core.CaseResult>()
    const val MAX_LOG = 200

    fun log(line: String) {
        keyLog.addLast(line)
        while (keyLog.size > MAX_LOG) keyLog.removeFirst()
    }
}

/** What a tool screen gets from its host activity. */
class ToolHost(val activity: ToolActivity, val env: ToolsEnv, val tool: Tool) {
    val collectors = Collectors(env)

    fun toast(msg: String) = Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()

    fun openLink(link: SettingsLink, pkg: String? = null) {
        if (!Links.open(activity, link, pkg)) toast("${link.label} is not available on this build")
    }

    fun openTool(t: Tool) = activity.startActivity(ToolActivity.intent(activity, t))
}

/** One tool screen. Views are built once; sensors/camera/mic run only between start() and stop(). */
interface ToolController {
    fun view(): View
    fun start() {}
    fun stop() {}

    /** Local commands (C/R/H/Space/Menu actions). Return true when handled. */
    fun onCommand(c: Command): Boolean = false

    /** KEY_CAPTURE tools see every key event. */
    fun onKeyCapture(e: KeyEvent) {}
    fun onMotion(e: MotionEvent): Boolean = false
    fun info(): String
    fun copyText(): String? = null

    /** Extra actions offered by Menu / Fn+Enter, besides Settings hand-offs. */
    fun actions(): List<Pair<String, () -> Unit>> = emptyList()
}

/**
 * Hosts one tool. Re-checks capability gating on arrival (callers from other apps cannot open a hidden tool),
 * requests runtime permissions only now (PERMISSION_REQUEST_AT_FEATURE_USE), and maps keys through
 * [KeyCommands] so single-letter commands are tool-local and off while a text field has focus.
 */
class ToolActivity : Activity() {
    private lateinit var env: ToolsEnv
    private lateinit var tool: Tool
    private var controller: ToolController? = null
    private var started = false
    private val exit = KeyCommands.DoubleBackExit()

    companion object {
        const val ACTION_OPEN_TOOL = "org.sableos.tools.action.OPEN_TOOL"
        const val ACTION_SEARCH = "org.sableos.tools.action.SEARCH"
        const val EXTRA_TOOL = "org.sableos.tools.extra.TOOL"
        const val EXTRA_QUERY = "org.sableos.tools.extra.QUERY"
        const val EXTRA_NOTICE = "org.sableos.tools.extra.NOTICE"
        private const val REQ_PERMS = 41

        fun intent(ctx: Context, t: Tool): Intent =
            Intent(ctx, ToolActivity::class.java).putExtra(EXTRA_TOOL, t.name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        env = ToolsEnv(this)
        var notice = intent.getStringExtra(EXTRA_NOTICE)
        tool = if (intent.action == ACTION_SEARCH) {
            val (t, n) = resolveSearch(intent.getStringExtra(EXTRA_QUERY).orEmpty())
            if (n != null) notice = n
            t
        } else {
            Tool.byName(intent.getStringExtra(EXTRA_TOOL)) ?: Tool.HARDWARE_TESTS
        }
        title = tool.title
        if (!HomeModel.isListed(tool, env.resolutions, env.developerMode)) {
            showMessage(tool.title, unavailableReason())
            return
        }
        val missing = PermissionPlan.missingFor(tool, env.granted())
        if (missing.isNotEmpty()) {
            showPermissionExplainer(missing)
        } else {
            install(notice)
        }
    }

    /** Friendly names go to the matching diagnostic; the raw dialer code goes through the bridge decision. */
    private fun resolveSearch(q: String): Pair<Tool, String?> {
        val bridge = DialerCodes.bridge(
            env.resolutions.getValue(Capability.FACTORY_TEST_BRIDGE),
            env.developerMode
        )
        return when (val r = DialerCodes.resolveQuery(q, bridge)) {
            is DialerCodes.QueryResult.Category -> r.tool to null

            is DialerCodes.QueryResult.Bridge ->
                (if (r.decision.opensBridge) Tool.FACTORY_BRIDGE else Tool.HARDWARE_TESTS) to
                    r.decision.reason

            DialerCodes.QueryResult.NoMatch -> {
                val hit = Tool.entries.firstOrNull {
                    HomeModel.matches(it, q) &&
                        HomeModel.isListed(it, env.resolutions, env.developerMode)
                }
                (hit ?: Tool.HARDWARE_TESTS) to if (hit == null) "Nothing matched \"$q\"." else null
            }
        }
    }

    private fun unavailableReason(): String {
        val cap = tool.capability
        return when {
            tool.developerOnly && !env.developerMode ->
                "This tool is part of the developer/service tier. Turn on Developer options " +
                    "and Developer diagnostics in Sable Tools."

            cap != null -> {
                val why = env.resolutions.getValue(cap).reason
                "Not available on this device profile (${env.profile.id}): $why"
            }

            else -> "Not available."
        }
    }

    private fun showMessage(t: String, msg: String) {
        val col = Ui.column(this)
        col.addView(Ui.title(this, t))
        col.addView(Ui.body(this, msg))
        col.addView(Ui.row(this, "Back", null) { finish() }.also { it.post { it.requestFocus() } })
        setContentView(col)
    }

    private fun showPermissionExplainer(missing: List<String>) {
        val col = Ui.column(this)
        col.addView(Ui.title(this, tool.title))
        val why = when (tool) {
            Tool.NOISE_METER ->
                "The noise meter reads the microphone level while it is open. " +
                    "No audio is recorded or kept."

            Tool.MAGNIFIER ->
                "The magnifier shows the camera preview while it is open. Nothing is captured or saved."

            Tool.SPEEDOMETER ->
                "The speedometer uses your location while it is open. Nothing is stored after you leave."

            Tool.PEDOMETER ->
                "The pedometer reads the step counter. Without it, an accelerometer estimate is used."

            else -> "This tool needs: " + missing.joinToString { it.substringAfterLast('.') }
        }
        col.addView(Ui.body(this, why))
        col.addView(
            Ui.row(this, "Allow", null) {
                requestPermissions(missing.toTypedArray(), REQ_PERMS)
            }.also { it.post { it.requestFocus() } }
        )
        col.addView(Ui.row(this, "Not now", null) { finish() })
        setContentView(col)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMS) return
        val refused = permissions.filterIndexed { i, _ ->
            grantResults.getOrNull(i) !=
                PackageManager.PERMISSION_GRANTED
        }
        if (PermissionPlan.usableWithout(tool, refused)) {
            install(null)
            if (started) controller?.start()
        } else {
            showMessage(
                tool.title,
                "Permission not granted, so this tool cannot read anything. You can change this in App info."
            )
        }
    }

    private fun install(notice: String?) {
        val host = ToolHost(this, env, tool)
        val c = Controllers.create(host)
        controller = c
        val col = Ui.column(this)
        col.addView(Ui.title(this, tool.title))
        if (notice != null) col.addView(Ui.subtitle(this, notice).apply { setTextColor(Ui.ACCENT) })
        col.addView(
            c.view(),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        col.addView(Ui.hint(this, footer()))
        setContentView(col)
    }

    private fun footer(): String = when (tool.kind) {
        ToolKind.MEASUREMENT -> (
            if (tool.capability?.calibratable ==
                true
            ) {
                "C calibrate · "
            } else {
                ""
            }
            ) +
            "R reset · H hold · I info · Esc back"

        ToolKind.KEY_CAPTURE -> "Every key is captured. Press Back or Esc twice to leave."

        ToolKind.TEXT_TEST -> "Tab next field · Enter check · Esc back"

        ToolKind.REPORT -> "Space select · Enter open · Menu actions · Esc back"

        else -> "Ctrl+C copy · I info · Menu actions · Esc back"
    }

    override fun onResume() {
        super.onResume()
        started = true
        controller?.start()
    }

    override fun onPause() {
        controller?.stop()
        started = false
        super.onPause()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val c = controller
        if (c != null && tool.kind == ToolKind.KEY_CAPTURE) {
            c.onKeyCapture(event)
            if (event.action == KeyEvent.ACTION_UP && exit.onKeyUp(event.keyCode)) finish()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean =
        controller?.onMotion(ev) == true || super.dispatchGenericMotionEvent(ev)

    override fun dispatchTrackballEvent(ev: MotionEvent): Boolean =
        controller?.onMotion(ev) == true || super.dispatchTrackballEvent(ev)

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (tool.kind == ToolKind.KEY_CAPTURE) controller?.onMotion(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val c = controller ?: return super.onKeyDown(keyCode, event)
        val ctx = KeyCommands.contextFor(tool, currentFocus is EditText)
        return when (
            val cmd = KeyCommands.map(
                ctx,
                KeyPress(keyCode, event.unicodeChar, event.metaState, event.repeatCount)
            )
        ) {
            Command.Back -> {
                finish()
                true
            }

            Command.Info -> {
                AlertDialog.Builder(
                    this
                ).setTitle(tool.title).setMessage(c.info()).setPositiveButton("Close", null).show()
                true
            }

            Command.Copy -> {
                val text = c.copyText()
                if (text != null) {
                    getSystemService(
                        ClipboardManager::class.java
                    )?.setPrimaryClip(ClipData.newPlainText(tool.title, text))
                    Toast.makeText(this, "Copied (identifiers masked)", Toast.LENGTH_SHORT).show()
                }
                true
            }

            Command.Actions -> {
                showActions(c)
                true
            }

            Command.Calibrate, Command.Reset, Command.Hold, Command.Toggle, is Command.Move ->
                c.onCommand(cmd) || super.onKeyDown(keyCode, event)

            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun showActions(c: ToolController) {
        val items =
            c.actions() +
                Handoffs.forTool(tool).map { l ->
                    "Open ${l.label}" to
                        { ToolHost(this, env, tool).openLink(l) }
                }
        if (items.isEmpty()) return
        AlertDialog.Builder(this).setTitle(tool.title)
            .setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }
            .setNegativeButton("Close", null).show()
    }
}
