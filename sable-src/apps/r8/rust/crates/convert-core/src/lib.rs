//! Offline fixed-point unit conversion extracted from the Rustmix Wave domain model.

pub const FIXED_SCALE: i64 = 1_000;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum Unit {
    Millimeters,
    Centimeters,
    Meters,
    Kilometers,
    Inches,
    Feet,
    Miles,
    Grams,
    Kilograms,
    Ounces,
    Pounds,
    Celsius,
    Fahrenheit,
    Kelvin,
    Milliliters,
    Liters,
    Cups,
    Gallons,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ConvertError {
    IncompatibleUnits,
    BelowAbsoluteZero,
    Overflow,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum Category {
    Length,
    Mass,
    Temperature,
    Volume,
}

impl Unit {
    const fn category(self) -> Category {
        match self {
            Self::Millimeters
            | Self::Centimeters
            | Self::Meters
            | Self::Kilometers
            | Self::Inches
            | Self::Feet
            | Self::Miles => Category::Length,
            Self::Grams | Self::Kilograms | Self::Ounces | Self::Pounds => Category::Mass,
            Self::Celsius | Self::Fahrenheit | Self::Kelvin => Category::Temperature,
            Self::Milliliters | Self::Liters | Self::Cups | Self::Gallons => Category::Volume,
        }
    }
}

pub fn convert_milli(value_milli: i64, from: Unit, to: Unit) -> Result<i64, ConvertError> {
    if from.category() != to.category() {
        return Err(ConvertError::IncompatibleUnits);
    }
    if from.category() == Category::Temperature {
        return convert_temperature(value_milli, from, to);
    }
    let (from_num, from_den) = ratio(from).ok_or(ConvertError::IncompatibleUnits)?;
    let (to_num, to_den) = ratio(to).ok_or(ConvertError::IncompatibleUnits)?;
    let numerator = i128::from(value_milli)
        .checked_mul(from_num)
        .and_then(|value| value.checked_mul(to_den))
        .ok_or(ConvertError::Overflow)?;
    let denominator = from_den.checked_mul(to_num).ok_or(ConvertError::Overflow)?;
    i64::try_from(div_round(numerator, denominator)).map_err(|_| ConvertError::Overflow)
}

fn convert_temperature(value_milli: i64, from: Unit, to: Unit) -> Result<i64, ConvertError> {
    let celsius = match from {
        Unit::Celsius => i128::from(value_milli),
        Unit::Fahrenheit => div_round((i128::from(value_milli) - 32_000) * 5, 9),
        Unit::Kelvin => i128::from(value_milli) - 273_150,
        _ => return Err(ConvertError::IncompatibleUnits),
    };
    if celsius < -273_150 {
        return Err(ConvertError::BelowAbsoluteZero);
    }
    let converted = match to {
        Unit::Celsius => celsius,
        Unit::Fahrenheit => div_round(celsius * 9, 5) + 32_000,
        Unit::Kelvin => celsius + 273_150,
        _ => return Err(ConvertError::IncompatibleUnits),
    };
    i64::try_from(converted).map_err(|_| ConvertError::Overflow)
}

fn ratio(unit: Unit) -> Option<(i128, i128)> {
    match unit {
        Unit::Millimeters => Some((1, 1)),
        Unit::Centimeters => Some((10, 1)),
        Unit::Meters => Some((1_000, 1)),
        Unit::Kilometers => Some((1_000_000, 1)),
        Unit::Inches => Some((254, 10)),
        Unit::Feet => Some((3_048, 10)),
        Unit::Miles => Some((1_609_344, 1)),
        Unit::Grams => Some((1, 1)),
        Unit::Kilograms => Some((1_000, 1)),
        Unit::Ounces => Some((28_349_523_125, 1_000_000_000)),
        Unit::Pounds => Some((45_359_237, 100_000)),
        Unit::Milliliters => Some((1, 1)),
        Unit::Liters => Some((1_000, 1)),
        Unit::Cups => Some((2_365_882_365, 10_000_000)),
        Unit::Gallons => Some((3_785_411_784, 1_000_000)),
        Unit::Celsius | Unit::Fahrenheit | Unit::Kelvin => None,
    }
}

fn div_round(numerator: i128, denominator: i128) -> i128 {
    debug_assert!(denominator > 0);
    if numerator >= 0 {
        (numerator + denominator / 2) / denominator
    } else {
        -((-numerator + denominator / 2) / denominator)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reference_conversions() {
        assert_eq!(
            convert_milli(5_000, Unit::Miles, Unit::Kilometers).unwrap(),
            8_047
        );
        assert_eq!(
            convert_milli(1_000, Unit::Kilograms, Unit::Grams).unwrap(),
            1_000_000
        );
        assert_eq!(
            convert_milli(1_000, Unit::Liters, Unit::Milliliters).unwrap(),
            1_000_000
        );
    }

    #[test]
    fn temperatures_and_bounds() {
        assert_eq!(
            convert_milli(0, Unit::Celsius, Unit::Fahrenheit).unwrap(),
            32_000
        );
        assert_eq!(
            convert_milli(273_150, Unit::Kelvin, Unit::Celsius).unwrap(),
            0
        );
        assert_eq!(
            convert_milli(-1, Unit::Kelvin, Unit::Celsius),
            Err(ConvertError::BelowAbsoluteZero)
        );
    }

    #[test]
    fn incompatible_categories_fail_closed() {
        assert_eq!(
            convert_milli(1_000, Unit::Meters, Unit::Grams),
            Err(ConvertError::IncompatibleUnits)
        );
    }
}
