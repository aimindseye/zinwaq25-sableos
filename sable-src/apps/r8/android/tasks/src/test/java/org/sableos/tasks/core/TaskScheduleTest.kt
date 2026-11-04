package org.sableos.tasks.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TaskScheduleTest {
    private val ny = ZoneId.of("America/New_York")
    private val utc = ZoneId.of("UTC")

    private fun ms(instant: String): Long = Instant.parse(instant).toEpochMilli()

    @Test
    fun springForwardTomorrowMorningKeepsLocalTime() {
        val now = Instant.parse("2026-03-07T21:00:00Z")
        assertEquals(
            Instant.parse("2026-03-08T13:00:00Z").toEpochMilli(),
            TaskSchedule.preset(ReminderPreset.TomorrowMorning, now, ny),
        )
    }

    @Test
    fun dailyRepeatCrossesDstAndSkipsMissedDays() {
        val before = Instant.parse("2026-03-07T14:00:00Z").toEpochMilli()
        assertEquals(
            Instant.parse("2026-03-08T13:00:00Z").toEpochMilli(),
            TaskSchedule.nextOccurrence(before, TaskRepeat.Daily, ny.id),
        )
        val now = Instant.parse("2026-03-10T02:00:00Z").toEpochMilli()
        assertEquals(
            Instant.parse("2026-03-10T13:00:00Z").toEpochMilli(),
            TaskSchedule.advanceAfter(before, TaskRepeat.Daily, ny.id, now),
        )
    }

    @Test
    fun presetsHandleMidnightEveningAndMonthEnd() {
        val late = Instant.parse("2026-11-02T23:00:00Z")
        val morning = Instant.parse("2026-11-02T10:00:00Z")
        assertEquals(ms("2026-11-03T09:00:00Z"), TaskSchedule.preset(ReminderPreset.LaterToday, late, utc))
        assertEquals(ms("2026-11-02T12:00:00Z"), TaskSchedule.preset(ReminderPreset.LaterToday, morning, utc))
        assertEquals(ms("2026-11-02T18:00:00Z"), TaskSchedule.preset(ReminderPreset.ThisEvening, morning, utc))
        assertEquals(ms("2026-11-03T18:00:00Z"), TaskSchedule.preset(ReminderPreset.ThisEvening, late, utc))
        val jan = Instant.parse("2026-01-31T09:00:00Z").toEpochMilli()
        assertEquals(ms("2026-02-28T09:00:00Z"), TaskSchedule.nextOccurrence(jan, TaskRepeat.Monthly, "UTC"))
        assertEquals(jan, TaskSchedule.advanceAfter(jan, TaskRepeat.Never, "UTC", late.toEpochMilli()))
        assertNull(TaskSchedule.advanceAfter(null, TaskRepeat.Daily, "UTC", late.toEpochMilli()))
    }

    @Test
    fun groupsUseLocalDatesAndPastDueIsOverdue() {
        val now = Instant.parse("2026-11-02T16:00:00Z")

        fun due(hours: Long) = TaskRecord(title = "Dated", dueAtEpochMs = now.plusSeconds(hours * 3600).toEpochMilli())
        assertEquals(TaskGroup.Overdue, TaskSchedule.group(due(-1), now, ny))
        assertEquals(TaskGroup.Today, TaskSchedule.group(due(2), now, ny))
        assertEquals(TaskGroup.Tomorrow, TaskSchedule.group(due(24), now, ny))
        assertEquals(TaskGroup.ThisWeek, TaskSchedule.group(due(5 * 24), now, ny))
        assertEquals(TaskGroup.Later, TaskSchedule.group(due(9 * 24), now, ny))
        assertEquals(TaskGroup.NoDate, TaskSchedule.group(TaskRecord(title = "Undated"), now, ny))
        assertEquals(TaskGroup.Done, TaskSchedule.group(due(-1).copy(completed = true), now, ny))
        val remindOnly = TaskRecord(title = "Call", remindAtEpochMs = now.minusSeconds(60).toEpochMilli())
        assertEquals(TaskGroup.Overdue, TaskSchedule.group(remindOnly, now, ny))
    }

    @Test
    fun sectionsAreOrderedAndHideDoneUnlessAsked() {
        val now = Instant.parse("2026-11-02T16:00:00Z")
        val later = TaskRecord(title = "Later", dueAtEpochMs = now.plusSeconds(20 * 86_400).toEpochMilli())
        val soon = TaskRecord(title = "Soon", dueAtEpochMs = now.plusSeconds(3600).toEpochMilli())
        val done = TaskRecord(title = "Done", completed = true)
        val sections = TaskSchedule.sections(listOf(later, done, soon), withDone = false, now = now, zone = utc)
        assertEquals(listOf(TaskGroup.Today, TaskGroup.Later), sections.map { it.first })
        val all = TaskSchedule.sections(listOf(later, done, soon), withDone = true, now = now, zone = utc)
        assertEquals(TaskGroup.Done, all.last().first)
    }

    @Test
    fun sharedTextBecomesTitleAndDetails() {
        val task = TaskRecord.fromSharedText("  Buy milk\nsemi-skimmed, 2 l  ")!!
        assertEquals("Buy milk", task.title)
        assertEquals("semi-skimmed, 2 l", task.details)
        assertNull(TaskRecord.fromSharedText("   "))
    }
}
