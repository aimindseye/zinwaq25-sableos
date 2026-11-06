package org.sableos.phone.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.telecom.Call
import org.sableos.phone.R
import org.sableos.phone.core.PhoneText.t

/**
 * The call notifications: the incoming call (heads-up with Answer and Decline, full screen when locked or asleep),
 * the ongoing call (back to the call screen, Hang up) and missed calls. Telecom rings and vibrates itself, so the
 * channels are silent.
 */
object CallNotifications {
    private const val CHANNEL_INCOMING = "incoming"
    private const val CHANNEL_ONGOING = "ongoing"
    private const val CHANNEL_MISSED = "missed"
    private const val ID_CALL = 1
    private const val ID_MISSED = 2

    private fun manager(context: Context): NotificationManager {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                channel(
                    CHANNEL_INCOMING,
                    "channel_incoming",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                },
                channel(CHANNEL_ONGOING, "channel_ongoing", NotificationManager.IMPORTANCE_LOW),
                channel(CHANNEL_MISSED, "channel_missed", NotificationManager.IMPORTANCE_DEFAULT)
            )
        )
        return nm
    }

    private fun channel(id: String, name: String, importance: Int) =
        NotificationChannel(id, t(name), importance)

    /** Shows the notification for the current primary call, or removes it when there is none. */
    fun update(context: Context) {
        val call = CallBook.primary()?.takeIf { it.details.state != Call.STATE_DISCONNECTED }
        if (call == null) {
            cancelCall(context)
            return
        }
        val nm = manager(context)
        val ringing = call.details.state == Call.STATE_RINGING
        nm.notify(ID_CALL, if (ringing) incoming(context, call) else ongoing(context, call))
    }

    fun cancelCall(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID_CALL)
    }

    private fun incoming(context: Context, call: Call): Notification {
        val caller = Person.Builder().setName(
            CallInfo.name(context, call)
        ).setImportant(true).build()
        return Notification.Builder(context, CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_sable_phone_monochrome)
            .setContentText(CallInfo.subtitle(context, call).ifEmpty { t("incoming_call") })
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setFullScreenIntent(callScreen(context), true)
            .setContentIntent(callScreen(context))
            .setStyle(
                Notification.CallStyle.forIncomingCall(
                    caller,
                    action(context, CallActionReceiver.DECLINE),
                    action(context, CallActionReceiver.ANSWER)
                )
            )
            .build()
    }

    private fun ongoing(context: Context, call: Call): Notification {
        val connected = call.details.connectTimeMillis
        return Notification.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_sable_phone_monochrome)
            .setContentTitle(CallInfo.name(context, call))
            .setContentText(CallInfo.stateLabel(call).ifEmpty { t("ongoing_call") })
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(connected > 0 && call.details.state == Call.STATE_ACTIVE)
            .setWhen(if (connected > 0) connected else System.currentTimeMillis())
            .setShowWhen(connected > 0)
            .setContentIntent(callScreen(context))
            .addAction(
                Notification.Action.Builder(
                    null,
                    t("hang_up"),
                    action(context, CallActionReceiver.HANG_UP)
                ).build()
            )
            .build()
    }

    /** "Missed call" from [who], or "N missed calls"; [count] 0 removes it. */
    fun missed(context: Context, count: Int, who: String, number: String) {
        val nm = manager(context)
        if (count <= 0) {
            nm.cancel(ID_MISSED)
            return
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, PhoneActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = Notification.Builder(context, CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_sable_phone_monochrome)
            .setContentTitle(if (count == 1) t("missed_call") else t("missed_calls", count))
            .setContentText(who)
            .setCategory(Notification.CATEGORY_MISSED_CALL)
            .setNumber(count)
            .setAutoCancel(true)
            .setContentIntent(open)
        if (count == 1 && number.isNotEmpty()) {
            val callBack = PendingIntent.getBroadcast(
                context,
                1,
                Intent(context, CallActionReceiver::class.java)
                    .setAction(CallActionReceiver.CALL_BACK)
                    .putExtra(CallActionReceiver.EXTRA_NUMBER, number),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(Notification.Action.Builder(null, t("call_back"), callBack).build())
        }
        nm.notify(ID_MISSED, builder.build())
    }

    private fun callScreen(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, InCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun action(context: Context, what: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        what.hashCode(),
        Intent(context, CallActionReceiver::class.java).setAction(what),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
