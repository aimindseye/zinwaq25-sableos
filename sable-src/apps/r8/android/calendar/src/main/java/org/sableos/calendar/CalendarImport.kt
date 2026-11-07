package org.sableos.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.util.TimeZone

/**
 * Calendars that need no account: the "Phone" calendar kept on this device, and the events of an
 * .ics file added to it. Accounts (Google, Exchange, CalDAV and so on) are added in Android's own
 * add-account screen, which Calendar only opens.
 */
class CalendarImport(
    private val context: Context,
) {
    private val resolver get() = context.contentResolver

    private fun canWrite(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** The id of the Phone calendar, made on first use; null without calendar access. */
    fun phoneCalendarId(): Long? {
        if (!canWrite()) return null
        return existingPhoneCalendar() ?: createPhoneCalendar()
    }

    private fun existingPhoneCalendar(): Long? =
        runCatching {
            resolver
                .query(
                    CalendarContract.Calendars.CONTENT_URI,
                    arrayOf(CalendarContract.Calendars._ID),
                    CalendarContract.Calendars.ACCOUNT_TYPE + "=? AND " +
                        CalendarContract.Calendars.ACCOUNT_NAME + "=?",
                    arrayOf(CalendarContract.ACCOUNT_TYPE_LOCAL, PHONE_ACCOUNT),
                    null,
                )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
        }.getOrNull()

    private fun createPhoneCalendar(): Long? {
        val uri =
            CalendarContract.Calendars.CONTENT_URI
                .buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, PHONE_ACCOUNT)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                .build()
        val values =
            ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, PHONE_ACCOUNT)
                put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(CalendarContract.Calendars.NAME, PHONE_ACCOUNT)
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, PHONE_ACCOUNT)
                put(CalendarContract.Calendars.CALENDAR_COLOR, PHONE_COLOR)
                put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                put(CalendarContract.Calendars.OWNER_ACCOUNT, PHONE_ACCOUNT)
                put(CalendarContract.Calendars.VISIBLE, 1)
                put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
            }
        return runCatching { resolver.insert(uri, values)?.let(ContentUris::parseId) }.getOrNull()
    }

    /** Adds [events] to the Phone calendar; returns how many were stored (0 when nothing could be). */
    fun importEvents(events: List<IcsEvent>): Int {
        val calendarId = phoneCalendarId() ?: return 0
        var stored = 0
        events.take(MAX_EVENTS).forEach { event ->
            val inserted =
                runCatching { resolver.insert(CalendarContract.Events.CONTENT_URI, values(calendarId, event)) }
            if (inserted.getOrNull() != null) stored += 1
        }
        if (stored > 0) resolver.notifyChange(CalendarContract.Events.CONTENT_URI, null)
        return stored
    }

    private fun values(
        calendarId: Long,
        event: IcsEvent,
    ): ContentValues =
        ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.DTSTART, event.startMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, if (event.allDay) "UTC" else TimeZone.getDefault().id)
            if (event.allDay) put(CalendarContract.Events.ALL_DAY, 1)
            if (event.rrule.isNotEmpty()) {
                // A repeating event carries a DURATION, not a DTEND.
                put(CalendarContract.Events.RRULE, event.rrule)
                put(CalendarContract.Events.DURATION, durationOf(event))
            } else {
                put(CalendarContract.Events.DTEND, event.endMillis)
            }
            event.location.takeIf(String::isNotEmpty)?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
            event.description.takeIf(String::isNotEmpty)?.let { put(CalendarContract.Events.DESCRIPTION, it) }
        }

    private fun durationOf(event: IcsEvent): String {
        val seconds = (event.endMillis - event.startMillis) / MILLIS_PER_SECOND
        return if (event.allDay) "P${maxOf(1L, seconds / SECONDS_PER_DAY)}D" else "P${seconds}S"
    }

    private companion object {
        const val PHONE_ACCOUNT = "Phone"
        const val PHONE_COLOR = -0xd25a1f
        const val MAX_EVENTS = 2000
        const val MILLIS_PER_SECOND = 1000L
        const val SECONDS_PER_DAY = 86_400L
    }
}
