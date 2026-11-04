package org.sableos.tasks.platform

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import org.sableos.tasks.MainActivity
import org.sableos.tasks.R
import org.sableos.tasks.core.TaskRecord
import org.sableos.tasks.core.TaskRepository
import org.sableos.tasks.core.TaskScheduler
import org.sableos.tasks.core.TaskStore
import org.sableos.tasks.core.TasksText
import org.sableos.tasks.core.TasksText.t

/** The app's one repository over its private files, with Android alarms as the scheduler. */
fun taskRepository(context: Context): TaskRepository {
    val app = context.applicationContext
    return TaskRepository(
        TaskStore(app.filesDir.resolve("tasks-v1")),
        object : TaskScheduler {
            override fun cancel(id: String) = TaskAlarms.cancel(app, id)

            override fun schedule(task: TaskRecord) = TaskAlarms.schedule(app, task)
        },
    )
}

/**
 * Inexact, idle-allowed wake-ups (setAndAllowWhileIdle): no SCHEDULE_EXACT_ALARM grant, so a reminder
 * may come a few minutes late in deep sleep. Overdue reminders always show in the app.
 */
object TaskAlarms {
    const val ACTION_DUE = "org.sableos.tasks.action.DUE"
    const val ACTION_DONE = "org.sableos.tasks.action.DONE"
    const val ACTION_SNOOZE = "org.sableos.tasks.action.SNOOZE"
    const val EXTRA_ID = "id"
    const val CHANNEL = "reminders"

    fun broadcast(
        context: Context,
        action: String,
        id: String,
    ): PendingIntent {
        val intent =
            Intent(context, TaskAlarmReceiver::class.java).apply {
                this.action = action
                data = Uri.parse("sable-task://" + action.substringAfterLast('.').lowercase() + "/" + id)
                putExtra(EXTRA_ID, id)
            }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun cancel(
        context: Context,
        id: String,
    ) {
        context.getSystemService(AlarmManager::class.java)?.cancel(broadcast(context, ACTION_DUE, id))
        context.getSystemService(NotificationManager::class.java)?.cancel(id, NOTIFICATION_ID)
    }

    fun schedule(
        context: Context,
        task: TaskRecord,
    ) {
        val at = task.remindAtEpochMs ?: return
        if (task.completed || at <= System.currentTimeMillis()) return
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, broadcast(context, ACTION_DUE, task.id))
    }

    fun notificationsAllowed(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * The reminder as a notification, so it also shows in Hub, whose row menu offers the two actions
     * here (Done, Snooze 1 hour) under the app's own names. The lock screen sees only "Task reminder".
     */
    fun notify(
        context: Context,
        task: TaskRecord,
    ) {
        if (!notificationsAllowed(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, t("reminders"), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val icon = Icon.createWithResource(context, R.drawable.ic_sable_tasks_monochrome)
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .setData(Uri.parse("sable-task://open/" + task.id))
                    .putExtra(MainActivity.EXTRA_TASK_ID, task.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val public =
            Notification
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_sable_tasks_monochrome)
                .setContentTitle(t("task_reminder"))
                .build()
        val notification =
            Notification
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_sable_tasks_monochrome)
                .setContentTitle(task.title)
                .setContentText(task.details.lineSequence().firstOrNull().orEmpty())
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(public)
                .setContentIntent(open)
                .setAutoCancel(true)
                .addAction(Notification.Action.Builder(icon, t("done"), broadcast(context, ACTION_DONE, task.id)).build())
                .addAction(
                    Notification.Action.Builder(icon, t("snooze_1_hour"), broadcast(context, ACTION_SNOOZE, task.id)).build(),
                ).build()
        manager.notify(task.id, NOTIFICATION_ID, notification)
    }

    private const val NOTIFICATION_ID = 1
}

/** Due reminders and the notification's Done and Snooze 1 hour; not exported. */
class TaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        TasksText.t.bindTo(context)
        val id = intent.getStringExtra(TaskAlarms.EXTRA_ID) ?: return
        val repository = taskRepository(context)
        val task = repository.find(id) ?: return
        when (intent.action) {
            TaskAlarms.ACTION_DUE -> {
                val at = task.remindAtEpochMs ?: return
                when {
                    task.completed -> Unit
                    at > System.currentTimeMillis() -> TaskAlarms.schedule(context, task)
                    else -> TaskAlarms.notify(context, task)
                }
            }

            TaskAlarms.ACTION_DONE -> {
                // Saving cancels this notification and sets the next reminder of a repeating task.
                if (!task.completed) repository.complete(task)
            }

            TaskAlarms.ACTION_SNOOZE -> {
                repository.snooze(task)
            }
        }
    }
}

/** After a restart or a clock or time zone change, every pending reminder is set again. */
class TaskBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> taskRepository(context).rescheduleAll()
        }
    }
}
