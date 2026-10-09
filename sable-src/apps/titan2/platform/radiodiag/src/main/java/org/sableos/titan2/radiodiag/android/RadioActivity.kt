package org.sableos.titan2.radiodiag.android

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.telephony.TelephonyManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.sableos.titan2.radiodiag.core.Consistency
import org.sableos.titan2.radiodiag.core.RadioNames
import org.sableos.titan2.radiodiag.core.RadioReport
import org.sableos.titan2.radiodiag.core.Reading
import org.sableos.titan2.radiodiag.core.Redact
import org.sableos.titan2.radiodiag.core.Row
import org.sableos.titan2.radiodiag.core.Section

private const val TEXT_SIZE_SP = 12f
private const val PADDING_PX = 24

/**
 * Read-only radio and IMS status from public APIs. It cannot see IMS registration (that needs a privileged
 * permission), so it shows the telephony feature flags, the telephony packages that are present and the radio
 * properties instead, and it never changes a setting.
 * Every read reports an explicit state (value, needs permission, unavailable, not supported, unknown), so a value
 * that could not be read is never shown as data. IR-007 and KL-013 stay open and canonical-owned.
 * Authoritative radio evidence comes from the canonical C3B capture, not from this app.
 */
class RadioActivity : Activity() {
    private lateinit var out: TextView
    private var report = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        out =
            TextView(this).apply {
                typeface = Typeface.MONOSPACE
                textSize = TEXT_SIZE_SP
                setTextColor(Color.WHITE)
                setTextIsSelectable(true)
            }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PADDING_PX, PADDING_PX, PADDING_PX, PADDING_PX)
            addView(
                Button(this@RadioActivity).apply {
                    text = "Refresh"
                    isAllCaps = false
                    setOnClickListener { refresh() }
                }
            )
            addView(
                Button(this@RadioActivity).apply {
                    text = "Copy report"
                    isAllCaps = false
                    setOnClickListener { copyReport() }
                }
            )
            addView(out)
        }
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(Color.parseColor("#0C1E2D"))
                addView(col)
            }
        )
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun copyReport() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        val message =
            if (clipboard == null) {
                "Clipboard is not available"
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("radio", report))
                "Report copied (identifiers masked)"
            }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun <T> read(f: () -> T): Reading = try {
        Reading.Value(f().toString())
    } catch (_: SecurityException) {
        Reading.NeedsPermission
    } catch (_: IllegalStateException) {
        Reading.Unavailable("service not ready")
    } catch (_: IllegalArgumentException) {
        Reading.Unavailable("rejected by the platform")
    } catch (_: UnsupportedOperationException) {
        Reading.NotSupported("not supported on this device")
    }

    // The permission check and the protected call stay in the same function so Android Lint can see the guard.
    // READ_BASIC_PHONE_STATE is a normal permission: granted at install, never prompted for.
    private fun readDataNetworkType(tm: TelephonyManager): Reading {
        if (checkSelfPermission(Manifest.permission.READ_BASIC_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Reading.NeedsPermission
        }
        return try {
            Reading.Value(RadioNames.networkType(tm.dataNetworkType))
        } catch (
            _: SecurityException
        ) {
            Reading.NeedsPermission
        } catch (_: IllegalStateException) {
            Reading.Unavailable("service not ready")
        }
    }

    private fun readDataEnabled(tm: TelephonyManager): Reading {
        if (checkSelfPermission(Manifest.permission.READ_BASIC_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Reading.NeedsPermission
        }
        return try {
            Reading.Value(tm.isDataEnabled.toString())
        } catch (
            _: SecurityException
        ) {
            Reading.NeedsPermission
        } catch (_: IllegalStateException) {
            Reading.Unavailable("service not ready")
        }
    }

    private fun refresh() {
        val tm = getSystemService(TelephonyManager::class.java)
        val pm = packageManager
        val features = featureStates(pm)
        val packages = packageStates(pm)
        val sections = listOf(
            simSection(tm),
            Section("Features", features.map { Row(it.key, it.value.toString()) }),
            Section("Telephony packages", packages.map { Row(it.key, it.value.label) }),
            Section("Properties", propertyRows())
        )
        val line = "profile " + SysProps.get("ro.sable.profile.id").ifBlank { "unknown" }
        val present = packages.mapNotNull { (name, state) ->
            state.present?.let { name to it }
        }.toMap()
        val notes = Consistency.notes(features, present)
        report = RadioReport.render(line, sections, notes)
        out.text = report
    }

    private fun slotCount(tm: TelephonyManager): Int = try {
        tm.activeModemCount.coerceAtLeast(1)
    } catch (_: SecurityException) {
        1
    } catch (_: IllegalStateException) {
        1
    }

    private fun simSection(tm: TelephonyManager?): Section {
        if (tm == null) {
            return Section(
                "SIM and network",
                listOf(Row("telephony service", Reading.Unavailable("no TelephonyManager").text()))
            )
        }
        val sims = (0 until slotCount(tm)).map { slot ->
            Row("slot $slot sim state", read { RadioNames.simState(tm.getSimState(slot)) }.text())
        }
        return Section(
            "SIM and network",
            sims + listOf(
                Row("modems", read { tm.activeModemCount }.text()),
                Row("sim operator", read { tm.simOperatorName }.text()),
                Row("network operator", read { tm.networkOperatorName }.text()),
                Row("network type", readDataNetworkType(tm).text()),
                Row("data state", read { RadioNames.dataState(tm.dataState) }.text()),
                Row("data enabled", readDataEnabled(tm).text()),
                Row("roaming", read { tm.isNetworkRoaming }.text())
            )
        )
    }

    private fun featureStates(pm: PackageManager): Map<String, Boolean> = listOf(
        "android.hardware.telephony",
        "android.hardware.telephony.calling",
        "android.hardware.telephony.messaging",
        "android.hardware.telephony.data",
        "android.hardware.telephony.ims",
        "android.hardware.telephony.euicc",
        "android.hardware.telephony.mbms"
    ).associate { it.removePrefix("android.hardware.") to pm.hasSystemFeature(it) }

    /** [present] is null when the query itself failed, so a failed query is never reported as MISSING. */
    private data class PackageState(val present: Boolean?, val label: String)

    private fun packageState(pm: PackageManager, p: String): PackageState = try {
        PackageState(true, pm.getPackageInfo(p, 0).versionName ?: "present")
    } catch (_: PackageManager.NameNotFoundException) {
        PackageState(false, "MISSING")
    } catch (_: SecurityException) {
        PackageState(null, Reading.NeedsPermission.text())
    } catch (_: IllegalStateException) {
        PackageState(null, Reading.Unavailable("package query failed").text())
    }

    private fun packageStates(pm: PackageManager): Map<String, PackageState> = listOf(
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.providers.telephony",
        "com.android.carrierconfig",
        "com.android.cellbroadcastreceiver",
        "com.android.stk",
        "com.android.mms.service"
    ).associateWith { packageState(pm, it) }

    private fun propertyRows(): List<Row> = listOf(
        "gsm.version.baseband",
        "gsm.sim.state",
        "gsm.network.type",
        "gsm.operator.alpha",
        "persist.radio.multisim.config",
        "ro.telephony.sim_slots.count",
        "ro.boot.hardware.sku",
        "ro.sable.profile.id"
    ).map { Row(it, propertyReading(SysProps.get(it)).text()) }

    // SysProps returns an empty string both for an unset property and for a failed hidden-API read (IR-001), so a
    // blank value is reported as exactly that and never as a real empty setting.
    private fun propertyReading(raw: String): Reading = if (raw.isBlank()) {
        Reading.Unavailable(
            "unset or unreadable"
        )
    } else {
        Reading.Value(Redact.text(raw))
    }
}
