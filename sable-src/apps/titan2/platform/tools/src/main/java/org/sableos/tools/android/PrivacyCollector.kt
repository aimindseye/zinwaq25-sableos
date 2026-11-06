package org.sableos.tools.android

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.sableos.tools.core.CheckState
import org.sableos.tools.core.PrivacyCheck
import org.sableos.tools.core.PrivacyCheckId
import org.sableos.tools.core.PrivacyScore
import org.sableos.tools.core.ToolsText.t

/**
 * Reads the privacy checks from public platform APIs (KeyguardManager, DevicePolicyManager, PackageManager,
 * AppOpsManager, Settings). Read-only: it never changes a setting. A value that cannot be read is
 * [CheckState.UNKNOWN], never a guess.
 */
class PrivacyCollector(private val ctx: Context) {
    private val pm: PackageManager = ctx.packageManager

    fun checks(): List<PrivacyCheck> {
        val packages = userApps()
        return listOf(
            flag(PrivacyCheckId.SCREEN_LOCK, screenLock(), goodWhenOn = true),
            flag(PrivacyCheckId.ENCRYPTION, encrypted(), goodWhenOn = true),
            counted(
                PrivacyCheckId.INSTALL_APPS,
                installers(packages),
                PrivacyScore::installAppsState
            ),
            counted(
                PrivacyCheckId.SENSITIVE_APPS,
                sensitive(packages),
                PrivacyScore::sensitiveAppsState
            ),
            privateSpace(),
            flag(PrivacyCheckId.USB_DEBUGGING, usbDebugging(), goodWhenOn = false),
            patch()
        )
    }

    private fun flag(id: PrivacyCheckId, on: Boolean?, goodWhenOn: Boolean) = PrivacyCheck(
        id,
        PrivacyScore.flagState(on, goodWhenOn),
        when (on) {
            null -> t("check_unknown")
            true -> t("check_on")
            false -> t("check_off")
        }
    )

    private fun counted(id: PrivacyCheckId, count: Int?, state: (Int?) -> CheckState) =
        PrivacyCheck(
            id,
            state(count),
            count?.let { t("check_apps_count", it) } ?: t("check_unknown")
        )

    private fun screenLock(): Boolean? =
        ctx.getSystemService(KeyguardManager::class.java)?.isDeviceSecure

    private fun encrypted(): Boolean? =
        ctx.getSystemService(DevicePolicyManager::class.java)?.storageEncryptionStatus?.let {
            it == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE ||
                it == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER ||
                it == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY
        }

    private fun usbDebugging(): Boolean? = runCatching {
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.ADB_ENABLED) == 1
    }.getOrNull()

    /** Apps the user installed (system apps that were never updated are the phone's own). */
    private fun userApps(): List<PackageInfo>? = runCatching {
        pm.getInstalledPackages(
            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
        )
            .filter { info ->
                val flags = info.applicationInfo?.flags ?: 0
                flags and ApplicationInfo.FLAG_SYSTEM == 0 ||
                    flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
            }
            .filter { it.packageName != ctx.packageName }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun installers(packages: List<PackageInfo>?): Int? {
        val ops = ctx.getSystemService(AppOpsManager::class.java) ?: return null
        return packages?.count { info ->
            val uid = info.applicationInfo?.uid ?: return@count false
            INSTALL_PERMISSION in info.requestedPermissions.orEmpty() &&
                runCatching {
                    ops.unsafeCheckOpNoThrow(INSTALL_OP, uid, info.packageName) ==
                        AppOpsManager.MODE_ALLOWED
                }.getOrDefault(false)
        }
    }

    private fun sensitive(packages: List<PackageInfo>?): Int? = packages?.count { info ->
        val requested = info.requestedPermissions.orEmpty()
        val flags: IntArray? = info.requestedPermissionsFlags
        SENSITIVE_PERMISSIONS.any { perm ->
            val i = requested.indexOf(perm)
            i >= 0 &&
                ((flags?.getOrNull(i) ?: 0) and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
        }
    }

    /** Private Space is a profile of type private; shown as On when one is set up. */
    private fun privateSpace(): PrivacyCheck {
        val set =
            runCatching {
                val launcher = ctx.getSystemService(LauncherApps::class.java)
                ctx.getSystemService(UserManager::class.java)?.userProfiles?.any { user ->
                    launcher?.getLauncherUserInfo(user)?.userType == PRIVATE_PROFILE
                }
            }.getOrNull()
        // Not set up is not a problem, only worth knowing: it never counts against the phone.
        return PrivacyCheck(
            PrivacyCheckId.PRIVATE_SPACE,
            if (set == null) CheckState.UNKNOWN else CheckState.GOOD,
            when (set) {
                null -> t("check_unknown")
                true -> t("check_on")
                false -> t("check_off")
            }
        )
    }

    private fun patch(): PrivacyCheck {
        val date = runCatching { LocalDate.parse(Build.VERSION.SECURITY_PATCH) }.getOrNull()
        val age = date?.let { ChronoUnit.DAYS.between(it, LocalDate.now()) }
        return PrivacyCheck(
            PrivacyCheckId.SECURITY_PATCH,
            PrivacyScore.patchState(age),
            if (date == null ||
                age == null
            ) {
                t("check_unknown")
            } else {
                t("check_patch_age", date.toString(), age.toInt())
            }
        )
    }

    private companion object {
        const val INSTALL_PERMISSION = "android.permission.REQUEST_INSTALL_PACKAGES"
        const val INSTALL_OP = "android:request_install_packages"
        const val PRIVATE_PROFILE = "android.os.usertype.profile.PRIVATE"
        val SENSITIVE_PERMISSIONS =
            listOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.RECORD_AUDIO",
                "android.permission.CAMERA"
            )
    }
}
