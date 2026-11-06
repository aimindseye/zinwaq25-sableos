package org.sableos.calendar

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** One VEVENT read from an .ics file, in Android Calendar's terms (all-day times are UTC midnights). */
data class IcsEvent(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val location: String = "",
    val description: String = "",
    /** The RRULE value ("FREQ=WEEKLY;BYDAY=MO"), empty for a single event. */
    val rrule: String = "",
)

/**
 * A small, forgiving reader for the iCalendar files people share (invitations, exports): VEVENT with
 * SUMMARY, DTSTART, DTEND or DURATION, LOCATION, DESCRIPTION and RRULE. Anything it does not
 * understand is skipped; an event without a usable start is dropped, never guessed.
 */
object IcsParser {
    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 86_400_000L
    private val DATE = DateTimeFormatter.BASIC_ISO_DATE
    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun parse(
        text: String,
        defaultZone: ZoneId = ZoneId.systemDefault(),
    ): List<IcsEvent> {
        val events = mutableListOf<IcsEvent>()
        var current: MutableMap<String, Pair<Map<String, String>, String>>? = null
        unfold(text).forEach { line ->
            when {
                line.equals("BEGIN:VEVENT", ignoreCase = true) -> {
                    current = mutableMapOf()
                }

                line.equals("END:VEVENT", ignoreCase = true) -> {
                    current?.let { toEvent(it, defaultZone)?.let(events::add) }
                    current = null
                }

                else -> {
                    current?.let { props -> property(line)?.let { (name, p) -> props.putIfAbsent(name, p) } }
                }
            }
        }
        return events
    }

    /** Folded lines continue on the next line that starts with a space or a tab. */
    private fun unfold(text: String): List<String> {
        val lines = mutableListOf<String>()
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { raw ->
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && lines.isNotEmpty()) {
                lines[lines.lastIndex] = lines.last() + raw.substring(1)
            } else {
                lines.add(raw)
            }
        }
        return lines.filter { it.isNotBlank() }
    }

    /** "DTSTART;TZID=Europe/London:2026..." becomes ("DTSTART", (TZID to ..., "2026...")). */
    private fun property(line: String): Pair<String, Pair<Map<String, String>, String>>? {
        val colon = line.indexOf(':')
        if (colon <= 0) return null
        val head = line.substring(0, colon).split(';')
        val params =
            head
                .drop(1)
                .mapNotNull { part ->
                    part.indexOf('=').takeIf { it > 0 }?.let {
                        part.substring(0, it).uppercase() to
                            part.substring(it + 1)
                    }
                }.toMap()
        return head[0].uppercase() to (params to line.substring(colon + 1))
    }

    private fun toEvent(
        props: Map<String, Pair<Map<String, String>, String>>,
        defaultZone: ZoneId,
    ): IcsEvent? {
        val (startParams, startValue) = props["DTSTART"] ?: return null
        val allDay = startParams["VALUE"].equals("DATE", ignoreCase = true) || startValue.length == DATE_ONLY_LENGTH
        val start = millis(startParams, startValue, allDay, defaultZone) ?: return null
        val end =
            props["DTEND"]?.let { (p, v) -> millis(p, v, allDay, defaultZone) }
                ?: props["DURATION"]?.let { start + duration(it.second) }
                ?: (start + if (allDay) DAY_MS else 0L)
        return IcsEvent(
            title = unescape(props["SUMMARY"]?.second.orEmpty()).ifBlank { "(no title)" },
            startMillis = start,
            endMillis = maxOf(end, start),
            allDay = allDay,
            location = unescape(props["LOCATION"]?.second.orEmpty()),
            description = unescape(props["DESCRIPTION"]?.second.orEmpty()),
            rrule = props["RRULE"]?.second.orEmpty(),
        )
    }

    private fun millis(
        params: Map<String, String>,
        value: String,
        allDay: Boolean,
        defaultZone: ZoneId,
    ): Long? =
        runCatching {
            if (allDay) {
                LocalDate
                    .parse(
                        value.take(DATE_ONLY_LENGTH),
                        DATE,
                    ).atStartOfDay()
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            } else if (value.endsWith("Z", ignoreCase = true)) {
                LocalDateTime.parse(value.dropLast(1), DATE_TIME).toInstant(ZoneOffset.UTC).toEpochMilli()
            } else {
                val zone = params["TZID"]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: defaultZone
                LocalDateTime
                    .parse(value, DATE_TIME)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
            }
        }.getOrNull()

    /** An RFC 5545 duration such as P1D, PT1H30M, P1W; zero when it cannot be read. */
    internal fun duration(value: String): Long {
        val match =
            Regex(
                "^([+-])?P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$",
            ).find(value.trim())
        if (match == null) return 0L
        val g = match.groupValues

        fun n(i: Int) = g[i].toLongOrNull() ?: 0L
        val total = n(2) * WEEK_DAYS * DAY_MS + n(3) * DAY_MS + n(4) * HOUR_MS + n(5) * MINUTE_MS + n(6) * SECOND_MS
        return if (g[1] == "-") -total else total
    }

    private fun unescape(value: String): String =
        value
            .replace("\\n", "\n")
            .replace("\\N", "\n")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")

    private const val DATE_ONLY_LENGTH = 8
    private const val WEEK_DAYS = 7L
    private const val SECOND_MS = 1000L
}
