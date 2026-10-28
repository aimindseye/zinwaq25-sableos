package org.sableos.titan2.setup.core

import org.sableos.titan2.theme.SableTexts

/**
 * Every line Sable Setup shows, in English, by string resource name. `res/values/strings.xml` repeats them
 * (SetupTextTest keeps the two equal) and `res/values-<lang>/strings.xml` translates them.
 */
object SetupText {
    val EN: Map<String, String> = mapOf(
        "setup_title" to "Sable Setup",
        "setup_self_description" to
            "Choose your home screen, keyboard and default apps, " +
            "and check what this phone reports. Not the Android Setup Wizard.",
        "setup_footnote" to
            "Setup changes nothing by itself: every choice is made on the screen it opens.",
        "setup_no_action_required" to "%1\$s · no action required",
        "summary_confirmed" to "%1\$d of %2\$d confirmed",
        "summary_action_required" to "%1\$d action required",
        "summary_unavailable" to "%1\$d unavailable",
        "summary_unknown" to "%1\$d unknown",
        "summary_separator" to "%1\$s · %2\$s",
        "profile_unknown" to "Profile unknown (not reported by this build)",
        "profile_unrecognized" to "Unrecognized profile id: %1\$s",
        "row_style_title" to "Home screen style",
        "row_style_detail" to "Sable Start in the BlackBerry 10 or BlackBerry 7 style",
        "row_home_title" to "Home app",
        "row_home_in_use" to "In use: %1\$s · choose Sable Start or another launcher",
        "row_home_none" to "No home app resolved",
        "row_home_failed" to "Could not read the home app",
        "row_keyboard_title" to "Keyboard",
        "row_keyboard_in_use" to "In use: %1\$s",
        "row_keyboard_none" to "No keyboard value reported",
        "row_keyboard_failed" to "Could not read the keyboard setting",
        "row_ime_add_title" to "Add a keyboard",
        "row_ime_add_detail" to
            "Turn on Gboard or another keyboard you installed, then choose it above",
        "row_defaults_title" to "Default apps",
        "row_defaults_detail" to
            "Browser, phone, messages and the other apps Android opens by default",
        "row_dialer_title" to "Phone app",
        "row_sms_title" to "Messages app",
        "row_default_set" to
            "A default app is configured: %1\$s (not checked against a specific app)",
        "row_default_none" to "No default app is configured",
        "row_default_failed" to "Could not read the default app",
        "row_display_title" to "Display compatibility",
        "row_display_detail" to "Open to choose a screen profile for apps that do not fit",
        "row_radio_title" to "Radio diagnostics",
        "row_radio_detail" to
            "Read-only SIM and network diagnostics; IMS registration visibility requires canonical IR-007",
        "row_network_title" to "Network and SIM settings",
        "row_network_detail" to "Opens Android Settings",
        "row_unverifiable" to "%1\$s · could not verify that this screen is available",
        "row_needs_attention_unavailable" to
            "%1\$s · needs attention, but its screen is not available on this device",
        "row_not_available" to "Not available on this device",
        "button_fix" to "Fix",
        "button_choose_city" to "Choose city",
        "button_choose" to "Choose",
        "button_open" to "Open",
        "action_home_style" to "Home screen style",
        "action_home_settings" to "Home settings",
        "action_default_apps" to "Default apps settings",
        "action_keyboard_picker" to "Keyboard picker",
        "action_keyboard_settings" to "Keyboard settings",
        "action_display_compat" to "Display compatibility",
        "action_radio_diag" to "Radio diagnostics",
        "action_network_settings" to "Network settings",
        "action_weather_city" to "Weather city",
        "launch_that_screen" to "that screen",
        "launch_missing" to "Could not open %1\$s: it is not available on this device.",
        "launch_denied" to "Could not open %1\$s: the system blocked it.",
        "launch_failed" to "Could not open %1\$s.",
        "weather_title" to "Weather city",
        "weather_hint" to "Type a city",
        "weather_help" to
            "Type to filter, ↑ ↓ to move, Enter to choose. No location permission is used.",
        "weather_no_match" to "No listed city matches. Try a nearby larger city.",
        "weather_unreachable" to
            "Sable Weather did not answer. Skip, and pick a city in Weather later.",
        "weather_current" to "Current: %1\$s",
        "weather_current_unknown" to "Current city not reported",
        "weather_skip" to "Skip",
        "weather_skip_keep" to "Skip · keep %1\$s",
        "weather_selected" to "Weather now uses %1\$s. The forecast loads in the background.",
        "weather_unchanged" to "Weather already uses %1\$s.",
        "weather_full" to
            "Weather's city list is full. Remove a city in Sable Weather, then try again.",
        "weather_unknown_city" to "%1\$s is not in Weather's city list.",
        "weather_failed" to "Weather could not switch to %1\$s.",
        "weather_row_city" to "Forecasts for %1\$s; no location permission",
        "weather_row_none" to "Choose the city Weather shows; no location permission",
        "weather_row_failed" to "Could not read the Weather city"
    )

    val t = SableTexts(EN)
}
