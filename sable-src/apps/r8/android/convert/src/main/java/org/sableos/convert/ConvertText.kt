package org.sableos.convert

import org.sableos.design.SableTexts

/**
 * Every line Sable Calculator's converter shows, in English, by string resource name.
 * res/values/strings.xml repeats them (ConvertTextTest keeps the two equal) and
 * res/values-<lang>/strings.xml translates them.
 */
object ConvertText {
    val EN: Map<String, String> =
        mapOf(
            "convert_length" to "Length",
            "convert_mass" to "Mass",
            "convert_temperature" to "Temperature",
            "convert_volume" to "Volume",
            "convert_millimeters" to "Millimeters",
            "convert_centimeters" to "Centimeters",
            "convert_meters" to "Meters",
            "convert_kilometers" to "Kilometers",
            "convert_inches" to "Inches",
            "convert_feet" to "Feet",
            "convert_miles" to "Miles",
            "convert_grams" to "Grams",
            "convert_kilograms" to "Kilograms",
            "convert_ounces" to "Ounces",
            "convert_pounds" to "Pounds",
            "convert_celsius" to "Celsius",
            "convert_fahrenheit" to "Fahrenheit",
            "convert_kelvin" to "Kelvin",
            "convert_milliliters" to "Milliliters",
            "convert_liters" to "Liters",
            "convert_cups" to "Cups",
            "convert_gallons" to "Gallons",
            "convert_ready" to "Ready",
            "convert_value" to "Value",
            "convert_from" to "From",
            "convert_to" to "To",
            "convert_convert" to "Convert",
            "convert_enter_a_number_with_up" to "Enter a number with up to three decimal places",
            "convert_length_mass_temperature_and_volume" to
                "Length, mass, temperature and volume conversions are local and offline.",
            "convert_native_bridge_failure" to "Native bridge failure",
        )

    val t = SableTexts(EN)
}
