package org.sableos.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
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
}
