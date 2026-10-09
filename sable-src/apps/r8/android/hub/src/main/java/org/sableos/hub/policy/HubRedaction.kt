package org.sableos.hub.policy

import org.sableos.hub.ConnectedAppKey
import org.sableos.hub.HubConversation
import org.sableos.hub.HubConversationSource
import org.sableos.hub.HubMessage

/** The two levels that apply to one connected source right now. */
data class HubSourcePrivacy(
    /** For list rows and peeks: privacy state narrowed further by the Hub preview preference. */
    val listLevel: HubContentLevel,
    /** For opened conversations: privacy state only (the preview preference governs previews). */
    val contentLevel: HubContentLevel,
    /** "Work" for work-profile sources (`WORK_PROFILE_BADGING=REQUIRED`), else null. */
    val profileBadge: String? = null,
)

/**
 * Applies [HubSourcePrivacy] to the connected-app part of a Hub snapshot before it reaches UI, so
 * redaction cannot be skipped by a screen (`CROSS_PROFILE_PREVIEW_LEAK=NO`). SMS rows are left
 * alone: they are Hub's own telephony data, not notification-derived.
 */
object HubRedaction {
    const val WORK_PROFILE_LABEL = "Work profile"

    fun apply(
        conversations: List<HubConversation>,
        messages: List<HubMessage>,
        privacyFor: (ConnectedAppKey) -> HubSourcePrivacy,
    ): Pair<List<HubConversation>, List<HubMessage>> {
        val byThread = mutableMapOf<Long, HubSourcePrivacy>()
        val redactedConversations =
            conversations.mapNotNull { conversation ->
                val key = conversation.connectedKey() ?: return@mapNotNull conversation
                val privacy = privacyFor(key)
                byThread[conversation.threadId] = privacy
                redactConversation(conversation, privacy)
            }
        val redactedMessages =
            messages.mapNotNull { message ->
                if (message.source != HubConversationSource.ConnectedApp) {
                    message
                } else {
                    // A connected message without a visible conversation is dropped (fail closed).
                    byThread[message.threadId]?.let { redactMessage(message, it.contentLevel) }
                }
            }
        return redactedConversations to redactedMessages
    }

    private fun redactConversation(
        conversation: HubConversation,
        privacy: HubSourcePrivacy,
    ): HubConversation? {
        val badged = conversation.copy(profileBadge = privacy.profileBadge)
        val level = maxOf(privacy.listLevel, privacy.contentLevel)
        return when (level) {
            HubContentLevel.Full -> {
                badged
            }

            HubContentLevel.SenderOnly -> {
                badged.copy(lastBody = "")
            }

            HubContentLevel.SourceAndCount -> {
                badged.copy(
                    displayName = conversation.sourceLabel ?: conversation.displayName,
                    lastBody = "",
                )
            }

            HubContentLevel.GenericProfile -> {
                badged.copy(
                    displayName = WORK_PROFILE_LABEL,
                    lastBody = "",
                    sourceLabel = WORK_PROFILE_LABEL,
                    canQuickReply = false,
                )
            }

            HubContentLevel.Hidden -> {
                null
            }
        }.let { redacted ->
            if (redacted != null && privacy.contentLevel >= HubContentLevel.GenericProfile) {
                redacted.copy(canQuickReply = false)
            } else {
                redacted
            }
        }
    }

    private fun redactMessage(
        message: HubMessage,
        level: HubContentLevel,
    ): HubMessage? =
        when (level) {
            HubContentLevel.Full,
            HubContentLevel.SenderOnly,
            -> message

            HubContentLevel.SourceAndCount -> message.copy(body = "", senderLabel = null, address = "")

            HubContentLevel.GenericProfile,
            HubContentLevel.Hidden,
            -> null
        }

    private fun HubConversation.connectedKey(): ConnectedAppKey? {
        val packageName = sourcePackage
        val serial = sourceUserSerial
        return if (source == HubConversationSource.ConnectedApp && packageName != null && serial != null) {
            ConnectedAppKey(packageName, serial)
        } else {
            null
        }
    }
}
