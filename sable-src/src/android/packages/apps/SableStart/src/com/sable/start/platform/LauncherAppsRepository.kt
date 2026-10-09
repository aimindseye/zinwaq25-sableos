package org.sableos.start.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import org.sableos.start.model.AppEntry
import org.sableos.start.model.stableKey
import org.sableos.start.privacy.AppInstanceKey
import org.sableos.start.privacy.PrivacySnapshotCache
import java.util.Locale

/**
 * Sable-owned launcher inventory.
 *
 * This replaces Launcher3's AllAppsStore as the HOME-facing app model. Android's
 * LauncherApps service remains the platform authority for launchable activities
 * and work-profile routing.
 *
 * Privacy (DESIGN-KF-D): [loadApps] runs on the inventory background thread and
 * attaches each app instance's effective privacy snapshot, read through
 * [PrivacyFactsReader] in the app's own user and cached per (user, package) in
 * the in-memory [PrivacySnapshotCache]. Row binding only reads the result.
 */
class LauncherAppsRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val launcherApps =
        checkNotNull(appContext.getSystemService(LauncherApps::class.java))
    private val userManager =
        checkNotNull(appContext.getSystemService(UserManager::class.java))
    private val launcherUser: UserHandle = Process.myUserHandle()

    val privacyCache = PrivacySnapshotCache()

    private val privacyReader =
        PrivacyFactsReader(
            contextForUser = { user ->
                if (user == launcherUser) appContext else appContext.createContextAsUser(user, 0)
            },
            isLauncherUser = { user -> user == launcherUser },
        )

    /** Must be called off the main thread. */
    fun loadApps(): List<AppEntry> {
        val locale = Locale.getDefault()
        val hiddenApps = loadHiddenApps()
        val liveInstances = mutableSetOf<AppInstanceKey>()

        val apps =
            userManager.userProfiles
                .flatMap { user ->
                    val userSerial = userManager.getSerialNumberForUser(user)
                    val profileLabel = profileLabelFor(user)
                    launcherApps
                        .getActivityList(null, user)
                        .asSequence()
                        .filter { info ->
                            val packageName = info.componentName.packageName
                            packageName != appContext.packageName &&
                                packageName != GRAPHENE_MESSAGING_PACKAGE &&
                                HiddenAppKey(packageName, userSerial) !in hiddenApps
                        }.map { info ->
                            val packageName = info.componentName.packageName
                            val instance = AppInstanceKey(user.identifier, packageName)
                            liveInstances += instance
                            AppEntry(
                                label = info.label.toString().trim(),
                                component = info.componentName,
                                user = user,
                                privacy =
                                    privacyCache.getOrCompute(instance) {
                                        privacyReader.read(packageName, user)
                                    },
                                profileLabel = profileLabel,
                                canUninstall = isUserRemovable(info),
                            )
                        }.filter { it.label.isNotEmpty() }
                        .toList()
                }.distinctBy { entry -> entry.stableKey() }
                .sortedWith(
                    compareBy<AppEntry> {
                        it.label.lowercase(locale)
                    }.thenBy {
                        it.component.packageName
                    }.thenBy {
                        it.component.className
                    }.thenBy {
                        userManager.getSerialNumberForUser(it.user)
                    },
                )
        privacyCache.retainOnly(liveInstances)
        return apps
    }

    /** Package added/changed/removed in [user]: its privacy snapshot is stale. */
    fun invalidatePrivacy(
        packageName: String,
        user: UserHandle,
    ) {
        privacyCache.invalidate(user.identifier, packageName)
    }

    /** Runtime-permission change signal (OnPermissionsChangedListener carries a uid). */
    fun invalidatePrivacyForUid(uid: Int) {
        val userId = PrivacySnapshotCache.userIdOfUid(uid)
        val packages =
            runCatching { appContext.packageManager.getPackagesForUid(uid)?.toList() }.getOrNull()
        if (packages.isNullOrEmpty()) {
            privacyCache.invalidateUser(userId)
        } else {
            privacyCache.invalidatePackages(userId, packages)
        }
    }

    /** Profile added/removed/locked/unlocked/paused. */
    fun invalidatePrivacyForUser(user: UserHandle?) {
        if (user == null) privacyCache.clear() else privacyCache.invalidateUser(user.identifier)
    }

    private fun profileLabelFor(user: UserHandle): String? {
        if (user == launcherUser) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val type = runCatching { launcherApps.getLauncherUserInfo(user)?.userType }.getOrNull()
            return when (type) {
                UserManager.USER_TYPE_PROFILE_MANAGED -> PROFILE_WORK
                UserManager.USER_TYPE_PROFILE_PRIVATE -> PROFILE_PRIVATE
                else -> PROFILE_OTHER
            }
        }
        return if (runCatching { userManager.isManagedProfile(user.identifier) }.getOrDefault(false)) {
            PROFILE_WORK
        } else {
            PROFILE_OTHER
        }
    }

    private fun isUserRemovable(info: LauncherActivityInfo): Boolean {
        val flags = info.applicationInfo?.flags ?: return false
        return flags and ApplicationInfo.FLAG_SYSTEM == 0
    }

    /** System uninstaller (it shows its own confirmation, after Sable's confirm step). */
    fun requestUninstall(entry: AppEntry): Boolean {
        // Work/private-profile copies are removed from App info, never from here:
        // the personal copy of the same package must not be hit by mistake.
        if (entry.user != launcherUser || !entry.canUninstall) return false
        return runCatching {
            val intent =
                Intent(Intent.ACTION_DELETE, Uri.fromParts("package", entry.component.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /** Per-app notification settings; only for apps of the launcher's own user. */
    fun openNotificationSettings(entry: AppEntry): Boolean {
        if (entry.user != launcherUser) return openAppDetails(entry)
        return runCatching {
            appContext.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, entry.component.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }

    fun launch(entry: AppEntry): Boolean =
        try {
            launcherApps.startMainActivity(
                entry.component,
                entry.user,
                null,
                null,
            )
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }

    fun openAppDetails(entry: AppEntry): Boolean =
        try {
            launcherApps.startAppDetailsActivity(
                entry.component,
                entry.user,
                null,
                null,
            )
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }

    fun registerCallback(callback: LauncherApps.Callback) {
        launcherApps.registerCallback(callback)
    }

    fun unregisterCallback(callback: LauncherApps.Callback) {
        launcherApps.unregisterCallback(callback)
    }

    private fun loadHiddenApps(): Set<HiddenAppKey> =
        runCatching {
            appContext.contentResolver
                .query(
                    HIDDEN_APPS_URI,
                    HIDDEN_APP_COLUMNS,
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    val packageIndex =
                        cursor.getColumnIndexOrThrow(HIDDEN_PACKAGE_COLUMN)
                    val userIndex =
                        cursor.getColumnIndexOrThrow(HIDDEN_USER_COLUMN)
                    buildSet {
                        while (cursor.moveToNext()) {
                            add(
                                HiddenAppKey(
                                    packageName = cursor.getString(packageIndex),
                                    userSerial = cursor.getLong(userIndex),
                                ),
                            )
                        }
                    }
                }.orEmpty()
        }.getOrDefault(emptySet())

    private data class HiddenAppKey(
        val packageName: String,
        val userSerial: Long,
    )

    companion object {
        val HIDDEN_APPS_URI: Uri =
            Uri.parse("content://org.sableos.hub.connected_apps/hidden")

        const val PROFILE_WORK = "work"
        const val PROFILE_PRIVATE = "private"
        const val PROFILE_OTHER = "profile"

        private const val GRAPHENE_MESSAGING_PACKAGE = "com.android.messaging"
        private const val HIDDEN_PACKAGE_COLUMN = "package_name"
        private const val HIDDEN_USER_COLUMN = "user_serial"
        private val HIDDEN_APP_COLUMNS =
            arrayOf(
                HIDDEN_PACKAGE_COLUMN,
                HIDDEN_USER_COLUMN,
            )
    }
}
