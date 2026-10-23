package org.sableos.messages.core

enum class MessageKind {
    Sms,
    Mms,

    /** An MMS that has been announced (M-Notification.ind) but not downloaded yet. */
    MmsNotification,
}

enum class Direction {
    Incoming,
    Outgoing,
}

/** What the bubble says about a message. */
enum class DeliveryState {
    Received,
    Sending,
    Sent,
    Failed,
    Draft,
    Downloading,
    DownloadFailed,
}

data class Attachment(
    /** Row id in content://mms/part. */
    val partId: Long,
    val contentType: String,
    val name: String?,
) {
    val isImage: Boolean get() = contentType.lowercase().startsWith("image/")
}

data class ConversationMessage(
    val id: Long,
    val kind: MessageKind,
    val threadId: Long,
    /** The other party: the sender of an incoming message, the recipient of an outgoing one. */
    val address: String,
    val body: String,
    val dateMillis: Long,
    val direction: Direction,
    val state: DeliveryState,
    val read: Boolean = true,
    val errorCode: Int = 0,
    val subject: String? = null,
    val attachments: List<Attachment> = emptyList(),
) {
    /** Stable across reloads and unique across the SMS and MMS tables. */
    val key: String get() = "${kind.name}:$id"
}

/** A row of the provider's threads table (content://mms-sms/conversations?simple=true). */
data class ThreadRow(
    val threadId: Long,
    val dateMillis: Long,
    val snippet: String,
    val recipientIds: List<Long>,
    val messageCount: Int,
    val read: Boolean,
    val hasError: Boolean,
)

data class ThreadSummary(
    val threadId: Long,
    val addresses: List<String>,
    val title: String,
    val snippet: String,
    val dateMillis: Long,
    val unreadCount: Int,
    val hasError: Boolean,
)
