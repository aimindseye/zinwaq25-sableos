package org.sableos.calendar

import android.view.KeyEvent
import org.sableos.calendar.CalendarText.t
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Screen-wide calendar keys (design `sable-mail-calendar-titan2`: "[C Create] [T Today] [V View]
 * [J/K Event]", agenda dock "J K T R"). They act only while no text field has the keys: the
 * activity sees them after every focused view has declined them, and editors are excluded.
 */
enum class CalendarScreenKey {
    Create,
    Today,
    View,
    Next,
    Previous,
    Refresh,
}

object CalendarScreenKeys {
    /** C, T, V, J, K, R; anything with Alt/Ctrl held (a digit or shortcut on the Q25) is left alone. */
    fun action(
        keyCode: Int,
        modified: Boolean,
    ): CalendarScreenKey? =
        if (modified) {
            null
        } else {
            when (keyCode) {
                KeyEvent.KEYCODE_C -> CalendarScreenKey.Create
                KeyEvent.KEYCODE_T -> CalendarScreenKey.Today
                KeyEvent.KEYCODE_V -> CalendarScreenKey.View
                KeyEvent.KEYCODE_J -> CalendarScreenKey.Next
                KeyEvent.KEYCODE_K -> CalendarScreenKey.Previous
                KeyEvent.KEYCODE_R -> CalendarScreenKey.Refresh
                else -> null
            }
        }

    /** The hint dock under the agenda and week views; every key named here is mapped above. */
    val DOCK: String get() = t("c_create_t_today_v")
}

/** The browsing views V cycles through: agenda (list with detail), week (5 columns), month. */
enum class CalendarView {
    Agenda,
    Week,
    Month,
    ;

    fun next(): CalendarView = entries[(ordinal + 1) % entries.size]
}

/** J/K move between events in start order; at either end the selection stays put. */
object EventCursor {
    fun step(
        events: List<CalendarEvent>,
        current: CalendarEvent?,
        forward: Boolean,
    ): CalendarEvent? {
        if (events.isEmpty()) return null
        val ordered = events.sortedWith(compareBy({ it.startMillis }, { it.id }))
        val index = ordered.indexOfFirst { it.id == current?.id && it.startMillis == current.startMillis }
        val next =
            when {
                index < 0 -> if (forward) 0 else ordered.lastIndex
                forward -> minOf(index + 1, ordered.lastIndex)
                else -> maxOf(index - 1, 0)
            }
        return ordered[next]
    }

    /** The event a fresh agenda selects: the one in progress or next to start, else the last. */
    fun initial(
        events: List<CalendarEvent>,
        nowMillis: Long,
    ): CalendarEvent? =
        events.sortedBy { it.startMillis }.let { ordered ->
            ordered.firstOrNull { it.endMillis > nowMillis } ?: ordered.lastOrNull()
        }
}

/** One timed event in one day column, as fractions of the visible hour window. */
data class WeekBlock(
    val event: CalendarEvent,
    val column: Int,
    val top: Float,
    val bottom: Float,
)

/** Week view geometry, pure so it is JVM-tested. */
object WeekLayout {
    const val COLUMNS = 5
    private const val DAY_START_HOUR = 8
    private const val DAY_END_HOUR = 18
    private const val HOURS_PER_DAY = 24
    private const val MINUTES_PER_HOUR = 60f

    /**
     * Five days holding [selected]: Monday to Friday, or for a weekend day the five days ending on
     * it, so the selection is always visible.
     */
    fun days(selected: LocalDate): List<LocalDate> {
        val monday = selected.minusDays((selected.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
        val start = maxOf(monday, selected.minusDays((COLUMNS - 1).toLong()))
        return List(COLUMNS) { start.plusDays(it.toLong()) }
    }

    /** Hours shown: 08-18, widened to every timed event and to the current hour when today is shown. */
    fun hours(
        days: List<LocalDate>,
        events: List<CalendarEvent>,
        now: LocalDateTime,
        zone: ZoneId,
    ): IntRange {
        var first = DAY_START_HOUR
        var last = DAY_END_HOUR
        events.filterNot { it.allDay }.forEach { e ->
            val s = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.startMillis), zone)
            val f = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.endMillis), zone)
            first = minOf(first, if (s.toLocalDate() < days.first()) 0 else s.hour)
            val endHour = if (f.minute > 0 || f.second > 0) f.hour + 1 else f.hour
            last = maxOf(last, if (f.toLocalDate() > s.toLocalDate()) HOURS_PER_DAY else endHour)
        }
        if (now.toLocalDate() in days) {
            first = minOf(first, now.hour)
            last = maxOf(last, now.hour + 1)
        }
        return first.coerceIn(0, HOURS_PER_DAY - 1)..last.coerceIn(first + 1, HOURS_PER_DAY)
    }

    private fun fraction(
        time: LocalDateTime,
        day: LocalDate,
        hours: IntRange,
    ): Float {
        val minutes =
            when {
                time.toLocalDate() < day -> 0f
                time.toLocalDate() > day -> HOURS_PER_DAY * MINUTES_PER_HOUR
                else -> time.hour * MINUTES_PER_HOUR + time.minute
            }
        val span = (hours.last - hours.first) * MINUTES_PER_HOUR
        return ((minutes - hours.first * MINUTES_PER_HOUR) / span).coerceIn(0f, 1f)
    }

    /** Timed events cut per day column; all-day events are listed above the grid instead. */
    fun blocks(
        days: List<LocalDate>,
        events: List<CalendarEvent>,
        hours: IntRange,
        zone: ZoneId,
    ): List<WeekBlock> =
        events.filterNot { it.allDay }.flatMap { e ->
            val s = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.startMillis), zone)
            val f = LocalDateTime.ofInstant(Instant.ofEpochMilli(maxOf(e.endMillis, e.startMillis)), zone)
            days.mapIndexedNotNull { column, day ->
                val dayStart = day.atStartOfDay()
                val dayEnd = day.plusDays(1).atStartOfDay()
                val touches = (s < dayEnd && f > dayStart) || (s == f && s.toLocalDate() == day)
                if (touches) WeekBlock(e, column, fraction(s, day, hours), fraction(f, day, hours)) else null
            }
        }

    /** Where the NOW line sits (0 top, 1 bottom), or null when today or the time is not shown. */
    fun nowLine(
        days: List<LocalDate>,
        hours: IntRange,
        now: LocalDateTime,
    ): Float? =
        if (now.toLocalDate() !in days || now.hour < hours.first || now.hour >= hours.last) {
            null
        } else {
            fraction(now, now.toLocalDate(), hours)
        }

    /** "Sep 2026 · Wk 39" over the week grid (ISO week of the first column). */
    fun title(days: List<LocalDate>): String {
        val first = days.first()
        val month = abbreviation(first.month.name)
        val week = first.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        return t("wk", month, first.year, week)
    }

    /** "Mon 28" for a column header. */
    fun columnLabel(day: LocalDate): String = abbreviation(day.dayOfWeek.name) + " " + day.dayOfMonth

    private const val ABBREVIATION_LENGTH = 3

    private fun abbreviation(name: String): String =
        name.take(ABBREVIATION_LENGTH).lowercase().replaceFirstChar { it.uppercase() }
}
