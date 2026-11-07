package org.sableos.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

class EventSearchTest {
    private fun event(
        id: Long,
        title: String,
        start: Long,
        calendar: String = "Phone",
    ) = CalendarEvent(id, title, start, start + 1, false, calendar)

    private val events =
        listOf(
            event(1, "Lunch with Jenn", 1_000),
            event(2, "Planning marketing research", 5_000),
            event(3, "Review with PM", 9_000, "Work"),
        )

    @Test
    fun everyWordMustMatchTitleOrCalendar() {
        assertEquals(listOf(3L), EventSearch.matches(events, "review work", 0).map { it.id })
        assertEquals(listOf(2L), EventSearch.matches(events, "MARKETING", 0).map { it.id })
    }

    @Test
    fun nearestToNowComesFirstAndBlankFindsNothing() {
        assertEquals(listOf(3L, 1L), EventSearch.matches(events, "with", 10_000).map { it.id })
        assertEquals(emptyList<CalendarEvent>(), EventSearch.matches(events, "  ", 0))
    }
}
