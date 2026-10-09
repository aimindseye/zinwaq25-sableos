package org.sableos.tools.core

/** One way to open a target: an intent action, optionally pinned to a package, optionally with a package: URI. */
data class LinkIntent(val action: String, val pkg: String? = null, val packageUri: Boolean = false)

/**
 * Deep links to the surfaces that OWN configuration (SETTINGS_OWNS_CONFIGURATION=YES). Sable Tools never writes a
 * setting and keeps no policy store of its own; it shows effective state and hands off. Each link is a fallback chain:
 * the Android layer opens the first candidate that resolves, and shows "not available" when none does.
 */
enum class SettingsLink(val label: String, val candidates: List<LinkIntent>) {
    KEYBOARD(
        "Settings > Keyboard & input",
        listOf(
            LinkIntent("android.settings.HARD_KEYBOARD_SETTINGS"),
            LinkIntent("android.settings.INPUT_METHOD_SETTINGS")
        )
    ),
    DISPLAY_COMPAT(
        "Settings > Display > App display compatibility",
        listOf(
            LinkIntent("org.sableos.settings.APP_DISPLAY_COMPATIBILITY"),
            LinkIntent("android.intent.action.MAIN", "org.sableos.titan2.displaycompat")
        )
    ),
    DISPLAY("Settings > Display", listOf(LinkIntent("android.settings.DISPLAY_SETTINGS"))),
    SOUND("Settings > Sound", listOf(LinkIntent("android.settings.SOUND_SETTINGS"))),
    BATTERY(
        "Settings > Battery",
        listOf(
            LinkIntent("android.intent.action.POWER_USAGE_SUMMARY"),
            LinkIntent("android.settings.BATTERY_SAVER_SETTINGS")
        )
    ),
    NOTIFICATIONS(
        "Settings > Notifications",
        listOf(LinkIntent("android.settings.NOTIFICATION_SETTINGS"))
    ),
    NETWORK(
        "Settings > Network Manager",
        listOf(
            LinkIntent("org.sableos.settings.NETWORK_MANAGER"),
            LinkIntent("android.settings.WIRELESS_SETTINGS")
        )
    ),
    MOBILE_NETWORK(
        "Settings > Mobile network",
        listOf(LinkIntent("android.settings.NETWORK_OPERATOR_SETTINGS"))
    ),
    VPN("Settings > VPN", listOf(LinkIntent("android.settings.VPN_SETTINGS"))),
    STORAGE("Settings > Storage", listOf(LinkIntent("android.settings.INTERNAL_STORAGE_SETTINGS"))),
    APP_SECURITY(
        "App Security & Privacy",
        listOf(
            LinkIntent("org.sableos.settings.APP_SECURITY"),
            LinkIntent("android.settings.MANAGE_APPLICATIONS_SETTINGS")
        )
    ),
    APP_INFO(
        "App info",
        listOf(LinkIntent("android.settings.APPLICATION_DETAILS_SETTINGS", packageUri = true))
    ),
    PRIVACY("Settings > Privacy", listOf(LinkIntent("android.settings.PRIVACY_SETTINGS"))),
    SECURITY("Settings > Security", listOf(LinkIntent("android.settings.SECURITY_SETTINGS"))),
    DEVELOPER(
        "Settings > Developer options",
        listOf(LinkIntent("android.settings.APPLICATION_DEVELOPMENT_SETTINGS"))
    ),
    ABOUT("Settings > About phone", listOf(LinkIntent("android.settings.DEVICE_INFO_SETTINGS"))),

    /** FM_RADIO_OWNER=Sable_Media: Tools only hands off, it never plays FM. */
    MEDIA_FM(
        "Sable Media > FM radio",
        listOf(LinkIntent("org.sableos.media.action.FM_RADIO", "org.sableos.media"))
    )
}

/** Which link each diagnostic offers as its "Open in Settings" action. */
object Handoffs {
    fun forTool(tool: Tool): List<SettingsLink> = when (tool) {
        Tool.KEY_VIEWER, Tool.KEYBOARD_PROFILE, Tool.TEXT_ENTRY -> listOf(SettingsLink.KEYBOARD)
        Tool.DISPLAY_INPUT -> listOf(SettingsLink.DISPLAY, SettingsLink.DISPLAY_COMPAT)
        Tool.RADIO -> listOf(SettingsLink.MOBILE_NETWORK, SettingsLink.MEDIA_FM)
        Tool.NETWORK -> listOf(SettingsLink.NETWORK, SettingsLink.VPN)
        Tool.STORAGE -> listOf(SettingsLink.STORAGE)
        Tool.APPS -> listOf(SettingsLink.APP_SECURITY)
        Tool.ATTENTION -> listOf(SettingsLink.NOTIFICATIONS)
        Tool.DEVICE_IDENTITY -> listOf(SettingsLink.ABOUT, SettingsLink.SECURITY)
        Tool.BUGREPORT -> listOf(SettingsLink.DEVELOPER)
        else -> emptyList()
    }
}
