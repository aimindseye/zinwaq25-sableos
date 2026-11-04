package org.sableos.start.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class StartMonthTest {
    @Test
    fun ringsFollowBookedMinutesWithAFloor() {
        assertEquals(0f, StartMonth.sweepDegrees(0))
        assertEquals(StartMonth.MIN_SWEEP_DEGREES, StartMonth.sweepDegrees(10))
        assertEquals(180f, StartMonth.sweepDegrees(360))
        assertEquals(360f, StartMonth.sweepDegrees(2000))
    }

    @Test
    fun cellsStartOnMondayInWholeWeeks() {
        val cells = StartMonth.cells(YearMonth.of(2026, 11))
        assertEquals(42, cells.size)
        assertEquals(LocalDate.of(2026, 11, 1), cells[6])
        assertEquals(35, StartMonth.cells(YearMonth.of(2026, 12)).size)
    }

    @Test
    fun upcomingDaysSayTodayTomorrowOrTheWeekday() {
        val today = LocalDate.of(2026, 11, 4)
        val words = "Today" to "Tomorrow"
        val weekday: (LocalDate) -> String = { it.dayOfWeek.name }
        assertEquals("Today", StartMonth.dayWord(today, today, weekday, words))
        assertEquals("Tomorrow", StartMonth.dayWord(today.plusDays(1), today, weekday, words))
        assertEquals("FRIDAY", StartMonth.dayWord(today.plusDays(2), today, weekday, words))
    }

    @Test
    fun theMonthFrameGetsAWholeRow() {
        val rows = GlanceWidgets.rows(listOf("weather", "month", "battery", "media", "hub"), 2)
        assertEquals(listOf(listOf("weather"), listOf("month"), listOf("battery", "media"), listOf("hub")), rows)
        assertEquals(
            listOf(listOf("month"), listOf("weather", "battery"), listOf("media")),
            GlanceWidgets.rows(GlanceWidgets.DEFAULT, 2),
        )
    }
}
