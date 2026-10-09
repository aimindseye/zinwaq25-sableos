# Sable Weather user-managed cities (portable handoff source)

Pure-Kotlin model of the user-managed city list for Sable Weather: add, remove, select, reset to the built-in cities, a versioned
persistence codec with a small repository over a key-value store, the Start/Live refresh and per-city cache rules, and a keyboard-first
model of the manual city screen. It is a **handoff/source candidate intended to return to canonical** (`aimindseye/sableos`
`apps/r8/android/weather`); it is not a Titan-only fork of Weather and it mirrors none of the canonical Android source.

```text
ROLE=PORTABLE_REFERENCE_SOURCE
CANONICAL_RETURN=aimindseye/sableos apps/r8/android/weather @ caf98dde723d07a071d95aaa1ef27d578d3208d8
CANONICAL_CONSUMER=canonical Sable Weather (interface request IR-015)
MIRRORED_CANONICAL_FILES=NONE
GRADLE_MODULE=NO
ANDROID_MANIFEST=NO
APK=NO
NETWORK_CODE=NO
LOCATION_PERMISSION=NO
ANDROID_FRAMEWORK_DEPENDENCY=NO
NEW_EXTERNAL_DEPENDENCY=NO
```

It consumes the normalized key events of `reference/sable-start-type-to-find` (P1), following the canonical `platform_sable` NORMALIZED_KEY_INPUT_CONTRACT (head 80e3097bdef4cd2d5cbfd8ebde16390c29767918). The HTTPS/Open-Meteo transport, the JNI parsing and the
local snapshot cache stay in canonical Weather, unchanged. See `docs/SABLE_WEATHER_CITIES_SOURCE.md`.
