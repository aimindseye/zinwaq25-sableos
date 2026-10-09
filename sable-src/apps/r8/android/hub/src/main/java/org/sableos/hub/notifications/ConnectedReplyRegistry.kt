package org.sableos.hub.notifications

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.sableos.hub.ConnectedAppKey
import org.sableos.hub.HubSendResult
import org.sableos.hub.platform.PrivacyReader
import org.sableos.hub.platform.ProfileDirectory
import org.sableos.hub.policy.PrivacyContext
import org.sableos.hub.policy.PrivacyPosture
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

    fun clear() {
        handles.clear()
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

            requiresUnlock(context, handle) -> {
                HubSendResult(
                    success = false,
                    message = "Unlock the device and profile to reply.",
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

    /** Reply needs the device and the source's profile unlocked (DESIGN-KF-A "Privacy states"). */
    private fun requiresUnlock(
        context: Context,
        handle: ConnectedReplyHandle,
    ): Boolean {
        val profile = runCatching { ProfileDirectory(context).forSerial(handle.sourceKey.userSerial) }.getOrNull()
        return PrivacyPosture.requiresUnlockForActions(
            PrivacyContext(
                deviceLocked = PrivacyReader(context).deviceLocked(),
                profileLocked = profile?.locked ?: true,
            ),
        )
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
            // The user typed this text; tell the source it is not a generated/choice reply.
            RemoteInput.setResultsSource(fillInIntent, RemoteInput.SOURCE_FREE_FORM_INPUT)
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
