use jni::objects::JObject;
use jni::sys::{jint, jlong, jstring};
use jni::JNIEnv;
use sable_convert_core::{convert_milli, ConvertError, Unit};

fn unit_from_code(code: i32) -> Option<Unit> {
    match code {
        0 => Some(Unit::Millimeters),
        1 => Some(Unit::Centimeters),
        2 => Some(Unit::Meters),
        3 => Some(Unit::Kilometers),
        4 => Some(Unit::Inches),
        5 => Some(Unit::Feet),
        6 => Some(Unit::Miles),
        7 => Some(Unit::Grams),
        8 => Some(Unit::Kilograms),
        9 => Some(Unit::Ounces),
        10 => Some(Unit::Pounds),
        11 => Some(Unit::Celsius),
        12 => Some(Unit::Fahrenheit),
        13 => Some(Unit::Kelvin),
        14 => Some(Unit::Milliliters),
        15 => Some(Unit::Liters),
        16 => Some(Unit::Cups),
        17 => Some(Unit::Gallons),
        _ => None,
    }
}

fn convert_error(error: ConvertError) -> &'static str {
    match error {
        ConvertError::IncompatibleUnits => "ERR:INCOMPATIBLE_UNITS",
        ConvertError::BelowAbsoluteZero | ConvertError::Overflow => "ERR:OUT_OF_RANGE",
    }
}

fn convert_protocol(value_milli: i64, from_code: i32, to_code: i32) -> String {
    let Some(from) = unit_from_code(from_code) else {
        return "ERR:INVALID_UNIT".to_owned();
    };

    let Some(to) = unit_from_code(to_code) else {
        return "ERR:INVALID_UNIT".to_owned();
    };

    match convert_milli(value_milli, from, to) {
        Ok(value) => format!("OK:{value}"),
        Err(error) => convert_error(error).to_owned(),
    }
}

fn qualification() -> String {
    match convert_protocol(5_000, 6, 3).as_str() {
        "OK:8047" => "PASS:convert-core:8047".to_owned(),
        result => format!("FAIL:convert-core:{result}"),
    }
}

#[allow(non_snake_case)]
#[no_mangle]
pub extern "system" fn Java_org_sableos_convert_NativeBridge_selfTest(
    env: JNIEnv<'_>,
    _this: JObject<'_>,
) -> jstring {
    match env.new_string(qualification()) {
        Ok(value) => value.into_raw(),
        Err(_) => core::ptr::null_mut(),
    }
}

#[allow(non_snake_case)]
#[no_mangle]
pub extern "system" fn Java_org_sableos_convert_NativeBridge_convertMilli(
    env: JNIEnv<'_>,
    _this: JObject<'_>,
    value_milli: jlong,
    from_unit: jint,
    to_unit: jint,
) -> jstring {
    match env.new_string(convert_protocol(value_milli, from_unit, to_unit)) {
        Ok(value) => value.into_raw(),
        Err(_) => core::ptr::null_mut(),
    }
}

#[cfg(test)]
mod tests {
    use super::{convert_protocol, unit_from_code};

    #[test]
    fn all_frozen_unit_codes_are_defined() {
        for code in 0..=17 {
            assert!(unit_from_code(code).is_some(), "missing unit code {code}");
        }

        assert!(unit_from_code(-1).is_none());
        assert!(unit_from_code(18).is_none());
    }

    #[test]
    fn product_protocol_matches_reference_conversions() {
        assert_eq!(convert_protocol(5_000, 6, 3), "OK:8047");
        assert_eq!(convert_protocol(0, 11, 12), "OK:32000");
        assert_eq!(convert_protocol(273_150, 13, 11), "OK:0");
    }

    #[test]
    fn product_protocol_maps_errors() {
        assert_eq!(convert_protocol(1_000, -1, 3), "ERR:INVALID_UNIT");
        assert_eq!(convert_protocol(1_000, 0, 8), "ERR:INCOMPATIBLE_UNITS",);
        assert_eq!(convert_protocol(-273_151, 11, 12), "ERR:OUT_OF_RANGE",);
        assert_eq!(convert_protocol(i64::MAX, 6, 0), "ERR:OUT_OF_RANGE",);
    }
}
