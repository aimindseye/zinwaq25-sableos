package org.sableos.weather

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle

class WeatherSnapshotProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        if (!isAllowedCaller(WeatherSetupContract.SNAPSHOT_CALLERS)) {
            return null
        }

        if (
            uri.authority != WeatherSnapshotContract.AUTHORITY ||
            uri.pathSegments != listOf(WeatherSnapshotContract.PATH_CURRENT)
        ) {
            return null
        }

        val appContext = context?.applicationContext ?: return null
        val state = WeatherRepository(appContext).load()
        val snapshot = state.snapshot
        // Start reads this on every home screen visit: fetch in the background when nothing current is cached.
        // A scheduling failure must not hide the cached forecast from Start.
        runCatching {
            WeatherRefreshJob.ensureScheduled(
                appContext,
                needsFetch = snapshot == null || state.availability != WeatherAvailability.Live,
            )
        }

        val cursor =
            MatrixCursor(
                arrayOf(
                    WeatherSnapshotContract.COLUMN_TITLE,
                    WeatherSnapshotContract.COLUMN_DETAIL,
                    WeatherSnapshotContract.COLUMN_AVAILABILITY,
                    WeatherSnapshotContract.COLUMN_OBSERVED_AT,
                ),
                1,
            )

        val detail =
            snapshot?.let { value ->
                buildString {
                    append(value.location.name)
                    append(" · ")
                    append(value.current.temperature.toInt())
                    append("° · ")
                    append(conditionLabel(value.current.condition))
                }
            } ?: state.message

        cursor
            .newRow()
            .add("Weather")
            .add(detail)
            .add(state.availability.name.uppercase())
            .add(snapshot?.observedAtEpochSeconds?.times(1000L) ?: 0L)

        cursor.setNotificationUri(
            appContext.contentResolver,
            WeatherSnapshotContract.SNAPSHOT_URI,
        )
        return cursor
    }

    override fun getType(uri: Uri): String? =
        if (
            uri.authority == WeatherSnapshotContract.AUTHORITY &&
            uri.pathSegments == listOf(WeatherSnapshotContract.PATH_CURRENT)
        ) {
            "vnd.android.cursor.item/vnd.sableos.weather.snapshot"
        } else {
            null
        }

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    /**
     * Sable Setup's city hand-off ([WeatherSetupContract]). Only Setup (and Weather itself) may call it; any
     * other caller, or an unknown method, gets null.
     */
    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle? {
        val appContext =
            context?.applicationContext?.takeIf {
                isAllowedCaller(WeatherSetupContract.SETUP_CALLERS)
            }
        return appContext?.let { SetupCityHandOff.handle(it, method, arg) }
    }

    private fun isAllowedCaller(allowed: Set<String>): Boolean {
        val appContext = context?.applicationContext ?: return false
        val callingUid = Binder.getCallingUid()
        return WeatherCallers.allowed(callingUid, appContext.applicationInfo.uid, allowed) {
            appContext.packageManager
                .getPackagesForUid(callingUid)
                .orEmpty()
                .toList()
        }
    }

    private fun conditionLabel(condition: WeatherCondition): String =
        when (condition) {
            WeatherCondition.Clear -> "clear"
            WeatherCondition.PartlyCloudy -> "partly cloudy"
            WeatherCondition.Cloudy -> "cloudy"
            WeatherCondition.Fog -> "fog"
            WeatherCondition.Rain -> "rain"
            WeatherCondition.Snow -> "snow"
            WeatherCondition.Storm -> "storms"
            WeatherCondition.Unknown -> "conditions unavailable"
        }
}
