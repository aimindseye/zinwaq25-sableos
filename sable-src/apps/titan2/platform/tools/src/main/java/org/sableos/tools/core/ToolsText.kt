package org.sableos.tools.core

import org.sableos.titan2.theme.SableTexts

/**
 * Every line Sable Tools shows, in English, by string resource name. res/values/strings.xml
 * repeats them (ToolsTextTest keeps the two equal) and res/values-<lang>/strings.xml translates
 * them.
 */
object ToolsText {
    val EN: Map<String, String> =
        mapOf(
            "is_not_available_on_this" to "%1\$s is not available on this build",
            "could_not_save_the_file" to "Could not save the file",
            "saved_to" to "Saved to %1\$s",
            "nothing_matched" to "Nothing matched \"%1\$s\".",
            "this_tool_is_part_of" to
                "This tool is part of the developer/service tier. Turn on Developer options and " +
                "Developer diagnostics in Sable Tools.",
            "not_available_on_this_device" to "Not available on this device profile (%1\$s): %2\$s",
            "not_available" to "Not available.",
            "back" to "Back",
            "the_noise_meter_reads_the" to
                "The noise meter reads the microphone level while it is open. No audio is " +
                "recorded or kept.",
            "the_magnifier_shows_the_camera" to
                "The magnifier shows the camera preview while it is open. Nothing is captured or " +
                "saved.",
            "the_speedometer_uses_your_location" to
                "The speedometer uses your location while it is open. Nothing is stored after " +
                "you leave.",
            "the_pedometer_reads_the_step" to
                "The pedometer reads the step counter. Without it, an accelerometer estimate is " +
                "used.",
            "this_tool_needs" to "This tool needs: ",
            "allow" to "Allow",
            "not_now" to "Not now",
            "permission_not_granted_so_this" to
                "Permission not granted, so this tool cannot read anything. You can change this " +
                "in App info.",
            "actions" to "Actions",
            "nothing_to_copy_yet" to "Nothing to copy yet",
            "copied_identifiers_masked" to "Copied (identifiers masked)",
            "nothing_to_save_yet" to "Nothing to save yet",
            "close" to "Close",
            "copy_c" to "Copy (C)",
            "save_to_phone_s" to "Save to phone, %1\$s (S)",
            "about_this_tool" to "About this tool",
            "open" to "Open %1\$s",
            "filter_del_deletes_back_clears" to "Filter: %1\$s▏  (Del deletes, Back clears)",
            "no_tool_matches" to "No tool matches \"%1\$s\".",
            "developer" to "Developer",
            "developer_diagnostics" to "Developer diagnostics: ",
            "asks_when_opened" to "Asks when opened: ",
            "developer_service_tier" to "Developer/service tier",
            "open_2" to "Open",
            "full_system_bug_reports_contain" to
                "Full system bug reports contain private logs, so Sable Tools does not collect " +
                "them. Android's own bug report flow lives in Developer options and asks before " +
                "sharing.",
            "open_developer_options" to "Open Developer options",
            "sections" to "Sections",
            "before_sharing" to "Before sharing",
            "preview" to "Preview",
            "copy_report" to "Copy report",
            "save_to_phone" to "Save to phone",
            "share_report" to "Share report…",
            "included" to "Included: ",
            "nothing_selected" to "nothing selected",
            "warning" to "Warning: %1\$s",
            "not_included_unless_selected" to "Not included unless selected: ",
            "never_included" to "Never included: ",
            "sable_tools" to "Sable Tools · %1\$s",
            "sable_tools_report" to "Sable Tools report",
            "share_report_2" to "Share report",
            "reports_are_built_on_this" to
                "Reports are built on this phone from read-only state. Identifiers are masked; " +
                "sensitive sections need your selection.",
            "sensor_service_unavailable" to "Sensor service unavailable",
            "level" to "Level",
            "lay_the_phone_on_the" to "Lay the phone on the surface",
            "straight" to "Straight",
            "stand_the_phone_on_its" to "Stand the phone on its edge along the frame",
            "vertical" to "Vertical",
            "hold_the_phone_s_side" to "Hold the phone's side edge against the surface",
            "r_sets_zero_here_rotate" to "R sets zero here; rotate to measure",
            "held" to "HELD · ",
            "zero_point" to "zero point: ",
            "factory_press_c_on_a" to "factory (press C on a known level surface)",
            "user_calibrated" to "user calibrated",
            "calibrated_this_position_is_now" to "Calibrated: this position is now zero",
            "angles_are_rounded_to_0" to
                "Angles are rounded to 0.1°; a phone accelerometer is good to about ±0.5° after " +
                "calibration. C sets the current position as zero (do it on a surface you know " +
                "is level). R clears it. H holds the reading.",
            "needs_calibration_press_c" to "Needs calibration: press C",
            "hold_the_phone_flat" to "Hold the phone flat",
            "magnetic_field_0f_t_accuracy" to "magnetic field %.0f µT · accuracy %d/3",
            "calibrate_move_the_phone_in" to
                "Calibrate: move the phone in a slow figure-8 for a few seconds, away from metal " +
                "and magnets.",
            "heading_of_the_phone_s" to
                "Heading of the phone's top edge from magnetic north (true north would need " +
                "location, which this tool does not use). Magnet cases and nearby metal disturb " +
                "it.",
            "aim_the_top_edge_at" to "Aim the top edge at the BASE of the object and press H",
            "now_aim_at_the_top" to "Now aim at the TOP and press H",
            "estimate" to "Estimate: ",
            "cannot_estimate_from_these_angles" to
                "Cannot estimate from these angles; press R and try again further away",
            "eye_height_2f_m_c" to "eye height %.2f m (C cycles 1.40–1.90 m)",
            "estimate_only_of_aiming_error" to
                "Estimate only: ±%1\$s° of aiming error gives the range shown. Stand on level " +
                "ground; C sets your eye height.",
            "remotes" to "Remotes",
            "built_in" to " · built-in",
            "add_a_custom_remote" to "Add a custom remote",
            "built_in_codes_are_common" to
                "Built-in codes are common published codes, not verified on every model.",
            "add_a_button" to "Add a button",
            "all_remotes" to "All remotes",
            "ir_service_unavailable" to "IR service unavailable",
            "transmit_refused" to "Transmit refused: %1\$s",
            "remote_name" to "Remote name",
            "new_remote_nec_codes" to "New remote (NEC codes)",
            "create" to "Create",
            "cancel" to "Cancel",
            "button_name" to "Button name",
            "n32_bit_code_e_g" to "32-bit code, e.g. 0x20DF10EF",
            "add_button_to" to "Add button to %1\$s",
            "add" to "Add",
            "enter_a_name_and_a" to "Enter a name and a hex code up to 8 digits",
            "delete" to "Delete %1\$s",
            "remotes_are_stored_only_on" to
                "Remotes are stored only on this phone. No network is used. Learning from an " +
                "original remote is not offered because Android has no public IR receiver API.",
            "read_only_values_come_from" to
                "Read-only. Values come from public Android APIs; identifiers are masked. Menu " +
                "opens the owning Settings page.",
            "vibrate_once_test" to "Vibrate once (test)",
            "no_vibrator" to "No vibrator",
            "modifiers" to "modifiers: -",
            "press_keys_back_twice_leaves" to "Press keys. Back twice leaves.",
            "modifiers_2" to "modifiers: %1\$s",
            "shows_what_an_ordinary_app" to
                "Shows what an ordinary app receives. Software keyboard fallback is covered by " +
                "the critical text-entry test.",
            "move_the_trackpad_touch_the" to
                "Move the trackpad, touch the screen or press keys. Back twice leaves.",
            "trackpads_that_report_as_a" to
                "Trackpads that report as a D-pad show up as keys; ones that report as a pointer " +
                "show up as motion.",
            "covers" to "Covers ",
            "only_keys_this_keyboard_has" to
                ". Only keys this keyboard has are tested. Press Enter in a field to check it. " +
                "Only pass/fail is kept.",
            "engineering_only" to "engineering only",
            "not_on_this_device" to "not on this device",
            "is_not_available_on_this_2" to "%1\$s is not available on this device profile",
            "factory_test_categories_mapped_to" to
                "Factory test (%1\$s) categories mapped to Sable diagnostics. Calibration, " +
                "logging and aging tests change device state and are never one-tap actions here.",
            "factory_test_bridge" to "Factory test bridge",
            "raw_vendor_launch" to "raw vendor launch",
            "not_offered_raw_factory_test" to
                "not offered (RAW_FACTORY_TEST_DIRECT_LAUNCH=NO_BY_DEFAULT)",
            "calibration_logging_aging" to "calibration / logging / aging",
            "engineering_gated_use_the_vendor" to
                "engineering-gated; use the vendor tool only under a service procedure",
            "read_only_equivalents" to "read-only equivalents",
            "diagnostics_hardware_test_categories" to "Diagnostics > Hardware test categories",
            "hardware_test_categories" to "Hardware test categories",
            "turn_on_off" to "Turn on / off",
            "turns_off_when_you_leave" to
                "Turns off when you leave this screen. Quick Settings has a persistent " +
                "flashlight.",
            "no_flash_unit_found" to "No flash unit found",
            "flash_busy_another_app_is" to "Flash busy (another app is using the camera)",
            "uses_the_camera_flash_in" to
                "Uses the camera flash in torch mode. No camera permission is needed and no " +
                "image is taken.",
            "no_back_camera" to "No back camera",
            "camera_permission_not_granted" to "Camera permission not granted",
            "camera_unavailable" to "Camera unavailable (%1\$s)",
            "camera_error" to "Camera error %1\$s",
            "preview_not_supported" to "Preview not supported",
            "n1fx_up_down_zoom_s" to "%.1fx (Up/Down zoom)%s",
            "light_on" to " · light on",
            "light_on_off" to "Light on / off",
            "live_camera_preview_only_nothing" to
                "Live camera preview only. Nothing is captured, recorded or saved.",
            "approximate_phone_microphones_are_not" to
                "Approximate: phone microphones are not calibrated sound meters.",
            "microphone_unavailable" to "Microphone unavailable",
            "n0f_db" to "%.0f dB",
            "reads_the_microphone_level_only" to
                "Reads the microphone level only while open. Audio is never recorded, kept or " +
                "sent.",
            "waiting_for_gps" to "Waiting for GPS…",
            "location_is_off_turn_it" to "Location is off. Turn it on in Quick Settings.",
            "n1f_mph" to "%.1f mph",
            "n1f_km_h" to "%.1f km/h",
            "weak_fix_ignored" to "weak fix ignored · ",
            "distance_2f_km_max_s" to "distance %.2f km · max %s · avg %s · %d s",
            "switch_km_h_mph" to "Switch km/h / mph",
            "gps_speed_and_trip_distance" to
                "GPS speed and trip distance while this screen is open. Nothing is stored or " +
                "shared.",
            "step_counter" to "step counter",
            "accelerometer_estimate" to "accelerometer estimate",
            "source_counts_only_while_open" to "source: %1\$s · counts only while open",
            "counts_steps_while_this_screen" to
                "Counts steps while this screen is open. An estimate, not a health measurement.",
            "air_conditioner" to "Air conditioner",
            "projector" to "Projector",
            "audio" to "Audio",
            "custom" to "Custom",
            "samsung_tv" to "Samsung TV",
            "power" to "Power",
            "volume_up" to "Volume up",
            "volume_down" to "Volume down",
            "mute" to "Mute",
            "channel_up" to "Channel up",
            "channel_down" to "Channel down",
            "lg_tv" to "LG TV",
            "no_ir_transmitter_on_this" to "No IR transmitter on this device profile.",
            "the_ir_transmitter_does_not" to "The IR transmitter does not support %1\$s kHz.",
            "pattern_too_long" to "Pattern too long.",
            "c_calibrate" to "C calibrate · ",
            "r_reset_h_hold_m" to "R reset · H hold · M more · Back to leave",
            "every_key_is_captured_press" to "Every key is captured. Press Back twice to leave.",
            "enter_check_back_to_leave" to "Enter check · Back to leave",
            "space_select_enter_open_m" to "Space select · Enter open · M more · Back to leave",
            "c_copy_s_save_to" to "C copy · S save to phone · M more · Back to leave",
            "display_2" to "Display",
            "keyboard_pointer" to "Keyboard & pointer",
            "sensors" to "Sensors",
            "radio" to "Radio",
            "attention" to "Attention",
            "engineering" to "Engineering",
            "settings_battery" to "Settings > Battery",
            "settings_display" to "Settings > Display",
            "settings_sound" to "Settings > Sound",
            "factory_test_bridge_2" to "Factory Test bridge",
            "this_device_has_no_factory" to
                "This device has no factory test surface. Sable hardware tests are shown instead.",
            "the_factory_test_bridge_is" to
                "The factory test bridge is not proven on this device profile. Sable hardware " +
                "tests are shown instead.",
            "the_factory_test_bridge_needs" to
                "The factory test bridge needs developer mode. Sable hardware tests are shown " +
                "instead.",
            "engineering_surface_calibration_logging_and" to
                "Engineering surface: calibration, logging and aging actions stay gated.",
            "utilities" to "Utilities",
            "diagnostics" to "Diagnostics",
            "reports" to "Reports",
            "compass" to "Compass",
            "bubble_level" to "Bubble level",
            "plumb_bob" to "Plumb bob",
            "protractor" to "Protractor",
            "picture_hanging" to "Picture hanging",
            "flashlight" to "Flashlight",
            "magnifier" to "Magnifier",
            "height_estimate" to "Height estimate",
            "noise_meter" to "Noise meter",
            "speedometer" to "Speedometer",
            "pedometer" to "Pedometer",
            "ir_remote" to "IR remote",
            "device_build_and_security" to "Device, build and security",
            "keyboard_event_viewer" to "Keyboard event viewer",
            "keyboard_layout_and_profile" to "Keyboard layout and profile",
            "pointer_and_touch_surface" to "Pointer and touch surface",
            "display_and_input_geometry" to "Display and input geometry",
            "camera_capabilities" to "Camera capabilities",
            "sensor_inventory" to "Sensor inventory",
            "radio_ims_and_fm_status" to "Radio, IMS and FM status",
            "network_ip_dns_and_vpn" to "Network, IP, DNS and VPN",
            "storage" to "Storage",
            "installed_apps" to "Installed apps",
            "critical_text_entry_test" to "Critical text-entry test",
            "attention_and_subscreen_status" to "Attention and SubScreen status",
            "capability_status_2" to "Capability status",
            "device_report" to "Device report",
            "keyboard_and_input_report" to "Keyboard and input report",
            "radio_and_network_report" to "Radio and network report",
            "app_inventory_report" to "App inventory report",
            "custom_report" to "Custom report",
            "system_bug_report" to "System bug report",
            "magnetic" to "%1\$s° %2\$s (magnetic)",
            "quiet" to "Quiet",
            "moderate" to "Moderate",
            "loud" to "Loud",
            "very_loud" to "Very loud",
            "harmful_with_long_exposure" to "Harmful with long exposure",
            "about_1f_m_1f_1f" to "about %.1f m (%.1f–%.1f m), %.1f m away",
            "recent" to "Recent",
            "developer_mode" to " · developer mode",
            "n1_utility" to "1 utility",
            "utilities_2" to "%1\$s utilities",
            "available" to "Available",
            "needs_permission" to "Needs permission",
            "needs_calibration" to "Needs calibration",
            "developer_only" to "Developer only",
            "unavailable_on_this_device" to "Unavailable on this device",
            "profile_unsupported" to "profile: unsupported (%1\$s)",
            "hardware_not_reported_by_this" to "hardware not reported by this device",
            "profile_unproven" to "profile: unproven (%1\$s)",
            "profile_diagnostic_only" to "profile: diagnostic only (%1\$s)",
            "profile_supported" to "profile: supported (%1\$s)",
            "settings_keyboard_input" to "Settings > Keyboard & input",
            "settings_display_app_display_compatibility" to
                "Settings > Display > App display compatibility",
            "settings_notifications" to "Settings > Notifications",
            "settings_network_manager" to "Settings > Network Manager",
            "settings_mobile_network" to "Settings > Mobile network",
            "settings_vpn" to "Settings > VPN",
            "settings_storage" to "Settings > Storage",
            "app_security_privacy" to "App Security & Privacy",
            "app_info" to "App info",
            "settings_privacy" to "Settings > Privacy",
            "settings_security" to "Settings > Security",
            "settings_developer_options" to "Settings > Developer options",
            "settings_about_phone" to "Settings > About phone",
            "sable_media_fm_radio" to "Sable Media > FM radio",
            "shift" to "Shift",
            "hold_or_lock_shift_and" to "Hold or lock Shift and type a capital letter",
            "alt_layer_digits" to "Alt-layer digits",
            "hold_or_lock_alt_and" to "Hold or lock Alt and type: 2468",
            "letters" to "Letters",
            "type_sable" to "Type: sable",
            "numbers" to "Numbers",
            "type_2468" to "Type: 2468",
            "symbols" to "Symbols",
            "type" to "Type: @#\$%&*!?",
            "alt_layer" to "Alt layer",
            "hold_or_lock_alt_and_2" to
                "Hold or lock Alt and type any key printed with a second character",
            "sym" to "Sym",
            "use_sym_to_enter_any" to "Use Sym to enter any symbol",
            "fn" to "Fn",
            "use_fn_with_any_key" to "Use Fn with any key that has an Fn character",
            "bluetooth_pairing_code" to "Bluetooth pairing code",
            "simulated_pairing_dialog_enter_482915" to "Simulated pairing dialog. Enter: 482915",
            "wi_fi_password" to "Wi-Fi password",
            "simulated_wi_fi_password_enter" to "Simulated Wi-Fi password. Enter: Tr4il-mix#26!",
            "lockscreen_pin" to "Lockscreen PIN",
            "simulated_pin_pad_enter_7391" to "Simulated PIN pad. Enter: 7391",
            "software_keyboard_fallback" to "Software keyboard fallback",
            "use_the_on_screen_keyboard" to "Use the on-screen keyboard to type: ok",
            "critical_text_entry" to "critical text entry",
            "pass_fail_not_run" to "%1\$s/%2\$s PASS, %3\$s FAIL, %4\$s NOT_RUN",
            "media_quick_control" to "Media quick control",
            "safe_ir_quick_control" to "Safe IR quick control",
            "camera_magnifier_preview" to "Camera / magnifier preview",
            "battery_and_device_status" to "Battery and device status",
            "keyboard_and_input" to "Keyboard and input",
            "display_geometry" to "Display geometry",
            "radio_and_sim_state" to "Radio and SIM state",
            "storage_summary" to "Storage summary",
            "tools_capability_status" to "Tools capability status",
            "network_addresses_dns_and_vpn" to "Network addresses, DNS and VPN",
            "includes_partially_masked_ip_and" to
                "Includes partially masked IP and DNS addresses, which can hint at your network " +
                "or location.",
            "installed_app_list" to "Installed app list",
            "your_app_list_can_reveal" to
                "Your app list can reveal your bank, health, dating or work apps.",
            "diagnostic_session_log" to "Diagnostic session log",
            "key_events_captured_in_this" to
                "Key events captured in this session can contain anything you typed while the " +
                "key viewer was open.",
            "message_bodies" to "message bodies",
            "contact_contents" to "contact contents",
            "photos_and_media" to "photos and media",
            "authentication_secrets" to "authentication secrets",
            "full_private_logs" to "full private logs",
            "precise_location_history" to "precise location history",
            "provider_credentials" to "provider credentials",
            "select_at_least_one_section" to "Select at least one section",
            "tv" to "TV"
        )

    val t = SableTexts(EN)
}
