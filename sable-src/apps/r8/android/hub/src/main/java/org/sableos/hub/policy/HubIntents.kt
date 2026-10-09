package org.sableos.hub.policy

/**
 * Intents other Sable surfaces use to hand off to Hub. The names are shared with the framework
 * patches in `patches/framework/` (Settings entries and the SystemUI shade `H` command), so they
 * must not change without updating those patches.
 */
object HubIntents {
    const val HUB_PACKAGE = "org.sableos.hub"

    /** Settings > Notifications > Sable Attention. */
    const val ACTION_ATTENTION_SETTINGS = "org.sableos.hub.action.ATTENTION_SETTINGS"

    /** Settings > Notifications > App notifications > (app) > Sable Hub and Attention. */
    const val ACTION_APP_NOTIFICATION_SETTINGS = "org.sableos.hub.action.APP_NOTIFICATION_SETTINGS"

    /** Shade `H`: open the focused notification's conversation in Hub when eligible. */
    const val ACTION_OPEN_NOTIFICATION = "org.sableos.hub.action.OPEN_NOTIFICATION"

    /** Existing Panther V1 entry to the connected-apps screen. */
    const val ACTION_CONNECTED_APPS = "org.sableos.hub.action.CONNECTED_APPS"

    /** android.provider.Settings.EXTRA_APP_PACKAGE. */
    const val EXTRA_APP_PACKAGE = "android.provider.extra.APP_PACKAGE"

    /** Settings' "app_uid" extra (uid of the app in its own user/profile). */
    const val EXTRA_APP_UID = "app_uid"

    /** StatusBarNotification.getKey() of the focused notification. */
    const val EXTRA_NOTIFICATION_KEY = "org.sableos.hub.extra.NOTIFICATION_KEY"
}
