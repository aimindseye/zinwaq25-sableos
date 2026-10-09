use jni::objects::{JObject, JString};
use jni::sys::{jboolean, jdouble, jlong, jstring};
use jni::JNIEnv;
use sable_weather_core::{build_open_meteo_url, parse_open_meteo, provider_state, TemperatureUnit};

fn jni_string(env: &JNIEnv<'_>, value: String) -> jstring {
    match env.new_string(value) {
        Ok(value) => value.into_raw(),
        Err(_) => core::ptr::null_mut(),
    }
}

fn qualification() -> String {
    let fixture = r#"{
      "current":{
        "time":"2026-09-21T09:37",
        "temperature_2m":72.4,
        "apparent_temperature":71.8,
        "relative_humidity_2m":58,
        "weather_code":2,
        "wind_speed_10m":8.2,
        "wind_direction_10m":315
      },
      "hourly":{
        "time":["2026-09-21T09:00"],
        "temperature_2m":[72.4],
        "precipitation_probability":[12],
        "weather_code":[2]
      },
      "daily":{
        "time":["2026-09-21"],
        "weather_code":[2],
        "temperature_2m_max":[78.0],
        "temperature_2m_min":[64.0],
        "precipitation_probability_max":[18]
      }
    }"#;

    match parse_open_meteo(fixture) {
        Ok(snapshot)
            if snapshot.current.weather_code == 2
                && snapshot.hourly.len() == 1
                && snapshot.daily.len() == 1 =>
        {
            "PASS:weather-core:open-meteo:hourly:daily".to_owned()
        }
        Ok(snapshot) => format!(
            "FAIL:weather-core:code={}:hourly={}:daily={}",
            snapshot.current.weather_code,
            snapshot.hourly.len(),
            snapshot.daily.len(),
        ),
        Err(error) => format!("FAIL:weather-core:{error}"),
    }
}

#[allow(non_snake_case)]
#[no_mangle]
pub extern "system" fn Java_org_sableos_weather_WeatherNative_selfTest(
    env: JNIEnv<'_>,
    _this: JObject<'_>,
) -> jstring {
    jni_string(&env, qualification())
}

#[allow(non_snake_case)]
#[no_mangle]
pub extern "system" fn Java_org_sableos_weather_WeatherNative_buildForecastUrl(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    latitude: jdouble,
    longitude: jdouble,
    timezone: JString<'_>,
    fahrenheit: jboolean,
) -> jstring {
    let timezone: String = match env.get_string(&timezone) {
        Ok(value) => value.into(),
        Err(_) => return core::ptr::null_mut(),
    };
    let unit = if fahrenheit != 0 {
        TemperatureUnit::Fahrenheit
    } else {
        TemperatureUnit::Celsius
    };
    jni_string(
        &env,
        build_open_meteo_url(latitude, longitude, &timezone, unit),
    )
}

#[allow(non_snake_case)]
#[no_mangle]
pub extern "system" fn Java_org_sableos_weather_WeatherNative_parseForecast(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    json: JString<'_>,
) -> jstring {
    let json: String = match env.get_string(&json) {
        Ok(value) => value.into(),
        Err(_) => return core::ptr::null_mut(),
    };

    let result = match parse_open_meteo(&json) {
        Ok(snapshot) => format!("OK\n{}", snapshot.to_protocol()),
        Err(error) => format!("ERR|{error}"),
    };
    jni_string(&env, result)
}

#[allow(non_snake_case)]
#[no_mangle]
pub extern "system" fn Java_org_sableos_weather_WeatherNative_providerState(
    env: JNIEnv<'_>,
    _this: JObject<'_>,
    has_cached_snapshot: jboolean,
    last_success_epoch_seconds: jlong,
    now_epoch_seconds: jlong,
    fetch_failed: jboolean,
) -> jstring {
    let state = provider_state(
        has_cached_snapshot != 0,
        last_success_epoch_seconds,
        now_epoch_seconds,
        fetch_failed != 0,
    );
    jni_string(&env, state.as_str().to_owned())
}

#[cfg(test)]
mod tests {
    use super::qualification;

    #[test]
    fn jni_protocol_self_test_is_stable() {
        assert_eq!(qualification(), "PASS:weather-core:open-meteo:hourly:daily");
    }
}
