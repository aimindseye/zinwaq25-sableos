package org.sableos.weather

internal object WeatherNative {
    init {
        System.loadLibrary("sable_weather_jni")
    }

    external fun selfTest(): String

    external fun buildForecastUrl(
        latitude: Double,
        longitude: Double,
        timezone: String,
        fahrenheit: Boolean,
    ): String

    external fun parseForecast(json: String): String

    external fun providerState(
        hasCachedSnapshot: Boolean,
        lastSuccessEpochSeconds: Long,
        nowEpochSeconds: Long,
        fetchFailed: Boolean,
    ): String
}
