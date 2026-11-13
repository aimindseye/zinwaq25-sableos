package org.sableos.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class CalendarChromeTest {
    private val friday = LocalDate.of(2026, 11, 13)

    @Test
    fun `arrows step a day, a week or a month`() {
        assertEquals(LocalDate.of(2026, 11, 14), CalendarChrome.step(CalendarView.Agenda, friday, true))
        assertEquals(LocalDate.of(2026, 11, 12), CalendarChrome.step(CalendarView.Day, friday, false))
        assertEquals(LocalDate.of(2026, 11, 20), CalendarChrome.step(CalendarView.Week, friday, true))
        assertEquals(LocalDate.of(2026, 10, 13), CalendarChrome.step(CalendarView.Month, friday, false))
    }

    @Test
    fun `the strip is the seven days of the week from its first day`() {
        val sunday = CalendarChrome.weekStrip(friday, DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 11, 8), sunday.first())
        assertEquals(LocalDate.of(2026, 11, 14), sunday.last())
        val monday = CalendarChrome.weekStrip(LocalDate.of(2026, 11, 15), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 11, 9), monday.first())
        assertEquals(7, monday.size)
    }

    @Test
    fun `titles name the shown period`() {
        val us = Locale.US
        assertEquals("Today · Nov 13", CalendarChrome.title(CalendarView.Agenda, friday, friday, DayOfWeek.SUNDAY, "Today", us))
        assertEquals("Sat, Nov 14", CalendarChrome.title(CalendarView.Agenda, friday.plusDays(1), friday, DayOfWeek.SUNDAY, "Today", us))
        assertEquals("Fri, Nov 13", CalendarChrome.title(CalendarView.Day, friday, friday, DayOfWeek.SUNDAY, "Today", us))
        assertEquals("Nov 8 – Nov 14", CalendarChrome.title(CalendarView.Week, friday, friday, DayOfWeek.SUNDAY, "Today", us))
        assertEquals("November 2026", CalendarChrome.title(CalendarView.Month, friday, friday, DayOfWeek.SUNDAY, "Today", us))
    }

    @Test
    fun `each view selects its tab`() {
        assertEquals(CalendarChrome.Tab.Today, CalendarChrome.tabFor(CalendarView.Agenda))
        assertEquals(CalendarChrome.Tab.Month, CalendarChrome.tabFor(CalendarView.Month))
    }

    @Test
    fun `the week list puts each event on the days it covers`() {
        val newYork = ZoneId.of("America/New_York")
        fun at(
            day: Int,
            hour: Int,
        ) = LocalDate.of(2026, 11, day).atTime(hour, 0).atZone(newYork).toInstant().toEpochMilli()

        fun utc(day: Int) = LocalDate.of(2026, 11, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val veterans = CalendarEvent(1, "Veterans Day", utc(11), utc(12), allDay = true, calendarName = "Holidays")
        val lunch = CalendarEvent(2, "Lunch", at(11, 12), at(11, 13), allDay = false, calendarName = "Me")
        val late = CalendarEvent(3, "Late", at(10, 22), at(11, 0), allDay = false, calendarName = "Me")
        val trip = CalendarEvent(4, "Trip", at(12, 20), at(13, 9), allDay = false, calendarName = "Me")
        val events = listOf(trip, lunch, late, veterans)

        // An all-day event stays on its own date even west of UTC; it sorts first.
        assertEquals(listOf(veterans, lunch), CalendarChrome.eventsOn(LocalDate.of(2026, 11, 11), events, newYork))
        // Ending at midnight keeps an event off the next day.
        assertEquals(listOf(late), CalendarChrome.eventsOn(LocalDate.of(2026, 11, 10), events, newYork))
        // An overnight event is on both days.
        assertEquals(listOf(trip), CalendarChrome.eventsOn(LocalDate.of(2026, 11, 12), events, newYork))
        assertEquals(listOf(trip), CalendarChrome.eventsOn(LocalDate.of(2026, 11, 13), events, newYork))
        assertEquals(emptyList<CalendarEvent>(), CalendarChrome.eventsOn(LocalDate.of(2026, 11, 14), events, newYork))
    }
}
