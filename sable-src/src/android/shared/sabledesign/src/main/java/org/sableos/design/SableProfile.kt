package org.sableos.design

import android.content.Context
import android.provider.Settings

/**
 * The whole-OS profile: BlackBerry 10 or BlackBerry 7. A profile is the experience as a whole (home,
 * menus, tabs, motion), not a home screen style, and every Sable app reads the same value.
 *
 * It lives in one system setting, [SETTING_KEY] in `Settings.Secure`, so any app can read it and only
 * the system, Sable Setup and Sable Launcher (they hold WRITE_SECURE_SETTINGS) can change it. A missing
 * or unknown value reads as [BB10], the default profile.
 */
enum class SableProfile(
    val stableValue: String,
) {
    BB10("bb10"),
    BB7("bb7"),
    ;

    companion object {
        const val SETTING_KEY = "sable_profile"

        /** The profile a stored value names; anything else is [BB10]. */
        fun fromStableValue(value: String?): SableProfile = entries.firstOrNull { it.stableValue == value } ?: BB10

        fun read(context: Context): SableProfile =
            fromStableValue(
                runCatching { Settings.Secure.getString(context.contentResolver, SETTING_KEY) }.getOrNull(),
            )

        /** False when the caller may not write secure settings; the setting is then left as it was. */
        fun write(
            context: Context,
            profile: SableProfile,
        ): Boolean =
            runCatching {
                Settings.Secure.putString(context.contentResolver, SETTING_KEY, profile.stableValue)
            }.getOrDefault(false)
    }
}
