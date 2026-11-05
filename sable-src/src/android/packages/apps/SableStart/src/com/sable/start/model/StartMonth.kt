package org.sableos.start.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * Start's Month widget (owner decision 2026-11-04): this month with Sable Calendar's busy rings and
 * the next events. Calendar's snapshot provider gives booked minutes per day between 08:00 and 20:00
 * from the calendars the user shows; this draws them. Pure policy, unit tested in
 * apps/r8/android/sablestart-presentation-check/src/test.
 */
object StartMonth {
    const val WINDOW_MINUTES = 12 * 60
    const val MIN_SWEEP_DEGREES = 24f
    const val DAYS_PER_WEEK = 7

    /** Arc of a day's ring in degrees: none when free, at least [MIN_SWEEP_DEGREES] when booked. */
    fun sweepDegrees(busyMinutes: Int): Float {
        if (busyMinutes <= 0) return 0f
        return maxOf(MIN_SWEEP_DEGREES, busyMinutes.coerceAtMost(WINDOW_MINUTES) * 360f / WINDOW_MINUTES)
    }

    /** Month grid cells, Monday first, padded with blanks to whole weeks. */
    fun cells(month: YearMonth): List<LocalDate?> {
        val leading = month.atDay(1).dayOfWeek.value - 1
        val cells = List<LocalDate?>(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
        return cells + List<LocalDate?>((DAYS_PER_WEEK - cells.size % DAYS_PER_WEEK) % DAYS_PER_WEEK) { null }
    }

    /** "Today", "Tomorrow" or the weekday for an upcoming event's day. */
    fun dayWord(
        day: LocalDate,
        today: LocalDate,
        weekday: (LocalDate) -> String,
        words: Pair<String, String>,
    ): String =
        when (day) {
            today -> words.first
            today.plusDays(1) -> words.second
            else -> weekday(day)
        }

    /**
     * Hub's Upcoming strip: the events that start today or tomorrow, in Calendar's order.
     * [dayOf] gives an event's local day (all-day events are stored at UTC midnight).
     */
    fun <T> todayAndTomorrow(events: List<T>, today: LocalDate, dayOf: (T) -> LocalDate): List<T> {
        val tomorrow = today.plusDays(1)
        return events.filter { dayOf(it).let { day -> day == today || day == tomorrow } }
    }
}
