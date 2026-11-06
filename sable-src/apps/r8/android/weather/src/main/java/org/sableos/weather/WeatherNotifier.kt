package org.sableos.weather

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import org.sableos.weather.WeatherText.t
import java.time.LocalDateTime

/** The alert switches and what was last posted, in Weather's private preferences. */
internal class AlertStore(
    private val preferences: SharedPreferences,
) {
    var settings: AlertSettings
        get() = AlertSettings(preferences.getBoolean(KEY_MORNING, false), preferences.getBoolean(KEY_RAIN, false))
        set(value) {
            preferences
                .edit()
                .putBoolean(KEY_MORNING, value.morning)
                .putBoolean(KEY_RAIN, value.rain)
                .apply()
        }

    var memory: AlertMemory
        get() = AlertMemory(preferences.getString(KEY_MORNING_DATE, null), preferences.getLong(KEY_RAIN_AT, 0L))
        set(value) {
            preferences
                .edit()
                .putString(KEY_MORNING_DATE, value.morningDate)
                .putLong(KEY_RAIN_AT, value.rainAtEpochSeconds)
                .apply()
        }

    private companion object {
        const val KEY_MORNING = "alert_morning"
        const val KEY_RAIN = "alert_rain"
        const val KEY_MORNING_DATE = "alert_morning_date"
        const val KEY_RAIN_AT = "alert_rain_at"
    }
}

/**
 * Posts the opt-in weather alerts after a background refresh. Each one opens Sable Weather. Nothing is posted
 * while both switches are off or notifications are not allowed.
 */
internal object WeatherNotifier {
    private const val CHANNEL_MORNING = "morning_forecast"
    private const val CHANNEL_RAIN = "rain_alerts"
    private const val ID_MORNING = 1
    private const val ID_RAIN = 2

    fun canPost(context: Context): Boolean {
        val granted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true
    }

    // canPost() checks POST_NOTIFICATIONS first; lint only sees a check made in the same function.
    @SuppressLint("MissingPermission")
    fun afterRefresh(
        context: Context,
        store: AlertStore,
        snapshot: WeatherSnapshot?,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
    ) {
        val settings = store.settings
        if (!settings.any || !canPost(context)) return
        val now = LocalDateTime.now()
        val alerts =
            WeatherAlerts.due(settings, store.memory, snapshot, now.toLocalDate().toString(), now.hour, nowEpochSeconds)
        if (alerts.isEmpty()) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        createChannels(manager)
        var memory = store.memory
        alerts.forEach { alert ->
            when (alert) {
                is WeatherAlert.Morning -> {
                    manager.notify(ID_MORNING, build(context, CHANNEL_MORNING, alert))
                    memory = memory.copy(morningDate = alert.date)
                }

                is WeatherAlert.RainSoon -> {
                    manager.notify(ID_RAIN, build(context, CHANNEL_RAIN, alert))
                    memory = memory.copy(rainAtEpochSeconds = nowEpochSeconds)
                }
            }
        }
        store.memory = memory
    }

    private fun createChannels(manager: NotificationManager) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MORNING, t("alert_morning_forecast"), NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RAIN, t("alert_rain_alerts"), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun build(
        context: Context,
        channel: String,
        alert: WeatherAlert,
    ): Notification {
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return Notification
            .Builder(context, channel)
            .setSmallIcon(R.drawable.ic_sable_weather_monochrome)
            .setContentTitle(alert.title)
            .setContentText(alert.text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_RECOMMENDATION)
            .build()
    }
}
