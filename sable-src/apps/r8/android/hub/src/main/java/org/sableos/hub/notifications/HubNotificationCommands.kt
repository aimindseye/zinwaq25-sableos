package org.sableos.hub.notifications

import android.app.PendingIntent
import android.content.Context
import android.service.notification.NotificationListenerService
import android.util.Log
import org.sableos.hub.HubText.t
import org.sableos.hub.platform.PrivacyReader
import org.sableos.hub.policy.HubMenuEntry
import org.sableos.hub.policy.HubMessageMenu
import org.sableos.hub.policy.HubRowAction
import java.lang.ref.WeakReference

/**
 * The only place Hub acts on a notification, and only when the user picks Mark read, Snooze,
 * Dismiss or one of the source's own actions on one Hub row. It snoozes or dismisses that one
 * notification by key through Hub's own listener, or sends the source's own PendingIntent. Hub
 * still never posts, re-ranks or bulk-clears notifications, nor touches channels or Do Not
 * Disturb (DESIGN-KF-A, tests/kf-a-notification-policy-check.sh).
 */
object HubNotificationCommands {
    private const val TAG = "HubNotificationCommands"

    @Volatile
    private var listener: WeakReference<NotificationListenerService>? = null

    internal fun attach(service: NotificationListenerService) {
        listener = WeakReference(service)
    }

    internal fun detach(service: NotificationListenerService) {
        if (listener?.get() === service) listener = null
    }

    /** What Android exposes for the row's notification now. */
    fun facts(notificationKey: String) = ConnectedActionRegistry.facts(notificationKey)

    /**
     * Runs [entry] on the notification [notificationKey]; returns the status line to show, or
     * null for an entry that is not a notification command.
     */
    fun run(
        context: Context,
        notificationKey: String,
        entry: HubMenuEntry,
    ): String? {
        if (PrivacyReader(context).deviceLocked()) return t("unlock_the_device_and_profile")
        return when (entry) {
            is HubMenuEntry.App -> {
                send(context, notificationKey, entry.action.index)
            }

            is HubMenuEntry.Hub -> {
                when (entry.action) {
                    HubRowAction.MarkRead -> {
                        val index = facts(notificationKey).markReadIndex
                        if (index < 0) t("the_notification_is_gone") else send(context, notificationKey, index)
                    }

                    HubRowAction.Snooze -> {
                        onListener { it.snoozeNotification(notificationKey, HubMessageMenu.SNOOZE_MILLIS) }
                            ?.let { t("snoozed_for_an_hour") } ?: t("the_notification_is_gone")
                    }

                    HubRowAction.Dismiss -> {
                        onListener { it.cancelNotification(notificationKey) }
                            ?.let { t("notification_dismissed") } ?: t("the_notification_is_gone")
                    }

                    else -> {
                        null
                    }
                }
            }
        }
    }

    private fun send(
        context: Context,
        notificationKey: String,
        index: Int,
    ): String {
        val action = ConnectedActionRegistry.action(notificationKey, index) ?: return t("the_notification_is_gone")
        return try {
            action.pendingIntent.send(context, 0, null)
            t("sent_to_the_app")
        } catch (e: PendingIntent.CanceledException) {
            ConnectedActionRegistry.remove(notificationKey)
            t("the_notification_is_gone")
        }
    }

    private fun onListener(block: (NotificationListenerService) -> Unit): Unit? {
        val service = listener?.get() ?: return null
        return try {
            block(service)
        } catch (e: RuntimeException) {
            // SecurityException once the listener is unbound, or a key Android no longer has.
            Log.w(TAG, "notification command failed", e)
            null
        }
    }
}
