package org.sableos.tools.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import org.sableos.tools.core.Capability
import org.sableos.tools.core.Consistency
import org.sableos.tools.core.FmStatus
import org.sableos.tools.core.RadioLimits
import org.sableos.tools.core.RadioNames
import org.sableos.tools.core.Reading
import org.sableos.tools.core.ReportCategory
import org.sableos.tools.core.ReportRow
import org.sableos.tools.core.ReportSection

/**
 * Radio, IMS-visibility and FM status. Carried over from Sable Radio Diag (RadioActivity), which this replaces:
 * public read-only state only, every read an explicit [Reading] state, the fixed limits block always present.
 */
class RadioCollector(env: ToolsEnv) : CollectorBase(env) {
    private val features = listOf(
        "android.hardware.telephony",
        "android.hardware.telephony.calling",
        "android.hardware.telephony.messaging",
        "android.hardware.telephony.data",
        "android.hardware.telephony.ims",
        "android.hardware.telephony.euicc",
        "android.hardware.telephony.mbms"
    )
    private val packages = listOf(
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.providers.telephony",
        "com.android.carrierconfig",
        "com.android.cellbroadcastreceiver",
        "com.android.stk",
        "com.android.mms.service"
    )
    private val properties = listOf(
        "gsm.version.baseband",
        "gsm.sim.state",
        "gsm.network.type",
        "gsm.operator.alpha",
        "persist.radio.multisim.config",
        "ro.telephony.sim_slots.count",
        "ro.boot.hardware.sku"
    )

    // READ_BASIC_PHONE_STATE is a normal permission: granted at install, never prompted for.
    private fun basic() = ctx.checkSelfPermission(Manifest.permission.READ_BASIC_PHONE_STATE) ==
        PackageManager.PERMISSION_GRANTED

    // READ_BASIC_PHONE_STATE reads are gated on basic() and run inside read {}, which reports a
    // SecurityException as NeedsPermission; Lint cannot see either through the helpers.
    @SuppressLint("MissingPermission")
    private fun simRows(tm: TelephonyManager?): List<ReportRow> {
        if (tm ==
            null
        ) {
            return listOf(
                row("telephony service", Reading.Unavailable("no TelephonyManager").text())
            )
        }
        val slots = (read { tm.activeModemCount }.toIntOrNull() ?: 1).coerceAtLeast(1)
        val needs = Reading.NeedsPermission.text()
        return (0 until slots).map { s ->
            row("slot $s sim state", read { RadioNames.simState(tm.getSimState(s)) })
        } +
            listOf(
                row("modems", read { tm.activeModemCount }),
                row("sim operator", read { tm.simOperatorName }),
                row("network operator", read { tm.networkOperatorName }),
                row(
                    "network type",
                    if (basic()) {
                        read {
                            RadioNames.networkType(tm.dataNetworkType)
                        }
                    } else {
                        needs
                    }
                ),
                row("data state", read { RadioNames.dataState(tm.dataState) }),
                row("data enabled", if (basic()) read { tm.isDataEnabled } else needs),
                row("roaming", read { tm.isNetworkRoaming })
            )
    }

    /** Package presence; a failed query is never reported as MISSING. */
    private fun packageState(p: String): Pair<Boolean?, String> = try {
        true to (pm.getPackageInfo(p, 0).versionName ?: "present")
    } catch (_: PackageManager.NameNotFoundException) {
        false to "MISSING"
    } catch (_: SecurityException) {
        null to Reading.NeedsPermission.text()
    }

    fun sections(): List<ReportSection> {
        val tm = ctx.getSystemService(TelephonyManager::class.java)
        val feat = features.associate {
            it.removePrefix("android.hardware.") to
                pm.hasSystemFeature(it)
        }
        val pkgs = packages.associateWith(::packageState)
        val notes = Consistency.notes(
            feat,
            pkgs.mapNotNull { (k, v) ->
                v.first?.let { k to it }
            }.toMap()
        )
        val fm = FmStatus.of(
            env.resolutions.getValue(Capability.FM_RADIO),
            Links.mediaFmInstalled(ctx)
        )
        val c = ReportCategory.RADIO
        return listOf(
            ReportSection(c, "SIM and network", simRows(tm)),
            ReportSection(c, "Telephony features", feat.map { row(it.key, it.value.toString()) }),
            ReportSection(c, "Telephony packages", pkgs.map { row(it.key, it.value.second) }),
            ReportSection(c, "Radio properties", properties.map { row(it, prop(it)) }),
            ReportSection(
                c,
                "Consistency notes",
                notes.map {
                    row("note", it)
                }.ifEmpty { listOf(row("notes", "none")) }
            ),
            ReportSection(
                c,
                "FM radio (owned by Sable Media)",
                listOf(row("state", fm.state), row("detail", fm.detail))
            ),
            ReportSection(c, "Limits of this report", RadioLimits.rows())
        )
    }
}
