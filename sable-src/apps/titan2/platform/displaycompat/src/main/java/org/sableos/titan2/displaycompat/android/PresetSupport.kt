package org.sableos.titan2.displaycompat.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import java.io.File
import org.sableos.titan2.displaycompat.R
import org.sableos.titan2.displaycompat.core.AppLayout
import org.sableos.titan2.displaycompat.core.DisplayCompatText.t
import org.sableos.titan2.displaycompat.core.DisplayProfile
import org.sableos.titan2.displaycompat.core.Presets
import org.sableos.titan2.displaycompat.core.SaveStatus

/**
 * Out-of-box presets (2.0). The list is a data file: the image can carry a newer one at
 * [OVERRIDE] (product/q25/displaycompat), and the copy built into the app is the fallback.
 */
object PresetSource {
    const val OVERRIDE = "/product/etc/sable/app-compat-presets.tsv"

    fun read(context: Context): Map<String, DisplayProfile> {
        val text =
            runCatching { File(OVERRIDE).takeIf { it.canRead() }?.readText() }.getOrNull()
                ?: runCatching {
                    context.resources.openRawResource(
                        R.raw.app_compat_presets
                    ).bufferedReader().use {
                        it.readText()
                    }
                }.getOrDefault("")
        return Presets.parse(text)
    }
}

/** Which apps were given their preset already, so it is given only once. */
class SeededPresets(context: Context) {
    private val prefs = context.getSharedPreferences("presets", Context.MODE_PRIVATE)

    fun read(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty()

    fun add(pkgs: Collection<String>) {
        if (pkgs.isEmpty()) return
        prefs.edit().putStringSet(KEY, read() + pkgs).apply()
    }

    private companion object {
        const val KEY = "seeded"
    }
}

/** Gives installed apps their preset; returns the labels and layouts of apps that got a visible one. */
fun DisplayCompatApp.seedPresets(): List<Pair<String, String>> {
    if (!unlocked() || presets.isEmpty()) return emptyList()
    val resolver = TraitsResolver(this)
    val apps = resolver.launchable().associateBy { it.pkg }
    val stored = store.packages()
    val seeded = seededPresets.read()
    // An app the person set up before its preset arrived keeps their choice.
    seededPresets.add(apps.keys.filter { it in presets && it in stored && it !in seeded })
    val todo = Presets.toSeed(apps.keys, presets, seeded, stored)
    val native = runCatching { WmDisplayPort(contentResolver).initialSize() }.getOrNull()
    val shown = mutableListOf<Pair<String, String>>()
    todo.forEach { pkg ->
        val preset = presets.getValue(pkg)
        if (!preset.isNative && native != null) {
            val outcome = controller.save(
                resolver.resolve(pkg),
                preset,
                native,
                advanced = false,
                acknowledged = true
            )
            if (outcome.status ==
                SaveStatus.STORED
            ) {
                shown += apps.getValue(pkg).label to AppLayout.of(preset).label
            } else {
                Log.i("SableDisplayTools", "preset for $pkg not applied: ${outcome.status}")
            }
        }
    }
    seededPresets.add(todo)
    if (todo.isNotEmpty()) refreshDisplay()
    return shown
}

/** "Reset" in the app's page: the preset again when the app has one, else Square. */
fun DisplayCompatApp.resetToPreset(
    pkg: String,
    traitsOf: (String) -> org.sableos.titan2.displaycompat.core.AppTraits
): String {
    val preset = presets[pkg]
    val native = runCatching { WmDisplayPort(contentResolver).initialSize() }.getOrNull()
    val message =
        if (preset == null || preset.isNative || native == null) {
            controller.resetChecked(pkg).message()
        } else {
            controller.save(
                traitsOf(pkg),
                preset,
                native,
                advanced = false,
                acknowledged = true
            ).message()
        }
    refreshDisplay()
    return message
}

/** A new app gets its preset as soon as it is installed, with a one-line notice. */
class PresetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED ||
            intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        ) {
            return
        }
        notify(context, context.displayCompat.seedPresets())
    }

    companion object {
        fun notify(context: Context, shown: List<Pair<String, String>>) {
            shown.forEach { (label, layout) ->
                Toast.makeText(context, t("preset_notice", label, layout), Toast.LENGTH_LONG).show()
            }
        }
    }
}
