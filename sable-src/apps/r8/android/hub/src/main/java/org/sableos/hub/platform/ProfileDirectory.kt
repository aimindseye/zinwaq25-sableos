package org.sableos.hub.platform

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import org.sableos.hub.policy.ProfileKind

/** One Android user/profile visible to Hub. */
data class ProfileState(
    val user: UserHandle,
    val serial: Long,
    val kind: ProfileKind,
    /** Work profile paused (quiet mode) or not yet unlocked, or private space locked. */
    val locked: Boolean,
)

/**
 * Android user/profile facts for policy scoping (`POLICY_KEY=android_user_or_profile + ...`).
 * Everything comes from public UserManager/LauncherApps APIs; nothing is keyed by package alone.
 */
class ProfileDirectory(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val userManager = checkNotNull(appContext.getSystemService(UserManager::class.java))
    private val launcherApps = checkNotNull(appContext.getSystemService(LauncherApps::class.java))

    fun profiles(): List<ProfileState> {
        val current = Process.myUserHandle()
        return userManager.userProfiles.mapNotNull { user ->
            val serial = userManager.getSerialNumberForUser(user)
            if (serial < 0L) {
                null
            } else {
                ProfileState(
                    user = user,
                    serial = serial,
                    kind = ProfileKind.fromUserType(userTypeOf(user), isCurrentUser = user == current),
                    locked =
                        user != current &&
                            (userManager.isQuietModeEnabled(user) || !userManager.isUserUnlocked(user)),
                )
            }
        }
    }

    fun forSerial(serial: Long): ProfileState? = profiles().firstOrNull { it.serial == serial }

    fun forUser(user: UserHandle): ProfileState? = profiles().firstOrNull { it.user == user }

    fun liveSerials(): Set<Long> = profiles().map(ProfileState::serial).toSet()

    private fun userTypeOf(user: UserHandle): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            runCatching { launcherApps.getLauncherUserInfo(user)?.userType }.getOrNull()
        } else {
            null
        }
}
