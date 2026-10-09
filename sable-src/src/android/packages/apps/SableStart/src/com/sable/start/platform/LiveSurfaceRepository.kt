package org.sableos.start.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sableos.start.live.LiveAvailability
import org.sableos.start.live.LiveDatum
import org.sableos.start.live.LiveSurfaceSnapshot

class LiveSurfaceRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    fun initialSnapshot(
        nowEpochMs: Long = System.currentTimeMillis(),
    ): LiveSurfaceSnapshot {
        val loading =
            LiveDatum(
                title = "Loading",
                detail = "loading",
                availability = LiveAvailability.LOADING,
                observedAtEpochMs = nowEpochMs,
            )

        return LiveSurfaceSnapshot(
            dateText =
                DateFormat.getMediumDateFormat(appContext)
                    .format(Date(nowEpochMs)),
            timeText =
                DateFormat.getTimeFormat(appContext)
                    .format(Date(nowEpochMs)),
            weather = loading.copy(title = "Weather"),
            calendar = loading.copy(title = "Calendar"),
            messages = loading.copy(title = "Hub"),
            mail = loading.copy(title = "Mail"),
            music = loading.copy(title = "Media"),
            photos = loading.copy(title = "Photos"),
            observedAtEpochMs = nowEpochMs,
        )
    }

    suspend fun snapshot(
        nowEpochMs: Long = System.currentTimeMillis(),
    ): LiveSurfaceSnapshot =
        withContext(Dispatchers.IO) {
            LiveSurfaceSnapshot(
                dateText =
                    DateFormat.getMediumDateFormat(appContext)
                        .format(Date(nowEpochMs)),
                timeText =
                    DateFormat.getTimeFormat(appContext)
                        .format(Date(nowEpochMs)),
                weather =
                    providerSnapshot(
                        uri = WEATHER_URI,
                        fallbackTitle = "Weather",
                    ),
                calendar =
                    providerSnapshot(
                        uri = CALENDAR_URI,
                        fallbackTitle = "Calendar",
                    ),
                messages =
                    providerSnapshot(
                        uri = MESSAGES_URI,
                        fallbackTitle = "Hub",
                    ),
                mail =
                    providerSnapshot(
                        uri = MAIL_URI,
                        fallbackTitle = "Mail",
                    ),
                music =
                    providerSnapshot(
                        uri = MEDIA_URI,
                        fallbackTitle = "Media",
                    ),
                photos =
                    mediaCount(
                        permission =
                            if (Build.VERSION.SDK_INT >= 33) {
                                Manifest.permission.READ_MEDIA_IMAGES
                            } else {
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            },
                        uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        title = "Photos",
                        noun = "photo",
                    ),
                observedAtEpochMs = nowEpochMs,
            )
        }

    private fun providerSnapshot(
        uri: Uri,
        fallbackTitle: String,
    ): LiveDatum =
        runCatching {
            resolver.query(
                uri,
                arrayOf(
                    "title",
                    "detail",
                    "availability",
                    "observed_at",
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null

                val title =
                    cursor.getString(
                        cursor.getColumnIndexOrThrow("title"),
                    )
                        ?.trim()
                        .orEmpty()
                        .ifEmpty { fallbackTitle }
                val detail =
                    cursor.getString(
                        cursor.getColumnIndexOrThrow("detail"),
                    )
                        ?.trim()
                        .orEmpty()
                        .ifEmpty { "unavailable" }
                val availability =
                    cursor.getString(
                        cursor.getColumnIndexOrThrow("availability"),
                    ).toLiveAvailability()
                val observedAt =
                    cursor.getColumnIndex("observed_at")
                        .takeIf { it >= 0 }
                        ?.let(cursor::getLong)
                        ?: 0L

                LiveDatum(
                    title = title,
                    detail = detail,
                    availability = availability,
                    observedAtEpochMs = observedAt,
                )
            }
        }.getOrNull()
            ?: LiveDatum(
                title = fallbackTitle,
                detail = "unavailable",
                availability = LiveAvailability.UNAVAILABLE,
            )

    private fun String?.toLiveAvailability(): LiveAvailability =
        when (this?.uppercase()) {
            "LIVE", "STALE" -> LiveAvailability.LIVE
            "EMPTY" -> LiveAvailability.EMPTY
            "PERMISSION_REQUIRED" -> LiveAvailability.PERMISSION_REQUIRED
            "LOADING", "FETCHING", "LOCAL" -> LiveAvailability.LOADING
            "ERROR", "FAILED" -> LiveAvailability.ERROR
            else -> LiveAvailability.UNAVAILABLE
        }

    private fun mediaCount(
        permission: String,
        uri: Uri,
        title: String,
        noun: String,
    ): LiveDatum {
        if (
            appContext.checkSelfPermission(permission) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            return LiveDatum(
                title = title,
                detail = "permission required",
                availability = LiveAvailability.PERMISSION_REQUIRED,
            )
        }

        return try {
            val count =
                resolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns._ID),
                    null,
                    null,
                    null,
                )?.use { cursor -> cursor.count } ?: 0

            LiveDatum(
                title = title,
                detail =
                    if (count == 0) {
                        "no " + noun + "s found"
                    } else {
                        count.toString() +
                            " " +
                            if (count == 1) noun else noun + "s"
                    },
                availability =
                    if (count == 0) {
                        LiveAvailability.EMPTY
                    } else {
                        LiveAvailability.LIVE
                    },
                observedAtEpochMs = System.currentTimeMillis(),
            )
        } catch (_: SecurityException) {
            LiveDatum(
                title = title,
                detail = "permission required",
                availability = LiveAvailability.PERMISSION_REQUIRED,
            )
        } catch (_: RuntimeException) {
            LiveDatum(
                title = title,
                detail = "provider error",
                availability = LiveAvailability.ERROR,
            )
        }
    }

    companion object {
        val WEATHER_URI: Uri =
            Uri.parse("content://org.sableos.weather.snapshot/current")
        val CALENDAR_URI: Uri =
            Uri.parse("content://org.sableos.calendar.snapshot/current")
        val MESSAGES_URI: Uri =
            Uri.parse("content://org.sableos.hub.snapshot/current")
        val MAIL_URI: Uri =
            Uri.parse("content://org.sableos.mail.snapshot/current")
        val MEDIA_URI: Uri =
            Uri.parse("content://org.sableos.media.snapshot/current")

        val OBSERVED_URIS: List<Uri> =
            listOf(
                WEATHER_URI,
                CALENDAR_URI,
                MESSAGES_URI,
                MAIL_URI,
                MEDIA_URI,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            )
    }
}
