package org.sableos.weather

import org.sableos.weather.cities.City
import org.sableos.weather.cities.DefaultCities

internal enum class WeatherAvailability {
    Local,
    Fetching,
    Live,
    Stale,
    Failed,
}

internal enum class WeatherCondition {
    Clear,
    PartlyCloudy,
    Cloudy,
    Fog,
    Rain,
    Snow,
    Storm,
    Unknown,
}

internal data class WeatherLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
)

internal data class CurrentWeather(
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val weatherCode: Int,
    val windSpeedMph: Double,
    val windDirectionDegrees: Int,
    val condition: WeatherCondition,
)

internal data class HourWeather(
    val time: String,
    val temperature: Double,
    val precipitationProbability: Int,
    val weatherCode: Int,
    val condition: WeatherCondition,
)

internal data class DayWeather(
    val date: String,
    val weatherCode: Int,
    val high: Double,
    val low: Double,
    val precipitationProbability: Int,
    val condition: WeatherCondition,
)

internal data class WeatherSnapshot(
    val location: WeatherLocation,
    val current: CurrentWeather,
    val hourly: List<HourWeather>,
    val daily: List<DayWeather>,
    val availability: WeatherAvailability,
    val observedAtEpochSeconds: Long,
)

internal data class WeatherUiState(
    val location: WeatherLocation,
    val snapshot: WeatherSnapshot? = null,
    val availability: WeatherAvailability = WeatherAvailability.Local,
    val message: String = "Forecast not loaded yet.",
)

internal object WeatherProtocol {
    fun parse(
        protocol: String,
        location: WeatherLocation,
        availability: WeatherAvailability,
        observedAtEpochSeconds: Long,
    ): WeatherSnapshot? {
        val lines = protocol.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.firstOrNull() != "OK") {
            return null
        }

        val currentLine =
            lines.firstOrNull { line ->
                line.startsWith("CURRENT|")
            } ?: return null

        val current = parseCurrent(currentLine) ?: return null
        val hourly =
            lines.mapNotNull { line ->
                if (line.startsWith("HOURLY|")) {
                    parseHour(line)
                } else {
                    null
                }
            }
        val daily =
            lines.mapNotNull { line ->
                if (line.startsWith("DAILY|")) {
                    parseDay(line)
                } else {
                    null
                }
            }

        return WeatherSnapshot(
            location = location,
            current = current,
            hourly = hourly,
            daily = daily,
            availability = availability,
            observedAtEpochSeconds = observedAtEpochSeconds,
        )
    }

    private fun parseCurrent(line: String): CurrentWeather? {
        val parts = line.split('|')
        if (parts.size != 8) return null

        return CurrentWeather(
            temperature = parts[1].toDoubleOrNull() ?: return null,
            apparentTemperature = parts[2].toDoubleOrNull() ?: return null,
            humidity = parts[3].toIntOrNull() ?: return null,
            weatherCode = parts[4].toIntOrNull() ?: return null,
            windSpeedMph = parts[5].toDoubleOrNull() ?: return null,
            windDirectionDegrees = parts[6].toIntOrNull() ?: return null,
            condition = conditionFromProtocol(parts[7]),
        )
    }

    private fun parseHour(line: String): HourWeather? {
        val parts = line.split('|')
        if (parts.size != 6) return null

        return HourWeather(
            time = parts[1],
            temperature = parts[2].toDoubleOrNull() ?: return null,
            precipitationProbability = parts[3].toIntOrNull() ?: return null,
            weatherCode = parts[4].toIntOrNull() ?: return null,
            condition = conditionFromProtocol(parts[5]),
        )
    }

    private fun parseDay(line: String): DayWeather? {
        val parts = line.split('|')
        if (parts.size != 7) return null

        return DayWeather(
            date = parts[1],
            weatherCode = parts[2].toIntOrNull() ?: return null,
            high = parts[3].toDoubleOrNull() ?: return null,
            low = parts[4].toDoubleOrNull() ?: return null,
            precipitationProbability = parts[5].toIntOrNull() ?: return null,
            condition = conditionFromProtocol(parts[6]),
        )
    }

    private fun conditionFromProtocol(value: String): WeatherCondition =
        when (value) {
            "CLEAR" -> WeatherCondition.Clear
            "PARTLY_CLOUDY" -> WeatherCondition.PartlyCloudy
            "CLOUDY" -> WeatherCondition.Cloudy
            "FOG" -> WeatherCondition.Fog
            "RAIN" -> WeatherCondition.Rain
            "SNOW" -> WeatherCondition.Snow
            "STORM" -> WeatherCondition.Storm
            else -> WeatherCondition.Unknown
        }
}

/** The built-in cities; the user-managed list lives in [org.sableos.weather.cities.CityRepository]. */
internal val DefaultWeatherLocations: List<WeatherLocation> = DefaultCities.ALL.map { it.toLocation() }

internal fun City.toLocation(): WeatherLocation = WeatherLocation(name, latitude, longitude, timezone)

internal fun WeatherLocation.toCity(): City = City(name, latitude, longitude, timezone)
