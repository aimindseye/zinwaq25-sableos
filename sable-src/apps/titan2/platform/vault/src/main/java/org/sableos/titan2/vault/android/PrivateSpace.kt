package org.sableos.titan2.vault.android

import android.content.Context
import android.os.Process
import android.os.UserManager

/** The private profile this app runs in. */
object PrivateSpace {
    /**
     * True only when this app runs in Android's private profile. `UserManager.isPrivateProfile()` is a system API
     * (no permission needed for the caller's own profile group), reached by reflection. Anything unexpected reads as
     * false, so the vault fails closed and stores nothing.
     */
    fun isPrivateProfile(context: Context): Boolean = runCatching {
        val um = context.getSystemService(UserManager::class.java)
        UserManager::class.java.getMethod("isPrivateProfile").invoke(um) as Boolean
    }.getOrDefault(false)

    /**
     * Locks Private Space now (quiet mode on for this profile; MODIFY_QUIET_MODE, privileged and allowlisted).
     * Returns false when the platform refused; the caller then closes the vault and says so.
     */
    fun lockNow(context: Context): Boolean = runCatching {
        context.getSystemService(UserManager::class.java)
            .requestQuietModeEnabled(true, Process.myUserHandle())
    }.getOrDefault(false)
}
