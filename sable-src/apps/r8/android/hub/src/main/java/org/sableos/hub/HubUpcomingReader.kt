package org.sableos.hub

import android.content.Context
import android.net.Uri

/**
 * The next events from Sable Calendar's snapshot provider. Calendar answers only known callers;
 * a refusal or a missing Calendar is an empty list, never an error in Hub.
 */
class HubUpcomingReader(
    private val context: Context,
) {
    fun load(): List<HubUpcomingEvent> =
        try {
            context.contentResolver
                .query(UPCOMING_URI, null, null, null, null)
                ?.use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(
                                HubUpcomingEvent(
                                    title = cursor.getString(TITLE).orEmpty(),
                                    beginMillis = cursor.getLong(BEGIN),
                                    allDay = cursor.getInt(ALL_DAY) != 0,
                                    calendar = cursor.getString(CALENDAR).orEmpty(),
                                ),
                            )
                        }
                    }
                }.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }

    private companion object {
        val UPCOMING_URI: Uri = Uri.parse("content://org.sableos.calendar.snapshot/next")
        const val TITLE = 0
        const val BEGIN = 1
        const val ALL_DAY = 2
        const val CALENDAR = 3
    }
}
