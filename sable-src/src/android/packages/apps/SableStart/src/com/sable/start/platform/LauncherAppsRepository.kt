package org.sableos.start.platform

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.UserHandle
import android.os.UserManager
import org.sableos.start.model.AppEntry
import org.sableos.start.model.stableKey
import java.util.Locale

/**
 * Sable-owned launcher inventory.
 *
 * This replaces Launcher3's AllAppsStore as the HOME-facing app model. Android's
 * LauncherApps service remains the platform authority for launchable activities
 * and work-profile routing.
 */
class LauncherAppsRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val launcherApps =
        checkNotNull(appContext.getSystemService(LauncherApps::class.java))
    private val userManager =
        checkNotNull(appContext.getSystemService(UserManager::class.java))

    fun loadApps(): List<AppEntry> {
        val locale = Locale.getDefault()
        val hiddenApps = loadHiddenApps()

        return userManager.userProfiles
            .flatMap { user ->
                val userSerial = userManager.getSerialNumberForUser(user)
                launcherApps
                    .getActivityList(null, user)
                    .asSequence()
                    .filter { info ->
                        val packageName = info.componentName.packageName
                        packageName != appContext.packageName &&
                            packageName != GRAPHENE_MESSAGING_PACKAGE &&
                            HiddenAppKey(packageName, userSerial) !in hiddenApps
                    }.map { info ->
                        AppEntry(
                            label = info.label.toString().trim(),
                            component = info.componentName,
                            user = user,
                            privacySummary =
                                buildPrivacyPermissionSummary(
                                    packageName = info.componentName.packageName,
                                    user = user,
                                ),
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

    private fun buildPrivacyPermissionSummary(
        packageName: String,
        user: UserHandle,
    ): String {
        val profileContext =
            runCatching {
                appContext.createContextAsUser(user, 0)
            }.getOrNull() ?: return "permissions · unavailable"

        val packageInfo =
            runCatching {
                @Suppress("DEPRECATION")
                profileContext.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_PERMISSIONS,
                )
            }.getOrNull() ?: return "permissions · unavailable"

        val requested = packageInfo.requestedPermissions.orEmpty()
        val flags = packageInfo.requestedPermissionsFlags ?: IntArray(0)

        val granted =
            buildSet {
                requested.forEachIndexed { index, permission ->
                    val permissionFlags = flags.getOrNull(index) ?: 0
                    if (
                        permissionFlags and
                            PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
                    ) {
                        add(permission)
                    }
                }
            }

        val labels =
            PRIVACY_PERMISSION_GROUPS.mapNotNull { group ->
                group.label.takeIf {
                    group.permissions.any(granted::contains)
                }
            }

        return if (labels.isEmpty()) {
            "permissions · none sensitive"
        } else {
            "permissions · " + labels.joinToString(" · ")
        }
    }

    private data class PrivacyPermissionGroup(
        val label: String,
        val permissions: Set<String>,
    )

    companion object {
        val HIDDEN_APPS_URI: Uri =
            Uri.parse("content://org.sableos.hub.connected_apps/hidden")

        private const val GRAPHENE_MESSAGING_PACKAGE = "com.android.messaging"
        private const val HIDDEN_PACKAGE_COLUMN = "package_name"
        private const val HIDDEN_USER_COLUMN = "user_serial"
        private val HIDDEN_APP_COLUMNS =
            arrayOf(
                HIDDEN_PACKAGE_COLUMN,
                HIDDEN_USER_COLUMN,
            )

        private val PRIVACY_PERMISSION_GROUPS =
            listOf(
                PrivacyPermissionGroup(
                    "location",
                    setOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    ),
                ),
                PrivacyPermissionGroup("camera", setOf(Manifest.permission.CAMERA)),
                PrivacyPermissionGroup("microphone", setOf(Manifest.permission.RECORD_AUDIO)),
                PrivacyPermissionGroup(
                    "contacts",
                    setOf(
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.WRITE_CONTACTS,
                        Manifest.permission.GET_ACCOUNTS,
                    ),
                ),
                PrivacyPermissionGroup(
                    "phone",
                    setOf(
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.READ_PHONE_NUMBERS,
                        Manifest.permission.CALL_PHONE,
                        Manifest.permission.ANSWER_PHONE_CALLS,
                    ),
                ),
                PrivacyPermissionGroup(
                    "messages",
                    setOf(
                        Manifest.permission.READ_SMS,
                        Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.RECEIVE_MMS,
                    ),
                ),
                PrivacyPermissionGroup(
                    "calendar",
                    setOf(
                        Manifest.permission.READ_CALENDAR,
                        Manifest.permission.WRITE_CALENDAR,
                    ),
                ),
                PrivacyPermissionGroup(
                    "photos",
                    setOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                    ),
                ),
                PrivacyPermissionGroup("audio", setOf(Manifest.permission.READ_MEDIA_AUDIO)),
                PrivacyPermissionGroup(
                    "nearby",
                    setOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.NEARBY_WIFI_DEVICES,
                    ),
                ),
                PrivacyPermissionGroup(
                    "notifications",
                    setOf(Manifest.permission.POST_NOTIFICATIONS),
                ),
            )
    }
}
