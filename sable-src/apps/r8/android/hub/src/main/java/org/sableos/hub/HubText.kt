package org.sableos.hub

import org.sableos.design.SableTexts

/**
 * Every line Sable Hub shows, in English, by string resource name. res/values/strings.xml repeats
 * them (HubTextTest keeps the two equal) and res/values-<lang>/strings.xml translates them.
 */
object HubText {
    val EN: Map<String, String> =
        mapOf(
            "this_caller_s_number_is" to "This caller's number is not available.",
            "the_phone_app_is_unavailable" to "The Phone app is unavailable.",
            "opened_the_system_mms_composer" to "Opened the system MMS composer.",
            "mms_composer_is_unavailable" to "MMS composer is unavailable.",
            "priority_messages_calls_email_and" to
                "Priority, messages, calls, email and people, kept local to this device.",
            "loading_conversations" to "Loading conversations…",
            "local_communication_across_your_enabled" to
                "Local communication across your enabled sources.",
            "show_list" to "Show list",
            "show_list_without_previews" to "Show list without previews",
            "unlock_to_view_details" to "Unlock to view details",
            "private_mode_settings" to "Private mode settings",
            "android_notification_settings_are_unavailable" to
                "Android notification settings are unavailable.",
            "opened" to "Opened %1\$s.",
            "the_source_app_is_unavailable" to "The source app is unavailable.",
            "text_message" to "Text message",
            "reply" to "Reply",
            "conversation_unavailable" to "conversation unavailable",
            "the_source_conversation_is_no" to
                "The source conversation is no longer available in local Sable history.",
            "back" to "Back",
            "nothing_needs_your_attention" to "Nothing needs your attention.",
            "email" to "Email",
            "open_sable_mail" to "Open Sable Mail",
            "sable_mail" to "Sable Mail",
            "hub_reads_only_the_bounded" to
                "Hub reads only the bounded local mail snapshot. Accounts, sync and mailbox " +
                "storage remain in Sable Mail.",
            "new_message" to "new message",
            "connected_apps" to "connected apps",
            "search_messages_calls_mail" to "/ Search messages, calls, mail",
            "nothing_new" to "Nothing new",
            "show_fewer_sources" to "Show fewer sources",
            "show_all_sources" to "Show all sources",
            "nothing_matches" to "Nothing matches “%1\$s”.",
            "grant_sms_access_to_show" to "Grant SMS access to show conversations.",
            "grant_call_log_access_to" to "Grant call log access to show recent calls.",
            "no_recent_calls" to "No recent calls.",
            "grant_call_log_access_to_2" to "Grant call log access to show missed calls.",
            "no_missed_calls" to "No missed calls.",
            "grant_contacts_access_to_match" to "Grant contacts access to match people.",
            "no_conversations_or_calls_with" to "No conversations or calls with your contacts.",
            "no_connected_app_conversations_choose" to
                "No connected-app conversations. Choose apps under connected apps.",
            "no_mail_notifications_turn_on" to
                "No mail notifications. Turn on a mail app under connected apps.",
            "no_conversations_yet" to "No conversations yet.",
            "allow_sms_access_to_read" to
                "Allow SMS access to read conversations and send messages.",
            "allow_sending_access_to_send" to "Allow sending access to send SMS from Hub.",
            "allow_call_log_access_to" to
                "Allow call log access to show recent and missed calls. Calls stay in the Phone " +
                "app.",
            "access_needed" to "Access needed",
            "call" to "Call · ",
            "text" to "Text",
            "sms" to "SMS · ",
            "connected_app" to "connected app",
            "content_hidden" to "%1\$s · content hidden",
            "contacts_access_is_optional_and" to
                "Contacts access is optional and only improves people matching.",
            "search_people" to "Search people",
            "no_matching_people_are_available" to "No matching people are available.",
            "privacy" to "Privacy: ",
            "call_2" to "Call",
            "attach" to "Attach",
            "open_app" to "Open app",
            "notifications" to "Notifications",
            "no_messages_yet_write_the" to "No messages yet. Write the first one below.",
            "no_messages_yet" to "No messages yet.",
            "search_this_conversation" to "Search this conversation",
            "sms_sending_needs_permission" to "SMS sending needs permission.",
            "send" to "Send",
            "send_sms_or_hand_off" to
                "Send SMS, or hand off to the system MMS composer for attachments.",
            "phone_number" to "Phone number",
            "message" to "Message",
            "send_sms" to "Send SMS",
            "mms_attachment" to "MMS / attachment",
            "content_hidden_by_source_app" to "Content hidden by source app",
            "empty_sms_body" to "Empty SMS body",
            "handed_off" to "Handed off · ",
            "review_access" to "Review access",
            "search_pageup_history_end_latest" to
                "/ search · PageUp history · End latest · Esc back to latest",
            "search_alt_enter_actions_a" to
                "/ search · Alt+Enter actions · A P M X C E N O filters · D dock · T/B top/bottom",
            "choose_installed_apps_that_may" to
                "Choose installed apps that may contribute notification-derived conversations.",
            "sable_does_not_sign_into" to
                "Sable does not sign into these services. The installed app keeps networking, " +
                "encryption, account state and complete history.",
            "loading_installed_apps" to "Loading installed apps…",
            "notification_access_is_enabled" to "Notification access is enabled.",
            "notification_access_is_required_before" to
                "Notification access is required before connected apps can contribute messages.",
            "review_notification_access" to "Review notification access",
            "enable_notification_access" to "Enable notification access",
            "private_mode_hub_shows_counts" to
                "Private mode: Hub shows counts only, Phone hides names in Recents, Contacts " +
                "hides numbers",
            "combine_conversations" to "Combine conversations",
            "group_adjacent_messages_from_the" to
                "Group adjacent messages from the same conversation into a streamlined thread, " +
                "hiding duplicate titles and app icons.",
            "sable_attention" to "Sable Attention",
            "search_installed_apps" to "Search installed apps",
            "use_one_app_per_email" to
                "Use one app per email account. Sable Mail covers Gmail, Outlook, Yahoo, iCloud " +
                "and IMAP. Turn on another mail app only for accounts Sable Mail can't open " +
                "(Proton, Tuta, managed work accounts).",
            "more_than_one_mail_app" to
                "More than one mail app is on; an account signed in to both will appear twice.",
            "no_connected_apps_are_enabled" to "No connected apps are enabled.",
            "no_favorite_apps_are_set" to "No favorite apps are set.",
            "no_favorite_apps_match_this" to "No favorite apps match this search.",
            "no_additional_installed_apps_are" to "No additional installed apps are available.",
            "no_installed_apps_match_this" to "No installed apps match this search.",
            "delivery_android_notification_settings" to "Delivery: Android notification settings",
            "hub_priority_ordering_only_not" to
                "Hub priority (ordering only, not a Do Not Disturb exception)",
            "mail" to "Mail · ",
            "allow_quick_reply" to "Allow quick reply",
            "hide_from_sablelauncher" to "Hide from SableLauncher",
            "history_retention" to "History retention: %1\$s",
            "hub_preview" to "Hub preview: %1\$s",
            "attention" to "Attention: %1\$s",
            "sound" to "Sound",
            "vibration" to "Vibration",
            "notification_light" to "Notification light",
            "keyboard_backlight" to "Keyboard backlight",
            "glance_display" to "Glance display",
            "always_on_display" to "Always-on display",
            "sable_attention_2" to "sable attention",
            "android_decides_whether_a_notification" to
                "Android decides whether a notification alerts. Sable Attention only maps that " +
                "decision to outputs this device supports, and never bypasses Do Not Disturb.",
            "android_notification_settings" to "Android notification settings",
            "notifications_sound_vibration_pop_up" to
                "Notifications: sound, vibration, pop-up, lock screen",
            "conversations" to "Conversations",
            "do_not_disturb" to "Do Not Disturb",
            "notification_history" to "Notification history",
            "this_device" to "This device",
            "this_device_profile_declares_no" to
                "This device profile declares no Sable-controlled attention outputs, so " +
                "notifications use only what Android provides.",
            "off_unless_you_turn_it" to "Off unless you turn it on for an app.",
            "on_for_apps_you_have" to
                "On for apps you have not configured; locked and private content is redacted.",
            "choose_apps_in_connected_apps" to "%1\$s Choose apps in Connected apps.",
            "connected_apps_2" to "Connected apps",
            "follows_android_notification_settings" to "Follows Android notification settings.",
            "open_android_settings" to "Open Android settings",
            "sable_mail_is_unavailable" to "Sable Mail is unavailable.",
            "private_number" to "Private number",
            "recipient_is_required" to "Recipient is required.",
            "message_is_empty" to "Message is empty.",
            "sms_sending_permission_is_not" to "SMS sending permission is not available.",
            "n0_active" to "0 active",
            "n1_active" to "1 active",
            "hub" to "Hub",
            "message_handed_to_android_telephony" to "Message handed to Android telephony.",
            "sms_send_capability_is_unavailable" to "SMS send capability is unavailable.",
            "n1_day" to "1 day",
            "n7_days" to "7 days",
            "n30_days" to "30 days",
            "sender_and_message" to "Sender and message",
            "sender_only" to "Sender only",
            "app_and_count_only" to "App and count only",
            "not_in_hub" to "Not in Hub",
            "in_hub" to "In Hub",
            "paused_android_notification_access_is" to "Paused: Android notification access is off",
            "paused_profile_is_locked_or" to "Paused: profile is locked or paused",
            "no_matches" to "No matches",
            "of" to "%1\$s of %2\$s",
            "new_message_enter_for_latest" to "New message · Enter for latest",
            "work_profile" to "Work profile",
            "locked" to "Locked",
            "private_mode" to "Private mode",
            "previews_on" to "Previews on",
            "counts_only_unlock_to_view" to "Counts only · unlock to view details",
            "counts_only_senders_and_text" to "Counts only · senders and text hidden",
            "showing_what_each_source_allows" to "Showing what each source allows",
            "messages" to "Messages",
            "mail_2" to "Mail",
            "missed_calls" to "Missed calls",
            "hidden" to "%1\$s %2\$s · hidden",
            "private_ok" to "Private OK",
            "connected_app_2" to "Connected app",
            "preview_hidden" to "Preview hidden",
            "open" to "Open",
            "call_back" to "Call back",
            "open_in_messages" to "Open in Messages",
            "open_source_app" to "Open source app",
            "notification_settings" to "Notification settings",
            "privacy_options" to "Privacy options",
            "calls" to "Calls",
            "phone_calls" to "Phone calls",
            "work" to "Work",
            "private_label" to "Private",
            "profile" to "Profile",
            "all" to "All",
            "priority" to "Priority",
            "missed" to "Missed",
            "people" to "People",
            "reply_is_empty" to "Reply is empty.",
            "quick_reply_is_no_longer" to
                "Quick reply is no longer available. Open the source app instead.",
            "unlock_the_device_and_profile" to "Unlock the device and profile to reply.",
            "reply_handed_to_the_source" to "Reply handed to the source app.",
            "the_source_app_no_longer" to "The source app no longer accepts quick reply.",
            "personal" to "Personal",
            "private_space" to "Private space",
            "profile_2" to "Profile %1\$s",
        )

    val t = SableTexts(EN)
}
