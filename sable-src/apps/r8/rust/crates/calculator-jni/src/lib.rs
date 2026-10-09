use jni::objects::{JObject, JString};
use jni::sys::{jint, jstring};
use jni::JNIEnv;
use sable_calculator_core::{
    apply_scientific, ArithmeticError, BinaryOperation, ExactNumber, ScientificOperation,
};

fn operation_from_code(code: i32) -> Option<BinaryOperation> {
    match code {
        0 => Some(BinaryOperation::Add),
        1 => Some(BinaryOperation::Subtract),
        2 => Some(BinaryOperation::Multiply),
        3 => Some(BinaryOperation::Divide),
        _ => None,
    }
}

fn scientific_operation_from_code(code: i32) -> Option<ScientificOperation> {
    match code {
        0 => Some(ScientificOperation::SquareRoot),
        1 => Some(ScientificOperation::Square),
        2 => Some(ScientificOperation::Reciprocal),
        3 => Some(ScientificOperation::SinDegrees),
        4 => Some(ScientificOperation::CosDegrees),
        5 => Some(ScientificOperation::TanDegrees),
        6 => Some(ScientificOperation::NaturalLog),
        7 => Some(ScientificOperation::Log10),
        _ => None,
    }
}

fn arithmetic_error(error: ArithmeticError) -> &'static str {
    match error {
        ArithmeticError::InvalidNumber => "ERR:INVALID_NUMBER",
        ArithmeticError::DivisionByZero => "ERR:DIVIDE_BY_ZERO",
        ArithmeticError::Overflow => "ERR:OVERFLOW",
        ArithmeticError::Domain => "ERR:DOMAIN",
    }
}

fn apply_binary(left: &str, operation: i32, right: &str) -> String {
    let Some(operation) = operation_from_code(operation) else {
        return "ERR:INVALID_OPERATION".to_owned();
    };

    let left = match ExactNumber::parse_decimal(left) {
        Ok(value) => value,
        Err(error) => return arithmetic_error(error).to_owned(),
    };

    let right = match ExactNumber::parse_decimal(right) {
        Ok(value) => value,
        Err(error) => return arithmetic_error(error).to_owned(),
    };

    match left.apply(operation, right) {
        Ok(value) => format!("OK:{value}"),
        Err(error) => arithmetic_error(error).to_owned(),
    }
}

fn format_scientific(value: f64) -> String {
    if value == 0.0 {
        return "0".to_owned();
    }

    let magnitude = value.abs();
    if !(1.0e-9..1.0e12).contains(&magnitude) {
        return format!("{value:.10e}");
    }

    let mut rendered = format!("{value:.12}");
    while rendered.contains('.') && rendered.ends_with('0') {
        rendered.pop();
    }
    if rendered.ends_with('.') {
        rendered.pop();
    }
    rendered
}

fn apply_scientific_protocol(input: &str, operation: i32) -> String {
    let Some(operation) = scientific_operation_from_code(operation) else {
        return "ERR:INVALID_OPERATION".to_owned();
    };

    match apply_scientific(input, operation) {
        Ok(value) => format!("OK:{}", format_scientific(value)),
        Err(error) => arithmetic_error(error).to_owned(),
    }
}

fn qualification() -> String {
    match (
        apply_binary("0.1", 0, "0.2").as_str(),
        apply_scientific_protocol("9", 0).as_str(),
    ) {
        ("OK:3/10", "OK:3") => "PASS:calculator-core:3/10:scientific:3".to_owned(),
        (binary, scientific) => format!("FAIL:calculator-core:{binary}:{scientific}"),
    }
}

#[allow(non_snake_case)]
#[no_mangle]
pub extern "system" fn Java_org_sableos_calculator_NativeBridge_selfTest(
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
pub extern "system" fn Java_org_sableos_calculator_NativeBridge_applyBinary(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    left: JString<'_>,
    operation: jint,
    right: JString<'_>,
) -> jstring {
    let left: String = match env.get_string(&left) {
        Ok(value) => value.into(),
        Err(_) => return core::ptr::null_mut(),
    };

    let right: String = match env.get_string(&right) {
        Ok(value) => value.into(),
        Err(_) => return core::ptr::null_mut(),
    };

    match env.new_string(apply_binary(&left, operation, &right)) {
        Ok(value) => value.into_raw(),
        Err(_) => core::ptr::null_mut(),
    }
}

#[allow(non_snake_case)]
#[no_mangle]
pub extern "system" fn Java_org_sableos_calculator_NativeBridge_applyScientific(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    input: JString<'_>,
    operation: jint,
) -> jstring {
    let input: String = match env.get_string(&input) {
        Ok(value) => value.into(),
        Err(_) => return core::ptr::null_mut(),
    };

    match env.new_string(apply_scientific_protocol(&input, operation)) {
        Ok(value) => value.into_raw(),
        Err(_) => core::ptr::null_mut(),
    }
}

#[cfg(test)]
mod tests {
    use super::{apply_binary, apply_scientific_protocol};

    #[test]
    fn product_protocol_preserves_exact_arithmetic() {
        assert_eq!(apply_binary("0.1", 0, "0.2"), "OK:3/10");
        assert_eq!(apply_binary("1", 3, "4"), "OK:1/4");
        assert_eq!(apply_binary("-2.50", 0, "1"), "OK:-3/2");
    }

    #[test]
    fn product_protocol_maps_errors() {
        assert_eq!(apply_binary("1", 99, "2"), "ERR:INVALID_OPERATION");
        assert_eq!(apply_binary("invalid", 0, "2"), "ERR:INVALID_NUMBER");
        assert_eq!(apply_binary("1", 3, "0"), "ERR:DIVIDE_BY_ZERO");
        assert_eq!(
            apply_binary("170141183460469231731687303715884105728", 0, "1",),
            "ERR:OVERFLOW",
        );
    }

    #[test]
    fn scientific_protocol_is_stable() {
        assert_eq!(apply_scientific_protocol("9", 0), "OK:3");
        assert_eq!(apply_scientific_protocol("30", 3), "OK:0.5");
        assert_eq!(apply_scientific_protocol("100", 7), "OK:2");
        assert_eq!(apply_scientific_protocol("-1", 0), "ERR:DOMAIN");
        assert_eq!(apply_scientific_protocol("0", 2), "ERR:DIVIDE_BY_ZERO");
    }
}
