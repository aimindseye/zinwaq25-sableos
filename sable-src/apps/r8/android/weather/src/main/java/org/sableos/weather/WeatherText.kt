package org.sableos.weather

import org.sableos.design.SableTexts

/**
 * Every line Sable Weather shows, in English, by string resource name. res/values/strings.xml
 * repeats them (WeatherTextTest keeps the two equal) and res/values-<lang>/strings.xml translates
 * them.
 */
object WeatherText {
    val EN: Map<String, String> =
        mapOf(
            "city_or_name_lat_lon" to "city, or Name, lat, lon, Area/Zone",
            "no_match_enter_name_latitude" to
                "No match. Enter Name, latitude, longitude, Area/Zone.",
            "remove" to "Remove %1\$s?",
            "reset_to_the_built_in" to "Reset to the built-in cities? Added cities are removed.",
            "current_manual_city" to "current · manual city",
            "add_city" to "add city",
            "type_a_name_or_press" to "type a name, or press any letter",
            "reset_to_built_in_cities" to "reset to built-in cities",
            "refreshing_forecast" to "Refreshing forecast…",
            "loading_forecast" to "loading forecast",
            "forecast_unavailable" to "forecast unavailable",
            "weather_ready" to "weather ready",
            "open_meteo_https_cached_locally" to
                "Open-Meteo · HTTPS · cached locally · approximate location or a chosen city · " +
                "precise location is never used",
            "use_current_location" to "Use current location",
            "finding_where_you_are" to "Finding where you are…",
            "off_showing_the_chosen_city" to "Off: showing the chosen city",
            "feels_like" to "feels like ",
            "next_hours" to "next hours",
            "n7_days" to "7 days",
            "live" to " · live",
            "cached" to " · cached",
            "fetching" to " · fetching",
            "unavailable" to " · unavailable",
            "local" to " · local",
            "partly_cloudy" to "partly cloudy",
            "conditions_unavailable" to "conditions unavailable",
            "forecast_not_loaded_yet" to "Forecast not loaded yet.",
            "refresh_to_load_the_forecast" to "Refresh to load the forecast.",
            "cached_forecast_could_not_be" to "Cached forecast could not be read.",
            "forecast_is_current" to "Forecast is current.",
            "showing_cached_forecast" to "Showing cached forecast.",
            "forecast_available" to "Forecast available.",
            "offline_showing_last_known_forecast" to "Offline · showing last known forecast.",
            "forecast_unavailable_check_the_connection" to
                "Forecast unavailable. Check the connection and retry.",
            "provider_response_could_not_be" to "Provider response could not be normalized.",
            "updated_from_open_meteo" to "Updated from Open-Meteo.",
        )

    val t = SableTexts(EN)
}
