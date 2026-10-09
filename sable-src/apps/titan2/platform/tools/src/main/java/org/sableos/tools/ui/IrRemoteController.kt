package org.sableos.tools.ui

import android.app.AlertDialog
import android.hardware.ConsumerIrManager
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import org.sableos.tools.core.ApplianceType
import org.sableos.tools.core.Capability
import org.sableos.tools.core.IrButton
import org.sableos.tools.core.IrProtocol
import org.sableos.tools.core.IrTransmit
import org.sableos.tools.core.Remote
import org.sableos.tools.core.RemoteCodec
import org.sableos.tools.core.RemoteLibrary

/**
 * Local-first IR remote: built-in table plus the user's own remotes, all on the device; no network. Shown only when
 * the profile proves an IR transmitter (the Q25 profile does not). No learning mode: Android has no public IR
 * receive API.
 */
class IrRemoteController(private val host: ToolHost) : ToolController {
    private val ctx = host.activity
    private val ir = ctx.getSystemService(ConsumerIrManager::class.java)
    private val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private var current: Remote? = null

    private fun all(): List<Remote> = RemoteLibrary.BUILT_IN + host.env.prefs.remotes

    override fun view(): View = android.widget.ScrollView(ctx).apply {
        addView(root)
    }.also { showList() }

    private fun showList() {
        current = null
        root.removeAllViews()
        root.addView(Ui.header(ctx, "Remotes"))
        all().forEach { r ->
            root.addView(
                Ui.row(ctx, r.name, r.type.title + if (r.builtIn) " · built-in" else "") {
                    showRemote(r)
                }
            )
        }
        root.addView(Ui.row(ctx, "Add a custom remote", null) { addRemote() })
        root.addView(
            Ui.hint(ctx, "Built-in codes are common published codes, not verified on every model.")
        )
        root.getChildAt(1)?.post { root.getChildAt(1)?.requestFocus() }
    }

    private fun showRemote(r: Remote) {
        current = r
        root.removeAllViews()
        root.addView(Ui.header(ctx, "${r.name} · ${r.protocol.name}"))
        r.buttons.forEach { b -> root.addView(Ui.row(ctx, b.name, null) { send(r, b) }) }
        if (!r.builtIn) root.addView(Ui.row(ctx, "Add a button", null) { addButton(r) })
        root.addView(Ui.row(ctx, "All remotes", null) { showList() })
        root.getChildAt(1)?.post { root.getChildAt(1)?.requestFocus() }
    }

    private fun send(r: Remote, b: IrButton) {
        val ranges = ir?.carrierFrequencies?.map { it.minFrequency..it.maxFrequency }.orEmpty()
        when (
            val d = IrTransmit.decide(
                r,
                b,
                host.env.resolutions.getValue(Capability.IR_REMOTE),
                ranges
            )
        ) {
            is IrTransmit.Decision.Send -> try {
                ir?.transmit(d.carrierHz, d.pattern) ?: host.toast("IR service unavailable")
            } catch (e: IllegalArgumentException) {
                host.toast("Transmit refused: ${e.message}")
            }

            is IrTransmit.Decision.Refuse -> host.toast(d.reason)
        }
    }

    private fun field(hint: String, type: Int = InputType.TYPE_CLASS_TEXT) = EditText(ctx).apply {
        this.hint = hint
        inputType = type
        setSingleLine()
    }

    private fun addRemote() {
        val name = field("Remote name")
        val form = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(name)
        }
        val types = ApplianceType.entries.map { it.title }.toTypedArray()
        var type = ApplianceType.CUSTOM
        AlertDialog.Builder(ctx).setTitle("New remote (NEC codes)").setView(form)
            .setSingleChoiceItems(types, types.lastIndex) { _, i ->
                type = ApplianceType.entries[i]
            }
            .setPositiveButton("Create") { _, _ ->
                val n = name.text.toString().trim()
                if (n.isNotEmpty()) {
                    val r = Remote(n, type, IrProtocol.NEC, emptyList())
                    host.env.prefs.remotes = host.env.prefs.remotes + r
                    showRemote(r)
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun addButton(r: Remote) {
        val name = field("Button name")
        val code = field("32-bit code, e.g. 0x20DF10EF")
        val form = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(name)
            addView(code)
        }
        AlertDialog.Builder(ctx).setTitle("Add button to ${r.name}").setView(form)
            .setPositiveButton("Add") { _, _ ->
                val c = RemoteCodec.parseCode(code.text.toString())
                val n = name.text.toString().trim()
                if (c == null || n.isEmpty()) {
                    host.toast("Enter a name and a hex code up to 8 digits")
                } else {
                    val updated = r.copy(buttons = r.buttons + IrButton(n, c))
                    host.env.prefs.remotes =
                        host.env.prefs.remotes.map { if (it.name == r.name) updated else it }
                    showRemote(updated)
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    override fun actions(): List<Pair<String, () -> Unit>> {
        val r = current?.takeUnless { it.builtIn } ?: return emptyList()
        return listOf(
            "Delete ${r.name}" to {
                host.env.prefs.remotes = host.env.prefs.remotes.filterNot { it.name == r.name }
                showList()
            }
        )
    }

    override fun info() =
        "Remotes are stored only on this phone. No network is used. Learning from an " +
            "original remote is not offered because Android has no public IR receiver API."
}
