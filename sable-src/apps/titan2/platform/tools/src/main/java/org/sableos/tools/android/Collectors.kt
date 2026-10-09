package org.sableos.tools.android

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.os.storage.StorageManager
import android.system.Os
import android.system.OsConstants
import android.view.WindowManager
import org.sableos.tools.core.Reading
import org.sableos.tools.core.ReportCategory
import org.sableos.tools.core.ReportRow
import org.sableos.tools.core.ReportSection

private const val BYTES_PER_MIB = 1024L * 1024L
private const val MS_PER_MIN = 60_000L

/** Shared read helpers: every failure becomes an explicit [Reading] state, never "false" or blank. */
open class CollectorBase(protected val env: ToolsEnv) {
    protected val ctx: Context = env.ctx
    protected val pm: PackageManager = ctx.packageManager

    protected fun <T> read(f: () -> T?): String = try {
        f()?.let { Reading.Value(it.toString()).text() } ?: Reading.Unknown.text()
    } catch (_: SecurityException) {
        Reading.NeedsPermission.text()
    } catch (_: IllegalStateException) {
        Reading.Unavailable("service not ready").text()
    } catch (_: IllegalArgumentException) {
        Reading.Unavailable("rejected by the platform").text()
    } catch (_: UnsupportedOperationException) {
        Reading.NotSupported("not supported on this device").text()
    }

    protected fun prop(key: String): String =
        SysProps.get(key).ifBlank { Reading.Unavailable("unset or unreadable").text() }

    protected fun row(k: String, v: String) = ReportRow(k, v)

    protected fun unavailable(c: ReportCategory, title: String, what: String) =
        ReportSection(c, title, listOf(row(what, Reading.Unavailable("no system service").text())))
}

/**
 * Read-only collectors for Diagnostics screens and Reports. Each returns report sections whose values are plain
 * text; the report renderer redacts every value again.
 */
class Collectors(env: ToolsEnv) : CollectorBase(env) {
    private val radio = RadioCollector(env)
    private val input = InputCollector(env)

    fun collect(
        c: ReportCategory,
        sessionLog: List<String> = emptyList(),
        textEntry: List<ReportRow> = emptyList()
    ): List<ReportSection> = when (c) {
        ReportCategory.DEVICE -> listOf(device())

        ReportCategory.INPUT -> input.sections(textEntry)

        ReportCategory.DISPLAY -> listOf(display())

        ReportCategory.SENSORS -> listOf(sensors())

        ReportCategory.CAMERA -> listOf(camera())

        ReportCategory.RADIO -> radio.sections()

        ReportCategory.STORAGE -> listOf(storage())

        ReportCategory.CAPABILITIES -> listOf(capabilities())

        ReportCategory.NETWORK -> listOf(network())

        ReportCategory.APPS -> listOf(apps())

        ReportCategory.SESSION_LOG -> listOf(
            ReportSection(
                c,
                "Diagnostic session log",
                sessionLog.mapIndexed { i, l ->
                    row("#${i + 1}", l)
                }
            )
        )
    }

    fun attention(): ReportSection = input.attention()

    private fun device(): ReportSection {
        val rows = mutableListOf(
            row("manufacturer", Build.MANUFACTURER),
            row("model", Build.MODEL),
            row("device", Build.DEVICE),
            row("product", Build.PRODUCT),
            row("android", "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"),
            row("security patch", Build.VERSION.SECURITY_PATCH),
            row("build fingerprint", Build.FINGERPRINT),
            row("build type", "${Build.TYPE} / ${Build.TAGS}"),
            row("sable profile", env.profileLine()),
            row("sable release", prop("ro.sable.release")),
            row("sable build source", prop("ro.sable.build_source")),
            row("sable base", prop("ro.sable.base")),
            row("verified boot state", prop("ro.boot.verifiedbootstate")),
            row("bootloader lock", prop("ro.boot.vbmeta.device_state")),
            row("uptime", "${SystemClock.elapsedRealtime() / MS_PER_MIN} min"),
            row("abis", Build.SUPPORTED_ABIS.joinToString(",")),
            row("page size", read { Os.sysconf(OsConstants._SC_PAGESIZE) }),
            row("kernel", System.getProperty("os.version").orEmpty())
        )
        // Developer/service tier only: partition/slot and vendor detail.
        if (env.developerMode) {
            rows += listOf(
                "ro.boot.slot_suffix",
                "ro.vendor.build.fingerprint",
                "ro.vendor.build.security_patch",
                "ro.vndk.version",
                "ro.board.platform",
                "ro.product.first_api_level"
            ).map { row(it, prop(it)) }
        }
        return ReportSection(ReportCategory.DEVICE, "Device, build and security", rows)
    }

    private fun display(): ReportSection {
        val wm = ctx.getSystemService(WindowManager::class.java)
        val m = ctx.resources.displayMetrics
        val cfg = ctx.resources.configuration
        val rows = mutableListOf(
            row(
                "window bounds",
                read {
                    wm?.currentWindowMetrics?.bounds?.let { "${it.width()}x${it.height()}" }
                }
            ),
            row(
                "maximum bounds",
                read {
                    wm?.maximumWindowMetrics?.bounds?.let { "${it.width()}x${it.height()}" }
                }
            ),
            row("density", "${m.densityDpi} dpi (scale ${m.density})"),
            row("physical dpi", "%.1f x %.1f".format(m.xdpi, m.ydpi)),
            row("smallest width", "${cfg.smallestScreenWidthDp} dp"),
            row("screen dp", "${cfg.screenWidthDp}x${cfg.screenHeightDp} dp"),
            row("round", cfg.isScreenRound.toString()),
            row("font scale", cfg.fontScale.toString())
        )
        ctx.getSystemService(DisplayManager::class.java)?.displays?.forEach { d ->
            val mode = d.mode
            val size = "${mode.physicalWidth}x${mode.physicalHeight}"
            rows +=
                row("display ${d.displayId}", "${d.name} $size @ %.0f Hz".format(mode.refreshRate))
        }
        return ReportSection(ReportCategory.DISPLAY, "Display and input geometry", rows)
    }

    private fun sensors(): ReportSection {
        val title = "Sensor inventory"
        val sm = ctx.getSystemService(SensorManager::class.java)
            ?: return unavailable(ReportCategory.SENSORS, title, "sensor service")
        val rows = sm.getSensorList(Sensor.TYPE_ALL).map { s ->
            row(
                s.stringType.removePrefix("android.sensor."),
                "${s.name} (${s.vendor}) range ${s.maximumRange} res ${s.resolution} ${s.power} mA"
            )
        }
        return ReportSection(
            ReportCategory.SENSORS,
            title,
            rows.ifEmpty {
                listOf(row("sensors", "none reported"))
            }
        )
    }

    private fun facing(c: CameraCharacteristics) = when (c.get(CameraCharacteristics.LENS_FACING)) {
        CameraCharacteristics.LENS_FACING_BACK -> "back"
        CameraCharacteristics.LENS_FACING_FRONT -> "front"
        CameraCharacteristics.LENS_FACING_EXTERNAL -> "external"
        else -> "unknown"
    }

    private fun level(c: CameraCharacteristics) =
        when (c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "UNKNOWN"
        }

    private fun cameraLine(c: CameraCharacteristics): String {
        val flash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        val raw = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) == true
        val jpeg = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(android.graphics.ImageFormat.JPEG)
            ?.maxByOrNull { it.width.toLong() * it.height }
        val zoom = c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        return "${facing(
            c
        )} ${level(c)} flash=$flash raw=$raw maxJpeg=${jpeg ?: "-"} zoom=${zoom ?: "-"}"
    }

    /** Camera characteristics need no CAMERA permission; nothing is opened. */
    private fun camera(): ReportSection {
        val title = "Camera capabilities"
        val cm = ctx.getSystemService(CameraManager::class.java)
            ?: return unavailable(ReportCategory.CAMERA, title, "camera service")
        val rows = try {
            cm.cameraIdList.map { id ->
                row("camera $id", cameraLine(cm.getCameraCharacteristics(id)))
            }
        } catch (e: CameraAccessException) {
            listOf(row("cameras", Reading.Unavailable("camera access: ${e.reason}").text()))
        }
        return ReportSection(
            ReportCategory.CAMERA,
            title,
            rows.ifEmpty {
                listOf(row("cameras", "none reported"))
            }
        )
    }

    private fun linkRows(lp: LinkProperties?): List<ReportRow> {
        if (lp == null) return listOf(row("link", Reading.Unknown.text()))
        return listOf(row("interface", lp.interfaceName ?: "-")) +
            lp.linkAddresses.map { row("address", it.toString()) } +
            lp.dnsServers.map { row("dns", it.hostAddress.orEmpty()) } +
            row(
                "private dns",
                "${lp.isPrivateDnsActive} ${lp.privateDnsServerName.orEmpty()}".trim()
            ) +
            lp.routes.filter {
                it.isDefaultRoute
            }.map { row("default route", it.gateway?.hostAddress.orEmpty()) }
    }

    private fun network(): ReportSection {
        val title = "Network, IP, DNS and VPN"
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val n = cm?.activeNetwork
        return when {
            cm == null -> unavailable(ReportCategory.NETWORK, title, "connectivity")

            n == null -> ReportSection(
                ReportCategory.NETWORK,
                title,
                listOf(row("active network", "none"))
            )

            else -> ReportSection(ReportCategory.NETWORK, title, activeNetworkRows(cm, n))
        }
    }

    private fun activeNetworkRows(
        cm: ConnectivityManager,
        n: android.net.Network
    ): List<ReportRow> {
        val caps = cm.getNetworkCapabilities(n)
        val transports = listOf(
            NetworkCapabilities.TRANSPORT_WIFI to "wifi",
            NetworkCapabilities.TRANSPORT_CELLULAR to "cellular",
            NetworkCapabilities.TRANSPORT_VPN to "vpn",
            NetworkCapabilities.TRANSPORT_ETHERNET to "ethernet",
            NetworkCapabilities.TRANSPORT_BLUETOOTH to "bluetooth"
        ).filter { caps?.hasTransport(it.first) == true }.joinToString("+") { it.second }
        return listOf(
            row("transports", transports.ifEmpty { "unknown" }),
            row(
                "validated",
                (
                    caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ==
                        true
                    ).toString()
            ),
            row(
                "metered",
                (
                    caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) !=
                        true
                    ).toString()
            ),
            row(
                "vpn active",
                (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true).toString()
            )
        ) + linkRows(cm.getLinkProperties(n))
    }

    private fun storage(): ReportSection {
        val st = StatFs(Environment.getDataDirectory().path)
        val rows = mutableListOf(
            row("data total", "${st.totalBytes / BYTES_PER_MIB} MiB"),
            row("data free", "${st.availableBytes / BYTES_PER_MIB} MiB")
        )
        ctx.getSystemService(StorageManager::class.java)?.storageVolumes?.forEach { v ->
            rows +=
                row(
                    "volume",
                    "${v.getDescription(
                        ctx
                    )} removable=${v.isRemovable} primary=${v.isPrimary} state=${v.state}"
                )
        }
        if (env.developerMode) {
            rows +=
                listOf(
                    "ro.boot.slot_suffix",
                    "ro.virtual_ab.enabled",
                    "ro.boot.dynamic_partitions",
                    "ro.build.ab_update"
                )
                    .map { row(it, prop(it)) }
        }
        return ReportSection(ReportCategory.STORAGE, "Storage", rows)
    }

    private fun appRow(p: String): ReportRow? {
        val info: PackageInfo = try {
            pm.getPackageInfo(p, PackageManager.GET_PERMISSIONS)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        val ai = info.applicationInfo
        val system = ai != null && (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val installer = read { pm.getInstallSourceInfo(p).installingPackageName ?: "-" }
        val granted =
            info.requestedPermissionsFlags?.count {
                it and PackageInfo.REQUESTED_PERMISSION_GRANTED !=
                    0
            }
                ?: 0
        return row(
            p,
            "${info.versionName ?: "-"} ${if (system) "system" else "user"} " +
                "target=${ai?.targetSdkVersion} installer=$installer granted=$granted"
        )
    }

    /** Launcher-visible apps only (no QUERY_ALL_PACKAGES); a read-only summary, not a second app-security database. */
    private fun apps(): ReportSection {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pkgs = pm.queryIntentActivities(launcher, 0).map {
            it.activityInfo.packageName
        }.distinct().sorted()
        return ReportSection(
            ReportCategory.APPS,
            "Installed apps (launcher-visible)",
            pkgs.mapNotNull { appRow(it) } +
                row("controls", "App Security & Privacy / Android App info")
        )
    }

    private fun capabilities(): ReportSection = ReportSection(
        ReportCategory.CAPABILITIES,
        "Capability status (${env.profile.id})",
        env.resolutions.values.map { r ->
            val mm = if (r.mismatch) " MISMATCH" else ""
            row(
                r.capability.name.lowercase(),
                "${r.declared} runtime=${r.runtime} -> ${r.visibility}$mm; ${r.reason}"
            )
        }
    )
}
