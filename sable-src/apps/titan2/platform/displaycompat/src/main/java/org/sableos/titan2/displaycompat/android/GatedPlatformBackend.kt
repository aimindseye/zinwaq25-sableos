package org.sableos.titan2.displaycompat.android

import android.os.IBinder
import android.os.Process
import org.sableos.titan2.displaycompat.core.ApplyResult
import org.sableos.titan2.displaycompat.core.BackendAvailability
import org.sableos.titan2.displaycompat.core.CanvasPlan
import org.sableos.titan2.displaycompat.core.DisplayBackend
import org.sableos.titan2.displaycompat.core.DisplayCompatText.t
import org.sableos.titan2.displaycompat.core.DisplayProfile
import org.sableos.titan2.displaycompat.core.PlatformMapping
import org.sableos.titan2.displaycompat.core.PlatformRequest

/**
 * Backend that hands a profile to the platform (LineageOS 23.2 / Android 16):
 *
 *  - aspect ratio: `IPackageManager.setUserMinAspectRatio(packageName, userId, aspectRatio)`, the call Settings'
 *    own App aspect ratio page makes. It needs INSTALL_PACKAGES and, for another app's package, the system UID
 *    (`PackageManagerService.enforceOwnerRights`), so this app is platform-signed, privileged and runs as
 *    `android.uid.system`. The framework only honours the value when
 *    `config_appCompatUserAppAspectRatioSettingsIsEnabled` is true (SableFrameworkOverlay, sable_displaycompat.xml).
 *  - orientation: `IPlatformCompat.putOverridesOnReleaseBuilds` / `removeOverridesOnReleaseBuilds` with the
 *    overridable orientation changes (OVERRIDE_COMPAT_CHANGE_CONFIG_ON_RELEASE_BUILD).
 *
 * It is closed unless `persist.sable.displaycompat.backend=validated` (set by the Q4 product layer), so an image
 * without the overlay never sends requests. Failures are reported as [ApplyResult.Unsupported], never hidden.
 * Every call sends the full state, so moving to a stored-only profile clears what an earlier profile set.
 */
class GatedPlatformBackend : DisplayBackend {
    override val name = "platform aspect ratio + orientation (gated)"

    /** Closed unless the gate property is exactly `validated`; read on every call so a closed gate stays closed. */
    override val availability: BackendAvailability
        get() = if (open()) BackendAvailability.OPEN_UNVERIFIED else BackendAvailability.CLOSED

    private fun open() = isOpen()

    override fun apply(pkg: String, plan: CanvasPlan, p: DisplayProfile): ApplyResult {
        if (!open()) return ApplyResult.NoOp
        val request = PlatformMapping.request(p)
        val sent = send(pkg, request)
        return when {
            sent != null -> sent

            !request.hasEffect && request.live.isEmpty() -> ApplyResult.Unsupported(
                t("no_platform_equivalent_for") + request.notApplied.joinToString(", ") +
                    t("stored_only_platform_settings_cleared")
            )

            !request.hasEffect -> ApplyResult.Applied(request.describe() + LIVE)

            else -> ApplyResult.Applied(request.describe() + readBack(pkg) + NEXT_LAUNCH)
        }
    }

    override fun clear(pkg: String): ApplyResult {
        if (!open()) return ApplyResult.NoOp
        return send(pkg, PlatformRequest.CLEAR)
            ?: ApplyResult.Applied(t("platform_settings_cleared", NEXT_LAUNCH))
    }

    /** Sends [request]; null on success, else the failure. */
    private fun send(pkg: String, request: PlatformRequest): ApplyResult? = guarded {
        val pm = packageManagerService()
        PM_INTERFACE.getMethod(
            "setUserMinAspectRatio",
            String::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        ).invoke(pm, pkg, myUserId(), request.minAspectRatio)
        val compat = platformCompat()
        if (request.disableOrientation.isNotEmpty()) {
            val cls = Class.forName(
                "com.android.internal.compat.CompatibilityOverridesToRemoveConfig"
            )
            val config = cls.getConstructor(Set::class.java).newInstance(request.disableOrientation)
            COMPAT_INTERFACE.getMethod("removeOverridesOnReleaseBuilds", cls, String::class.java)
                .invoke(compat, config, pkg)
        }
        if (request.enableOrientation.isNotEmpty()) {
            val cls = Class.forName("com.android.internal.compat.CompatibilityOverrideConfig")
            val enabled = enabledOverride()
            val config = cls.getConstructor(Map::class.java)
                .newInstance(request.enableOrientation.associateWith { enabled })
            COMPAT_INTERFACE.getMethod("putOverridesOnReleaseBuilds", cls, String::class.java)
                .invoke(compat, config, pkg)
        }
    }

    /** What the platform now reports for the package, so the message never claims more than it says. */
    private fun readBack(pkg: String): String = runCatching {
        val v = PM_INTERFACE.getMethod(
            "getUserMinAspectRatio",
            String::class.java,
            Int::class.javaPrimitiveType
        ).invoke(packageManagerService(), pkg, myUserId()) as Int
        t("platform_reports_aspect", v)
    }.getOrDefault("")

    private fun guarded(call: () -> Unit): ApplyResult? = try {
        call()
        null
    } catch (t: ReflectiveOperationException) {
        unsupported(t)
    } catch (t: SecurityException) {
        unsupported(t)
    } catch (t: IllegalArgumentException) {
        unsupported(t)
    } catch (t: IllegalStateException) {
        unsupported(t)
    }

    private fun unsupported(t: Throwable): ApplyResult = ApplyResult.Unsupported(
        (t.cause ?: t).javaClass.simpleName + ": " + ((t.cause ?: t).message ?: t("no_detail"))
    )

    private fun packageManagerService(): Any = requireNotNull(
        Class.forName("android.app.ActivityThread").getMethod("getPackageManager").invoke(null)
    ) {
        t("package_manager_unavailable")
    }

    private fun platformCompat(): Any {
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, PLATFORM_COMPAT_SERVICE) as? IBinder
        checkNotNull(binder) { "platform_compat service unavailable" }
        return requireNotNull(
            Class.forName("com.android.internal.compat.IPlatformCompat\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, binder)
        ) { t("platform_compat_interface_unavailable") }
    }

    private fun enabledOverride(): Any {
        val builderClass = Class.forName("android.app.compat.PackageOverride\$Builder")
        val builder = builderClass.getConstructor().newInstance()
        builderClass.getMethod("setEnabled", Boolean::class.javaPrimitiveType).invoke(builder, true)
        return requireNotNull(builderClass.getMethod("build").invoke(builder)) {
            t("no_packageoverride")
        }
    }

    private fun myUserId(): Int = Process.myUid() / PER_USER_RANGE

    companion object {
        const val GATE_PROPERTY = "persist.sable.displaycompat.backend"
        const val GATE_OPEN = "validated"

        /** The same gate closes the display tools (Tall canvas, hotkey, forced rotation). */
        fun isOpen(): Boolean = SysProps.get(GATE_PROPERTY) == GATE_OPEN
        private const val PLATFORM_COMPAT_SERVICE = "platform_compat"
        private const val PER_USER_RANGE = 100_000
        private val NEXT_LAUNCH: String get() = t("takes_effect_the_next_time")
        private val LIVE: String get() = t("the_display_tools_service_holds")
        private val PM_INTERFACE: Class<*> by lazy {
            Class.forName("android.content.pm.IPackageManager")
        }
        private val COMPAT_INTERFACE: Class<*> by lazy {
            Class.forName("com.android.internal.compat.IPlatformCompat")
        }
    }
}
