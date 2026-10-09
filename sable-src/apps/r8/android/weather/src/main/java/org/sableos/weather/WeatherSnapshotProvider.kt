package org.sableos.weather

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder

class WeatherSnapshotProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        if (!isAllowedCaller()) {
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

    private fun isAllowedCaller(): Boolean {
        val appContext = context?.applicationContext ?: return false
        val callingUid = Binder.getCallingUid()

        if (callingUid == appContext.applicationInfo.uid) {
            return true
        }

        return appContext.packageManager
            .getPackagesForUid(callingUid)
            .orEmpty()
            .any { packageName ->
                packageName == "org.sableos.launcher"
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
