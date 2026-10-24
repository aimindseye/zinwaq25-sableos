package org.sableos.messages.core

/**
 * Text of the new-message notification. The private version carries sender and body; the public
 * version (shown on a locked screen that hides sensitive content) only says that messages came.
 */
object IncomingNotice {
    fun title(
        contactName: String?,
        address: String,
    ): String = contactName?.takeIf { it.isNotBlank() } ?: address.ifBlank { "Unknown sender" }

    fun publicText(count: Int): String = if (count <= 1) "New message" else "$count new messages"

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
            attachmentTypes.isEmpty() -> "Multimedia message"
            images == 1 && attachmentTypes.size == 1 -> "Picture"
            images == attachmentTypes.size -> "$images pictures"
            attachmentTypes.any { it.startsWith("video/") } -> "Video"
            attachmentTypes.any { it.startsWith("audio/") } -> "Audio"
            else -> "Attachment"
        }
    }
}
