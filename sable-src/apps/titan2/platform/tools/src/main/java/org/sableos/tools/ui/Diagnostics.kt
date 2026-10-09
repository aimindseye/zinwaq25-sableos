package org.sableos.tools.ui

import android.os.VibrationEffect
import android.os.VibratorManager
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.sableos.tools.core.CaseResult
import org.sableos.tools.core.DiagTarget
import org.sableos.tools.core.DialerCodes
import org.sableos.tools.core.FieldKind
import org.sableos.tools.core.HomeModel
import org.sableos.tools.core.KeyEventFormat
import org.sableos.tools.core.Redactor
import org.sableos.tools.core.ReportSection
import org.sableos.tools.core.TextEntryTest
import org.sableos.tools.core.Tool
import org.sableos.tools.core.TypedKey

/** Read-only information screen built from collector sections. Ctrl+C copies the redacted text. */
open class InfoController(
    protected val host: ToolHost,
    private val supply: () -> List<ReportSection>
) : ToolController {
    protected val ctx = host.activity
    private val text: TextView = Ui.mono(ctx)
    private var rendered = ""

    override fun view(): View = ScrollView(ctx).apply {
        isFocusable = true
        addView(text)
        post { requestFocus() }
    }

    override fun start() {
        rendered = buildString {
            supply().forEach { s ->
                appendLine("## ${s.title}")
                s.rows.forEach {
                    appendLine("${it.key}: ${Redactor.text(it.value).ifBlank { "-" }}")
                }
                appendLine()
            }
        }.trimEnd()
        text.text = rendered
    }

    override fun info() =
        "Read-only. Values come from public Android APIs; identifiers are masked. Menu " +
            "opens the owning Settings page."
    override fun copyText() = rendered
}

class AttentionController(host: ToolHost) :
    InfoController(host, {
        listOf(host.collectors.attention())
    }) {
    override fun actions(): List<Pair<String, () -> Unit>> = listOf(
        "Vibrate once (test)" to {
            val v = ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
            if (v?.hasVibrator() ==
                true
            ) {
                v.vibrate(
                    VibrationEffect.createOneShot(VIBRATE_MS, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                host.toast("No vibrator")
            }
        }
    )

    private companion object {
        const val VIBRATE_MS = 300L
    }
}

/**
 * Keyboard event viewer: every key event an ordinary app receives (key code, scan code, meta state, produced
 * character, device, repeat), using Sable Keyboard's Key Probe format. Keys the vendor framework intercepts never
 * arrive, which is itself the finding. Lines are kept in memory for an optional session-log report section.
 */
class KeyViewerController(host: ToolHost) : ToolController {
    private val ctx = host.activity
    private val meta = Ui.body(ctx, "modifiers: -")
    private val text = Ui.mono(ctx, "Press keys. Back or Esc twice leaves.")
    private val lines = ArrayDeque<String>()

    override fun view(): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        addView(meta)
        addView(
            ScrollView(ctx).apply {
                addView(text)
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
    }

    override fun onKeyCapture(e: KeyEvent) {
        val action = when (e.action) {
            KeyEvent.ACTION_DOWN -> "DOWN"
            KeyEvent.ACTION_UP -> "UP  "
            else -> "ACT${e.action}"
        }
        val l = KeyEventFormat.line(
            action,
            KeyEventFormat.ProbeKey(KeyEvent.keyCodeToString(e.keyCode), e.keyCode, e.scanCode),
            KeyEventFormat.ProbeChar(e.metaState, e.unicodeChar),
            e.device?.name ?: "dev${e.deviceId}",
            e.repeatCount
        )
        Session.log(l)
        lines.addLast(l)
        while (lines.size > MAX_LINES) lines.removeFirst()
        text.text = lines.joinToString("\n")
        meta.text = "modifiers: ${KeyEventFormat.metaNames(e.metaState)}"
    }

    override fun info() =
        "Shows what an ordinary app receives. Software keyboard fallback is covered by " +
            "the critical text-entry test."
    override fun copyText() = lines.joinToString("\n")

    private companion object {
        const val MAX_LINES = 40
    }
}

/** Pointer and touch-surface state: trackpad/mouse/touch events with source and coordinates. */
class PointerController(host: ToolHost) : ToolController {
    private val ctx = host.activity
    private val text = Ui.mono(
        ctx,
        "Move the trackpad, touch the screen or press keys. Back or Esc twice leaves."
    )
    private val lines = ArrayDeque<String>()

    override fun view(): View = ScrollView(ctx).apply { addView(text) }

    override fun onMotion(e: MotionEvent): Boolean {
        val src = listOf(
            android.view.InputDevice.SOURCE_TOUCHSCREEN to "touch",
            android.view.InputDevice.SOURCE_TOUCHPAD to "touchpad",
            android.view.InputDevice.SOURCE_MOUSE to "mouse",
            android.view.InputDevice.SOURCE_TRACKBALL to "trackball"
        ).filter {
            e.isFromSource(it.first)
        }.joinToString("+") { it.second }.ifEmpty { "src=0x%x".format(e.source) }
        val l = "%s %s x=%.1f y=%.1f p=%d dev=%s".format(
            MotionEvent.actionToString(e.actionMasked),
            src,
            e.x,
            e.y,
            e.pointerCount,
            e.device?.name ?: e.deviceId.toString()
        )
        lines.addLast(l)
        while (lines.size > MAX_LINES) lines.removeFirst()
        text.text = lines.joinToString("\n")
        return e.isFromSource(android.view.InputDevice.SOURCE_TRACKBALL)
    }

    override fun onKeyCapture(e: KeyEvent) {
        if (e.action == KeyEvent.ACTION_DOWN) {
            lines.addLast("KEY ${KeyEvent.keyCodeToString(e.keyCode)} scan=${e.scanCode}")
            text.text = lines.joinToString("\n")
        }
    }

    override fun info() =
        "Trackpads that report as a D-pad show up as keys; ones that report as a pointer " +
            "show up as motion."

    private companion object {
        const val MAX_LINES = 40
    }
}

/**
 * Critical text-entry test. A real EditText owns input, so single-letter commands are off (TEXT_INPUT_ALWAYS_WINS).
 * Only pass/fail is kept; the typed text is discarded.
 */
class TextEntryController(private val host: ToolHost) : ToolController {
    private val ctx = host.activity
    private val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    override fun view(): View = ScrollView(ctx).apply { addView(root) }.also { build() }

    private fun build() {
        root.removeAllViews()
        TextEntryTest.CASES.forEach { c ->
            val result = Ui.subtitle(ctx, (Session.textEntry[c.id] ?: CaseResult.NOT_RUN).name)
            val keys = mutableListOf<TypedKey>()
            val field = EditText(ctx).apply {
                hint = c.prompt
                setSingleLine()
                imeOptions = EditorInfo.IME_ACTION_DONE
                inputType = when (c.field) {
                    FieldKind.TEXT ->
                        InputType.TYPE_CLASS_TEXT or
                            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

                    FieldKind.NUMBER -> InputType.TYPE_CLASS_NUMBER

                    FieldKind.NUMBER_PASSWORD ->
                        InputType.TYPE_CLASS_NUMBER or
                            InputType.TYPE_NUMBER_VARIATION_PASSWORD

                    FieldKind.VISIBLE_PASSWORD ->
                        InputType.TYPE_CLASS_TEXT or
                            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                }
                setOnKeyListener { _, _, e ->
                    if (e.action == KeyEvent.ACTION_DOWN &&
                        e.unicodeChar > 0
                    ) {
                        keys.add(TypedKey(e.metaState, e.unicodeChar))
                    }
                    false
                }
                setOnFocusChangeListener { v, has ->
                    if (has &&
                        c.softwareKeyboard
                    ) {
                        ctx.getSystemService(
                            InputMethodManager::class.java
                        )?.showSoftInput(v, 0)
                    }
                }
                setOnEditorActionListener { v, _, _ ->
                    val r = TextEntryTest.check(c, (v as EditText).text.toString(), keys)
                    Session.textEntry[c.id] = r
                    result.text = r.name
                    v.text.clear()
                    keys.clear()
                    true
                }
            }
            root.addView(Ui.header(ctx, c.title))
            root.addView(field)
            root.addView(result)
        }
    }

    override fun info() =
        "Covers letters, numbers, symbols, Alt, Sym, Fn, a Bluetooth pairing code, a Wi-" +
            "Fi password, a PIN and the software keyboard fallback. Press Enter in a field to" +
            " check it. Only pass/fail is kept."

    override fun copyText() = TextEntryTest.summary(Session.textEntry).joinToString("\n") {
        "${it.key}: ${it.value}"
    }
}

/** Sable hardware test categories: factory-test concepts mapped to Sable tools, owners or gates. */
class HardwareTestsController(private val host: ToolHost) : ToolController {
    private val ctx = host.activity

    /** Badge text and action for one factory-test item. */
    private fun entry(target: DiagTarget): Pair<String, () -> Unit> = when (target) {
        is DiagTarget.Open -> openEntry(target.tool)

        is DiagTarget.Elsewhere ->
            target.owner to
                { target.link?.let { host.openLink(it) } ?: Unit }

        is DiagTarget.Gated -> "engineering only" to { host.toast(target.why) }
    }

    private fun openEntry(tool: Tool): Pair<String, () -> Unit> =
        if (HomeModel.isListed(tool, host.env.resolutions, host.env.developerMode)) {
            tool.title to { host.openTool(tool) }
        } else {
            "not on this device" to
                { host.toast("${tool.title} is not available on this device profile") }
        }

    override fun view(): View {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        DialerCodes.byCategory().forEach { (cat, items) ->
            col.addView(Ui.header(ctx, cat.title))
            items.forEach { item ->
                val (badge, open) = entry(item.target)
                col.addView(Ui.row(ctx, item.name, badge) { open() })
            }
        }
        col.getChildAt(1)?.post { col.getChildAt(1)?.requestFocus() }
        return ScrollView(ctx).apply { addView(col) }
    }

    override fun info() =
        "Factory test (${DialerCodes.FACTORY_TEST_DIAL}) categories mapped to Sable " +
            "diagnostics. Calibration, logging and aging tests change device state and are " +
            "never one-tap actions here."
}

/** Developer-tier explanation of the vendor factory surface; it never launches a raw vendor tool. */
class FactoryBridgeController(host: ToolHost) :
    InfoController(host, {
        listOf(
            ReportSection(
                org.sableos.tools.core.ReportCategory.CAPABILITIES,
                "Factory test bridge",
                listOf(
                    org.sableos.tools.core.ReportRow(
                        "profile",
                        host.env.resolutions.getValue(
                            org.sableos.tools.core.Capability.FACTORY_TEST_BRIDGE
                        ).reason
                    ),
                    org.sableos.tools.core.ReportRow(
                        "raw vendor launch",
                        "not offered (RAW_FACTORY_TEST_DIRECT_LAUNCH=NO_BY_DEFAULT)"
                    ),
                    org.sableos.tools.core.ReportRow(
                        "calibration / logging / aging",
                        "engineering-gated; use the vendor tool only under a service procedure"
                    ),
                    org.sableos.tools.core.ReportRow(
                        "read-only equivalents",
                        "Diagnostics > Hardware test categories"
                    )
                )
            )
        )
    }) {
    override fun actions(): List<Pair<String, () -> Unit>> = listOf(
        "Hardware test categories" to {
            host.openTool(Tool.HARDWARE_TESTS)
        }
    )
}
