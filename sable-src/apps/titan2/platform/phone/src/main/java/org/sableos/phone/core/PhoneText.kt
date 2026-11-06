package org.sableos.phone.core

import org.sableos.titan2.theme.SableTexts

/**
 * Every line Sable Phone shows, in English, by string resource name. res/values/strings.xml repeats them
 * (PhoneTextTest keeps the two equal) and res/values-<lang>/strings.xml translates them.
 */
object PhoneText {
    val EN: Map<String, String> =
        mapOf(
            "app_title" to "Phone",
            "tab_calls" to "Calls",
            "tab_contacts" to "Contacts",
            "tab_dial_pad" to "Dial pad",
            "search_hint" to "Type a name or number",
            "make_default" to
                "Sable Phone is not your phone app. Press here to make it the default.",
            "today" to "Today",
            "yesterday" to "Yesterday",
            "no_matches" to "No matches",
            "contact_hidden" to "Contact hidden",
            "private_number" to "Private number",
            "call_count" to "%1\$s (%2\$d)",
            "kind_incoming" to "Incoming",
            "kind_outgoing" to "Outgoing",
            "kind_missed" to "Missed",
            "kind_declined" to "Declined",
            "kind_blocked" to "Blocked",
            "kind_voicemail" to "Voicemail",
            "call" to "Call",
            "send_message" to "Send message",
            "edit_before_call" to "Edit number before calling",
            "copy_number" to "Copy number",
            "add_contact" to "Add to contacts",
            "remove_from_calls" to "Remove from Calls",
            "enter_number" to "Enter a number",
            "delete" to "Delete",
            "suggestion" to "Call %1\$s · %2\$s",
            "hint_calls" to
                "Letters search · digits dial · hold a letter for speed dial · Menu for more",
            "hint_contacts" to
                "Letters search · Call key or Enter calls · hold a letter for speed dial",
            "hint_dial_pad" to
                "W E R S D F Z X C are 1 to 9 · hold 1 for voicemail · Call key calls",
            "speed_dial" to "Speed dial",
            "speed_dial_hint" to
                "Hold a letter key in Phone or on the home screen to call its number.",
            "speed_dial_row" to "%1\$s · %2\$s",
            "speed_dial_row_empty" to "%1\$s · Not set",
            "speed_dial_change" to "Choose another contact",
            "speed_dial_remove" to "Remove",
            "speed_dial_unassigned" to "Nothing on %1\$s yet. Choose who holding %1\$s calls.",
            "speed_dial_assigned" to "Holding %1\$s now calls %2\$s",
            "speed_dial_no_number" to "That contact has no phone number.",
            "space_answers_on" to "Space answers calls: on",
            "space_answers_off" to "Space answers calls: off",
            "blocked_numbers" to "Blocked numbers",
            "call_settings" to "Call settings",
            "sim_settings" to "Calling accounts",
            "needs_call_permission" to "Sable Phone needs the Phone permission to make calls.",
            "imei_line" to "IMEI %1\$d: %2\$s",
            "imei_unavailable" to "The IMEI is not available.",
            "incoming_call" to "Incoming call",
            "calling" to "Calling…",
            "on_hold" to "On hold",
            "ending" to "Ending…",
            "call_ended" to "Call ended",
            "choose_sim" to "Choose a SIM for this call",
            "sim" to "SIM",
            "cancel" to "Cancel",
            "conference" to "Conference call",
            "ongoing_call" to "Ongoing call",
            "other_call" to "%1\$s · %2\$s",
            "answer" to "Answer",
            "decline" to "Decline",
            "end_call" to "End call",
            "hang_up" to "Hang up",
            "mute" to "Mute",
            "speaker" to "Speaker",
            "audio" to "Audio",
            "route_phone" to "Phone",
            "route_bluetooth" to "Bluetooth",
            "keypad" to "Keypad",
            "hold" to "Hold",
            "add_call" to "Add call",
            "merge" to "Merge",
            "swap" to "Swap",
            "hint_ringing" to "Call key or Space answers · End declines",
            "hint_call" to "M mute · S speaker · H hold · D keypad · End hangs up",
            "hint_keypad" to "Dial keys send tones · Back closes the keypad",
            "channel_incoming" to "Incoming calls",
            "channel_ongoing" to "Ongoing calls",
            "channel_missed" to "Missed calls",
            "missed_call" to "Missed call",
            "missed_calls" to "%1\$d missed calls",
            "call_back" to "Call back"
        )

    val t = SableTexts(EN)
}
