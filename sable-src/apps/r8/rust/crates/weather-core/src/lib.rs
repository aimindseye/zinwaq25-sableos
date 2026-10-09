//! Sable Weather interaction-neutral domain.
//!
//! The Android shell owns HTTPS transport, permissions, scheduling, widgets,
//! notifications and UI. This crate owns provider request construction,
//! provider freshness state, Open-Meteo normalization and condition mapping.

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ProviderState {
    Local,
    Fetching,
    Live,
    Stale,
    Failed,
}

impl ProviderState {
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Local => "LOCAL",
            Self::Fetching => "FETCHING",
            Self::Live => "LIVE",
            Self::Stale => "STALE",
            Self::Failed => "FAILED",
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TemperatureUnit {
    Fahrenheit,
    Celsius,
}

impl TemperatureUnit {
    const fn open_meteo_value(self) -> &'static str {
        match self {
            Self::Fahrenheit => "fahrenheit",
            Self::Celsius => "celsius",
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum WeatherCondition {
    Clear,
    PartlyCloudy,
    Cloudy,
    Fog,
    Rain,
    Snow,
    Storm,
    Unknown,
}

impl WeatherCondition {
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Clear => "CLEAR",
            Self::PartlyCloudy => "PARTLY_CLOUDY",
            Self::Cloudy => "CLOUDY",
            Self::Fog => "FOG",
            Self::Rain => "RAIN",
            Self::Snow => "SNOW",
            Self::Storm => "STORM",
            Self::Unknown => "UNKNOWN",
        }
    }
}

#[must_use]
pub const fn condition_for_code(code: i32) -> WeatherCondition {
    match code {
        0 => WeatherCondition::Clear,
        1 | 2 => WeatherCondition::PartlyCloudy,
        3 => WeatherCondition::Cloudy,
        45 | 48 => WeatherCondition::Fog,
        51 | 53 | 55 | 56 | 57 | 61 | 63 | 65 | 66 | 67 | 80 | 81 | 82 => WeatherCondition::Rain,
        71 | 73 | 75 | 77 | 85 | 86 => WeatherCondition::Snow,
        95 | 96 | 99 => WeatherCondition::Storm,
        _ => WeatherCondition::Unknown,
    }
}

#[must_use]
pub fn provider_state(
    has_cached_snapshot: bool,
    last_success_epoch_seconds: i64,
    now_epoch_seconds: i64,
    fetch_failed: bool,
) -> ProviderState {
    if !has_cached_snapshot {
        return if fetch_failed {
            ProviderState::Failed
        } else {
            ProviderState::Local
        };
    }

    if fetch_failed {
        return ProviderState::Stale;
    }

    let age = now_epoch_seconds.saturating_sub(last_success_epoch_seconds);
    if age <= 60 * 60 {
        ProviderState::Live
    } else {
        ProviderState::Stale
    }
}

#[must_use]
pub fn build_open_meteo_url(
    latitude: f64,
    longitude: f64,
    timezone: &str,
    unit: TemperatureUnit,
) -> String {
    format!(
        "https://api.open-meteo.com/v1/forecast?latitude={latitude:.5}&longitude={longitude:.5}&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m&hourly=temperature_2m,precipitation_probability,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=7&temperature_unit={}&wind_speed_unit=mph&timezone={}",
        unit.open_meteo_value(),
        percent_encode(timezone),
    )
}

#[derive(Clone, Debug, PartialEq)]
pub struct CurrentConditions {
    pub temperature: f64,
    pub apparent_temperature: f64,
    pub humidity: i32,
    pub weather_code: i32,
    pub wind_speed: f64,
    pub wind_direction: i32,
}

#[derive(Clone, Debug, PartialEq)]
pub struct HourForecast {
    pub time: String,
    pub temperature: f64,
    pub precipitation_probability: i32,
    pub weather_code: i32,
}

#[derive(Clone, Debug, PartialEq)]
pub struct DayForecast {
    pub date: String,
    pub weather_code: i32,
    pub high: f64,
    pub low: f64,
    pub precipitation_probability: i32,
}

#[derive(Clone, Debug, PartialEq)]
pub struct WeatherSnapshot {
    pub current: CurrentConditions,
    pub hourly: Vec<HourForecast>,
    pub daily: Vec<DayForecast>,
}

impl WeatherSnapshot {
    #[must_use]
    pub fn to_protocol(&self) -> String {
        let mut lines = Vec::with_capacity(2 + self.hourly.len() + self.daily.len());
        lines.push(format!(
            "CURRENT|{:.1}|{:.1}|{}|{}|{:.1}|{}|{}",
            self.current.temperature,
            self.current.apparent_temperature,
            self.current.humidity,
            self.current.weather_code,
            self.current.wind_speed,
            self.current.wind_direction,
            condition_for_code(self.current.weather_code).as_str(),
        ));

        for hour in &self.hourly {
            lines.push(format!(
                "HOURLY|{}|{:.1}|{}|{}|{}",
                hour.time,
                hour.temperature,
                hour.precipitation_probability,
                hour.weather_code,
                condition_for_code(hour.weather_code).as_str(),
            ));
        }

        for day in &self.daily {
            lines.push(format!(
                "DAILY|{}|{}|{:.1}|{:.1}|{}|{}",
                day.date,
                day.weather_code,
                day.high,
                day.low,
                day.precipitation_probability,
                condition_for_code(day.weather_code).as_str(),
            ));
        }

        lines.join("\n")
    }
}

pub fn parse_open_meteo(json: &str) -> Result<WeatherSnapshot, &'static str> {
    let current_object = object_for_key(json, "current").ok_or("current object missing")?;
    let hourly = object_for_key(json, "hourly").ok_or("hourly object missing")?;
    let daily = object_for_key(json, "daily").ok_or("daily object missing")?;
    let current_hour =
        string_for_key(current_object, "time").and_then(|value| local_hour_start(&value));

    let current = CurrentConditions {
        temperature: number_for_key(current_object, "temperature_2m")
            .ok_or("current temperature missing")?,
        apparent_temperature: number_for_key(current_object, "apparent_temperature")
            .ok_or("apparent temperature missing")?,
        humidity: number_for_key(current_object, "relative_humidity_2m")
            .ok_or("humidity missing")?
            .round() as i32,
        weather_code: number_for_key(current_object, "weather_code")
            .ok_or("weather code missing")?
            .round() as i32,
        wind_speed: number_for_key(current_object, "wind_speed_10m").ok_or("wind speed missing")?,
        wind_direction: number_for_key(current_object, "wind_direction_10m")
            .ok_or("wind direction missing")?
            .round() as i32,
    };

    let hourly_time = string_array_for_key(hourly, "time").ok_or("hourly time missing")?;
    let hourly_temp =
        number_array_for_key(hourly, "temperature_2m").ok_or("hourly temperature missing")?;
    let hourly_precip = number_array_for_key(hourly, "precipitation_probability")
        .ok_or("hourly precipitation missing")?;
    let hourly_code =
        number_array_for_key(hourly, "weather_code").ok_or("hourly weather code missing")?;

    let hourly_len = hourly_time
        .len()
        .min(hourly_temp.len())
        .min(hourly_precip.len())
        .min(hourly_code.len());
    let hourly_start = current_hour
        .as_deref()
        .and_then(|start| {
            hourly_time
                .iter()
                .position(|candidate| candidate.as_str() >= start)
        })
        .unwrap_or(0)
        .min(hourly_len);
    let hourly_end = hourly_start.saturating_add(24).min(hourly_len);

    let hourly = (hourly_start..hourly_end)
        .map(|index| HourForecast {
            time: hourly_time[index].clone(),
            temperature: hourly_temp[index],
            precipitation_probability: hourly_precip[index].round() as i32,
            weather_code: hourly_code[index].round() as i32,
        })
        .collect();

    let daily_date = string_array_for_key(daily, "time").ok_or("daily time missing")?;
    let daily_code =
        number_array_for_key(daily, "weather_code").ok_or("daily weather code missing")?;
    let daily_high =
        number_array_for_key(daily, "temperature_2m_max").ok_or("daily high missing")?;
    let daily_low = number_array_for_key(daily, "temperature_2m_min").ok_or("daily low missing")?;
    let daily_precip = number_array_for_key(daily, "precipitation_probability_max")
        .ok_or("daily precipitation missing")?;

    let daily_len = daily_date
        .len()
        .min(daily_code.len())
        .min(daily_high.len())
        .min(daily_low.len())
        .min(daily_precip.len())
        .min(7);

    let daily = (0..daily_len)
        .map(|index| DayForecast {
            date: daily_date[index].clone(),
            weather_code: daily_code[index].round() as i32,
            high: daily_high[index],
            low: daily_low[index],
            precipitation_probability: daily_precip[index].round() as i32,
        })
        .collect();

    Ok(WeatherSnapshot {
        current,
        hourly,
        daily,
    })
}

fn percent_encode(value: &str) -> String {
    let mut out = String::with_capacity(value.len());
    for byte in value.bytes() {
        match byte {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' => {
                out.push(char::from(byte));
            }
            _ => out.push_str(&format!("%{byte:02X}")),
        }
    }
    out
}

fn key_start<'a>(json: &'a str, key: &str) -> Option<&'a str> {
    let needle = format!("\"{key}\"");
    let start = json.find(&needle)? + needle.len();
    let rest = &json[start..];
    let colon = rest.find(':')? + 1;
    Some(rest[colon..].trim_start())
}

fn object_for_key<'a>(json: &'a str, key: &str) -> Option<&'a str> {
    let rest = key_start(json, key)?;
    balanced(rest, b'{', b'}')
}

fn array_for_key<'a>(json: &'a str, key: &str) -> Option<&'a str> {
    let rest = key_start(json, key)?;
    balanced(rest, b'[', b']')
}

fn balanced(text: &str, open: u8, close: u8) -> Option<&str> {
    let bytes = text.as_bytes();
    if bytes.first().copied()? != open {
        return None;
    }

    let mut depth = 0_i32;
    let mut in_string = false;
    let mut escaped = false;

    for (index, byte) in bytes.iter().copied().enumerate() {
        if in_string {
            if escaped {
                escaped = false;
                continue;
            }
            if byte == b'\\' {
                escaped = true;
            } else if byte == b'"' {
                in_string = false;
            }
            continue;
        }

        if byte == b'"' {
            in_string = true;
            continue;
        }

        if byte == open {
            depth += 1;
        } else if byte == close {
            depth -= 1;
            if depth == 0 {
                return Some(&text[..=index]);
            }
        }
    }

    None
}

fn string_for_key(json: &str, key: &str) -> Option<String> {
    let rest = key_start(json, key)?;
    let value = rest.strip_prefix('"')?;
    let end = value.find('"')?;
    Some(value[..end].to_owned())
}

fn local_hour_start(value: &str) -> Option<String> {
    let hour = value.get(..13)?;
    if value.as_bytes().get(10).copied() != Some(b'T') {
        return None;
    }
    Some(format!("{hour}:00"))
}

fn number_for_key(json: &str, key: &str) -> Option<f64> {
    let rest = key_start(json, key)?;
    let end = rest
        .find(|c: char| !(c.is_ascii_digit() || matches!(c, '-' | '+' | '.' | 'e' | 'E')))
        .unwrap_or(rest.len());
    rest[..end].parse().ok()
}

fn number_array_for_key(json: &str, key: &str) -> Option<Vec<f64>> {
    let array = array_for_key(json, key)?;
    let inner = array.strip_prefix('[')?.strip_suffix(']')?;
    let mut result = Vec::new();
    for value in inner.split(',') {
        let value = value.trim();
        if value.is_empty() || value == "null" {
            continue;
        }
        result.push(value.parse().ok()?);
    }
    Some(result)
}

fn string_array_for_key(json: &str, key: &str) -> Option<Vec<String>> {
    let array = array_for_key(json, key)?;
    let inner = array.strip_prefix('[')?.strip_suffix(']')?;
    let mut result = Vec::new();
    let mut cursor = inner;

    while let Some(start) = cursor.find('"') {
        cursor = &cursor[start + 1..];
        let end = cursor.find('"')?;
        result.push(cursor[..end].to_owned());
        cursor = &cursor[end + 1..];
    }

    Some(result)
}

#[cfg(test)]
mod tests {
    use super::*;

    const FIXTURE: &str = r#"
    {
      "current": {
        "time": "2026-09-21T09:37",
        "temperature_2m": 72.4,
        "apparent_temperature": 71.8,
        "relative_humidity_2m": 58,
        "weather_code": 2,
        "wind_speed_10m": 8.2,
        "wind_direction_10m": 315
      },
      "hourly": {
        "time": ["2026-09-21T00:00", "2026-09-21T09:00", "2026-09-21T10:00"],
        "temperature_2m": [60.0, 72.4, 73.1],
        "precipitation_probability": [0, 12, 14],
        "weather_code": [0, 2, 1]
      },
      "daily": {
        "time": ["2026-09-21", "2026-09-22"],
        "weather_code": [2, 0],
        "temperature_2m_max": [78.0, 80.0],
        "temperature_2m_min": [64.0, 65.0],
        "precipitation_probability_max": [18, 5]
      }
    }"#;

    #[test]
    fn open_meteo_parser_normalizes_current_hourly_and_daily() {
        let snapshot = parse_open_meteo(FIXTURE).unwrap();
        assert_eq!(snapshot.current.weather_code, 2);
        assert_eq!(snapshot.hourly.len(), 2);
        assert_eq!(snapshot.daily.len(), 2);
        assert_eq!(snapshot.hourly[0].time, "2026-09-21T09:00");
        assert_eq!(snapshot.hourly[1].temperature, 73.1);
        assert_eq!(snapshot.daily[0].precipitation_probability, 18);
    }

    #[test]
    fn provider_state_is_truthful_about_stale_cache() {
        assert_eq!(provider_state(false, 0, 10, false), ProviderState::Local);
        assert_eq!(provider_state(false, 0, 10, true), ProviderState::Failed);
        assert_eq!(provider_state(true, 1000, 1500, false), ProviderState::Live);
        assert_eq!(
            provider_state(true, 1000, 5000, false),
            ProviderState::Stale
        );
        assert_eq!(provider_state(true, 1000, 1200, true), ProviderState::Stale);
    }

    #[test]
    fn request_is_https_and_uses_provider_contract() {
        let url = build_open_meteo_url(
            40.7178,
            -74.0430,
            "America/New_York",
            TemperatureUnit::Fahrenheit,
        );
        assert!(url.starts_with("https://api.open-meteo.com/v1/forecast?"));
        assert!(url.contains("temperature_unit=fahrenheit"));
        assert!(url.contains("timezone=America%2FNew_York"));
        assert!(!url.starts_with("http://"));
    }

    #[test]
    fn weather_code_mapping_covers_expected_groups() {
        assert_eq!(condition_for_code(0), WeatherCondition::Clear);
        assert_eq!(condition_for_code(2), WeatherCondition::PartlyCloudy);
        assert_eq!(condition_for_code(63), WeatherCondition::Rain);
        assert_eq!(condition_for_code(75), WeatherCondition::Snow);
        assert_eq!(condition_for_code(95), WeatherCondition::Storm);
    }
}
