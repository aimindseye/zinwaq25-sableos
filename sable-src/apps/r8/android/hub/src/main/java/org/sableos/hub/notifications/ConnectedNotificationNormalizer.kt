package org.sableos.hub.notifications

import android.app.PendingIntent
import android.app.RemoteInput
import org.sableos.hub.ConnectedAppKey
import org.sableos.hub.ConnectedAppsInventory
import org.sableos.hub.ConnectedNotificationRecord

internal data class ConnectedNotificationMessageSnapshot(
    val senderLabel: String?,
    val body: String?,
    val timestampMillis: Long,
    val incoming: Boolean,
)

internal data class ConnectedNotificationReplyCandidate(
    val pendingIntent: PendingIntent,
    val remoteInput: RemoteInput,
)

internal data class ConnectedNotificationEnvelope(
    val key: ConnectedAppKey,
    val notificationKey: String,
    val notificationId: Int,
    val notificationTag: String?,
    val shortcutId: String?,
    val postTimeMillis: Long,
    val notificationTitle: String?,
    val notificationBody: String?,
    val conversationTitle: String?,
    val category: String?,
    val isGroupSummary: Boolean,
    val messages: List<ConnectedNotificationMessageSnapshot>,
    val replyCandidate: ConnectedNotificationReplyCandidate?,
    val allowQuickReply: Boolean,
)

internal data class ConnectedNotificationNormalization(
    val records: List<ConnectedNotificationRecord>,
    val replyHandle: ConnectedReplyHandle?,
)

internal class ConnectedNotificationNormalizer(
    private val inventory: ConnectedAppsInventory,
) {
    fun normalize(envelope: ConnectedNotificationEnvelope): ConnectedNotificationNormalization {
        val sourceLabel =
            inventory
                .labelFor(envelope.key)
                .trim()
                .take(MAX_NOTIFICATION_LABEL_LENGTH)
                .ifBlank { envelope.key.packageName }
        val conversationId =
            envelope.shortcutId
                ?: envelope.notificationTag
                ?: "notification:${envelope.notificationId}"
        val conversationTitle =
            envelope.conversationTitle
                ?: envelope.notificationTitle
                ?: envelope.messages
                    .asReversed()
                    .firstNotNullOfOrNull { message ->
                        message.senderLabel
                    }
                ?: sourceLabel

        return ConnectedNotificationNormalization(
            records =
                buildRecords(
                    envelope = envelope,
                    conversationId = conversationId,
                    conversationTitle = conversationTitle,
                    sourceLabel = sourceLabel,
                ),
            replyHandle =
                envelope.replyCandidate
                    ?.takeIf {
                        envelope.allowQuickReply
                    }?.let { candidate ->
                        ConnectedReplyHandle(
                            notificationKey = envelope.notificationKey,
                            sourceKey = envelope.key,
                            conversationId = conversationId,
                            pendingIntent = candidate.pendingIntent,
                            remoteInput = candidate.remoteInput,
                        )
                    },
        )
    }

    private fun buildRecords(
        envelope: ConnectedNotificationEnvelope,
        conversationId: String,
        conversationTitle: String,
        sourceLabel: String,
    ): List<ConnectedNotificationRecord> =
        if (envelope.messages.isEmpty()) {
            listOf(
                ConnectedNotificationRecord(
                    id =
                        recordId(
                            notificationKey = envelope.notificationKey,
                            timestampMillis = envelope.postTimeMillis,
                            index = 0,
                            senderLabel = null,
                            body = envelope.notificationBody,
                        ),
                    key = envelope.key,
                    notificationKey = envelope.notificationKey,
                    conversationId = conversationId,
                    conversationTitle = conversationTitle,
                    sourceLabel = sourceLabel,
                    senderLabel = null,
                    body = envelope.notificationBody,
                    timestampMillis = envelope.postTimeMillis,
                    incoming = true,
                ),
            )
        } else {
            envelope.messages.mapIndexed { index, message ->
                ConnectedNotificationRecord(
                    id =
                        recordId(
                            notificationKey = envelope.notificationKey,
                            timestampMillis = message.timestampMillis,
                            index = index,
                            senderLabel = message.senderLabel,
                            body = message.body,
                        ),
                    key = envelope.key,
                    notificationKey = envelope.notificationKey,
                    conversationId = conversationId,
                    conversationTitle = conversationTitle,
                    sourceLabel = sourceLabel,
                    senderLabel = message.senderLabel,
                    body = message.body,
                    timestampMillis = message.timestampMillis,
                    incoming = message.incoming,
                )
            }
        }

    private fun recordId(
        notificationKey: String,
        timestampMillis: Long,
        index: Int,
        senderLabel: String?,
        body: String?,
    ): String =
        buildString {
            append(notificationKey)
            append(':')
            append(timestampMillis)
            append(':')
            append(index)
            append(':')
            append(senderLabel.orEmpty().hashCode())
            append(':')
            append(body.orEmpty().hashCode())
        }
}

internal const val MAX_NOTIFICATION_MESSAGES = 25
internal const val MAX_NOTIFICATION_ACTIONS = 8
internal const val MAX_NOTIFICATION_REMOTE_INPUTS = 4
internal const val MAX_NOTIFICATION_LABEL_LENGTH = 256
internal const val MAX_NOTIFICATION_ID_LENGTH = 512
internal const val MAX_NOTIFICATION_BODY_LENGTH = 4096
