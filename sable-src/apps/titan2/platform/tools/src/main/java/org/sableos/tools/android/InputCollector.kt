package org.sableos.tools.android

import android.content.res.Configuration
import android.hardware.input.InputManager
import android.os.VibratorManager
import android.provider.Settings
import android.view.InputDevice
import org.sableos.tools.core.ReportCategory
import org.sableos.tools.core.ReportRow
import org.sableos.tools.core.ReportSection
import org.sableos.tools.core.SubscreenModes

/** Keyboard, pointer and attention-hardware facts from public input APIs. */
class InputCollector(env: ToolsEnv) : CollectorBase(env) {
    private fun devices(): List<InputDevice> =
        ctx.getSystemService(InputManager::class.java)?.inputDeviceIds?.toList().orEmpty()
            .mapNotNull { InputDevice.getDevice(it) }

    private val sourceNames = listOf(
        InputDevice.SOURCE_TOUCHSCREEN to "touchscreen",
        InputDevice.SOURCE_TOUCHPAD to "touchpad",
        InputDevice.SOURCE_MOUSE to "mouse",
        InputDevice.SOURCE_TRACKBALL to "trackball",
        InputDevice.SOURCE_DPAD to "dpad"
    )

    private fun deviceRow(d: InputDevice): ReportRow {
        val keyboard = when {
            !d.supportsSource(InputDevice.SOURCE_KEYBOARD) -> emptyList()
            d.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC -> listOf("alphabetic keyboard")
            else -> listOf("keys")
        }
        val kinds = (keyboard + sourceNames.filter { d.supportsSource(it.first) }.map { it.second })
            .ifEmpty { listOf("other") }
        val ext = if (d.isExternal) " external" else ""
        // Raw-but-safe identifiers only in the developer/service tier.
        val ids = if (env.developerMode) {
            " vid=0x%04x pid=0x%04x".format(
                d.vendorId,
                d.productId
            )
        } else {
            ""
        }
        return row("device ${d.id}", "${d.name}: ${kinds.joinToString("+")}$ext$ids")
    }

    private fun configRows(): List<ReportRow> {
        val cfg = ctx.resources.configuration
        val keyboard = when (cfg.keyboard) {
            Configuration.KEYBOARD_QWERTY -> "QWERTY"
            Configuration.KEYBOARD_12KEY -> "12KEY"
            Configuration.KEYBOARD_NOKEYS -> "NOKEYS"
            else -> "UNDEFINED(${cfg.keyboard})"
        }
        val hidden = when (cfg.hardKeyboardHidden) {
            Configuration.HARDKEYBOARDHIDDEN_NO -> "NO (open/usable)"
            Configuration.HARDKEYBOARDHIDDEN_YES -> "YES"
            else -> "UNDEFINED"
        }
        val nav = when (cfg.navigation) {
            Configuration.NAVIGATION_NONAV -> "NONAV"
            Configuration.NAVIGATION_DPAD -> "DPAD"
            Configuration.NAVIGATION_TRACKBALL -> "TRACKBALL"
            Configuration.NAVIGATION_WHEEL -> "WHEEL"
            else -> "UNDEFINED"
        }
        return listOf(
            row("configuration keyboard", keyboard),
            row("hard keyboard hidden", hidden),
            row("navigation", nav),
            row(
                "default input method",
                read {
                    Settings.Secure.getString(
                        ctx.contentResolver,
                        Settings.Secure.DEFAULT_INPUT_METHOD
                    )
                }
            )
        )
    }

    private fun profile() = ReportSection(
        ReportCategory.INPUT,
        "Keyboard profile",
        listOf(
            row("sable profile", env.profileLine()),
            row(
                "sable keyboard layout",
                "chosen by Sable Keyboard from the profile (KeyLayout.forProfile)"
            ),
            row("key remapping", "owned by Settings > Keyboard & input"),
            row(
                "evidence",
                "the key viewer shows what an ordinary app receives; vendor-intercepted keys do not appear"
            )
        )
    )

    fun sections(textEntry: List<ReportRow>): List<ReportSection> {
        val devices = ReportSection(
            ReportCategory.INPUT,
            "Keyboard and input devices",
            configRows() + devices().map(::deviceRow)
        )
        val text = if (textEntry.isEmpty()) {
            emptyList()
        } else {
            listOf(
                ReportSection(ReportCategory.INPUT, "Critical text-entry test", textEntry)
            )
        }
        return listOf(profile(), devices) + text
    }

    fun attention(): ReportSection {
        val v = ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        val rows = mutableListOf(
            row("vibrator", (v?.hasVibrator() == true).toString()),
            row("amplitude control", (v?.hasAmplitudeControl() == true).toString())
        )
        devices().forEach { d ->
            val lights = try {
                d.lightsManager.lights
            } catch (_: RuntimeException) {
                emptyList()
            }
            lights.forEach { l -> rows += row("light on ${d.name}", "${l.name} type=${l.type}") }
        }
        val modes = SubscreenModes.available(env.resolutions)
        rows +=
            row(
                "subscreen companion",
                if (modes.isEmpty()) "not available on this profile" else modes.joinToString(", ")
            )
        return ReportSection(ReportCategory.DEVICE, "Attention and SubScreen", rows)
    }
}
