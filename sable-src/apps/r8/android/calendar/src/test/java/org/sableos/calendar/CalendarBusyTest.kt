package org.sableos.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset

class CalendarBusyTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 11, 4)

    private fun timed(
        id: Long,
        start: LocalDateTime,
        minutes: Long,
    ) = CalendarEvent(
        id = id,
        title = "E$id",
        startMillis = start.toInstant(zone).toEpochMilli(),
        endMillis = start.plusMinutes(minutes).toInstant(zone).toEpochMilli(),
        allDay = false,
        calendarName = "Work",
    )

    private fun allDay(
        id: Long,
        from: LocalDate,
        days: Long,
    ) = CalendarEvent(
        id = id,
        title = "A$id",
        startMillis = from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        endMillis = from.plusDays(days).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        allDay = true,
        calendarName = "Holidays",
    )

    @Test
    fun overlappingEventsCountOnce() {
        val events =
            listOf(
                timed(1, day.atTime(9, 0), 60),
                timed(2, day.atTime(9, 30), 60),
                timed(3, day.atTime(14, 0), 30),
            )
        val busy = CalendarBusy.days(day, day.plusDays(1), events, zone).getValue(day)
        assertEquals(120, busy.busyMinutes)
        assertEquals(3, busy.count)
        assertFalse(busy.allDay)
        assertEquals(120f / 720f, busy.fraction, 0.0001f)
    }

    @Test
    fun onlyTheWakingWindowCountsAndNightEventsStillShow() {
        val events = listOf(timed(1, day.atTime(6, 0), 180), timed(2, day.atTime(22, 0), 60))
        val busy = CalendarBusy.days(day, day.plusDays(1), events, zone).getValue(day)
        assertEquals(60, busy.busyMinutes)
        assertEquals(2, busy.count)
    }

    @Test
    fun multiDayEventsSplitAcrossDays() {
        val events = listOf(timed(1, day.atTime(18, 0), 18 * 60))
        val busy = CalendarBusy.days(day, day.plusDays(2), events, zone)
        assertEquals(120, busy.getValue(day).busyMinutes)
        assertEquals(240, busy.getValue(day.plusDays(1)).busyMinutes)
    }

    @Test
    fun allDayEventsMarkTheirDaysWithoutBookingThem() {
        val events = listOf(allDay(1, day, 2))
        val busy = CalendarBusy.days(day, day.plusDays(3), events, zone)
        assertTrue(busy.getValue(day).allDay)
        assertTrue(busy.getValue(day.plusDays(1)).allDay)
        assertEquals(0, busy.getValue(day).busyMinutes)
        assertNull(busy[day.plusDays(2)])
        assertEquals(0f, CalendarBusy.sweepDegrees(busy.getValue(day)))
    }

    @Test
    fun freeDaysHaveNoRingAndShortMeetingsStillShow() {
        assertEquals(0f, CalendarBusy.sweepDegrees(null))
        val short = CalendarBusy.days(day, day.plusDays(1), listOf(timed(1, day.atTime(10, 0), 15)), zone).getValue(day)
        assertEquals(CalendarBusy.MIN_SWEEP_DEGREES, CalendarBusy.sweepDegrees(short))
        val full = CalendarBusy.days(day, day.plusDays(1), listOf(timed(1, day.atTime(7, 0), 14 * 60)), zone).getValue(day)
        assertEquals(360f, CalendarBusy.sweepDegrees(full))
    }

    @Test
    fun monthCellsStartOnMondayAndFillWholeWeeks() {
        val cells = CalendarBusy.monthCells(YearMonth.of(2026, 11))
        assertEquals(0, cells.size % 7)
        assertEquals(6, cells.indexOf(LocalDate.of(2026, 11, 1)))
        assertEquals(LocalDate.of(2026, 11, 30), cells.filterNotNull().last())
        assertEquals(42, cells.size)
    }
}
