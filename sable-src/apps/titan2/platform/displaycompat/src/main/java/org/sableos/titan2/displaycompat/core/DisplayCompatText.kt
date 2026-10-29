package org.sableos.titan2.displaycompat.core

import org.sableos.titan2.theme.SableTexts

/**
 * Every line App display compatibility shows, in English, by string resource name.
 * res/values/strings.xml repeats them (DisplayCompatTextTest keeps the two equal) and
 * res/values-<lang>/strings.xml translates them.
 */
object DisplayCompatText {
    val EN: Map<String, String> =
        mapOf(
            "display_tools" to "Display tools",
            "enforcement_backend_closed_these_settings" to
                "Enforcement backend: CLOSED. These settings are stored but not applied.",
            "alt_shift_r_sym_shift" to "Alt+Shift+R (Sym+Shift+R) cycles the app in front: ",
            "portrait_and_landscape_lock_the" to
                "Portrait and Landscape lock the screen and make apps that ask for another " +
                "orientation fit instead of turning it. While any app has an aspect ratio " +
                "profile, apps' orientation requests are fitted the same way (Android needs that " +
                "for aspect ratios). A per-app orientation wins while its app is in front.",
            "row_change_enter_change_act" to "↑↓ row · ←→ change · Enter change/act · Back",
            "system_rotation" to "System rotation: %1\$s.",
            "display_restored_profiles_apply_again" to
                "Display restored. Profiles apply again when their app comes to the front.",
            "could_not_restore_try_again" to "Could not restore: %1\$s. Try again or reboot.",
            "no_favourite_sizes_yet" to "no favourite sizes yet.",
            "native_label" to " → Native.",
            "system_rotation_2" to "System rotation",
            "favourite" to "Favourite ",
            "restore_the_native_display_now" to "Restore the native display now",
            "back" to "Back",
            "on" to "On",
            "off" to "Off",
            "profile" to "Profile",
            "tall_canvas" to "Tall canvas",
            "orientation" to "Orientation",
            "scaling" to "Scaling",
            "letterbox_background" to "Letterbox background",
            "keyboard_safe_area" to "Keyboard-safe area",
            "status_nav_bars" to "Status / nav bars",
            "stretch_distorts" to "Stretch (distorts)",
            "custom_ratio" to "Custom ratio",
            "advanced_profiles" to "Advanced profiles",
            "acknowledge_warnings" to "Acknowledge warnings",
            "save_profile" to "Save profile",
            "reset_this_app_to_native" to "Reset this app to Native",
            "yes" to "Yes",
            "no" to "No",
            "not_saved" to "Not saved",
            "not_enforced" to "NOT enforced",
            "could_not" to "could not",
            "not_verified" to "not verified",
            "platform" to "Platform: ",
            "keyboard_input_protected" to " · Keyboard input: protected",
            "app_display_compatibility" to "App display compatibility",
            "no_launchable_apps_were_found" to "No launchable apps were found for this user.",
            "no_apps_match" to "No apps match \"%1\$s\".",
            "type_to_filter_apps" to "Type to filter apps",
            "stored" to " · stored",
            "native_label_2" to "Native",
            "tall" to " + tall ",
            "rotation" to "Rotation ",
            "alt_shift_r" to " · Alt+Shift+R",
            "choose_enter_switch_open_move" to "←→ choose · Enter switch/open · ↑↓ move",
            "select_enter_edit_back" to "↑↓ select · Enter edit · Back",
            "press_ctrl_r_again_to" to "Press Ctrl+R again to reset ALL profiles",
            "ctrl_r_reset_all_profiles" to "Ctrl+R: reset all profiles",
            "sable_app_style" to "Sable app style",
            "corners_for_all_sable_apps" to "Corners for all Sable apps. %1\$s",
            "window_manager_unavailable" to "window manager unavailable",
            "this_app" to "this app",
            "no_platform_equivalent_for" to "no platform equivalent for ",
            "stored_only_platform_settings_cleared" to "; stored only, platform settings cleared",
            "platform_settings_cleared" to "platform settings cleared%1\$s",
            "platform_reports_aspect" to "; platform reports aspect %1\$s",
            "no_detail" to "no detail",
            "package_manager_unavailable" to "package manager unavailable",
            "platform_compat_interface_unavailable" to "platform_compat interface unavailable",
            "no_packageoverride" to "no PackageOverride",
            "takes_effect_the_next_time" to "; takes effect the next time the app starts",
            "the_display_tools_service_holds" to
                "; the display tools service holds it while the app is in front",
            "compact" to "Compact",
            "tight_2_6dp_corners_the" to "Tight 2-6dp corners, the original Sable app look.",
            "rounded" to "Rounded",
            "n8dp_controls_12dp_cards_and" to
                "8dp controls, 12dp cards and sheets, like Quick Settings.",
            "sable_apps_now_use_corners" to "Sable apps now use %1\$s corners.",
            "sable_app_style_could_not" to
                "Sable app style could not be saved; Sable apps keep their current corners.",
            "sable_app_style_needs_the" to
                "Sable app style needs the SableOS appearance service in Settings; not on this " +
                "build.",
            "mini_mode_opens_the_app" to
                "Mini mode opens the app in a 3:2 phone-shaped window (480x720 on a 720x720 " +
                "screen).",
            "the_phone_app_must_keep" to
                "The phone app must keep the native display so calls stay easy to read and " +
                "answer.",
            "check_codes_and_prompts_stay" to
                "Check codes and prompts stay readable on the smaller tall canvas.",
            "the_camera_preview_is_shown" to
                "The camera preview is shown smaller on a tall canvas.",
            "tall_canvas_already_gives_a" to
                "Tall canvas already gives a phone shape; an aspect profile narrows it again.",
            "tall_canvas_while_this_app" to
                "Tall canvas: while this app is in front the screen gets a taller size, drawn " +
                "smaller. Leaving the app, locking or a crash puts the native size back.",
            "locking_orientation_can_rotate_the" to
                "Locking orientation can rotate the camera preview unexpectedly.",
            "landscape_turns_the_app_sideways" to
                "Landscape turns the app sideways (reverse landscape) above the keyboard. Typing " +
                "still works.",
            "auto_turns_auto_rotate_on" to
                "Auto turns auto-rotate on while this app is in front; an app that fixes its own " +
                "orientation keeps it.",
            "with_an_aspect_ratio_android" to
                "With an aspect ratio Android keeps the app portrait unless the app itself asks " +
                "for landscape.",
            "system_ui_settings_keyboards_accessibility" to
                "System UI, Settings, keyboards, accessibility services and the launcher cannot " +
                "use display profiles.",
            "the_phone_app_must_keep_2" to
                "The phone app must keep the full display so calls and emergency dialling stay " +
                "reachable.",
            "authenticator_and_payment_apps_cannot" to
                "Authenticator and payment apps cannot be cropped, stretched or given a custom " +
                "ratio.",
            "check_codes_and_prompts_stay_2" to
                "Check codes and prompts stay fully visible after applying this profile.",
            "camera_preview_must_not_be" to "Camera preview must not be cropped or stretched.",
            "media_apps_usually_look_best" to
                "Media apps usually look best with Fullscreen media instead of bars.",
            "fill_can_crop_edges_use" to
                "Fill can crop edges. Use letterbox if buttons or text are missing.",
            "stretch_distorts_text_maps_camera" to
                "Stretch distorts text, maps, camera previews and games. It is off by default.",
            "custom_ratios_are_an_advanced" to
                "Custom ratios are an advanced setting. Enable advanced profiles first.",
            "choose_a_custom_ratio" to "Choose a custom ratio.",
            "custom_ratio_must_be_between" to "Custom ratio must be between %1\$s and %2\$s.",
            "a_custom_ratio_is_stored" to "A custom ratio is stored but not used by this profile.",
            "follow_apps" to "Follow apps",
            "portrait" to "Portrait",
            "landscape" to "Landscape",
            "native_x" to "Native %1\$sx%2\$s",
            "not_taller_here" to "%1\$s (not taller here)",
            "app_default" to "app default",
            "display_size" to "display size",
            "value" to "value %1\$s",
            "orientation_2" to ", orientation ",
            "while_in_front" to "; while in front: ",
            "stored_only" to "; stored only: ",
            "letterbox_background_2" to "letterbox background",
            "keyboard_safe_area_2" to "keyboard-safe area",
            "status_nav_bars_2" to "status/nav bars",
            "tall_canvas_2" to "tall canvas ",
            "rotation_portrait" to "rotation portrait",
            "rotation_landscape" to "rotation landscape",
            "native_app_and_platform_defaults" to "Native: app and platform defaults.",
            "fullscreen_media_no_forced_letterboxing" to
                "Fullscreen media: no forced letterboxing.",
            "square_safe_full_display_with" to
                "Square-safe: full display with a 1:1 minimum-aspect hint; no bars.",
            "n16_9_letterbox" to "16:9 letterbox",
            "n4_3_letterbox" to "4:3 letterbox",
            "keyboard_safe_reserves_px_for" to "Keyboard-safe: reserves %1\$spx for text entry.",
            "mini_mode_3_2_phone" to "Mini mode (3:2 phone window)",
            "custom_ratio_missing_no_op" to "Custom ratio missing: no-op.",
            "custom_ratio_out_of_range" to "Custom ratio %1\$s out of range: no-op.",
            "custom_letterbox" to "Custom %1\$s letterbox",
            "n16_9_fill_of_the" to
                "16:9 fill: %1\$s%% of the app canvas is cropped. Use letterbox if controls go " +
                "missing.",
            "x_centred_background" to "%1\$s: %2\$sx%3\$s centred, background %4\$s.",
            "not_saved_blocked" to "Not saved: blocked. ",
            "not_saved_acknowledge_the_warnings" to "Not saved: acknowledge the warnings first.",
            "not_saved_the_profile_could" to
                "Not saved: the profile could not be stored. Nothing was changed.",
            "saved" to "Saved. ",
            "the_package_name_is_not" to "The package name is not valid.",
            "backend_error" to "backend error: ",
            "enforcement_backend_closed_profiles_are" to
                "Enforcement backend: CLOSED. Profiles are validated and stored only; nothing is " +
                "enforced.",
            "enforcement_backend_open_gate_set" to
                "Enforcement backend: OPEN (gate set). Requests go to the platform; their effect " +
                "is not verified here.",
            "native_profile_nothing_to_enforce" to "Native profile: nothing to enforce.",
            "stored_only_not_enforced_the" to
                "Stored only. NOT enforced: the platform backend is closed until it is validated " +
                "on a device.",
            "stored_only_not_enforced" to "Stored only. NOT enforced: ",
            "stored_the_platform_accepted_the" to "Stored. The platform accepted the request (",
            "its_effect_on_the_app" to "); its effect on the app is not verified.",
            "reset_failed_the_stored_profile" to
                "Reset failed: the stored profile could not be removed.",
            "reset_to_native_the_stored" to
                "Reset to Native. The stored profile was removed, but the platform override " +
                "could not be cleared: ",
            "reset_to_native_the_app" to "Reset to Native. The app was not launched.",
            "reset_profile_s_to_native" to "Reset %1\$s profile(s) to Native.",
            "reset_profile_s_to_native_2" to
                "Reset %1\$s profile(s) to Native. The platform override could not be cleared " +
                "for ",
            "a_stored_profile_is_no" to
                "A stored profile is no longer allowed for this app and is ignored: %1\$s. Save " +
                "or reset to clear it.",
            "display_tools_are_off_on" to "Display tools are off on this build.",
            "open_an_app_first_then" to "Open an app first, then press Alt+Shift+R.",
            "choose_favourite_sizes_in_display" to
                "Choose favourite sizes in Display compatibility > Display tools.",
            "tall_canvas_is_not_available" to "Tall canvas is not available for %1\$s. %2\$s",
            "confirm_the_warnings_for_in" to
                "Confirm the warnings for %1\$s in Display compatibility first.",
            "not_changed_the_profile_could" to "Not changed: the profile could not be stored.",
            "auto_rotate" to "Auto-rotate"
        )

    val t = SableTexts(EN)
}
