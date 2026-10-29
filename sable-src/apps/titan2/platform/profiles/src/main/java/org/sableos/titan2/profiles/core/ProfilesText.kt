package org.sableos.titan2.profiles.core

import org.sableos.titan2.theme.SableTexts

/**
 * Every line Sound Profiles shows, in English, by string resource name. res/values/strings.xml
 * repeats them (ProfilesTextTest keeps the two equal) and res/values-<lang>/strings.xml translates
 * them.
 */
object ProfilesText {
    val EN: Map<String, String> =
        mapOf(
            "sound_profile" to "Sound profile",
            "allow_do_not_disturb_access" to "Allow Do Not Disturb access for Sound Profiles",
            "choose" to "Choose",
            "use" to "Use %1\$s",
            "edit" to "Edit %1\$s",
            "quick_settings_tile" to "Quick Settings Tile",
            "pull_down_the_shade_choose" to
                "Pull down the shade, choose Edit and drag the Sound profile tile in.",
            "close" to "Close",
            "sound_profiles" to "Sound Profiles",
            "enter_uses_a_profile_e" to "Enter uses a profile · E edits it · Menu for more",
            "quick_settings_pull_down_the" to
                "Quick Settings: pull down the shade, choose Edit and add the Sound profile tile.",
            "volume" to "Volume %1\$s%%",
            "enter_adds_10_shift_enter" to "Enter adds 10% · Shift+Enter takes 10% off",
            "ringtone" to "Ringtone",
            "notification_tone" to "Notification tone",
            "vibrate_for_calls" to "Vibrate for calls",
            "space_turns_it_on_or" to "Space turns it on or off",
            "notification_light" to "Notification light",
            "is_on_allow_changing_system" to
                "%1\$s is on. Allow changing system settings so it can set tones, vibration and " +
                "the light, then choose it again.",
            "allow_do_not_disturb_access_2" to
                "Allow Do Not Disturb access for Sound Profiles, then choose %1\$s again.",
            "phone_s_current_tone" to "Phone's current tone",
            "normal" to "Normal",
            "rings_and_alerts_at_your" to "Rings and alerts at your volume",
            "loud" to "Loud",
            "rings_and_alerts_at_full" to "Rings and alerts at full volume",
            "vibrate_only" to "Vibrate only",
            "vibrates_for_calls_and_alerts" to "Vibrates for calls and alerts, no sound",
            "phone_calls_only" to "Phone calls only",
            "only_calls_ring_messages_and" to "Only calls ring; messages and alerts are silent",
            "all_alerts_off" to "All alerts off",
            "nothing_rings_or_vibrates_alarms" to "Nothing rings or vibrates; alarms still sound"
        )

    val t = SableTexts(EN)
}
