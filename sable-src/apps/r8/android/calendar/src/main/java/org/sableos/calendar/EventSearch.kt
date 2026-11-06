package org.sableos.calendar

import java.time.LocalDate

/** Search over events by title or calendar name, nearest to today first. Pure, so it is tested. */
object EventSearch {
    /** How far either side of today a search looks. */
    const val SPAN_DAYS = 366L

    fun matches(
        events: List<CalendarEvent>,
        query: String,
        nowMillis: Long,
    ): List<CalendarEvent> {
        val words =
            query
                .trim()
                .lowercase()
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return events
            .filter { event ->
                val haystack = (event.title + " " + event.calendarName).lowercase()
                words.all { it in haystack }
            }.sortedBy { kotlin.math.abs(it.startMillis - nowMillis) }
    }

    fun first(today: LocalDate): LocalDate = today.minusDays(SPAN_DAYS)

    fun endExclusive(today: LocalDate): LocalDate = today.plusDays(SPAN_DAYS)
}
