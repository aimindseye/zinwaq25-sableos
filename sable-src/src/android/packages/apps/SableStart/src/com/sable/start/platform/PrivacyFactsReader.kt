package org.sableos.start.platform

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserHandle
import android.view.accessibility.AccessibilityManager
import org.sableos.start.privacy.AppPrivacy
import org.sableos.start.privacy.OpMode
import org.sableos.start.privacy.PermissionFact
import org.sableos.start.privacy.PrivacyEvaluator
import org.sableos.start.privacy.PrivacyFacts
import org.sableos.start.privacy.PrivacyPermissions

/**
 * Reads the effective privacy state of ONE app instance (package + Android
 * user) from Android's own permission and App-op state, then hands it to the
 * pure [PrivacyEvaluator]. Nothing is stored (SEPARATE_PERMISSION_DATABASE=NO).
 *
 * Public SDK only, so the non-product visual-review host can reuse it: the
 * caller supplies a Context for the target user ([contextForUser], which in
 * SableLauncher is createContextAsUser) and says which user the launcher runs
 * as. Must be called off the main thread (binder calls per app).
 *
 * Every read is guarded: anything that fails becomes Unknown/null and is
 * omitted by the evaluator (UNKNOWN_STATE=OMIT_NOT_GUESS).
 */
class PrivacyFactsReader(
    private val contextForUser: (UserHandle) -> Context?,
    private val isLauncherUser: (UserHandle) -> Boolean,
) {
    /** null = the app's permission state could not be read at all ("permissions · unavailable"). */
    fun read(
        packageName: String,
        user: UserHandle,
    ): AppPrivacy? = readFacts(packageName, user)?.let(PrivacyEvaluator::evaluate)

    fun readFacts(
        packageName: String,
        user: UserHandle,
    ): PrivacyFacts? {
        val context = runCatching { contextForUser(user) }.getOrNull() ?: return null
        val info = packageInfo(context.packageManager, packageName) ?: return null
        val applicationInfo = info.applicationInfo ?: return null
        val uid = applicationInfo.uid
        val appOps = context.getSystemService(AppOpsManager::class.java)

        val requested = info.requestedPermissions.orEmpty()
        val flags = info.requestedPermissionsFlags ?: IntArray(0)
        val permissions =
            buildMap {
                requested.forEachIndexed { index, permission ->
                    if (permission !in PrivacyPermissions.all) return@forEachIndexed
                    val granted =
                        (flags.getOrNull(index) ?: 0) and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
                    put(
                        permission,
                        PermissionFact(
                            granted = granted,
                            opMode = opModeFor(appOps, permission, uid, packageName),
                        ),
                    )
                }
            }

        return PrivacyFacts(
            permissions = permissions,
            targetSdk = applicationInfo.targetSdkVersion,
            deviceSdk = Build.VERSION.SDK_INT,
            accessibilityServiceEnabled = accessibilityEnabled(context, packageName, user),
            activeDeviceAdmin = activeAdmin(context, packageName),
            installAppsOp = permissions[PrivacyPermissions.REQUEST_INSTALL_PACKAGES]?.opMode,
            overlayOp = permissions[PrivacyPermissions.SYSTEM_ALERT_WINDOW]?.opMode,
            usageAccessOp = permissions[PrivacyPermissions.PACKAGE_USAGE_STATS]?.opMode,
        )
    }

    private fun packageInfo(
        packageManager: PackageManager,
        packageName: String,
    ): PackageInfo? =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            }
        }.getOrNull()

    private fun opModeFor(
        appOps: AppOpsManager?,
        permission: String,
        uid: Int,
        packageName: String,
    ): OpMode {
        val op = runCatching { AppOpsManager.permissionToOp(permission) }.getOrNull() ?: return OpMode.NotApplicable
        if (appOps == null) return OpMode.Unknown
        return runCatching {
            when (appOps.unsafeCheckOpNoThrow(op, uid, packageName)) {
                AppOpsManager.MODE_ALLOWED -> OpMode.Allowed
                AppOpsManager.MODE_FOREGROUND -> OpMode.Foreground
                AppOpsManager.MODE_DEFAULT -> OpMode.Default
                AppOpsManager.MODE_IGNORED -> OpMode.Ignored
                AppOpsManager.MODE_ERRORED -> OpMode.Errored
                else -> OpMode.Unknown
            }
        }.getOrDefault(OpMode.Unknown)
    }

    /**
     * Accessibility services are bound only for the user the launcher runs as;
     * for other profiles the launcher has no reliable answer, so null (omitted).
     */
    private fun accessibilityEnabled(
        context: Context,
        packageName: String,
        user: UserHandle,
    ): Boolean? {
        if (!isLauncherUser(user)) return null
        return runCatching {
            val manager = context.getSystemService(AccessibilityManager::class.java) ?: return@runCatching null
            manager
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info -> info.resolveInfo?.serviceInfo?.packageName == packageName }
        }.getOrNull()
    }

    private fun activeAdmin(
        context: Context,
        packageName: String,
    ): Boolean? =
        runCatching {
            val manager = context.getSystemService(DevicePolicyManager::class.java) ?: return@runCatching null
            manager.activeAdmins.orEmpty().any { admin -> admin.packageName == packageName }
        }.getOrNull()
}
