package org.sableos.messages.receive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.provider.Telephony
import org.sableos.messages.MainActivity
import org.sableos.messages.R
import org.sableos.messages.core.IncomingNotice
import org.sableos.messages.data.ContactNames
import org.sableos.messages.data.MessagesRepository
import org.sableos.messages.data.finishInBackground

/** The thread the user is looking at, so a text arriving in it does not also ring a notification. */
object ForegroundThread {
    @Volatile
    var threadId: Long = NONE

    const val NONE = -1L
}

/**
 * New-message notifications, one per thread. The lock screen gets the public version (sender and
 * body hidden) unless the user allowed sensitive content (VISIBILITY_PRIVATE).
 */
object MessageNotifier {
    private const val CHANNEL_ID = "incoming_messages"
    private const val TAG_THREAD = "thread"

    fun notifyIncoming(
        context: Context,
        threadId: Long,
        address: String,
        body: String,
    ) {
        if (threadId == ForegroundThread.threadId) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context, manager)
        val unread = unreadCount(context, threadId).coerceAtLeast(1)
        val title = IncomingNotice.title(ContactNames(context).nameFor(address), address)
        val open = openThreadIntent(context, threadId)
        val publicVersion =
            Notification
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sable_messages_monochrome)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(IncomingNotice.publicText(unread))
                .setContentIntent(open)
                .build()
        val notification =
            Notification
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sable_messages_monochrome)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setNumber(unread)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setContentIntent(open)
                .addAction(markReadAction(context, threadId))
                .build()
        manager.notify(TAG_THREAD, threadId.toInt(), notification)
    }

    fun cancel(
        context: Context,
        threadId: Long,
    ) {
        context.getSystemService(NotificationManager::class.java).cancel(TAG_THREAD, threadId.toInt())
    }

    private fun ensureChannel(
        context: Context,
        manager: NotificationManager,
    ) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_incoming),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                enableLights(true)
                enableVibration(true)
            }
        manager.createNotificationChannel(channel)
    }

    private fun unreadCount(
        context: Context,
        threadId: Long,
    ): Int =
        runCatching {
            context.contentResolver
                .query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms._ID),
                    "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                    arrayOf(threadId.toString()),
                    null,
                )?.use { it.count } ?: 0
        }.getOrDefault(0)

    private fun openThreadIntent(
        context: Context,
        threadId: Long,
    ): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_THREAD)
                .putExtra(MainActivity.EXTRA_THREAD_ID, threadId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            threadId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun markReadAction(
        context: Context,
        threadId: Long,
    ): Notification.Action {
        val intent =
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(NotificationActionReceiver.ACTION_MARK_READ)
                .putExtra(MainActivity.EXTRA_THREAD_ID, threadId)
        val pending =
            PendingIntent.getBroadcast(
                context,
                threadId.toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return Notification.Action
            .Builder(
                Icon.createWithResource(context, R.drawable.ic_sable_messages_monochrome),
                context.getString(R.string.action_mark_read),
                pending,
            ).build()
    }
}

/** "Mark read" on a new-message notification. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_MARK_READ) return
        val threadId = intent.getLongExtra(MainActivity.EXTRA_THREAD_ID, ForegroundThread.NONE)
        if (threadId == ForegroundThread.NONE) return
        MessageNotifier.cancel(context, threadId)
        finishInBackground { MessagesRepository(context).markRead(threadId) }
    }

    companion object {
        const val ACTION_MARK_READ = "org.sableos.messages.action.MARK_READ"
    }
}
