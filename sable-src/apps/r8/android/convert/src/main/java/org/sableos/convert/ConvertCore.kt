package org.sableos.convert

import java.math.BigInteger

/**
 * Offline fixed-point unit conversion, in Kotlin (it was the convert-core Rust crate).
 *
 * Values are thousandths (milli-units). Length, mass and volume convert through exact ratios
 * with round-half-away-from-zero; temperature goes through milli-degrees Celsius and refuses
 * anything below absolute zero. Results keep the screen's string protocol: `OK:<milli>` or
 * `ERR:INVALID_UNIT`, `ERR:INCOMPATIBLE_UNITS`, `ERR:OUT_OF_RANGE`.
 */
object ConvertCore {
    private const val ERR_INVALID_UNIT = "ERR:INVALID_UNIT"
    private const val ERR_INCOMPATIBLE = "ERR:INCOMPATIBLE_UNITS"
    private const val ERR_OUT_OF_RANGE = "ERR:OUT_OF_RANGE"

    private enum class Category { Length, Mass, Temperature, Volume }

    /** One unit per frozen code 0..17, in the order [ConverterScreen] numbers them. */
    private class UnitSpec(
        val category: Category,
        val numerator: Long = 0,
        val denominator: Long = 1,
    )

    private val UNITS =
        listOf(
            UnitSpec(Category.Length, 1, 1), // 0 mm
            UnitSpec(Category.Length, 10, 1), // 1 cm
            UnitSpec(Category.Length, 1_000, 1), // 2 m
            UnitSpec(Category.Length, 1_000_000, 1), // 3 km
            UnitSpec(Category.Length, 254, 10), // 4 in
            UnitSpec(Category.Length, 3_048, 10), // 5 ft
            UnitSpec(Category.Length, 1_609_344, 1), // 6 mi
            UnitSpec(Category.Mass, 1, 1), // 7 g
            UnitSpec(Category.Mass, 1_000, 1), // 8 kg
            UnitSpec(Category.Mass, 28_349_523_125, 1_000_000_000), // 9 oz
            UnitSpec(Category.Mass, 45_359_237, 100_000), // 10 lb
            UnitSpec(Category.Temperature), // 11 °C
            UnitSpec(Category.Temperature), // 12 °F
            UnitSpec(Category.Temperature), // 13 K
            UnitSpec(Category.Volume, 1, 1), // 14 mL
            UnitSpec(Category.Volume, 1_000, 1), // 15 L
            UnitSpec(Category.Volume, 2_365_882_365, 10_000_000), // 16 cup
            UnitSpec(Category.Volume, 3_785_411_784, 1_000_000), // 17 gal
        )

    private const val CELSIUS = 11
    private const val FAHRENHEIT = 12
    private const val ABSOLUTE_ZERO_MILLI = 273_150L
    private const val FREEZING_F_MILLI = 32_000L

    private val I128_MAX = BigInteger.ONE.shiftLeft(127) - BigInteger.ONE
    private val I128_MIN = BigInteger.ONE.shiftLeft(127).negate()

    private fun within128(value: BigInteger): BigInteger? = value.takeIf { it in I128_MIN..I128_MAX }

    private val LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE)
    private val LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE)

    fun convertMilli(
        valueMilli: Long,
        fromUnit: Int,
        toUnit: Int,
    ): String {
        val from = UNITS.getOrNull(fromUnit) ?: return ERR_INVALID_UNIT
        val to = UNITS.getOrNull(toUnit) ?: return ERR_INVALID_UNIT
        if (from.category != to.category) return ERR_INCOMPATIBLE
        val value = BigInteger.valueOf(valueMilli)
        val result =
            if (from.category == Category.Temperature) {
                temperature(value, fromUnit, toUnit) ?: return ERR_OUT_OF_RANGE
            } else {
                // The Rust domain multiplied in signed 128 bits and called overflow out of range.
                val scaled = within128(value * BigInteger.valueOf(from.numerator)) ?: return ERR_OUT_OF_RANGE
                val numerator = within128(scaled * BigInteger.valueOf(to.denominator)) ?: return ERR_OUT_OF_RANGE
                val denominator =
                    within128(BigInteger.valueOf(from.denominator) * BigInteger.valueOf(to.numerator)) ?: return ERR_OUT_OF_RANGE
                divRound(numerator, denominator)
            }
        if (result < LONG_MIN || result > LONG_MAX) return ERR_OUT_OF_RANGE
        return "OK:$result"
    }

    /** Null below absolute zero. */
    private fun temperature(
        value: BigInteger,
        from: Int,
        to: Int,
    ): BigInteger? {
        val zero = BigInteger.valueOf(ABSOLUTE_ZERO_MILLI)
        val freezing = BigInteger.valueOf(FREEZING_F_MILLI)
        val celsius =
            when (from) {
                CELSIUS -> value
                FAHRENHEIT -> divRound((value - freezing) * BigInteger.valueOf(5), BigInteger.valueOf(9))
                else -> value - zero
            }
        if (celsius < zero.negate()) return null
        return when (to) {
            CELSIUS -> celsius
            FAHRENHEIT -> divRound(celsius * BigInteger.valueOf(9), BigInteger.valueOf(5)) + freezing
            else -> celsius + zero
        }
    }

    /** Rounds half away from zero; the denominator is always positive. */
    private fun divRound(
        numerator: BigInteger,
        denominator: BigInteger,
    ): BigInteger {
        val half = denominator.shiftRight(1)
        return if (numerator.signum() >= 0) {
            (numerator + half) / denominator
        } else {
            ((numerator.negate() + half) / denominator).negate()
        }
    }
}
