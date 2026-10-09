//! Platform-neutral Sable Media domain extracted from the accepted ESP32
//! assistant concepts.
//!
//! Android owns playback, MediaSession, audio focus, storage access, Bluetooth
//! routing and lifecycle. This crate owns only deterministic parsing/probing
//! that can be host-qualified without Android or ESP hardware.

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct RadioStation {
    pub name: String,
    pub url: String,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct StationList {
    pub stations: Vec<RadioStation>,
    pub skipped_lines: usize,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct WavInfo {
    pub channels: u16,
    pub sample_rate: u32,
    pub bits_per_sample: u16,
    pub data_bytes: u32,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct Mp3Info {
    pub has_id3: bool,
    pub frame_sync: bool,
    pub sample_rate: u32,
    pub version: MpegVersion,
    pub layer: MpegLayer,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum MpegVersion {
    Mpeg1,
    Mpeg2,
    Mpeg25,
    Reserved,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum MpegLayer {
    Layer1,
    Layer2,
    Layer3,
    Reserved,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ProbeError {
    TooSmall,
    InvalidContainer,
    UnsupportedWavFormat,
    MissingFormatChunk,
    MissingDataChunk,
}

/// Parse TXT/CSV/M3U-style station lists accepted by the ESP assistant.
///
/// Supported rows include `name=url`, `name|url`, comma/semicolon/tab pairs,
/// bare HTTP(S) URLs, and `#EXTINF` followed by a URL.
#[must_use]
pub fn parse_station_list(text: &str) -> StationList {
    let mut stations = Vec::new();
    let mut skipped_lines = 0usize;
    let mut pending_m3u_name: Option<String> = None;

    for raw_line in text.lines() {
        let line = clean_field(raw_line);
        if line.is_empty() {
            continue;
        }

        if let Some(name) = parse_extinf_name(&line) {
            pending_m3u_name = Some(name);
            continue;
        }

        if line.starts_with('#') {
            continue;
        }

        if is_supported_radio_url(&line) {
            let name = pending_m3u_name
                .take()
                .unwrap_or_else(|| station_name_from_url(&line, stations.len() + 1));
            stations.push(RadioStation { name, url: line });
            continue;
        }

        if let Some(station) = parse_station_pair(&line, stations.len() + 1) {
            pending_m3u_name = None;
            stations.push(station);
        } else {
            pending_m3u_name = None;
            skipped_lines = skipped_lines.saturating_add(1);
        }
    }

    StationList {
        stations,
        skipped_lines,
    }
}

#[must_use]
pub fn is_supported_radio_url(url: &str) -> bool {
    url.starts_with("http://") || url.starts_with("https://")
}

/// Probe a complete or prefix WAV byte slice without performing filesystem I/O.
pub fn probe_wav(bytes: &[u8]) -> Result<WavInfo, ProbeError> {
    if bytes.len() < 12 {
        return Err(ProbeError::TooSmall);
    }
    if &bytes[0..4] != b"RIFF" || &bytes[8..12] != b"WAVE" {
        return Err(ProbeError::InvalidContainer);
    }

    let mut cursor = 12usize;
    let mut format: Option<(u16, u16, u32, u16)> = None;
    let mut data_bytes: Option<u32> = None;

    while cursor.saturating_add(8) <= bytes.len() {
        let chunk_id = &bytes[cursor..cursor + 4];
        let chunk_len = le_u32(&bytes[cursor + 4..cursor + 8]) as usize;
        cursor = cursor.saturating_add(8);
        let end = cursor.saturating_add(chunk_len);

        if chunk_id == b"fmt " {
            if chunk_len < 16 || end > bytes.len() {
                return Err(ProbeError::TooSmall);
            }
            let audio_format = le_u16(&bytes[cursor..cursor + 2]);
            let channels = le_u16(&bytes[cursor + 2..cursor + 4]);
            let sample_rate = le_u32(&bytes[cursor + 4..cursor + 8]);
            let bits_per_sample = le_u16(&bytes[cursor + 14..cursor + 16]);
            if audio_format != 1 {
                return Err(ProbeError::UnsupportedWavFormat);
            }
            format = Some((audio_format, channels, sample_rate, bits_per_sample));
        } else if chunk_id == b"data" {
            data_bytes = Some(u32::try_from(chunk_len).unwrap_or(u32::MAX));
            break;
        }

        if end > bytes.len() {
            return Err(ProbeError::TooSmall);
        }
        cursor = end.saturating_add(chunk_len & 1);
    }

    let (_, channels, sample_rate, bits_per_sample) =
        format.ok_or(ProbeError::MissingFormatChunk)?;
    let data_bytes = data_bytes.ok_or(ProbeError::MissingDataChunk)?;

    Ok(WavInfo {
        channels,
        sample_rate,
        bits_per_sample,
        data_bytes,
    })
}

/// Probe the leading bytes of an MP3 file for ID3 and MPEG frame information.
#[must_use]
pub fn probe_mp3(bytes: &[u8]) -> Mp3Info {
    let has_id3 = bytes.len() >= 10 && &bytes[0..3] == b"ID3";
    let declared_tag_end = if has_id3 {
        let tag_size = (((bytes[6] & 0x7f) as usize) << 21)
            | (((bytes[7] & 0x7f) as usize) << 14)
            | (((bytes[8] & 0x7f) as usize) << 7)
            | ((bytes[9] & 0x7f) as usize);
        10usize.saturating_add(tag_size)
    } else {
        0
    };

    let start = if declared_tag_end < bytes.len().saturating_sub(3) {
        declared_tag_end
    } else {
        0
    };

    for index in start..bytes.len().saturating_sub(3) {
        if bytes[index] != 0xff || (bytes[index + 1] & 0xe0) != 0xe0 {
            continue;
        }

        let header = ((bytes[index] as u32) << 24)
            | ((bytes[index + 1] as u32) << 16)
            | ((bytes[index + 2] as u32) << 8)
            | bytes[index + 3] as u32;
        let version_bits = (header >> 19) & 0x03;
        let layer_bits = (header >> 17) & 0x03;
        let sample_rate_index = (header >> 10) & 0x03;

        return Mp3Info {
            has_id3,
            frame_sync: true,
            sample_rate: sample_rate_from_bits(version_bits, sample_rate_index),
            version: match version_bits {
                0 => MpegVersion::Mpeg25,
                2 => MpegVersion::Mpeg2,
                3 => MpegVersion::Mpeg1,
                _ => MpegVersion::Reserved,
            },
            layer: match layer_bits {
                1 => MpegLayer::Layer3,
                2 => MpegLayer::Layer2,
                3 => MpegLayer::Layer1,
                _ => MpegLayer::Reserved,
            },
        };
    }

    Mp3Info {
        has_id3,
        frame_sync: false,
        sample_rate: 0,
        version: MpegVersion::Reserved,
        layer: MpegLayer::Reserved,
    }
}

fn parse_station_pair(line: &str, fallback_index: usize) -> Option<RadioStation> {
    for separator in ['=', '|', ',', ';', '\t'] {
        if let Some((left, right)) = line.split_once(separator) {
            let left = clean_field(left);
            let right = clean_field(right);

            if is_supported_radio_url(&left) {
                let name = if right.is_empty() {
                    station_name_from_url(&left, fallback_index)
                } else {
                    right
                };
                return Some(RadioStation { name, url: left });
            }
            if is_supported_radio_url(&right) {
                let name = if left.is_empty() {
                    station_name_from_url(&right, fallback_index)
                } else {
                    left
                };
                return Some(RadioStation { name, url: right });
            }
        }
    }
    None
}

fn parse_extinf_name(line: &str) -> Option<String> {
    if !line.to_ascii_uppercase().starts_with("#EXTINF") {
        return None;
    }
    let (_, name) = line.rsplit_once(',')?;
    let name = clean_field(name);
    (!name.is_empty()).then_some(name)
}

fn clean_field(input: &str) -> String {
    input
        .trim_start_matches('\u{feff}')
        .trim()
        .trim_matches('"')
        .trim_matches('\'')
        .trim()
        .to_string()
}

fn station_name_from_url(url: &str, fallback_index: usize) -> String {
    let without_scheme = url
        .strip_prefix("http://")
        .or_else(|| url.strip_prefix("https://"))
        .unwrap_or(url);
    let host = without_scheme.split('/').next().unwrap_or("").trim();
    if host.is_empty() {
        format!("Station {fallback_index}")
    } else {
        host.chars().take(48).collect()
    }
}

fn sample_rate_from_bits(version_bits: u32, index: u32) -> u32 {
    if index >= 3 {
        return 0;
    }
    let mpeg1 = [44_100u32, 48_000, 32_000];
    let mpeg2 = [22_050u32, 24_000, 16_000];
    let mpeg25 = [11_025u32, 12_000, 8_000];
    match version_bits {
        3 => mpeg1[index as usize],
        2 => mpeg2[index as usize],
        0 => mpeg25[index as usize],
        _ => 0,
    }
}

fn le_u16(bytes: &[u8]) -> u16 {
    (bytes[0] as u16) | ((bytes[1] as u16) << 8)
}

fn le_u32(bytes: &[u8]) -> u32 {
    (bytes[0] as u32)
        | ((bytes[1] as u32) << 8)
        | ((bytes[2] as u32) << 16)
        | ((bytes[3] as u32) << 24)
}

#[cfg(test)]
mod tests {
    use super::{parse_station_list, probe_mp3, probe_wav, MpegLayer, MpegVersion, WavInfo};

    #[test]
    fn station_parser_accepts_text_pairs_and_m3u() {
        let parsed = parse_station_list(
            "#EXTM3U\n#EXTINF:-1,News Radio\nhttps://radio.example/live.mp3\nJazz|http://jazz.example/stream\ninvalid row\n",
        );
        assert_eq!(parsed.stations.len(), 2);
        assert_eq!(parsed.stations[0].name, "News Radio");
        assert_eq!(parsed.stations[1].name, "Jazz");
        assert_eq!(parsed.skipped_lines, 1);
    }

    #[test]
    fn wav_probe_reads_pcm_metadata_without_playback_code() {
        let mut wav = Vec::new();
        wav.extend_from_slice(b"RIFF");
        wav.extend_from_slice(&36u32.to_le_bytes());
        wav.extend_from_slice(b"WAVEfmt ");
        wav.extend_from_slice(&16u32.to_le_bytes());
        wav.extend_from_slice(&1u16.to_le_bytes());
        wav.extend_from_slice(&2u16.to_le_bytes());
        wav.extend_from_slice(&44_100u32.to_le_bytes());
        wav.extend_from_slice(&176_400u32.to_le_bytes());
        wav.extend_from_slice(&4u16.to_le_bytes());
        wav.extend_from_slice(&16u16.to_le_bytes());
        wav.extend_from_slice(b"data");
        wav.extend_from_slice(&4u32.to_le_bytes());
        wav.extend_from_slice(&[0, 0, 0, 0]);

        assert_eq!(
            probe_wav(&wav).unwrap(),
            WavInfo {
                channels: 2,
                sample_rate: 44_100,
                bits_per_sample: 16,
                data_bytes: 4,
            }
        );
    }

    #[test]
    fn mp3_probe_recognizes_mpeg1_layer3_header() {
        let info = probe_mp3(&[0xff, 0xfb, 0x90, 0x64]);
        assert!(info.frame_sync);
        assert_eq!(info.version, MpegVersion::Mpeg1);
        assert_eq!(info.layer, MpegLayer::Layer3);
        assert_eq!(info.sample_rate, 44_100);
    }
}
