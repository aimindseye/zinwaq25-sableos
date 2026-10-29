package org.sableos.calculator

import org.sableos.design.SableTexts

/**
 * Every line Sable Calculator shows, in English, by string resource name. res/values/strings.xml
 * repeats them (CalculatorTextTest keeps the two equal) and res/values-<lang>/strings.xml
 * translates them.
 */
object CalculatorText {
    val EN: Map<String, String> =
        mapOf(
            "basic" to "Basic",
            "scientific" to "Scientific",
            "convert" to "Convert",
            "basic_scientific_and_conversion_tools" to
                "Basic, scientific and conversion tools in one app.",
            "exact_result" to "Exact result",
            "choose_an_operation_first" to "Choose an operation first.",
            "degrees_for_trigonometry" to "%1\$s · degrees for trigonometry",
            "scientific_operations_and_the_core" to
                "Scientific operations and the core arithmetic path execute through the native " +
                "calculator domain.",
            "results_are_not_silently_rounded" to
                "Results are not silently rounded into floating-point values.",
            "scientific_functions_use_degrees" to "Scientific functions use degrees.",
            "exact_decimal_results_never_rounded" to "Exact decimal results, never rounded.",
            "native_bridge_failure" to "Native bridge failure",
        )

    val t = SableTexts(EN)
}
