package org.sableos.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
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
)

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

    fun agenda(date: LocalDate): List<CalendarEvent> {
        if (!canRead()) return emptyList()

        val zone = ZoneId.systemDefault()
        val begin = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end =
            date
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
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
                                ),
                            )
                        }
                    }
            }
        }.getOrDefault(emptyList())
    }

    fun createEvent(
        title: String,
        startMillis: Long,
        endMillis: Long,
    ): Long? {
        if (!canWrite() || title.isBlank() || endMillis <= startMillis) return null
        val calendarId = firstWritableCalendarId() ?: return null

        val eventUri =
            resolver.insert(
                CalendarContract.Events.CONTENT_URI,
                ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(CalendarContract.Events.TITLE, title.trim())
                    put(CalendarContract.Events.DTSTART, startMillis)
                    put(CalendarContract.Events.DTEND, endMillis)
                    put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
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
}
