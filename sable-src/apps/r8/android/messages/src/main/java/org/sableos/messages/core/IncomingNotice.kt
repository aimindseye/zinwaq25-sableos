package org.sableos.messages.core

import org.sableos.messages.core.MessagesText.t

/**
 * Text of the new-message notification. The private version carries sender and body; the public
 * version (shown on a locked screen that hides sensitive content) only says that messages came.
 */
object IncomingNotice {
    fun title(
        contactName: String?,
        address: String,
    ): String = contactName?.takeIf { it.isNotBlank() } ?: address.ifBlank { t("unknown_sender") }

    fun publicText(count: Int): String = if (count <= 1) t("new_message") else t("new_messages", count)

    /** Body line for an MMS: its text, else its subject, else what it carries. */
    fun mmsBody(
        text: String,
        subject: String?,
        attachmentTypes: List<String>,
    ): String {
        val images = attachmentTypes.count { it.startsWith("image/") }
        return when {
            text.isNotBlank() -> text
            !subject.isNullOrBlank() -> subject
            attachmentTypes.isEmpty() -> t("multimedia_message")
            images == 1 && attachmentTypes.size == 1 -> t("picture")
            images == attachmentTypes.size -> t("pictures", images)
            attachmentTypes.any { it.startsWith("video/") } -> t("video")
            attachmentTypes.any { it.startsWith("audio/") } -> t("audio")
            else -> t("attachment")
        }
    }
}
