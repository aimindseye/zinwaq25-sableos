package org.sableos.hub.notifications

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.sableos.hub.ConnectedAppKey
import org.sableos.hub.HubSendResult
import java.util.concurrent.ConcurrentHashMap

internal data class ConnectedReplyHandle(
    val notificationKey: String,
    val sourceKey: ConnectedAppKey,
    val conversationId: String,
    val pendingIntent: PendingIntent,
    val remoteInput: RemoteInput,
)

internal object ConnectedReplyRegistry {
    private val handles = ConcurrentHashMap<String, ConnectedReplyHandle>()

    fun register(handle: ConnectedReplyHandle?) {
        if (handle != null) {
            handles[handle.notificationKey] = handle
        }
    }

    fun remove(notificationKey: String) {
        handles.remove(notificationKey)
    }

    fun removeFor(key: ConnectedAppKey) {
        handles.entries.removeIf { entry ->
            entry.value.sourceKey == key
        }
    }

    fun canReply(notificationKey: String): Boolean = handles.containsKey(notificationKey)

    fun send(
        context: Context,
        notificationKey: String,
        body: String,
    ): HubSendResult {
        val text = body.trim()
        val handle = handles[notificationKey]

        return when {
            text.isEmpty() -> {
                HubSendResult(
                    success = false,
                    message = "Reply is empty.",
                )
            }

            handle == null -> {
                HubSendResult(
                    success = false,
                    message = "Quick reply is no longer available. Open the source app instead.",
                )
            }

            else -> {
                sendReply(
                    context = context,
                    notificationKey = notificationKey,
                    handle = handle,
                    text = text,
                )
            }
        }
    }

    private fun sendReply(
        context: Context,
        notificationKey: String,
        handle: ConnectedReplyHandle,
        text: String,
    ): HubSendResult =
        runCatching {
            val results =
                Bundle().apply {
                    putCharSequence(
                        handle.remoteInput.resultKey,
                        text,
                    )
                }
            val fillInIntent = Intent()
            RemoteInput.addResultsToIntent(
                arrayOf(handle.remoteInput),
                fillInIntent,
                results,
            )
            handle.pendingIntent.send(
                context,
                0,
                fillInIntent,
            )
            HubSendResult(
                success = true,
                message = "Reply handed to the source app.",
            )
        }.getOrElse { error ->
            remove(notificationKey)
            HubSendResult(
                success = false,
                message =
                    error.message
                        ?: "The source app no longer accepts quick reply.",
            )
        }
}
