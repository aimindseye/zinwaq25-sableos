package org.sableos.hub

import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.os.UserManager
import org.sableos.hub.platform.ProfileDirectory
import org.sableos.hub.policy.ProfileKind
import java.util.Locale

data class ConnectedAppCandidate(
    val key: ConnectedAppKey,
    val label: String,
    val profileLabel: String,
    val profileKind: ProfileKind = ProfileKind.Personal,
    val profileLocked: Boolean = false,
)

class ConnectedAppsInventory(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val launcherApps =
        checkNotNull(appContext.getSystemService(LauncherApps::class.java))
    private val userManager =
        checkNotNull(appContext.getSystemService(UserManager::class.java))

    private val profiles = ProfileDirectory(appContext)

    fun loadCandidates(): List<ConnectedAppCandidate> {
        val locale = Locale.getDefault()

        return profiles
            .profiles()
            .flatMap { profile ->
                val serial = profile.serial
                launcherApps
                    .getActivityList(null, profile.user)
                    .asSequence()
                    .filterNot { info ->
                        info.componentName.packageName in EXCLUDED_PACKAGES
                    }.map { info ->
                        ConnectedAppCandidate(
                            key =
                                ConnectedAppKey(
                                    packageName = info.componentName.packageName,
                                    userSerial = serial,
                                ),
                            label = info.label.toString().trim(),
                            profileLabel = profileLabel(profile.kind, serial),
                            profileKind = profile.kind,
                            profileLocked = profile.locked,
                        )
                    }.filter { it.label.isNotBlank() }
                    .toList()
            }.distinctBy { it.key }
            .sortedWith(
                compareBy<ConnectedAppCandidate> {
                    it.label.lowercase(locale)
                }.thenBy {
                    it.key.packageName
                }.thenBy {
                    it.key.userSerial
                },
            )
    }

    fun open(key: ConnectedAppKey): Boolean {
        val user = userForSerial(key.userSerial)
        val activity =
            user?.let { profile ->
                launcherApps
                    .getActivityList(key.packageName, profile)
                    .firstOrNull()
            }

        return if (user == null || activity == null) {
            false
        } else {
            runCatching {
                launcherApps.startMainActivity(
                    activity.componentName,
                    user,
                    null,
                    null,
                )
                true
            }.getOrDefault(false)
        }
    }

    fun labelFor(key: ConnectedAppKey): String =
        userForSerial(key.userSerial)
            ?.let { user ->
                launcherApps
                    .getActivityList(key.packageName, user)
                    .firstOrNull()
                    ?.label
                    ?.toString()
                    ?.trim()
            }?.takeIf { it.isNotBlank() }
            ?: key.packageName

    /** Uid of the package in its own profile, for Settings' per-app pages; null if unknown. */
    fun uidFor(key: ConnectedAppKey): Int? =
        userForSerial(key.userSerial)?.let { user ->
            runCatching { launcherApps.getApplicationInfo(key.packageName, 0, user).uid }.getOrNull()
        }

    /** The key for a package in the profile that owns [uid] (as Settings and SystemUI pass it). */
    fun keyForUid(
        packageName: String,
        uid: Int,
    ): ConnectedAppKey? {
        val serial = userManager.getSerialNumberForUser(UserHandle.getUserHandleForUid(uid))
        return if (serial >= 0L && packageName.isNotBlank()) ConnectedAppKey(packageName, serial) else null
    }

    private fun profileLabel(
        kind: ProfileKind,
        serial: Long,
    ): String =
        when (kind) {
            ProfileKind.Personal -> "Personal"
            ProfileKind.Work -> "Work profile"
            ProfileKind.Private -> "Private space"
            ProfileKind.Other -> "Profile $serial"
        }

    private fun userForSerial(serial: Long): UserHandle? =
        userManager.userProfiles.firstOrNull { user ->
            userManager.getSerialNumberForUser(user) == serial
        }

    private companion object {
        val EXCLUDED_PACKAGES =
            setOf(
                "org.sableos.hub",
                "org.sableos.launcher",
                "com.android.launcher3",
                "com.android.messaging",
            )
    }
}
