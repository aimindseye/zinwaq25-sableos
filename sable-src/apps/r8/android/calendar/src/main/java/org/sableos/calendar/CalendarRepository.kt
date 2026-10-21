package org.sableos.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class CalendarEvent(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val calendarName: String,
    val recurring: Boolean = false,
    val canModify: Boolean = false,
)

data class CalendarAttendee(
    val name: String,
    val email: String,
    val status: String,
)

/** One event as the detail view shows it: the opened occurrence's times plus the stored fields. */
data class CalendarEventDetail(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val calendarName: String,
    val location: String,
    val description: String,
    val organizer: String,
    val recurring: Boolean,
    val canModify: Boolean,
    val attendees: List<CalendarAttendee>,
) {
    val timesEditable: Boolean
        get() = EventEditPolicy.timesEditable(allDay = allDay, recurring = recurring)

    fun toDraft(): EventDraft =
        EventDraft(
            title = title,
            startMillis = startMillis,
            endMillis = endMillis,
            location = location,
            description = description,
        )
}

data class CalendarSnapshot(
    val title: String,
    val detail: String,
    val availability: String,
    val observedAt: Long,
)

class CalendarRepository(
    private val context: Context,
) {
    private val resolver = context.contentResolver

    fun canRead(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun canWrite(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun agenda(date: LocalDate): List<CalendarEvent> = range(date, date.plusDays(1))

    /** Event instances from the start of [first] up to the start of [endExclusive], by start time. */
    fun range(
        first: LocalDate,
        endExclusive: LocalDate,
    ): List<CalendarEvent> {
        if (!canRead()) return emptyList()

        val zone = ZoneId.systemDefault()
        val begin = first.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, begin)
        ContentUris.appendId(builder, end)

        val projection =
            arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                CalendarContract.Instances.RRULE,
                CalendarContract.Instances.CALENDAR_ACCESS_LEVEL,
            )

        return runCatching {
            buildList {
                resolver
                    .query(
                        builder.build(),
                        projection,
                        null,
                        null,
                        CalendarContract.Instances.BEGIN + " ASC",
                    )?.use { cursor ->
                        val id = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                        val title = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                        val start = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                        val finish = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                        val allDay = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                        val calendar =
                            cursor.getColumnIndexOrThrow(
                                CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                            )
                        val rrule = cursor.getColumnIndexOrThrow(CalendarContract.Instances.RRULE)
                        val access = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ACCESS_LEVEL)

                        while (cursor.moveToNext()) {
                            add(
                                CalendarEvent(
                                    id = cursor.getLong(id),
                                    title =
                                        cursor
                                            .getString(title)
                                            ?.trim()
                                            .orEmpty()
                                            .ifEmpty { "Calendar event" },
                                    startMillis = cursor.getLong(start),
                                    endMillis = cursor.getLong(finish),
                                    allDay = cursor.getInt(allDay) != 0,
                                    calendarName =
                                        cursor
                                            .getString(calendar)
                                            ?.trim()
                                            .orEmpty()
                                            .ifEmpty { "Calendar" },
                                    recurring = !cursor.getString(rrule).isNullOrBlank(),
                                    canModify =
                                        cursor.getInt(access) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                                ),
                            )
                        }
                    }
            }
        }.getOrDefault(emptyList())
    }

    fun createEvent(draft: EventDraft): Long? {
        if (!canWrite() || EventEditPolicy.validate(draft) != null) return null
        val calendarId = firstWritableCalendarId() ?: return null

        val eventUri =
            resolver.insert(
                CalendarContract.Events.CONTENT_URI,
                ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(CalendarContract.Events.TITLE, draft.title.trim())
                    put(CalendarContract.Events.DTSTART, draft.startMillis)
                    put(CalendarContract.Events.DTEND, draft.endMillis)
                    put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
                    draft.location.trim().takeIf(String::isNotEmpty)?.let {
                        put(CalendarContract.Events.EVENT_LOCATION, it)
                    }
                    draft.description.trim().takeIf(String::isNotEmpty)?.let {
                        put(CalendarContract.Events.DESCRIPTION, it)
                    }
                },
            ) ?: return null

        val eventId = ContentUris.parseId(eventUri)
        runCatching {
            resolver.insert(
                CalendarContract.Reminders.CONTENT_URI,
                ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, 15)
                    put(
                        CalendarContract.Reminders.METHOD,
                        CalendarContract.Reminders.METHOD_ALERT,
                    )
                },
            )
        }
        resolver.notifyChange(CalendarContract.Events.CONTENT_URI, null)
        return eventId
    }

    /**
     * The event behind an agenda row. [occurrence] supplies the opened occurrence's times, which
     * differ from the series' DTSTART for a repeating event.
     */
    fun event(occurrence: CalendarEvent): CalendarEventDetail? {
        if (!canRead()) return null
        return runCatching {
            resolver
                .query(eventUri(occurrence.id), EVENT_PROJECTION, null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) readDetail(cursor, occurrence) else null
                }
        }.getOrNull()?.copy(attendees = attendees(occurrence.id))
    }

    private fun readDetail(
        cursor: Cursor,
        occurrence: CalendarEvent,
    ): CalendarEventDetail {
        fun text(column: String): String = cursor.getString(cursor.getColumnIndexOrThrow(column))?.trim().orEmpty()

        fun number(column: String): Int = cursor.getInt(cursor.getColumnIndexOrThrow(column))
        return CalendarEventDetail(
            id = occurrence.id,
            title = text(CalendarContract.Events.TITLE).ifEmpty { "Calendar event" },
            startMillis = occurrence.startMillis,
            endMillis = occurrence.endMillis,
            allDay = number(CalendarContract.Events.ALL_DAY) != 0,
            calendarName = text(CalendarContract.Events.CALENDAR_DISPLAY_NAME).ifEmpty { "Calendar" },
            location = text(CalendarContract.Events.EVENT_LOCATION),
            description = text(CalendarContract.Events.DESCRIPTION),
            organizer = text(CalendarContract.Events.ORGANIZER),
            recurring = text(CalendarContract.Events.RRULE).isNotEmpty(),
            canModify =
                canWrite() &&
                    number(CalendarContract.Events.CALENDAR_ACCESS_LEVEL) >=
                    CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
            attendees = emptyList(),
        )
    }

    private fun attendees(eventId: Long): List<CalendarAttendee> =
        runCatching {
            CalendarContract.Attendees
                .query(resolver, eventId, ATTENDEE_PROJECTION)
                ?.use { cursor ->
                    buildList {
                        while (cursor.moveToNext() && size < MAX_ATTENDEES) {
                            val name = cursor.getString(0)?.trim().orEmpty()
                            val email = cursor.getString(1)?.trim().orEmpty()
                            if (name.isNotEmpty() || email.isNotEmpty()) {
                                add(CalendarAttendee(name, email, attendeeStatus(cursor.getInt(2))))
                            }
                        }
                    }
                }.orEmpty()
        }.getOrDefault(emptyList())

    private fun attendeeStatus(status: Int): String =
        when (status) {
            CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED -> "accepted"
            CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED -> "declined"
            CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE -> "maybe"
            CalendarContract.Attendees.ATTENDEE_STATUS_INVITED -> "invited"
            else -> ""
        }

    /** Writes only the fields the user changed; the account's sync adapter uploads them. */
    fun updateEvent(
        original: CalendarEventDetail,
        draft: EventDraft,
    ): Boolean {
        if (!canWrite() || !original.canModify || EventEditPolicy.validate(draft) != null) return false
        val changed = EventEditPolicy.changedFields(original.toDraft(), draft, original.timesEditable)
        // Nothing changed is a successful save.
        return changed.isEmpty() ||
            runCatching {
                resolver.update(eventUri(original.id), changedValues(changed, draft), null, null) > 0
            }.getOrDefault(false)
    }

    private fun changedValues(
        changed: Set<EventField>,
        draft: EventDraft,
    ): ContentValues =
        ContentValues().apply {
            changed.forEach { field ->
                when (field) {
                    EventField.Title -> put(CalendarContract.Events.TITLE, draft.title.trim())
                    EventField.Location -> put(CalendarContract.Events.EVENT_LOCATION, draft.location.trim())
                    EventField.Description -> put(CalendarContract.Events.DESCRIPTION, draft.description.trim())
                    EventField.Start -> put(CalendarContract.Events.DTSTART, draft.startMillis)
                    EventField.End -> put(CalendarContract.Events.DTEND, draft.endMillis)
                }
            }
            if (EventField.Start in changed || EventField.End in changed) {
                put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
            }
        }

    /** Deletes the event (every occurrence of a repeating one). Callers confirm first. */
    fun deleteEvent(eventId: Long): Boolean {
        if (!canWrite()) return false
        return runCatching { resolver.delete(eventUri(eventId), null, null) > 0 }.getOrDefault(false)
    }

    private fun eventUri(eventId: Long): Uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)

    fun snapshot(nowMillis: Long = System.currentTimeMillis()): CalendarSnapshot {
        if (!canRead()) {
            return CalendarSnapshot(
                title = "Calendar",
                detail = "permission required",
                availability = "PERMISSION_REQUIRED",
                observedAt = nowMillis,
            )
        }

        val today =
            Instant
                .ofEpochMilli(nowMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        val next = agenda(today).firstOrNull { event -> event.endMillis > nowMillis }

        return if (next == null) {
            CalendarSnapshot(
                title = "Calendar",
                detail = "nothing else today",
                availability = "EMPTY",
                observedAt = nowMillis,
            )
        } else {
            val time =
                if (next.allDay) {
                    "all day"
                } else {
                    android.text.format.DateFormat
                        .getTimeFormat(context)
                        .format(java.util.Date(next.startMillis))
                }
            CalendarSnapshot(
                title = "Calendar",
                detail = time + " · " + next.title,
                availability = "LIVE",
                observedAt = nowMillis,
            )
        }
    }

    private fun firstWritableCalendarId(): Long? {
        if (!canWrite()) return null
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection =
            CalendarContract.Calendars.VISIBLE + "=1 AND " +
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + ">=?"
        val args =
            arrayOf(
                CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString(),
            )

        return runCatching {
            resolver
                .query(
                    CalendarContract.Calendars.CONTENT_URI,
                    projection,
                    selection,
                    args,
                    CalendarContract.Calendars._ID + " ASC",
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getLong(
                            cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID),
                        )
                    } else {
                        null
                    }
                }
        }.getOrNull()
    }

    private companion object {
        const val MAX_ATTENDEES = 50
        val EVENT_PROJECTION =
            arrayOf(
                CalendarContract.Events.TITLE,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.CALENDAR_DISPLAY_NAME,
                CalendarContract.Events.ORGANIZER,
                CalendarContract.Events.RRULE,
                CalendarContract.Events.CALENDAR_ACCESS_LEVEL,
            )
        val ATTENDEE_PROJECTION =
            arrayOf(
                CalendarContract.Attendees.ATTENDEE_NAME,
                CalendarContract.Attendees.ATTENDEE_EMAIL,
                CalendarContract.Attendees.ATTENDEE_STATUS,
            )
    }
}
