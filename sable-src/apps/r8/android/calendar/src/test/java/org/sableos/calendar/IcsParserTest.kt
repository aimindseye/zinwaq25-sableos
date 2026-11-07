package org.sableos.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class IcsParserTest {
    private val utc = ZoneId.of("UTC")

    private fun ics(vararg body: String) =
        ("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n" + body.joinToString("\r\n") + "\r\nEND:VCALENDAR\r\n")

    @Test
    fun readsAUtcEventWithEscapesAndFolding() {
        val text =
            ics(
                "BEGIN:VEVENT",
                "SUMMARY:Review\\, with PM",
                "DTSTART:20261106T090000Z",
                "DTEND:20261106T100000Z",
                "LOCATION:Room 4",
                "DESCRIPTION:Line one\\nLine",
                "  two",
                "END:VEVENT",
            )
        val event = IcsParser.parse(text, utc).single()
        assertEquals("Review, with PM", event.title)
        assertEquals(3_600_000L, event.endMillis - event.startMillis)
        assertEquals("Room 4", event.location)
        assertEquals("Line one\nLine two", event.description)
        assertEquals(false, event.allDay)
    }

    @Test
    fun readsAllDayEventsAsUtcMidnights() {
        val event =
            IcsParser
                .parse(ics("BEGIN:VEVENT", "SUMMARY:Birthday", "DTSTART;VALUE=DATE:20261121", "END:VEVENT"), utc)
                .single()
        assertTrue(event.allDay)
        assertEquals(86_400_000L, event.endMillis - event.startMillis)
    }

    @Test
    fun zonedStartsUseTheirZoneAndDurationSetsTheEnd() {
        val event =
            IcsParser
                .parse(
                    ics(
                        "BEGIN:VEVENT",
                        "SUMMARY:Call",
                        "DTSTART;TZID=America/New_York:20261106T090000",
                        "DURATION:PT1H30M",
                        "RRULE:FREQ=WEEKLY;BYDAY=FR",
                        "END:VEVENT",
                    ),
                    utc,
                ).single()
        // 09:00 in New York on 6 Nov 2026 is 14:00 UTC (standard time).
        assertEquals(
            java.time.Instant
                .parse("2026-11-06T14:00:00Z")
                .toEpochMilli(),
            event.startMillis,
        )
        assertEquals(90 * 60_000L, event.endMillis - event.startMillis)
        assertEquals("FREQ=WEEKLY;BYDAY=FR", event.rrule)
    }

    @Test
    fun anEventWithoutAUsableStartIsDropped() {
        val text =
            ics("BEGIN:VEVENT", "SUMMARY:No start", "END:VEVENT", "BEGIN:VEVENT", "DTSTART:nonsense", "END:VEVENT")
        assertTrue(IcsParser.parse(text, utc).isEmpty())
    }

    @Test
    fun durationsParse() {
        assertEquals(7 * 86_400_000L, IcsParser.duration("P1W"))
        assertEquals(86_400_000L + 3_600_000L, IcsParser.duration("P1DT1H"))
        assertEquals(0L, IcsParser.duration("junk"))
    }
}
