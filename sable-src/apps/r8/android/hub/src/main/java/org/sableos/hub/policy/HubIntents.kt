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

    /**
     * Optional search text. Sable Start's Command sends it with ACTION_MAIN + CATEGORY_LAUNCHER to
     * open Hub with its search pre-filled; empty or missing means a normal launch.
     */
    const val EXTRA_QUERY = "org.sableos.hub.extra.QUERY"

    /**
     * Optional inbox filter, sent with ACTION_MAIN + CATEGORY_LAUNCHER (Sable Start's Hub page and
     * its base-bar Hub slot): one of [HubInboxFilter] by name in any case ("priority", "messages",
     * "missed", "calls", "mail", "notifications", "people", "all") or an alias in [FILTER_ALIASES].
     * Unknown values are ignored (a normal launch). Combines with [EXTRA_QUERY].
     */
    const val EXTRA_FILTER = "org.sableos.hub.extra.FILTER"

    /**
     * Contacts' Hub route chip (patches/framework/packages/apps/Contacts 0754): open the Hub
     * conversation for [EXTRA_ADDRESS], or an empty SMS thread for it when there is none yet. It
     * only navigates; nothing is sent.
     */
    const val ACTION_OPEN_CONVERSATION = "org.sableos.hub.action.OPEN_CONVERSATION"

    /** A phone number or SMS address (Contacts' default number for the person). */
    const val EXTRA_ADDRESS = "org.sableos.hub.extra.ADDRESS"

    const val MAX_QUERY_LENGTH = 120
    const val MAX_ADDRESS_LENGTH = 64

    /** Other names Start and Command may use for a filter. */
    val FILTER_ALIASES: Map<String, HubInboxFilter> =
        mapOf(
            "msgs" to HubInboxFilter.Messages,
            "sms" to HubInboxFilter.Messages,
            "missed_calls" to HubInboxFilter.Missed,
            "recent" to HubInboxFilter.Calls,
            "recents" to HubInboxFilter.Calls,
            "email" to HubInboxFilter.Mail,
            "contacts" to HubInboxFilter.People,
        )

    /** The inbox filter to open on, or null for a normal launch. */
    fun launchFilter(raw: String?): HubInboxFilter? {
        val name = raw?.trim()?.lowercase()?.replace('-', '_')?.replace(' ', '_') ?: return null
        return HubInboxFilter.entries.firstOrNull { it.name.lowercase() == name } ?: FILTER_ALIASES[name]
    }

    /** The address to open, or null when it is missing, too long or has nothing dialable. */
    fun launchAddress(raw: String?): String? =
        raw
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= MAX_ADDRESS_LENGTH && it.none(Char::isISOControl) }
            ?.takeIf { address -> address.any(Char::isLetterOrDigit) }

    /** The search to open with, or null for a normal launch. */
    fun launchQuery(raw: String?): String? =
        raw
            ?.replace('\n', ' ')
            ?.trim()
            ?.take(MAX_QUERY_LENGTH)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}

/** Who may read Sable private mode from Hub's privacy provider (`org.sableos.hub.privacy`). */
object HubPrivacyCallers {
    val ALLOWED_PACKAGES: Set<String> =
        setOf(
            "com.android.dialer",
            "com.android.contacts",
            "org.sableos.launcher",
        )

    /** A shared UID passes when any of its packages is allowed. */
    fun allowed(callerPackages: Collection<String>): Boolean = callerPackages.any { it in ALLOWED_PACKAGES }
}
