package org.sableos.tasks.core

import java.security.MessageDigest

/** Where reminders are scheduled; Android's inexact alarms on the phone, a recorder in tests. */
interface TaskScheduler {
    fun cancel(id: String)

    fun schedule(task: TaskRecord)
}

/**
 * Tasks and their reminders, independent of any screen. Ported from TenSeven Tasks: the file is
 * written before alarms change, so a failed write never cancels a reminder that was set.
 */
class TaskRepository(
    private val store: TaskStore,
    private val scheduler: TaskScheduler,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun list(): List<TaskRecord> = store.list()

    fun find(id: String): TaskRecord? = list().firstOrNull { it.id == id }

    fun save(task: TaskRecord) {
        store.save(task)
        scheduler.cancel(task.id)
        scheduler.schedule(task)
    }

    fun delete(id: String) {
        scheduler.cancel(id)
        store.delete(id)
    }

    /** Done (or not done again). A repeating dated task moves to its next occurrence instead. */
    fun complete(task: TaskRecord): TaskRecord {
        val next =
            if (!task.completed && task.repeat != TaskRepeat.Never &&
                (task.dueAtEpochMs != null || task.remindAtEpochMs != null)
            ) {
                task.copy(
                    dueAtEpochMs = TaskSchedule.advanceAfter(task.dueAtEpochMs, task.repeat, task.zoneId, now()),
                    remindAtEpochMs = TaskSchedule.advanceAfter(task.remindAtEpochMs, task.repeat, task.zoneId, now()),
                    updatedAtEpochMs = now(),
                )
            } else {
                task.copy(completed = !task.completed, updatedAtEpochMs = now())
            }
        save(next)
        return next
    }

    /** Reminder again in an hour (the notification's Snooze 1 hour). */
    fun snooze(task: TaskRecord): TaskRecord {
        val next = task.copy(remindAtEpochMs = now() + TaskSchedule.SNOOZE_MILLIS, updatedAtEpochMs = now())
        save(next)
        return next
    }

    /**
     * A follow-up for one Hub notification, made only when the user asks. Keeps the source app's package
     * and a SHA-256 of package and key, never the key or the notification's text; asking again for the
     * same notification updates the open follow-up instead of adding another.
     */
    fun followUp(
        packageName: String,
        notificationKey: String,
        title: String,
        remindAt: Long?,
    ): TaskRecord {
        val token = digest(packageName + "\n" + notificationKey)
        val existing = list().firstOrNull { !it.completed && it.originToken == token }
        val task =
            existing?.copy(remindAtEpochMs = remindAt, updatedAtEpochMs = now())
                ?: TaskRecord(
                    title = title.take(TaskRecord.MAX_TITLE),
                    remindAtEpochMs = remindAt,
                    originPackage = packageName,
                    originToken = token,
                    updatedAtEpochMs = now(),
                )
        save(task)
        return task
    }

    fun rescheduleAll() = list().forEach { scheduler.schedule(it) }

    private fun digest(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and BYTE_MASK) }

    private companion object {
        const val BYTE_MASK = 0xFF
    }
}
