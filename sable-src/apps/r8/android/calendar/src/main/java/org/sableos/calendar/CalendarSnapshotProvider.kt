package org.sableos.calendar

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import java.time.LocalDate
import java.time.YearMonth

class CalendarSnapshotProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val appContext = checkNotNull(context)
        enforceLauncherCaller(appContext)
        val repository = CalendarRepository(appContext)
        val cursor =
            when (uri.lastPathSegment) {
                PATH_MONTH -> monthCursor(repository)
                PATH_NEXT -> nextCursor(repository)
                else -> null
            }
        if (cursor != null) {
            cursor.setNotificationUri(appContext.contentResolver, SNAPSHOT_URI)
            return cursor
        }
        val snapshot = repository.snapshot()
        return MatrixCursor(COLUMNS).apply {
            addRow(
                arrayOf<Any?>(
                    snapshot.title,
                    snapshot.detail,
                    snapshot.availability,
                    snapshot.observedAt,
                ),
            )
            setNotificationUri(appContext.contentResolver, SNAPSHOT_URI)
        }
    }

    /** Start's month widget: one row per busy day of this month (shown calendars only). */
    private fun monthCursor(repository: CalendarRepository): Cursor =
        MatrixCursor(MONTH_COLUMNS).apply {
            val month = YearMonth.now()
            repository.busyMonth(month).values.forEach { day ->
                addRow(arrayOf<Any?>(day.date.toString(), day.busyMinutes, if (day.allDay) 1 else 0, day.count))
            }
        }

    /** Start's month widget: the next few events of the coming week (title, start, all day). */
    private fun nextCursor(repository: CalendarRepository): Cursor =
        MatrixCursor(NEXT_COLUMNS).apply {
            val now = System.currentTimeMillis()
            val today = LocalDate.now()
            repository
                .range(today, today.plusDays(NEXT_DAYS))
                .filter { it.endMillis > now }
                .take(MAX_NEXT)
                .forEach { event ->
                    addRow(arrayOf<Any?>(event.title, event.startMillis, if (event.allDay) 1 else 0, event.calendarName))
                }
        }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.org.sableos.calendar.snapshot"

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = throw UnsupportedOperationException("Read-only provider")

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Read-only provider")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Read-only provider")

    private fun enforceLauncherCaller(context: android.content.Context) {
        val callingUid = Binder.getCallingUid()
        if (callingUid == Process.myUid()) return
        val packages =
            context.packageManager
                .getPackagesForUid(callingUid)
                ?.toSet()
                .orEmpty()
        if ("org.sableos.launcher" !in packages) {
            throw SecurityException(
                "Sable live snapshot is restricted to SableLauncher.",
            )
        }
    }

    companion object {
        val SNAPSHOT_URI: Uri =
            Uri.parse("content://org.sableos.calendar.snapshot/current")
        private val COLUMNS =
            arrayOf("title", "detail", "availability", "observed_at")
        const val PATH_MONTH = "month"
        const val PATH_NEXT = "next"
        private val MONTH_COLUMNS = arrayOf("date", "busy_minutes", "all_day", "count")
        private val NEXT_COLUMNS = arrayOf("title", "begin", "all_day", "calendar")
        private const val NEXT_DAYS = 7L
        private const val MAX_NEXT = 3
    }
}
