//! Interaction-neutral arithmetic domain for Sable Calculator.
//!
//! R8 keeps handheld interaction semantics, expression precedence, percent
//! behavior, history and repeated-equals policy in the Android product layer.
//! The Rust domain owns exact checked binary arithmetic plus a bounded set of
//! standard unary scientific functions used by the Scientific surface.

use core::fmt;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ArithmeticError {
    InvalidNumber,
    DivisionByZero,
    Overflow,
    Domain,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum BinaryOperation {
    Add,
    Subtract,
    Multiply,
    Divide,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ScientificOperation {
    SquareRoot,
    Square,
    Reciprocal,
    SinDegrees,
    CosDegrees,
    TanDegrees,
    NaturalLog,
    Log10,
}

pub fn apply_scientific(
    text: &str,
    operation: ScientificOperation,
) -> Result<f64, ArithmeticError> {
    let value = text
        .trim()
        .parse::<f64>()
        .map_err(|_| ArithmeticError::InvalidNumber)?;
    if !value.is_finite() {
        return Err(ArithmeticError::Overflow);
    }

    let result = match operation {
        ScientificOperation::SquareRoot => {
            if value < 0.0 {
                return Err(ArithmeticError::Domain);
            }
            value.sqrt()
        }
        ScientificOperation::Square => value * value,
        ScientificOperation::Reciprocal => {
            if value == 0.0 {
                return Err(ArithmeticError::DivisionByZero);
            }
            1.0 / value
        }
        ScientificOperation::SinDegrees => value.to_radians().sin(),
        ScientificOperation::CosDegrees => value.to_radians().cos(),
        ScientificOperation::TanDegrees => value.to_radians().tan(),
        ScientificOperation::NaturalLog => {
            if value <= 0.0 {
                return Err(ArithmeticError::Domain);
            }
            value.ln()
        }
        ScientificOperation::Log10 => {
            if value <= 0.0 {
                return Err(ArithmeticError::Domain);
            }
            value.log10()
        }
    };

    if result.is_nan() {
        return Err(ArithmeticError::Domain);
    }
    if !result.is_finite() {
        return Err(ArithmeticError::Overflow);
    }
    Ok(result)
}

/// Exact rational value produced from a finite decimal input.
///
/// The value is normalized after every operation. No floating-point arithmetic
/// or user-visible rounding policy is embedded in this type.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct ExactNumber {
    numerator: i128,
    denominator: i128,
}

impl ExactNumber {
    pub const ZERO: Self = Self {
        numerator: 0,
        denominator: 1,
    };

    #[must_use]
    pub const fn numerator(self) -> i128 {
        self.numerator
    }

    #[must_use]
    pub const fn denominator(self) -> i128 {
        self.denominator
    }

    pub fn parse_decimal(text: &str) -> Result<Self, ArithmeticError> {
        let text = text.trim();
        if text.is_empty() {
            return Err(ArithmeticError::InvalidNumber);
        }

        let (negative, unsigned) = match text.as_bytes().first().copied() {
            Some(b'+') => (false, &text[1..]),
            Some(b'-') => (true, &text[1..]),
            _ => (false, text),
        };
        if unsigned.is_empty() {
            return Err(ArithmeticError::InvalidNumber);
        }

        let mut parts = unsigned.split('.');
        let whole = parts.next().unwrap_or_default();
        let fraction = parts.next();
        if parts.next().is_some()
            || whole.is_empty()
            || !whole.bytes().all(|byte| byte.is_ascii_digit())
        {
            return Err(ArithmeticError::InvalidNumber);
        }

        let whole_value = whole
            .parse::<i128>()
            .map_err(|_| ArithmeticError::Overflow)?;

        let (fraction_value, denominator) = if let Some(fraction) = fraction {
            if fraction.is_empty() || !fraction.bytes().all(|byte| byte.is_ascii_digit()) {
                return Err(ArithmeticError::InvalidNumber);
            }
            let digits = u32::try_from(fraction.len()).map_err(|_| ArithmeticError::Overflow)?;
            let denominator = 10_i128
                .checked_pow(digits)
                .ok_or(ArithmeticError::Overflow)?;
            let value = fraction
                .parse::<i128>()
                .map_err(|_| ArithmeticError::Overflow)?;
            (value, denominator)
        } else {
            (0, 1)
        };

        let mut numerator = whole_value
            .checked_mul(denominator)
            .and_then(|value| value.checked_add(fraction_value))
            .ok_or(ArithmeticError::Overflow)?;
        if negative {
            numerator = numerator.checked_neg().ok_or(ArithmeticError::Overflow)?;
        }

        Self::new(numerator, denominator)
    }

    pub fn apply(self, operation: BinaryOperation, rhs: Self) -> Result<Self, ArithmeticError> {
        match operation {
            BinaryOperation::Add => {
                let left = self
                    .numerator
                    .checked_mul(rhs.denominator)
                    .ok_or(ArithmeticError::Overflow)?;
                let right = rhs
                    .numerator
                    .checked_mul(self.denominator)
                    .ok_or(ArithmeticError::Overflow)?;
                let numerator = left.checked_add(right).ok_or(ArithmeticError::Overflow)?;
                let denominator = self
                    .denominator
                    .checked_mul(rhs.denominator)
                    .ok_or(ArithmeticError::Overflow)?;
                Self::new(numerator, denominator)
            }
            BinaryOperation::Subtract => {
                let left = self
                    .numerator
                    .checked_mul(rhs.denominator)
                    .ok_or(ArithmeticError::Overflow)?;
                let right = rhs
                    .numerator
                    .checked_mul(self.denominator)
                    .ok_or(ArithmeticError::Overflow)?;
                let numerator = left.checked_sub(right).ok_or(ArithmeticError::Overflow)?;
                let denominator = self
                    .denominator
                    .checked_mul(rhs.denominator)
                    .ok_or(ArithmeticError::Overflow)?;
                Self::new(numerator, denominator)
            }
            BinaryOperation::Multiply => {
                let numerator = self
                    .numerator
                    .checked_mul(rhs.numerator)
                    .ok_or(ArithmeticError::Overflow)?;
                let denominator = self
                    .denominator
                    .checked_mul(rhs.denominator)
                    .ok_or(ArithmeticError::Overflow)?;
                Self::new(numerator, denominator)
            }
            BinaryOperation::Divide => {
                if rhs.numerator == 0 {
                    return Err(ArithmeticError::DivisionByZero);
                }
                let numerator = self
                    .numerator
                    .checked_mul(rhs.denominator)
                    .ok_or(ArithmeticError::Overflow)?;
                let denominator = self
                    .denominator
                    .checked_mul(rhs.numerator)
                    .ok_or(ArithmeticError::Overflow)?;
                Self::new(numerator, denominator)
            }
        }
    }

    fn new(numerator: i128, denominator: i128) -> Result<Self, ArithmeticError> {
        if denominator == 0 {
            return Err(ArithmeticError::DivisionByZero);
        }
        if numerator == 0 {
            return Ok(Self::ZERO);
        }

        let negative = (numerator < 0) ^ (denominator < 0);
        let numerator_magnitude = numerator.unsigned_abs();
        let denominator_magnitude = denominator.unsigned_abs();
        let divisor = gcd(numerator_magnitude, denominator_magnitude);

        let normalized_numerator =
            i128::try_from(numerator_magnitude / divisor).map_err(|_| ArithmeticError::Overflow)?;
        let normalized_denominator = i128::try_from(denominator_magnitude / divisor)
            .map_err(|_| ArithmeticError::Overflow)?;

        Ok(Self {
            numerator: if negative {
                normalized_numerator
                    .checked_neg()
                    .ok_or(ArithmeticError::Overflow)?
            } else {
                normalized_numerator
            },
            denominator: normalized_denominator,
        })
    }
}

impl fmt::Display for ExactNumber {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(formatter, "{}/{}", self.numerator, self.denominator)
    }
}

const fn gcd(mut left: u128, mut right: u128) -> u128 {
    while right != 0 {
        let remainder = left % right;
        left = right;
        right = remainder;
    }
    left
}

#[cfg(test)]
mod tests {
    use super::{ArithmeticError, BinaryOperation, ExactNumber};

    #[test]
    fn decimal_inputs_are_exact() {
        let one_tenth = ExactNumber::parse_decimal("0.1").unwrap();
        let two_tenths = ExactNumber::parse_decimal("0.2").unwrap();
        assert_eq!((one_tenth.numerator(), one_tenth.denominator()), (1, 10));
        assert_eq!((two_tenths.numerator(), two_tenths.denominator()), (1, 5));
    }

    #[test]
    fn four_basic_operations_are_checked_and_exact() {
        let one = ExactNumber::parse_decimal("1").unwrap();
        let three = ExactNumber::parse_decimal("3").unwrap();
        let quarter = ExactNumber::parse_decimal("0.25").unwrap();

        assert_eq!(
            one.apply(BinaryOperation::Divide, three).unwrap(),
            ExactNumber::new(1, 3).unwrap()
        );
        assert_eq!(
            quarter
                .apply(
                    BinaryOperation::Multiply,
                    ExactNumber::parse_decimal("4").unwrap(),
                )
                .unwrap(),
            one
        );
        assert_eq!(
            one.apply(BinaryOperation::Add, quarter).unwrap(),
            ExactNumber::new(5, 4).unwrap()
        );
        assert_eq!(
            one.apply(BinaryOperation::Subtract, quarter).unwrap(),
            ExactNumber::new(3, 4).unwrap()
        );
    }

    #[test]
    fn division_by_zero_fails_closed() {
        let one = ExactNumber::parse_decimal("1").unwrap();
        assert_eq!(
            one.apply(BinaryOperation::Divide, ExactNumber::ZERO),
            Err(ArithmeticError::DivisionByZero)
        );
    }

    #[test]
    fn interaction_policy_is_not_encoded_in_the_domain() {
        // The core intentionally has no expression evaluator, precedence table,
        // percent key, repeated-equals state, or history store.
        assert_eq!(
            ExactNumber::parse_decimal("-2.50").unwrap().to_string(),
            "-5/2"
        );
    }

    #[test]
    fn scientific_operations_cover_standard_unary_functions() {
        use super::{apply_scientific, ScientificOperation};

        assert_eq!(
            apply_scientific("9", ScientificOperation::SquareRoot).unwrap(),
            3.0
        );
        assert_eq!(
            apply_scientific("12", ScientificOperation::Square).unwrap(),
            144.0
        );
        assert!(
            (apply_scientific("30", ScientificOperation::SinDegrees).unwrap() - 0.5).abs()
                < 1.0e-12
        );
        assert_eq!(
            apply_scientific("100", ScientificOperation::Log10).unwrap(),
            2.0
        );
    }

    #[test]
    fn scientific_domain_errors_fail_closed() {
        use super::{apply_scientific, ScientificOperation};

        assert_eq!(
            apply_scientific("-1", ScientificOperation::SquareRoot),
            Err(ArithmeticError::Domain)
        );
        assert_eq!(
            apply_scientific("0", ScientificOperation::Reciprocal),
            Err(ArithmeticError::DivisionByZero)
        );
        assert_eq!(
            apply_scientific("0", ScientificOperation::NaturalLog),
            Err(ArithmeticError::Domain)
        );
    }
}
