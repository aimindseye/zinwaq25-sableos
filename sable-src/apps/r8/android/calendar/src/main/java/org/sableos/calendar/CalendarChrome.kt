package org.sableos.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * The browsing chrome, after TenSeven Launcher's Calendar (owner, 2026-11-12): a top bar whose
 * arrows step the shown period, a seven-day strip and Today · Week · Month · Day · + tabs. Pure,
 * so it is unit-tested off the phone.
 */
object CalendarChrome {
    /** The tabs along the bottom, in order; [New] starts a new event. */
    enum class Tab { Today, Week, Month, Day, New }

    /** The tab shown as selected for a browsing [view]; the agenda is Today's list. */
    fun tabFor(view: CalendarView): Tab =
        when (view) {
            CalendarView.Agenda -> Tab.Today
            CalendarView.Week -> Tab.Week
            CalendarView.Month -> Tab.Month
            CalendarView.Day -> Tab.Day
        }

    /** The date the top bar's arrows move to: a day, a week or a month. */
    fun step(
        view: CalendarView,
        date: LocalDate,
        forward: Boolean,
    ): LocalDate {
        val sign = if (forward) 1L else -1L
        return when (view) {
            CalendarView.Agenda, CalendarView.Day -> date.plusDays(sign)
            CalendarView.Week -> date.plusWeeks(sign)
            CalendarView.Month -> date.plusMonths(sign)
        }
    }

    /** The seven days of [date]'s week, starting on [firstDay]. */
    fun weekStrip(
        date: LocalDate,
        firstDay: DayOfWeek,
    ): List<LocalDate> {
        val start = date.with(TemporalAdjusters.previousOrSame(firstDay))
        return List(DAYS_PER_WEEK) { start.plusDays(it.toLong()) }
    }

    /** The top bar's title: "Today · Nov 13", "Fri, Nov 13", "Nov 8 – Nov 14" or "November 2026". */
    fun title(
        view: CalendarView,
        date: LocalDate,
        today: LocalDate,
        firstDay: DayOfWeek,
        todayLabel: String,
        locale: Locale = Locale.getDefault(),
    ): String {
        val short = DateTimeFormatter.ofPattern("MMM d", locale)
        return when (view) {
            CalendarView.Agenda -> {
                if (date == today) "$todayLabel · " + date.format(short) else date.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale))
            }

            CalendarView.Day -> {
                date.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale))
            }

            CalendarView.Week -> {
                val days = weekStrip(date, firstDay)
                days.first().format(short) + " – " + days.last().format(short)
            }

            CalendarView.Month -> {
                date.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) }
            }
        }
    }

    private const val DAYS_PER_WEEK = 7
}
