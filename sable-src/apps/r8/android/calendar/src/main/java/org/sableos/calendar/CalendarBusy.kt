package org.sableos.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/** How full one day is: booked minutes between [CalendarBusy.DAY_START_HOUR] and [CalendarBusy.DAY_END_HOUR]. */
data class BusyDay(
    val date: LocalDate,
    val busyMinutes: Int,
    val allDay: Boolean,
    val count: Int,
) {
    val fraction: Float get() = (busyMinutes.toFloat() / CalendarBusy.WINDOW_MINUTES).coerceIn(0f, 1f)
}

/**
 * Busy rings (owner decision 2026-11-04): each day of the month carries a ring whose arc is the share
 * of its 08:00–20:00 that events fill, overlaps counted once. An all-day event draws a thin full
 * outer ring instead, so a holiday does not read as a booked day. Pure, so it is unit-tested.
 */
object CalendarBusy {
    const val DAY_START_HOUR = 8
    const val DAY_END_HOUR = 20
    const val WINDOW_MINUTES = (DAY_END_HOUR - DAY_START_HOUR) * 60

    /** A day with any event shows at least this much arc, so one short meeting is still visible. */
    const val MIN_SWEEP_DEGREES = 24f

    fun days(
        first: LocalDate,
        endExclusive: LocalDate,
        events: List<CalendarEvent>,
        zone: ZoneId,
    ): Map<LocalDate, BusyDay> {
        val result = LinkedHashMap<LocalDate, BusyDay>()
        var date = first
        while (date.isBefore(endExclusive)) {
            day(date, events, zone)?.let { result[date] = it }
            date = date.plusDays(1)
        }
        return result
    }

    private fun day(
        date: LocalDate,
        events: List<CalendarEvent>,
        zone: ZoneId,
    ): BusyDay? {
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val windowStart = date.atTime(DAY_START_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
        val windowEnd = date.atTime(DAY_END_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
        var count = 0
        var allDay = false
        val spans = mutableListOf<LongRange>()
        events.forEach { event ->
            if (event.allDay) {
                // The provider stores all-day events as UTC midnights.
                val from = utcDate(event.startMillis)
                val until = utcDate(event.endMillis).let { if (it == from) it.plusDays(1) else it }
                if (!date.isBefore(from) && date.isBefore(until)) {
                    count += 1
                    allDay = true
                }
            } else if (event.startMillis < dayEnd && event.endMillis > dayStart) {
                count += 1
                val start = maxOf(event.startMillis, windowStart)
                val end = minOf(event.endMillis, windowEnd)
                if (end > start) spans += start until end
            }
        }
        if (count == 0) return null
        return BusyDay(date = date, busyMinutes = (union(spans) / 60_000L).toInt(), allDay = allDay, count = count)
    }

    private fun union(spans: List<LongRange>): Long {
        var total = 0L
        var currentStart = Long.MIN_VALUE
        var currentEnd = Long.MIN_VALUE
        spans.sortedBy { it.first }.forEach { span ->
            val end = span.last + 1
            if (span.first > currentEnd) {
                if (currentEnd > currentStart) total += currentEnd - currentStart
                currentStart = span.first
                currentEnd = end
            } else if (end > currentEnd) {
                currentEnd = end
            }
        }
        if (currentEnd > currentStart) total += currentEnd - currentStart
        return total
    }

    private fun utcDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    /** Arc of the ring in degrees: none for a free day, at least [MIN_SWEEP_DEGREES] for any timed event. */
    fun sweepDegrees(day: BusyDay?): Float {
        if (day == null || day.busyMinutes == 0) return 0f
        return maxOf(MIN_SWEEP_DEGREES, day.fraction * 360f)
    }

    /** Month grid cells, Monday first: leading blanks, the days, trailing blanks to a whole week. */
    fun monthCells(month: YearMonth): List<LocalDate?> {
        val leading = month.atDay(1).dayOfWeek.value - 1
        val days = (1..month.lengthOfMonth()).map(month::atDay)
        val cells = List<LocalDate?>(leading) { null } + days
        val trailing = (7 - cells.size % 7) % 7
        return cells + List<LocalDate?>(trailing) { null }
    }
}
