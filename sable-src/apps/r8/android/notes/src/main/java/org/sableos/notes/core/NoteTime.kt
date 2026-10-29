package org.sableos.notes.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Short "when edited" labels: 14:05 today, Mon this week, 3 Oct this year, 3 Oct 2025 before. */
object NoteTime {
    private const val WEEK_DAYS = 6L

    fun label(
        at: Long,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val then = Instant.ofEpochMilli(at).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val day = then.toLocalDate()
        val pattern =
            when {
                day == today -> "HH:mm"
                !day.isAfter(today) && ChronoUnit.DAYS.between(day, today) <= WEEK_DAYS -> "EEE"
                day.year == today.year -> "d MMM"
                else -> "d MMM yyyy"
            }
        return then.format(DateTimeFormatter.ofPattern(pattern, locale))
    }
}
