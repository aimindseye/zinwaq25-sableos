package org.sableos.weather

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource

/**
 * Production Sable Weather condition icons.
 *
 * Glyph geometry is sourced from the pinned Phosphor Icons regular set.
 * Rust maps provider weather codes to semantic conditions only; presentation
 * stays in the Android UI layer.
 */
@Composable
internal fun WeatherGlyph(
    condition: WeatherCondition,
    modifier: Modifier = Modifier,
    accent: Color,
    muted: Color,
) {
    val icon =
        when (condition) {
            WeatherCondition.Clear -> R.drawable.ic_weather_clear
            WeatherCondition.PartlyCloudy -> R.drawable.ic_weather_partly_cloudy
            WeatherCondition.Cloudy -> R.drawable.ic_weather_cloudy
            WeatherCondition.Fog -> R.drawable.ic_weather_fog
            WeatherCondition.Rain -> R.drawable.ic_weather_rain
            WeatherCondition.Snow -> R.drawable.ic_weather_snow
            WeatherCondition.Storm -> R.drawable.ic_weather_storm
            WeatherCondition.Unknown -> R.drawable.ic_weather_unknown
        }

    val tint =
        when (condition) {
            WeatherCondition.Cloudy,
            WeatherCondition.Fog,
            WeatherCondition.Unknown,
            -> muted

            else -> accent
        }

    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = modifier,
        tint = tint,
    )
}
