package org.sableos.convert

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.sableos.convert.ConvertText.t
import org.sableos.design.SableActionButton
import org.sableos.design.SableSectionHeader
import org.sableos.design.SableSpacing
import org.sableos.design.SableTile
import org.sableos.design.SableValuePanel
import java.math.BigDecimal
import java.math.RoundingMode

private enum class UnitCategory(
    val label: String,
) {
    Length(t("convert_length")),
    Mass(t("convert_mass")),
    Temperature(t("convert_temperature")),
    Volume(t("convert_volume")),
}

private data class UnitChoice(
    val code: Int,
    val label: String,
    val symbol: String,
    val category: UnitCategory,
)

private val units =
    listOf(
        UnitChoice(0, t("convert_millimeters"), "mm", UnitCategory.Length),
        UnitChoice(1, t("convert_centimeters"), "cm", UnitCategory.Length),
        UnitChoice(2, t("convert_meters"), "m", UnitCategory.Length),
        UnitChoice(3, t("convert_kilometers"), "km", UnitCategory.Length),
        UnitChoice(4, t("convert_inches"), "in", UnitCategory.Length),
        UnitChoice(5, t("convert_feet"), "ft", UnitCategory.Length),
        UnitChoice(6, t("convert_miles"), "mi", UnitCategory.Length),
        UnitChoice(7, t("convert_grams"), "g", UnitCategory.Mass),
        UnitChoice(8, t("convert_kilograms"), "kg", UnitCategory.Mass),
        UnitChoice(9, t("convert_ounces"), "oz", UnitCategory.Mass),
        UnitChoice(10, t("convert_pounds"), "lb", UnitCategory.Mass),
        UnitChoice(11, t("convert_celsius"), "°C", UnitCategory.Temperature),
        UnitChoice(12, t("convert_fahrenheit"), "°F", UnitCategory.Temperature),
        UnitChoice(13, t("convert_kelvin"), "K", UnitCategory.Temperature),
        UnitChoice(14, t("convert_milliliters"), "mL", UnitCategory.Volume),
        UnitChoice(15, t("convert_liters"), "L", UnitCategory.Volume),
        UnitChoice(16, t("convert_cups"), "cup", UnitCategory.Volume),
        UnitChoice(17, t("convert_gallons"), "gal", UnitCategory.Volume),
    )

@Composable
fun ConverterScreen(modifier: Modifier = Modifier) {
    val nativeQualification = remember { NativeBridge.selfTest() }
    check(nativeQualification.startsWith("PASS:")) { nativeQualification }

    var category by remember { mutableStateOf(UnitCategory.Length) }
    var from by remember { mutableStateOf(units.first { it.code == 2 }) }
    var to by remember { mutableStateOf(units.first { it.code == 3 }) }
    var input by remember { mutableStateOf("1") }
    var result by remember { mutableStateOf(t("convert_ready")) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SableSpacing.Lg),
    ) {
        SableSectionHeader("convert")

        UnitCategory.entries.chunked(2).forEach { rowChoices ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
            ) {
                rowChoices.forEach { choice ->
                    SableTile(
                        title = choice.label,
                        active = category == choice,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            category = choice
                            val available = units.filter { it.category == choice }
                            from = available.first()
                            to = available.getOrElse(1) { available.first() }
                            result = t("convert_ready")
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(t("convert_value")) },
        )

        val availableUnits = units.filter { it.category == category }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
        ) {
            SableTile(
                title = t("convert_from"),
                subtitle = "${from.label} · ${from.symbol}",
                modifier = Modifier.weight(1f),
                onClick = {
                    from = cycleUnit(availableUnits, from)
                    result = t("convert_ready")
                },
            )

            SableTile(
                title = t("convert_to"),
                subtitle = "${to.label} · ${to.symbol}",
                modifier = Modifier.weight(1f),
                onClick = {
                    to = cycleUnit(availableUnits, to)
                    result = t("convert_ready")
                },
            )
        }

        SableActionButton(
            text = t("convert_convert"),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val valueMilli = parseMilli(input)
                result =
                    if (valueMilli == null) {
                        t("convert_enter_a_number_with_up")
                    } else {
                        renderResult(
                            NativeBridge.convertMilli(
                                valueMilli,
                                from.code,
                                to.code,
                            ),
                            to.symbol,
                        )
                    }
            },
        )

        SableValuePanel(
            label = "result",
            value = result,
            supportingText = "${from.symbol} → ${to.symbol}",
        )

        Text(
            t("convert_length_mass_temperature_and_volume"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun cycleUnit(
    available: List<UnitChoice>,
    current: UnitChoice,
): UnitChoice {
    val index = available.indexOfFirst { it.code == current.code }
    return available[(index + 1).mod(available.size)]
}

private fun parseMilli(text: String): Long? =
    try {
        BigDecimal(text.trim())
            .setScale(3, RoundingMode.HALF_UP)
            .movePointRight(3)
            .longValueExact()
    } catch (_: NumberFormatException) {
        null
    } catch (_: ArithmeticException) {
        null
    }

private fun renderResult(
    protocol: String,
    symbol: String,
): String =
    when {
        protocol.startsWith("OK:") -> {
            val milli = protocol.removePrefix("OK:").toLongOrNull()
            if (milli == null) {
                t("convert_native_bridge_failure")
            } else {
                "${formatMilli(milli)} $symbol"
            }
        }

        protocol.startsWith("ERR:") -> {
            protocol.removePrefix("ERR:").replace('_', ' ')
        }

        else -> {
            t("convert_native_bridge_failure")
        }
    }

private fun formatMilli(valueMilli: Long): String =
    BigDecimal
        .valueOf(valueMilli, 3)
        .stripTrailingZeros()
        .toPlainString()
