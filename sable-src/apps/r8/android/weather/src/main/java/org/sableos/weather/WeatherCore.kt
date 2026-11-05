package org.sableos.weather

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor

/**
 * Sable Weather's provider domain, in Kotlin (it was the weather-core Rust crate).
 *
 * It builds the Open-Meteo request, judges freshness, and normalizes the provider JSON into the
 * line protocol [WeatherRepository] reads: `OK` then `CURRENT|…`, `HOURLY|…` and `DAILY|…` lines,
 * or `ERR|<reason>`. HTTPS, permissions, scheduling and UI stay in the Android layer.
 */
internal object WeatherCore {
    private const val LIVE_SECONDS = 60L * 60L
    private const val HOURS_SHOWN = 24
    private const val DAYS_SHOWN = 7
    private const val COORDINATE_DECIMALS = 5

    fun providerState(
        hasCachedSnapshot: Boolean,
        lastSuccessEpochSeconds: Long,
        nowEpochSeconds: Long,
        fetchFailed: Boolean,
    ): String =
        when {
            !hasCachedSnapshot -> if (fetchFailed) "FAILED" else "LOCAL"
            fetchFailed -> "STALE"
            saturatingSub(nowEpochSeconds, lastSuccessEpochSeconds) <= LIVE_SECONDS -> "LIVE"
            else -> "STALE"
        }

    fun buildForecastUrl(
        latitude: Double,
        longitude: Double,
        timezone: String,
        fahrenheit: Boolean,
    ): String {
        val unit = if (fahrenheit) "fahrenheit" else "celsius"
        return "https://api.open-meteo.com/v1/forecast?latitude=${fixed(latitude, COORDINATE_DECIMALS)}" +
            "&longitude=${fixed(longitude, COORDINATE_DECIMALS)}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m," +
            "wind_direction_10m&hourly=temperature_2m,precipitation_probability,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&forecast_days=7&temperature_unit=$unit&wind_speed_unit=mph&timezone=${percentEncode(timezone)}"
    }

    fun parseForecast(json: String): String =
        try {
            "OK\n${toProtocol(json)}"
        } catch (error: ForecastError) {
            "ERR|${error.message}"
        }

    /** Open-Meteo weather code to the condition names [WeatherCondition] reads. */
    fun conditionFor(code: Int): String =
        when (code) {
            0 -> "CLEAR"
            1, 2 -> "PARTLY_CLOUDY"
            3 -> "CLOUDY"
            45, 48 -> "FOG"
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "RAIN"
            71, 73, 75, 77, 85, 86 -> "SNOW"
            95, 96, 99 -> "STORM"
            else -> "UNKNOWN"
        }

    private class ForecastError(
        reason: String,
    ) : Exception(reason)

    private fun <T : Any> need(
        value: T?,
        reason: String,
    ): T = value ?: throw ForecastError(reason)

    private fun toProtocol(json: String): String {
        val current = need(objectFor(json, "current"), "current object missing")
        val hourly = need(objectFor(json, "hourly"), "hourly object missing")
        val daily = need(objectFor(json, "daily"), "daily object missing")
        val currentHour = stringFor(current, "time")?.let(::localHourStart)

        fun number(key: String, reason: String) = need(numberFor(current, key), reason)

        val temperature = number("temperature_2m", "current temperature missing")
        val apparent = number("apparent_temperature", "apparent temperature missing")
        val humidity = roundToInt(number("relative_humidity_2m", "humidity missing"))
        val code = roundToInt(number("weather_code", "weather code missing"))
        val windSpeed = number("wind_speed_10m", "wind speed missing")
        val windDirection = roundToInt(number("wind_direction_10m", "wind direction missing"))
        val lines =
            mutableListOf(
                "CURRENT|${fixed(temperature, 1)}|${fixed(apparent, 1)}|$humidity|$code|" +
                    "${fixed(windSpeed, 1)}|$windDirection|${conditionFor(code)}",
            )

        val hourTime = strings(hourly, "time", "hourly time missing")
        val hourTemp = numbers(hourly, "temperature_2m", "hourly temperature missing")
        val hourPrecip = numbers(hourly, "precipitation_probability", "hourly precipitation missing")
        val hourCode = numbers(hourly, "weather_code", "hourly weather code missing")
        val hourCount = minOf(hourTime.size, hourTemp.size, hourPrecip.size, hourCode.size)
        val firstHour =
            (currentHour?.let { start -> hourTime.indexOfFirst { it >= start }.takeIf { it >= 0 } } ?: 0)
                .coerceAtMost(hourCount)
        for (index in firstHour until minOf(firstHour + HOURS_SHOWN, hourCount)) {
            val hourWeather = roundToInt(hourCode[index])
            lines +=
                "HOURLY|${hourTime[index]}|${fixed(hourTemp[index], 1)}|${roundToInt(hourPrecip[index])}|" +
                "$hourWeather|${conditionFor(hourWeather)}"
        }

        val dayDate = strings(daily, "time", "daily time missing")
        val dayCode = numbers(daily, "weather_code", "daily weather code missing")
        val dayHigh = numbers(daily, "temperature_2m_max", "daily high missing")
        val dayLow = numbers(daily, "temperature_2m_min", "daily low missing")
        val dayPrecip = numbers(daily, "precipitation_probability_max", "daily precipitation missing")
        val dayCount = minOf(minOf(dayDate.size, dayCode.size, dayHigh.size), minOf(dayLow.size, dayPrecip.size, DAYS_SHOWN))
        for (index in 0 until dayCount) {
            val dayWeather = roundToInt(dayCode[index])
            lines +=
                "DAILY|${dayDate[index]}|$dayWeather|${fixed(dayHigh[index], 1)}|${fixed(dayLow[index], 1)}|" +
                "${roundToInt(dayPrecip[index])}|${conditionFor(dayWeather)}"
        }
        return lines.joinToString("\n")
    }

    private fun strings(
        json: String,
        key: String,
        reason: String,
    ): List<String> = need(stringArrayFor(json, key), reason)

    private fun numbers(
        json: String,
        key: String,
        reason: String,
    ): List<Double> = need(numberArrayFor(json, key), reason)

    // --- Formatting, matching Rust's `{:.N}` and `f64::round` ---

    /** Fixed decimals from the exact binary value, ties to even; keeps the sign of -0.0. */
    internal fun fixed(
        value: Double,
        decimals: Int,
    ): String {
        if (!value.isFinite()) return if (value.isNaN()) "NaN" else if (value > 0) "inf" else "-inf"
        val negative = value < 0 || (value == 0.0 && 1.0 / value < 0)
        val digits = BigDecimal(abs(value)).setScale(decimals, RoundingMode.HALF_EVEN).toPlainString()
        return if (negative) "-$digits" else digits
    }

    /** Half away from zero, saturating at the Int range, NaN to 0. */
    private fun roundToInt(value: Double): Int {
        if (value.isNaN()) return 0
        val rounded = if (value < 0) -floor(-value + HALF) else floor(value + HALF)
        return rounded.coerceIn(Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble()).toInt()
    }

    private const val HALF = 0.5

    private fun saturatingSub(
        a: Long,
        b: Long,
    ): Long {
        val result = a - b
        return when {
            ((a xor b) and (a xor result)) >= 0 -> result
            a < 0 -> Long.MIN_VALUE
            else -> Long.MAX_VALUE
        }
    }

    private fun percentEncode(value: String): String =
        buildString {
            for (byte in value.toByteArray(Charsets.UTF_8)) {
                val c = byte.toInt() and BYTE_MASK
                val ch = c.toChar()
                if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.') {
                    append(ch)
                } else {
                    append('%').append(HEX[c shr NIBBLE]).append(HEX[c and NIBBLE_MASK])
                }
            }
        }

    private const val BYTE_MASK = 0xFF
    private const val NIBBLE = 4
    private const val NIBBLE_MASK = 0xF
    private const val HEX = "0123456789ABCDEF"

    // --- The small, forgiving JSON reader the provider contract needs ---

    private fun keyStart(
        json: String,
        key: String,
    ): String? {
        val needle = "\"$key\""
        val found = json.indexOf(needle)
        if (found < 0) return null
        val rest = json.substring(found + needle.length)
        val colon = rest.indexOf(':')
        if (colon < 0) return null
        return rest.substring(colon + 1).trimStart()
    }

    private fun objectFor(
        json: String,
        key: String,
    ): String? = keyStart(json, key)?.let { balanced(it, '{', '}') }

    private fun arrayFor(
        json: String,
        key: String,
    ): String? = keyStart(json, key)?.let { balanced(it, '[', ']') }

    private fun balanced(
        text: String,
        open: Char,
        close: Char,
    ): String? {
        if (text.firstOrNull() != open) return null
        var depth = 0
        var inString = false
        var escaped = false
        for ((index, c) in text.withIndex()) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return text.substring(0, index + 1)
                }
            }
        }
        return null
    }

    private fun stringFor(
        json: String,
        key: String,
    ): String? {
        val rest = keyStart(json, key) ?: return null
        if (!rest.startsWith('"')) return null
        val end = rest.indexOf('"', 1)
        return if (end < 0) null else rest.substring(1, end)
    }

    private const val HOUR_PREFIX = 13
    private const val DATE_TIME_SEPARATOR = 10

    private fun localHourStart(value: String): String? {
        if (value.length < HOUR_PREFIX || value[DATE_TIME_SEPARATOR] != 'T') return null
        return value.substring(0, HOUR_PREFIX) + ":00"
    }

    private val NUMBER = Regex("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")

    private fun parseNumber(text: String): Double? = if (NUMBER.matches(text)) text.toDoubleOrNull() else null

    private fun numberFor(
        json: String,
        key: String,
    ): Double? {
        val rest = keyStart(json, key) ?: return null
        val end = rest.indexOfFirst { !(it in '0'..'9' || it == '-' || it == '+' || it == '.' || it == 'e' || it == 'E') }
        return parseNumber(if (end < 0) rest else rest.substring(0, end))
    }

    private fun numberArrayFor(
        json: String,
        key: String,
    ): List<Double>? {
        val inner = arrayFor(json, key)?.removePrefix("[")?.removeSuffix("]") ?: return null
        val result = mutableListOf<Double>()
        for (raw in inner.split(',')) {
            val value = raw.trim()
            if (value.isEmpty() || value == "null") continue
            result += parseNumber(value) ?: return null
        }
        return result
    }

    private fun stringArrayFor(
        json: String,
        key: String,
    ): List<String>? {
        var cursor = arrayFor(json, key)?.removePrefix("[")?.removeSuffix("]") ?: return null
        val result = mutableListOf<String>()
        while (true) {
            val start = cursor.indexOf('"')
            if (start < 0) break
            cursor = cursor.substring(start + 1)
            val end = cursor.indexOf('"')
            if (end < 0) return null
            result += cursor.substring(0, end)
            cursor = cursor.substring(end + 1)
        }
        return result
    }
}
