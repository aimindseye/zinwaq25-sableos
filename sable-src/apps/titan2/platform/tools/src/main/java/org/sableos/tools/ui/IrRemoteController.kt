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
import org.sableos.tools.core.ToolsText.t

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
        root.addView(Ui.header(ctx, t("remotes")))
        all().forEach { r ->
            root.addView(
                Ui.row(ctx, r.name, r.type.title + if (r.builtIn) t("built_in") else "") {
                    showRemote(r)
                }
            )
        }
        root.addView(Ui.row(ctx, t("add_a_custom_remote"), null) { addRemote() })
        root.addView(
            Ui.hint(ctx, t("built_in_codes_are_common"))
        )
        root.getChildAt(1)?.post { root.getChildAt(1)?.requestFocus() }
    }

    private fun showRemote(r: Remote) {
        current = r
        root.removeAllViews()
        root.addView(Ui.header(ctx, "${r.name} · ${r.protocol.name}"))
        r.buttons.forEach { b -> root.addView(Ui.row(ctx, b.name, null) { send(r, b) }) }
        if (!r.builtIn) root.addView(Ui.row(ctx, t("add_a_button"), null) { addButton(r) })
        root.addView(Ui.row(ctx, t("all_remotes"), null) { showList() })
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
                ir?.transmit(d.carrierHz, d.pattern) ?: host.toast(t("ir_service_unavailable"))
            } catch (e: IllegalArgumentException) {
                host.toast(t("transmit_refused", e.message))
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
        val name = field(t("remote_name"))
        val form = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(name)
        }
        val types = ApplianceType.entries.map { it.title }.toTypedArray()
        var type = ApplianceType.CUSTOM
        AlertDialog.Builder(ctx).setTitle(t("new_remote_nec_codes")).setView(form)
            .setSingleChoiceItems(types, types.lastIndex) { _, i ->
                type = ApplianceType.entries[i]
            }
            .setPositiveButton(t("create")) { _, _ ->
                val n = name.text.toString().trim()
                if (n.isNotEmpty()) {
                    val r = Remote(n, type, IrProtocol.NEC, emptyList())
                    host.env.prefs.remotes = host.env.prefs.remotes + r
                    showRemote(r)
                }
            }
            .setNegativeButton(t("cancel"), null).show()
    }

    private fun addButton(r: Remote) {
        val name = field(t("button_name"))
        val code = field(t("n32_bit_code_e_g"))
        val form = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(name)
            addView(code)
        }
        AlertDialog.Builder(ctx).setTitle(t("add_button_to", r.name)).setView(form)
            .setPositiveButton(t("add")) { _, _ ->
                val c = RemoteCodec.parseCode(code.text.toString())
                val n = name.text.toString().trim()
                if (c == null || n.isEmpty()) {
                    host.toast(t("enter_a_name_and_a"))
                } else {
                    val updated = r.copy(buttons = r.buttons + IrButton(n, c))
                    host.env.prefs.remotes =
                        host.env.prefs.remotes.map { if (it.name == r.name) updated else it }
                    showRemote(updated)
                }
            }
            .setNegativeButton(t("cancel"), null).show()
    }

    override fun actions(): List<Pair<String, () -> Unit>> {
        val r = current?.takeUnless { it.builtIn } ?: return emptyList()
        return listOf(
            t("delete", r.name) to {
                host.env.prefs.remotes = host.env.prefs.remotes.filterNot { it.name == r.name }
                showList()
            }
        )
    }

    override fun info() = t("remotes_are_stored_only_on")
}
