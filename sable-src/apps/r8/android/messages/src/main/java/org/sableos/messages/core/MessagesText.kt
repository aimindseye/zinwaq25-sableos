package org.sableos.messages.core

import org.sableos.design.SableTexts

/**
 * Every line Sable Messages shows, in English, by string resource name. `res/values/strings.xml`
 * repeats them (MessagesTextTest keeps the two equal) and `res/values-<lang>/strings.xml`
 * translates them.
 */
object MessagesText {
    val EN: Map<String, String> =
        mapOf(
            "unknown_sender" to "Unknown sender",
            "new_message" to "New message",
            "new_messages" to "%1\$s new messages",
            "multimedia_message" to "Multimedia message",
            "picture" to "Picture",
            "pictures" to "%1\$s pictures",
            "video" to "Video",
            "audio" to "Audio",
            "attachment" to "Attachment",
            "sending" to "Sending…",
            "sent" to "Sent %1\$s",
            "draft" to "Draft",
            "not_sent_enter_to_retry" to "Not sent · %1\$s · Enter to retry",
            "multimedia_message_downloading" to "Multimedia message · downloading…",
            "multimedia_message_not_downloaded_enter" to
                "Multimedia message not downloaded · Enter to retry",
            "couldn_t_send" to "couldn't send",
            "radio_off" to "radio off",
            "no_service" to "no service",
            "sending_limit_reached" to "sending limit reached",
            "blocked_by_fixed_dialing" to "blocked by fixed dialing",
            "short_code_not_allowed" to "short code not allowed",
            "radio_not_available" to "radio not available",
            "network_rejected_it" to "network rejected it",
            "error" to "error %1\$s",
            "to_name_or_number" to "To: name or number",
            "text_message_enter_sends" to "Text message · Enter sends",
            "send" to "Send",
            "back" to "Back",
            "unknown" to "Unknown",
            "sms" to " · SMS",
            "new_message_end_to_latest" to "New message — End to latest",
            "attachment_2" to "Attachment · %1\$s",
            "picture_2" to "Picture · %1\$s",
            "group_conversation_replies_need_mms" to
                "Group conversation: replies need MMS, which Sable Messages doesn't send yet.",
            "sable_messages" to "Sable Messages",
            "no_mobile_network_for_texts" to "No mobile network for texts",
            "this_device_has_no_sms" to
                "This device has no SMS service (no SIM or no mobile radio), so it can't send or " +
                "receive texts.",
            "allow_sms" to "Allow SMS",
            "sable_messages_needs_to_read" to "Sable Messages needs to read and send texts",
            "texts_stay_on_this_phone" to
                "Texts stay on this phone. Sable Messages reads them from Android's message " +
                "store and sends only what you write.",
            "make_sable_messages_the_default" to "Make Sable Messages the default SMS app",
            "allow_sms_access" to "Allow SMS access",
            "android_won_t_ask_again" to
                "Android won't ask again. In Settings > Apps > Sable Messages > Permissions, " +
                "allow SMS.",
            "open_app_settings" to "Open app settings",
            "no_conversations_yet_choose_new" to
                "No conversations yet. Choose New to text someone.",
            "no_conversation_matches" to "No conversation matches.",
            "messages" to "Messages",
            "conversations_unread" to "%1\$s conversations · %2\$s unread",
            "conversations" to "%1\$s conversations",
            "new_label" to "New",
            "no_mobile_network_on_this" to
                "No mobile network on this device: texts can't be sent or received.",
            "another_app_is_the_default" to
                "Another app is the default SMS app, so it receives texts and pictures. Make " +
                "Sable Messages the default to get them here.",
            "make_default" to "Make default",
            "contact_names_are_hidden_until" to
                "Contact names are hidden until Sable Messages may read contacts.",
            "show_names" to "Show names",
            "type_to_filter_enter_opens" to "Type to filter · Enter opens",
            "not_sent" to "Not sent · %1\$s",
        )

    val t = SableTexts(EN)
}
