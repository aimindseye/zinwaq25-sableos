package org.sableos.tasks.core

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** The quick reminder times in the editor and on a focused row. */
enum class ReminderPreset { LaterToday, ThisEvening, TomorrowMorning }

/** List sections, in order. Overdue is past due (or past a reminder) and not done. */
enum class TaskGroup { Overdue, Today, Tomorrow, ThisWeek, Later, NoDate, Done }

/** Time-zone-aware scheduling, ported from TenSeven Tasks. Reminders are inexact, never exact alarms. */
object TaskSchedule {
    const val MORNING_HOUR = 9
    const val EVENING_HOUR = 18
    const val LATER_TODAY_HOURS = 2L
    const val SNOOZE_MILLIS = 60 * 60 * 1000L
    private const val MAX_STEPS = 4000
    private const val WEEK_DAYS = 7L

    fun nextOccurrence(
        epochMs: Long?,
        repeat: TaskRepeat,
        zoneName: String,
    ): Long? {
        if (epochMs == null || repeat == TaskRepeat.Never) return epochMs
        val at = Instant.ofEpochMilli(epochMs).atZone(ZoneId.of(zoneName))
        val shifted =
            when (repeat) {
                TaskRepeat.Daily -> at.plusDays(1)
                TaskRepeat.Weekly -> at.plusWeeks(1)
                TaskRepeat.Monthly -> at.plusMonths(1)
                TaskRepeat.Never -> at
            }
        return shifted.toInstant().toEpochMilli()
    }

    /** The first occurrence after [nowMs]; completing a future occurrence still moves to the next one. */
    fun advanceAfter(
        epochMs: Long?,
        repeat: TaskRepeat,
        zoneName: String,
        nowMs: Long,
    ): Long? {
        if (epochMs == null || repeat == TaskRepeat.Never) return epochMs
        var next = nextOccurrence(epochMs, repeat, zoneName)
        // Bounded, so a corrupt years-old record cannot loop for ever.
        var steps = 0
        while (next != null && next <= nowMs && steps < MAX_STEPS) {
            next = nextOccurrence(next, repeat, zoneName)
            steps++
        }
        return next
    }

    fun preset(
        preset: ReminderPreset,
        now: Instant,
        zone: ZoneId,
    ): Long {
        val local = now.atZone(zone)
        val at =
            when (preset) {
                ReminderPreset.LaterToday -> {
                    val later = local.plusHours(LATER_TODAY_HOURS)
                    if (later.toLocalDate() != local.toLocalDate()) {
                        tomorrowMorning(local, zone)
                    } else {
                        later
                    }
                }

                ReminderPreset.ThisEvening -> {
                    val today = local.toLocalDate().atTime(EVENING_HOUR, 0).atZone(zone)
                    if (today.toInstant() > now) today else today.plusDays(1)
                }

                ReminderPreset.TomorrowMorning -> {
                    tomorrowMorning(local, zone)
                }
            }
        return at.toInstant().toEpochMilli()
    }

    private fun tomorrowMorning(
        local: ZonedDateTime,
        zone: ZoneId,
    ): ZonedDateTime =
        local
            .toLocalDate()
            .plusDays(1)
            .atTime(MORNING_HOUR, 0)
            .atZone(zone)

    fun group(
        task: TaskRecord,
        now: Instant,
        zone: ZoneId,
    ): TaskGroup {
        // The due time, else the reminder, places a task; past it the task is overdue.
        val anchor = task.dueAtEpochMs ?: task.remindAtEpochMs
        return when {
            task.completed -> TaskGroup.Done
            anchor == null -> TaskGroup.NoDate
            anchor < now.toEpochMilli() -> TaskGroup.Overdue
            else -> dayGroup(anchor, now, zone)
        }
    }

    private fun dayGroup(
        anchor: Long,
        now: Instant,
        zone: ZoneId,
    ): TaskGroup {
        val date = Instant.ofEpochMilli(anchor).atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        return when {
            !date.isAfter(today) -> TaskGroup.Today
            date == today.plusDays(1) -> TaskGroup.Tomorrow
            !date.isAfter(today.plusDays(WEEK_DAYS)) -> TaskGroup.ThisWeek
            else -> TaskGroup.Later
        }
    }

    /** Sections in order, each sorted by its date (undated by last edit); done ones only when [withDone]. */
    fun sections(
        tasks: List<TaskRecord>,
        withDone: Boolean,
        now: Instant,
        zone: ZoneId,
    ): List<Pair<TaskGroup, List<TaskRecord>>> {
        val grouped = tasks.groupBy { group(it, now, zone) }
        return TaskGroup.entries
            .filter { it != TaskGroup.Done || withDone }
            .mapNotNull { section ->
                grouped[section]
                    ?.sortedWith(
                        compareBy<TaskRecord> { it.dueAtEpochMs ?: it.remindAtEpochMs ?: Long.MAX_VALUE }
                            .thenByDescending { it.priority }
                            .thenByDescending { it.updatedAtEpochMs },
                    )?.let { section to it }
            }
    }

    /** Tasks whose reminder has come and that are not done: what Start and the reminder line count. */
    fun dueReminders(
        tasks: List<TaskRecord>,
        nowMs: Long,
    ): List<TaskRecord> = tasks.filter { !it.completed && it.remindAtEpochMs != null && it.remindAtEpochMs <= nowMs }
}
