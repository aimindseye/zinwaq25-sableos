package org.sableos.hub.policy

import org.sableos.hub.HubCall
import org.sableos.hub.HubConversation
import org.sableos.hub.HubConversationSource
import org.sableos.hub.HubText.t

/**
 * Hub-wide privacy state at snapshot time (SABLE_HUB_KEYBOARD_FIRST_UX "Privacy model"). Either
 * one puts Hub in its counts-only view: categories and counts, no names, no text, no rows until
 * the user asks for the (already redacted) list.
 */
data class HubPrivacyState(
    val deviceLocked: Boolean = false,
    val privateMode: Boolean = false,
) {
    val countsOnly: Boolean
        get() = deviceLocked || privateMode

    /** Title of the counts-only card: "Locked" wins over "Private mode". */
    val title: String
        get() =
            when {
                deviceLocked -> t("locked")
                privateMode -> t("private_mode")
                else -> t("previews_on")
            }

    /** Line under the title. */
    val detail: String
        get() =
            when {
                deviceLocked -> t("counts_only_unlock_to_view")
                privateMode -> t("counts_only_senders_and_text")
                else -> t("showing_what_each_source_allows")
            }

    /** Rows can be listed (redacted) only while unlocked; a locked device shows counts alone. */
    val canShowList: Boolean
        get() = !deviceLocked
}

/** One line of the counts-only view: "Messages · 2 unread · hidden". */
data class HubPrivateCount(
    val filter: HubInboxFilter,
    val label: String,
    val count: Int,
    val detail: String,
)

object HubPrivateSummary {
    /**
     * Counts per category for the counts-only view, in the design's order (Messages, Mail,
     * Notifications, Missed calls); categories with nothing new are left out. Counts are
     * conversations with unread messages (not messages) and calls the user did not take, the same
     * units as the filter chips.
     */
    fun counts(
        conversations: List<HubConversation>,
        calls: List<HubCall>,
    ): List<HubPrivateCount> {
        val unread = conversations.filter { it.unreadCount > 0 }
        val messages = unread.count { it.source == HubConversationSource.Sms }
        val mail = unread.count(HubMailSources::isMail)
        val other = unread.count { it.source == HubConversationSource.ConnectedApp && !HubMailSources.isMail(it) }
        val missed = calls.count { it.kind.needsAttention }
        return listOfNotNull(
            line(HubInboxFilter.Messages, t("messages"), messages, "unread"),
            line(HubInboxFilter.Mail, t("mail_2"), mail, "unread"),
            line(HubInboxFilter.Notifications, t("notifications"), other, "new"),
            line(HubInboxFilter.Missed, t("missed_calls"), missed, "missed"),
        )
    }

    /** "2 Messages · 1 Mail · 1 Missed calls" style one-line total, or "Nothing new". */
    fun oneLine(counts: List<HubPrivateCount>): String =
        if (counts.isEmpty()) {
            t("nothing_new")
        } else {
            counts.joinToString(" · ") { it.count.toString() + " " + it.label }
        }

    private fun line(
        filter: HubInboxFilter,
        label: String,
        count: Int,
        word: String,
    ): HubPrivateCount? =
        if (count <= 0) {
            null
        } else {
            HubPrivateCount(filter, label, count, t("hidden", count, word))
        }
}

/**
 * The privacy badge in a conversation header (MESSAGES_CONVERSATION_UX "contact/source/privacy
 * header"): "SMS · Private OK", "Signal · Work · Private mode", "SMS · Locked".
 */
object HubPrivacyBadge {
    val VISIBLE: String get() = t("private_ok")

    fun text(
        conversation: HubConversation,
        privacy: HubPrivacyState,
    ): String {
        val source =
            if (conversation.source == HubConversationSource.Sms) {
                HubRedaction.SMS_LABEL
            } else {
                conversation.sourceLabel ?: t("connected_app_2")
            }
        val state =
            when {
                privacy.deviceLocked -> t("locked")

                privacy.privateMode -> t("private_mode")

                conversation.source == HubConversationSource.ConnectedApp &&
                    conversation.lastBody.isBlank() &&
                    conversation.lastDateMillis > 0L -> t("preview_hidden")

                else -> VISIBLE
            }
        return listOfNotNull(source, conversation.profileBadge, state).joinToString(" · ")
    }
}
