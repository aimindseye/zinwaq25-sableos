package org.sableos.start.model

import android.content.ComponentName
import android.os.UserHandle
import org.sableos.start.privacy.AppPrivacy

/**
 * One launchable activity of one app instance (package in one Android user).
 *
 * [privacy] is computed on the inventory background thread from the app's
 * effective permission/App-op state in [user] (never merged across profiles)
 * and only read during row binding. null = could not be read.
 */
data class AppEntry(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val privacy: AppPrivacy? = null,
    /** "work" / "private" / "profile" for apps outside the launcher's own user; null for personal apps. */
    val profileLabel: String? = null,
    /** Removable by the user (not a system app); the system uninstaller still confirms. */
    val canUninstall: Boolean = false,
)

fun AppEntry.stableKey(): String =
    "${user.hashCode()}:${component.flattenToString()}"

/** True when the app lives in the launcher's own user (personal profile). */
val AppEntry.inLauncherUser: Boolean
    get() = profileLabel == null
