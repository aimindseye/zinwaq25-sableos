package org.sableos.calendar

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class CalendarWeekTest {
    private val zone = ZoneOffset.UTC
    private val monday = LocalDate.of(2026, 9, 28)

    private fun event(
        id: Long,
        start: LocalDateTime,
        minutes: Long,
        allDay: Boolean = false,
    ) = CalendarEvent(
        id = id,
        title = "E$id",
        startMillis = start.toInstant(zone).toEpochMilli(),
        endMillis = start.plusMinutes(minutes).toInstant(zone).toEpochMilli(),
        allDay = allDay,
        calendarName = "Work",
    )

    @Test
    fun screenKeysAreCTVJKRAndNeverWithAlt() {
        val keys =
            mapOf(
                KeyEvent.KEYCODE_C to CalendarScreenKey.Create,
                KeyEvent.KEYCODE_T to CalendarScreenKey.Today,
                KeyEvent.KEYCODE_V to CalendarScreenKey.View,
                KeyEvent.KEYCODE_J to CalendarScreenKey.Next,
                KeyEvent.KEYCODE_K to CalendarScreenKey.Previous,
                KeyEvent.KEYCODE_R to CalendarScreenKey.Refresh,
            )
        keys.forEach { (code, key) ->
            assertEquals(key, CalendarScreenKeys.action(code, modified = false))
            // Alt+letter is a digit on the Q25: never a calendar command.
            assertNull(CalendarScreenKeys.action(code, modified = true))
        }
        assertNull(CalendarScreenKeys.action(KeyEvent.KEYCODE_E, modified = false))
        assertNull(CalendarScreenKeys.action(KeyEvent.KEYCODE_DPAD_DOWN, modified = false))
        // The screen keys never shadow the focused-element keys E / Del / Esc.
        keys.keys.forEach { assertNull(CalendarKeys.action(it, modified = false)) }
    }

    @Test
    fun dockNamesEveryScreenKey() {
        listOf("C create", "T today", "V view", "J/K event", "R refresh").forEach {
            assertTrue(it, CalendarScreenKeys.DOCK.contains(it))
        }
    }

    @Test
    fun viewKeyCyclesAgendaWeekMonth() {
        assertEquals(CalendarView.Week, CalendarView.Agenda.next())
        assertEquals(CalendarView.Month, CalendarView.Week.next())
        assertEquals(CalendarView.Agenda, CalendarView.Month.next())
    }

    @Test
    fun weekShowsFiveDaysHoldingTheSelection() {
        val wed = monday.plusDays(2)
        assertEquals((0L..4L).map { monday.plusDays(it) }, WeekLayout.days(wed))
        val sunday = monday.plusDays(6)
        val weekend = WeekLayout.days(sunday)
        assertEquals(5, weekend.size)
        assertEquals(sunday, weekend.last())
        assertEquals(monday.plusDays(2), weekend.first())
    }

    @Test
    fun hoursCoverTheWorkdayEventsAndNow() {
        val days = WeekLayout.days(monday)
        val notThisWeek = LocalDateTime.of(2026, 10, 20, 12, 0)
        assertEquals(8..18, WeekLayout.hours(days, emptyList(), notThisWeek, zone))
        val early = event(1, monday.atTime(6, 30), 60)
        val late = event(2, monday.plusDays(1).atTime(20, 0), 45)
        assertEquals(6..21, WeekLayout.hours(days, listOf(early, late), notThisWeek, zone))
        val lateNow = monday.plusDays(2).atTime(22, 10)
        assertEquals(8..23, WeekLayout.hours(days, emptyList(), lateNow, zone))
    }

    @Test
    fun blocksSitInTheirColumnAtTheirTime() {
        val days = WeekLayout.days(monday)
        val sync = event(1, monday.atTime(10, 0), 90)
        val review = event(2, monday.plusDays(2).atTime(10, 0), 60)
        val holiday = event(3, monday.plusDays(1).atStartOfDay(), 24 * 60, allDay = true)
        val blocks = WeekLayout.blocks(days, listOf(sync, review, holiday), 8..18, zone)
        assertEquals(2, blocks.size)
        val first = blocks.first { it.event == sync }
        assertEquals(0, first.column)
        assertEquals(0.2f, first.top, 0.001f)
        assertEquals(0.35f, first.bottom, 0.001f)
        assertEquals(2, blocks.first { it.event == review }.column)
    }

    @Test
    fun anOvernightEventIsCutAtMidnight() {
        val days = WeekLayout.days(monday)
        val overnight = event(1, monday.atTime(22, 0), 4 * 60)
        val blocks = WeekLayout.blocks(days, listOf(overnight), 0..24, zone)
        assertEquals(listOf(0, 1), blocks.map { it.column })
        assertEquals(1f, blocks[0].bottom, 0.001f)
        assertEquals(0f, blocks[1].top, 0.001f)
    }

    @Test
    fun nowLineOnlyWhenTodayAndTheHourAreShown() {
        val days = WeekLayout.days(monday)
        val now = monday.plusDays(2).atTime(12, 0)
        assertEquals(0.4f, WeekLayout.nowLine(days, 8..18, now)!!, 0.001f)
        assertNull(WeekLayout.nowLine(days, 8..18, LocalDateTime.of(2026, 10, 20, 12, 0)))
        assertNull(WeekLayout.nowLine(days, 8..11, now))
    }

    @Test
    fun weekTitleAndColumnsReadLikeTheDesign() {
        val days = WeekLayout.days(monday)
        assertEquals("Sep 2026 · Wk 40", WeekLayout.title(days))
        assertEquals("Mon 28", WeekLayout.columnLabel(monday))
    }

    @Test
    fun jAndKStepThroughEventsAndStopAtTheEnds() {
        val a = event(1, monday.atTime(9, 0), 30)
        val b = event(2, monday.atTime(11, 0), 30)
        val c = event(3, monday.atTime(13, 0), 30)
        val list = listOf(c, a, b)
        assertEquals(a, EventCursor.step(list, null, forward = true))
        assertEquals(c, EventCursor.step(list, null, forward = false))
        assertEquals(b, EventCursor.step(list, a, forward = true))
        assertEquals(c, EventCursor.step(list, c, forward = true))
        assertEquals(a, EventCursor.step(list, a, forward = false))
        assertNull(EventCursor.step(emptyList(), a, forward = true))
    }

    @Test
    fun theAgendaStartsOnTheCurrentOrNextEvent() {
        val a = event(1, monday.atTime(9, 0), 30)
        val b = event(2, monday.atTime(11, 0), 60)
        val at = { t: LocalDateTime -> t.toInstant(zone).toEpochMilli() }
        assertEquals(b, EventCursor.initial(listOf(a, b), at(monday.atTime(11, 30))))
        assertEquals(a, EventCursor.initial(listOf(a, b), at(monday.atTime(8, 0))))
        assertEquals(b, EventCursor.initial(listOf(a, b), at(monday.atTime(18, 0))))
        assertNull(EventCursor.initial(emptyList(), 0L))
    }
}
