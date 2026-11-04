package org.sableos.calculator

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Sable Calculator's arithmetic domain, in Kotlin (it was the calculator-core Rust crate).
 *
 * Binary arithmetic is exact: each decimal input becomes a normalized fraction whose numerator
 * and denominator stay within signed 128 bits, as before, so nothing is rounded into floating
 * point. The unary scientific functions use doubles in degrees. Results keep the string protocol
 * the screen already reads: `OK:<numerator>/<denominator>` or `OK:<decimal>`, else `ERR:<reason>`.
 */
internal object CalculatorCore {
    private const val ERR_INVALID_NUMBER = "ERR:INVALID_NUMBER"
    private const val ERR_DIVIDE_BY_ZERO = "ERR:DIVIDE_BY_ZERO"
    private const val ERR_OVERFLOW = "ERR:OVERFLOW"
    private const val ERR_DOMAIN = "ERR:DOMAIN"
    private const val ERR_INVALID_OPERATION = "ERR:INVALID_OPERATION"

    /** Operation codes 0 +, 1 −, 2 ×, 3 ÷, as [CalculatorKeys] sends them. */
    fun applyBinary(
        left: String,
        operation: Int,
        right: String,
    ): String {
        if (operation !in 0..3) return ERR_INVALID_OPERATION
        return try {
            val result = Fraction.parse(left).apply(operation, Fraction.parse(right))
            "OK:${result.numerator}/${result.denominator}"
        } catch (error: CalcError) {
            error.protocol
        }
    }

    /** Operation codes 0 √, 1 x², 2 1/x, 3 sin, 4 cos, 5 tan (degrees), 6 ln, 7 log. */
    fun applyScientific(
        input: String,
        operation: Int,
    ): String {
        if (operation !in 0..7) return ERR_INVALID_OPERATION
        return try {
            "OK:${formatScientific(scientific(input, operation))}"
        } catch (error: CalcError) {
            error.protocol
        }
    }

    private fun scientific(
        text: String,
        operation: Int,
    ): Double {
        val value = parseDouble(text.trim()) ?: throw CalcError(ERR_INVALID_NUMBER)
        if (!value.isFinite()) throw CalcError(ERR_OVERFLOW)
        val result =
            when (operation) {
                0 -> if (value < 0.0) throw CalcError(ERR_DOMAIN) else sqrt(value)
                1 -> value * value
                2 -> if (value == 0.0) throw CalcError(ERR_DIVIDE_BY_ZERO) else 1.0 / value
                3 -> sin(Math.toRadians(value))
                4 -> cos(Math.toRadians(value))
                5 -> tan(Math.toRadians(value))
                6 -> if (value <= 0.0) throw CalcError(ERR_DOMAIN) else ln(value)
                else -> if (value <= 0.0) throw CalcError(ERR_DOMAIN) else log10(value)
            }
        if (result.isNaN()) throw CalcError(ERR_DOMAIN)
        if (!result.isFinite()) throw CalcError(ERR_OVERFLOW)
        return result
    }

    /** Plain decimal or exponent form only; no hex, no `d`/`f` suffix, no padding. */
    private val DECIMAL = Regex("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")

    /** Rust's float parser also took inf, infinity and NaN; they still end as ERR:OVERFLOW. */
    private val NOT_FINITE = Regex("[+-]?(inf|infinity|nan)", RegexOption.IGNORE_CASE)

    private fun parseDouble(text: String): Double? =
        when {
            DECIMAL.matches(text) -> text.toDoubleOrNull()
            NOT_FINITE.matches(text) -> Double.POSITIVE_INFINITY
            else -> null
        }

    private const val PLAIN_MIN = 1.0e-9
    private const val PLAIN_MAX = 1.0e12
    private const val PLAIN_DECIMALS = 12
    private const val EXPONENT_DIGITS = 11

    /** Up to 12 decimals with trailing zeros dropped; outside 1e-9..1e12, ten-decimal exponent form. */
    internal fun formatScientific(value: Double): String {
        if (value == 0.0) return "0"
        val sign = if (value < 0) "-" else ""
        val magnitude = abs(value)
        val exact = BigDecimal(magnitude)
        if (magnitude < PLAIN_MIN || magnitude >= PLAIN_MAX) {
            val rounded = exact.round(MathContext(EXPONENT_DIGITS, RoundingMode.HALF_EVEN))
            val digits = rounded.unscaledValue().toString().padEnd(EXPONENT_DIGITS, '0').take(EXPONENT_DIGITS)
            val exponent = rounded.precision() - rounded.scale() - 1
            return "$sign${digits[0]}.${digits.substring(1)}e$exponent"
        }
        var rendered = exact.setScale(PLAIN_DECIMALS, RoundingMode.HALF_EVEN).toPlainString()
        rendered = rendered.trimEnd('0').trimEnd('.')
        return if (rendered == "0") "0" else "$sign$rendered"
    }

    private class CalcError(
        val protocol: String,
    ) : Exception(protocol)

    /** A normalized fraction kept within the signed 128-bit range the Rust domain used. */
    private class Fraction(
        val numerator: BigInteger,
        val denominator: BigInteger,
    ) {
        fun apply(
            operation: Int,
            rhs: Fraction,
        ): Fraction =
            when (operation) {
                0 -> of(checked(numerator * rhs.denominator) + checked(rhs.numerator * denominator), denominator * rhs.denominator)
                1 -> of(checked(numerator * rhs.denominator) - checked(rhs.numerator * denominator), denominator * rhs.denominator)
                2 -> of(numerator * rhs.numerator, denominator * rhs.denominator)
                else -> {
                    if (rhs.numerator.signum() == 0) throw CalcError(ERR_DIVIDE_BY_ZERO)
                    of(numerator * rhs.denominator, denominator * rhs.numerator)
                }
            }

        companion object {
            private val MAX = BigInteger.ONE.shiftLeft(127) - BigInteger.ONE
            private val MIN = BigInteger.ONE.shiftLeft(127).negate()

            fun checked(value: BigInteger): BigInteger {
                if (value > MAX || value < MIN) throw CalcError(ERR_OVERFLOW)
                return value
            }

            fun of(
                numerator: BigInteger,
                denominator: BigInteger,
            ): Fraction {
                checked(numerator)
                checked(denominator)
                if (denominator.signum() == 0) throw CalcError(ERR_DIVIDE_BY_ZERO)
                if (numerator.signum() == 0) return Fraction(BigInteger.ZERO, BigInteger.ONE)
                val negative = (numerator.signum() < 0) != (denominator.signum() < 0)
                val divisor = numerator.abs().gcd(denominator.abs())
                val top = checked(numerator.abs() / divisor)
                val bottom = checked(denominator.abs() / divisor)
                return Fraction(if (negative) top.negate() else top, bottom)
            }

            fun parse(raw: String): Fraction {
                val text = raw.trim()
                if (text.isEmpty()) throw CalcError(ERR_INVALID_NUMBER)
                val negative = text[0] == '-'
                val unsigned = if (text[0] == '-' || text[0] == '+') text.substring(1) else text
                val parts = unsigned.split('.')
                val whole = parts[0]
                if (unsigned.isEmpty() || parts.size > 2 || whole.isEmpty() || !whole.all { it in '0'..'9' }) {
                    throw CalcError(ERR_INVALID_NUMBER)
                }
                val fraction = parts.getOrNull(1)
                if (fraction != null && (fraction.isEmpty() || !fraction.all { it in '0'..'9' })) {
                    throw CalcError(ERR_INVALID_NUMBER)
                }
                val wholeValue = checked(BigInteger(whole))
                val denominator = checked(BigInteger.TEN.pow(fraction?.length ?: 0))
                val fractionValue = if (fraction == null) BigInteger.ZERO else checked(BigInteger(fraction))
                val numerator = checked(checked(wholeValue * denominator) + fractionValue)
                return of(if (negative) numerator.negate() else numerator, denominator)
            }
        }
    }
}
