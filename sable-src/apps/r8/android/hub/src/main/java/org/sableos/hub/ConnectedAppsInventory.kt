package org.sableos.hub

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import java.util.Locale

data class ConnectedAppCandidate(
    val key: ConnectedAppKey,
    val label: String,
    val profileLabel: String,
)

class ConnectedAppsInventory(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val launcherApps =
        checkNotNull(appContext.getSystemService(LauncherApps::class.java))
    private val userManager =
        checkNotNull(appContext.getSystemService(UserManager::class.java))

    fun loadCandidates(): List<ConnectedAppCandidate> {
        val locale = Locale.getDefault()
        val currentUser = Process.myUserHandle()

        return userManager.userProfiles
            .flatMap { user ->
                val serial = userManager.getSerialNumberForUser(user)
                if (serial < 0L) {
                    emptyList()
                } else {
                    launcherApps
                        .getActivityList(null, user)
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
                                profileLabel =
                                    if (user == currentUser) {
                                        "Current profile"
                                    } else {
                                        "Profile $serial"
                                    },
                            )
                        }.filter { it.label.isNotBlank() }
                        .toList()
                }
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
