package org.sableos.tools.android

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.ConsumerIrManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.provider.Settings
import org.sableos.tools.core.Capability
import org.sableos.tools.core.CapabilityGate
import org.sableos.tools.core.DeveloperMode
import org.sableos.tools.core.DialerCodes
import org.sableos.tools.core.Hardware
import org.sableos.tools.core.HomeModel
import org.sableos.tools.core.LinkIntent
import org.sableos.tools.core.Probe
import org.sableos.tools.core.Remote
import org.sableos.tools.core.RemoteCodec
import org.sableos.tools.core.Resolution
import org.sableos.tools.core.SettingsLink
import org.sableos.tools.core.TiltCalibration
import org.sableos.tools.core.Tool
import org.sableos.tools.core.ToolsDeviceProfile

/** Runtime hardware facts from public APIs only. Anything without a public probe stays NOT_PROBED. */
object HardwareProbes {
    fun read(ctx: Context): Map<Hardware, Probe> {
        val pm = ctx.packageManager
        val sm = ctx.getSystemService(SensorManager::class.java)
        fun sensor(type: Int) = when {
            sm == null -> Probe.NOT_PROBED
            sm.getDefaultSensor(type) != null -> Probe.PRESENT
            else -> Probe.ABSENT
        }
        fun feature(name: String) = if (pm.hasSystemFeature(name)) Probe.PRESENT else Probe.ABSENT
        val ir = ctx.getSystemService(ConsumerIrManager::class.java)
        return mapOf(
            Hardware.ACCELEROMETER to sensor(Sensor.TYPE_ACCELEROMETER),
            Hardware.MAGNETOMETER to sensor(Sensor.TYPE_MAGNETIC_FIELD),
            Hardware.GYROSCOPE to sensor(Sensor.TYPE_GYROSCOPE),
            Hardware.STEP_COUNTER to sensor(Sensor.TYPE_STEP_COUNTER),
            Hardware.CAMERA_BACK to feature(PackageManager.FEATURE_CAMERA),
            Hardware.CAMERA_FLASH to feature(PackageManager.FEATURE_CAMERA_FLASH),
            Hardware.MICROPHONE to feature(PackageManager.FEATURE_MICROPHONE),
            Hardware.LOCATION_GPS to feature(PackageManager.FEATURE_LOCATION_GPS),
            Hardware.IR_EMITTER to if (ir?.hasIrEmitter() == true) Probe.PRESENT else Probe.ABSENT,
            // No public IR receive API, no public FM tuner feature, no public sub-screen marker: profile decides.
            Hardware.IR_RECEIVER to Probe.NOT_PROBED,
            Hardware.FM_TUNER to Probe.NOT_PROBED,
            Hardware.SUBSCREEN to Probe.NOT_PROBED
        )
    }
}

/**
 * App-private view state: recents, the developer-tier opt-in, measurement zero-points and the user's own IR
 * remotes. None of it is device configuration (TOOLS_DUPLICATE_POLICY_STORE=NO).
 */
class ToolsPrefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("sable_tools", Context.MODE_PRIVATE)

    var recents: List<Tool>
        get() = HomeModel.decodeRecents(p.getString(KEY_RECENTS, null))
        set(v) = p.edit().putString(KEY_RECENTS, HomeModel.encodeRecents(v)).apply()

    var developerOptIn: Boolean
        get() = p.getBoolean(KEY_DEV, false)
        set(v) = p.edit().putBoolean(KEY_DEV, v).apply()

    fun calibration(c: Capability): TiltCalibration? =
        TiltCalibration.decode(p.getString("cal_${c.name}", null))

    fun setCalibration(c: Capability, cal: TiltCalibration?) {
        p.edit().apply {
            if (cal ==
                null
            ) {
                remove("cal_${c.name}")
            } else {
                putString("cal_${c.name}", cal.encode())
            }
        }.apply()
    }

    fun calibrated(): Set<Capability> = Capability.entries.filter {
        p.contains("cal_${it.name}")
    }.toSet()

    var remotes: List<Remote>
        get() = RemoteCodec.decode(p.getString(KEY_REMOTES, null))
        set(v) = p.edit().putString(KEY_REMOTES, RemoteCodec.encode(v)).apply()

    private companion object {
        const val KEY_RECENTS = "recents"
        const val KEY_DEV = "developer_opt_in"
        const val KEY_REMOTES = "remotes"
    }
}

/** Everything a screen needs to gate itself, read once per screen start. */
class ToolsEnv(val ctx: Context) {
    val prefs = ToolsPrefs(ctx)
    val profileId: String = SysProps.get("ro.sable.profile.id")
    val profile: ToolsDeviceProfile = ToolsDeviceProfile.byId(profileId)
    val probes: Map<Hardware, Probe> = HardwareProbes.read(ctx)
    val resolutions: Map<Capability, Resolution> = CapabilityGate.resolveAll(profile, probes)
    val systemDeveloperOptions: Boolean =
        Settings.Global.getInt(
            ctx.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) ==
            1
    val developerMode: Boolean = DeveloperMode.enabled(systemDeveloperOptions, prefs.developerOptIn)

    fun granted(): Set<String> = org.sableos.tools.core.Tool.entries.flatMap {
        it.permissions
    }.distinct()
        .filter { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }.toSet()

    fun profileLine(): String = "profile ${profileId.ifBlank { "unset" }} (${profile.displayName})"
}

/** Hand-offs to the owning surface. Tries each candidate in order; never writes a setting itself. */
object Links {
    private fun intentFor(activity: Activity, c: LinkIntent, packageForUri: String?): Intent? =
        if (c.action == Intent.ACTION_MAIN && c.pkg != null) {
            activity.packageManager.getLaunchIntentForPackage(c.pkg)
        } else {
            Intent(c.action).apply {
                c.pkg?.let { setPackage(it) }
                if (c.packageUri) {
                    data =
                        Uri.fromParts("package", packageForUri ?: activity.packageName, null)
                }
            }
        }

    private fun tryStart(activity: Activity, intent: Intent): Boolean = try {
        activity.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    fun open(activity: Activity, link: SettingsLink, packageForUri: String? = null): Boolean =
        link.candidates.any { c ->
            intentFor(activity, c, packageForUri)?.let { tryStart(activity, it) } ==
                true
        }

    /** Whether Sable Media advertises an FM mode (visible through the manifest <queries>). */
    fun mediaFmInstalled(ctx: Context): Boolean {
        val c = SettingsLink.MEDIA_FM.candidates.first()
        val i = Intent(c.action).setPackage(c.pkg)
        return ctx.packageManager.queryIntentActivities(i, 0).isNotEmpty()
    }
}

/** Keeps the dialer-code receiver enabled only when no vendor factory-test app answers *#*#3377#*#*. */
object SecretCodeSync {
    fun sync(ctx: Context) {
        val intent =
            Intent(
                "android.telephony.action.SECRET_CODE",
                Uri.parse("android_secret_code://${DialerCodes.FACTORY_TEST_CODE}")
            )
        val vendor = ctx.packageManager.queryBroadcastReceivers(
            intent,
            PackageManager.MATCH_DISABLED_COMPONENTS
        )
            .any { it.activityInfo?.packageName != ctx.packageName }
        val want = if (DialerCodes.receiverEnabled(vendor)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        val cn = ComponentName(ctx, SecretCodeReceiver::class.java)
        if (ctx.packageManager.getComponentEnabledSetting(cn) != want) {
            ctx.packageManager.setComponentEnabledSetting(cn, want, PackageManager.DONT_KILL_APP)
        }
    }
}
