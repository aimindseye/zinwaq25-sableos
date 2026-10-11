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
 * Applies [HubSourcePrivacy] to a Hub snapshot before it reaches UI, so redaction cannot be skipped
 * by a screen (`CROSS_PROFILE_PREVIEW_LEAK=NO`). Connected-app rows use their source's privacy;
 * Hub's own SMS rows use [smsPrivacy], so private mode and the locked device redact SMS bodies too.
 */
object HubRedaction {
    const val WORK_PROFILE_LABEL = "Work profile"
    const val SMS_LABEL = "SMS"

    private val FULL = HubSourcePrivacy(HubContentLevel.Full, HubContentLevel.Full)

    fun apply(
        conversations: List<HubConversation>,
        messages: List<HubMessage>,
        smsPrivacy: HubSourcePrivacy = FULL,
        privacyFor: (ConnectedAppKey) -> HubSourcePrivacy,
    ): Pair<List<HubConversation>, List<HubMessage>> {
        val byThread = mutableMapOf<Long, HubSourcePrivacy>()
        val redactedConversations =
            conversations.mapNotNull { conversation ->
                if (conversation.source != HubConversationSource.ConnectedApp) {
                    return@mapNotNull redactConversation(conversation, smsPrivacy)
                }
                val key = conversation.connectedKey() ?: return@mapNotNull conversation
                val privacy = privacyFor(key)
                byThread[conversation.threadId] = privacy
                redactConversation(conversation, privacy)
            }
        val redactedMessages =
            messages.mapNotNull { message ->
                if (message.source != HubConversationSource.ConnectedApp) {
                    redactMessage(message, smsPrivacy.contentLevel)
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
                    displayName = conversation.sourceLabel ?: conversation.sourceFallbackLabel(),
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

    private fun HubConversation.sourceFallbackLabel(): String =
        if (source == HubConversationSource.Sms) SMS_LABEL else displayName

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
