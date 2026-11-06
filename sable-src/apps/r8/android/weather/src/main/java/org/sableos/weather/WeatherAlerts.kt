package org.sableos.weather

import org.sableos.weather.WeatherText.t
import kotlin.math.roundToInt

/** The two opt-in alerts, both off until turned on in Sable Weather. */
internal data class AlertSettings(
    val morning: Boolean = false,
    val rain: Boolean = false,
) {
    val any: Boolean get() = morning || rain
}

/** What was last posted, so a forecast is sent once a day and a rain alert once per spell. */
internal data class AlertMemory(
    val morningDate: String? = null,
    val rainAtEpochSeconds: Long = 0L,
)

/** A notification to post: a title and one line, both already in the phone's language. */
internal sealed interface WeatherAlert {
    val title: String
    val text: String

    data class Morning(
        override val title: String,
        override val text: String,
        val date: String,
    ) : WeatherAlert

    data class RainSoon(
        override val title: String,
        override val text: String,
    ) : WeatherAlert
}

/**
 * Decides, after a background refresh, which alerts are due. Pure: the caller passes the phone's local date
 * (yyyy-MM-dd) and hour, and the clock.
 */
internal object WeatherAlerts {
    /** The morning forecast goes out on the first refresh in this window (the refresh runs every 3 hours). */
    const val MORNING_FROM_HOUR = 6
    const val MORNING_UNTIL_HOUR = 11

    /** An hour this likely to be wet, within the next [RAIN_LOOKAHEAD_HOURS], raises a rain alert. */
    const val RAIN_CHANCE = 60
    const val RAIN_LOOKAHEAD_HOURS = 3

    /** After a rain alert, another waits this long, so one wet afternoon is one alert. */
    const val RAIN_QUIET_SECONDS = 6L * 60 * 60

    fun due(
        settings: AlertSettings,
        memory: AlertMemory,
        snapshot: WeatherSnapshot?,
        localDate: String,
        localHour: Int,
        nowEpochSeconds: Long,
    ): List<WeatherAlert> {
        if (snapshot == null || snapshot.availability != WeatherAvailability.Live) return emptyList()
        return listOfNotNull(
            morning(snapshot, localDate, localHour).takeIf { settings.morning && memory.morningDate != localDate },
            rainSoon(snapshot).takeIf {
                settings.rain && nowEpochSeconds - memory.rainAtEpochSeconds >= RAIN_QUIET_SECONDS
            },
        )
    }

    fun morning(
        snapshot: WeatherSnapshot,
        localDate: String,
        localHour: Int,
    ): WeatherAlert.Morning? {
        if (localHour !in MORNING_FROM_HOUR until MORNING_UNTIL_HOUR) return null
        val today = snapshot.daily.firstOrNull { it.date == localDate } ?: return null
        return WeatherAlert.Morning(
            title = t("alert_today_in").format(snapshot.location.name),
            text =
                t("alert_today_line").format(
                    conditionLabel(today.condition),
                    today.high.roundToInt(),
                    today.low.roundToInt(),
                    today.precipitationProbability,
                ),
            date = localDate,
        )
    }

    /** The first wet hour coming up, unless it is raining already (then the phone owner knows). */
    fun rainSoon(snapshot: WeatherSnapshot): WeatherAlert.RainSoon? {
        if (snapshot.current.condition in WET) return null
        val hour =
            snapshot.hourly.take(RAIN_LOOKAHEAD_HOURS).firstOrNull {
                it.condition in WET || it.precipitationProbability >= RAIN_CHANCE
            } ?: return null
        return WeatherAlert.RainSoon(
            title = t("alert_rain_soon_in").format(snapshot.location.name),
            text = t("alert_rain_line").format(hour.time.substringAfter('T', hour.time), hour.precipitationProbability),
        )
    }

    private val WET = setOf(WeatherCondition.Rain, WeatherCondition.Storm, WeatherCondition.Snow)
}

/** A condition in words, as the app and its alerts show it. */
internal fun conditionLabel(condition: WeatherCondition): String =
    when (condition) {
        WeatherCondition.Clear -> "clear"
        WeatherCondition.PartlyCloudy -> t("partly_cloudy")
        WeatherCondition.Cloudy -> "cloudy"
        WeatherCondition.Fog -> "fog"
        WeatherCondition.Rain -> "rain"
        WeatherCondition.Snow -> "snow"
        WeatherCondition.Storm -> "storms"
        WeatherCondition.Unknown -> t("conditions_unavailable")
    }
