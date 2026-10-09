package org.sableos.hub.platform

import android.app.KeyguardManager
import android.content.Context
import android.provider.Settings
import org.sableos.hub.policy.LockscreenNotificationPolicy

/**
 * Reads, never writes, Android's lock state and lockscreen notification settings. When a setting
 * cannot be read (non-SDK key not readable by this app), the conservative value is used: show
 * notifications, but do not allow private content while locked.
 */
class PrivacyReader(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val keyguard = appContext.getSystemService(KeyguardManager::class.java)

    fun deviceLocked(): Boolean = keyguard?.isDeviceLocked ?: true

    fun lockscreenPolicy(): LockscreenNotificationPolicy =
        LockscreenNotificationPolicy(
            showNotifications = secureFlag(KEY_SHOW_NOTIFICATIONS, default = true),
            allowPrivateContent = secureFlag(KEY_ALLOW_PRIVATE, default = false),
        )

    private fun secureFlag(
        key: String,
        default: Boolean,
    ): Boolean =
        runCatching {
            Settings.Secure.getInt(appContext.contentResolver, key, if (default) 1 else 0) != 0
        }.getOrDefault(default)

    private companion object {
        // Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS / LOCK_SCREEN_ALLOW_PRIVATE_NOTIFICATIONS.
        const val KEY_SHOW_NOTIFICATIONS = "lock_screen_show_notifications"
        const val KEY_ALLOW_PRIVATE = "lock_screen_allow_private_notifications"
    }
}
