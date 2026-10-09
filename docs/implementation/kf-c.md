# KF-C: Sable Tools product consolidation

Package: **KF-C** (DESIGN-KF-C, TOOLS-I1 to TOOLS-I4; TOOLS-I5 is device qualification and is not done).
Branch: `q25/kf-c`.

Design inputs (read-only):
`platform_sable/docs/design/SABLE_TOOLS_PRODUCT_CONSOLIDATION.md` (the contract),
`HARDWARE_DIAGNOSTICS_AND_DIALER_CODES.md`, `TOOLBOX_HARDWARE_UTILITIES_UX.md`,
`SUBSCREEN_SHORTCUT_KEYBOARD_UX.md` (curated SubScreen modes), `MOBILE_MANAGER_APP_CONTROLS_UX.md`
(Network Manager ownership), `TITAN_FAMILY_BASE_CAPABILITY_MATRIX.md`, and the BB10 tools reference
(`sableos` branch `docs/keyboard-first-tools-bb10-reference-20260919`,
`docs/KEYBOARD_FIRST_SABLE_TOOLS_REFERENCE.md`).

```text
BUILD_TESTED=NO      (Gradle/AGP cannot run in this environment; the Android code is uncompiled)
DEVICE_TESTED=NO     (nothing here has run on a Q25 or any other phone)
PURE_TESTS=PASS      (66 JUnit tests over the android-free core, see "Tests")
KTLINT=PASS DETEKT=PASS (ktlint 1.8.0 android_studio style and detekt 1.23.8 with sable-src/config/detekt.yml,
                         run as CLIs over the new module; the Gradle plugins could not run)
```

## What was built

One new app, **Sable Tools** (`org.sableos.tools`), Gradle module `:tools` in
`sable-src/apps/titan2/platform/tools` (next to the other keyboard-first platform apps; plain framework
views like Radio Diag, no Compose, minSdk 36). It is common source for every Sable keyboard device:
device differences come only from the capability profile picked by `ro.sable.profile.id`; there is no
device-model branching in the UI.

### TOOLS-I1: shell, capability model, Utilities / Diagnostics / Reports

* `core/Capability.kt`: hardware facts (`Hardware`), runtime probe results (`Probe`), the capabilities
  that gate tools (`Capability`, with all-of/any-of hardware needs), and profile declarations
  (`Declared` = UNKNOWN / UNSUPPORTED / DIAGNOSTIC_ONLY / SUPPORTED, with an evidence string).
* `core/ToolsDeviceProfile.kt`: the only place device differences live. Profiles never inherit from
  each other. Current table:

  | Profile | Sensor tools | Flashlight / magnifier / noise / speed | IR remote | IR learning | FM | SubScreen | Factory bridge |
  |---|---|---|---|---|---|---|---|
  | `titan2` | SUPPORTED (stock Toolbox) | SUPPORTED | SUPPORTED | UNKNOWN | DIAGNOSTIC_ONLY | SUPPORTED | DIAGNOSTIC_ONLY |
  | `titan2-elite` | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
  | `zinwa-q27` | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
  | `zinwa-q25` | DIAGNOSTIC_ONLY (spec only, NOT_RUN) | DIAGNOSTIC_ONLY | **UNSUPPORTED** (no IR in the Q25 spec) | UNSUPPORTED | DIAGNOSTIC_ONLY (MT6631, NOT_RUN) | UNSUPPORTED | UNKNOWN |
  | anything else | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |

  So on a Q25 a normal user sees **no Utilities** until TOOLS-I5 qualifies them; with developer mode
  the sensor/camera/mic/GPS tools appear with a "Developer only" badge so they can be qualified. IR
  remote and SubScreen never appear on a Q25.
* `core/CapabilityGate.kt`: the design rule set (UNKNOWN and UNSUPPORTED hidden, DIAGNOSTIC_ONLY
  developer-mode only, SUPPORTED visible), plus: the runtime probe can only remove visibility, never
  grant it (a SUPPORTED capability whose hardware probes ABSENT is hidden and flagged as a mismatch in
  the developer capability status). Badges: Available / Needs permission / Needs calibration /
  Developer only / Unavailable on this device (the last never appears on the home screen).
  `DeveloperMode` needs both Android developer options and the in-app opt-in.
* `core/ToolCatalog.kt`: every tool with its section, kind, capability gate, runtime permissions,
  developer tier and search keywords; `PermissionPlan` (nothing at launch, missing permissions computed
  per tool when opened; the pedometer degrades to an accelerometer estimate without
  ACTIVITY_RECOGNITION).
* `core/HomeModel.kt`: title + profile summary, bounded Recent row (4), then Utilities, Diagnostics,
  Reports; only listed tools, empty sections dropped; type-to-filter matching (word prefixes plus
  infix for 3+ characters).
* `core/KeyCommands.kt`: the keyboard-first map. Printable keys filter on home, `/` explicit search,
  arrows move, Enter opens, Menu or Fn+Enter actions, Back/Esc one level, Tab/Shift+Tab fields,
  Ctrl+C copy; C/R/H only inside measurement tools (C only when calibratable), I info in tools; all
  single letters off while a text field has focus; key-capture screens take every key and leave on a
  double Back/Esc.
* UI: `ui/MainActivity.kt` (home, filter line, developer toggle shown only when Android developer
  options are on, dynamic launcher shortcuts for proven tools so they can be pinned into Sable Start),
  `ui/ToolActivity.kt` (hosts one tool, re-checks gating on arrival so another app cannot open a
  hidden tool, permission explainer and just-in-time request, key mapping, Menu hand-offs),
  `ui/Ui.kt` (dense rows with a strong focus ring for 720x720), `ui/Controllers.kt`.

### TOOLS-I2: read-only diagnostics

* Device, build and security identity; uptime, ABIs, page size, kernel; slot/vendor/VNDK detail only in
  developer mode (`android/Collectors.kt`).
* Keyboard event viewer (key code, scan code, meta state, produced character, device, repeat; Sable
  Keyboard's Key Probe format, `core/KeyEventFormat.kt`), keyboard layout/profile summary and input
  device inventory with configuration keyboard/navigation and default IME (`android/InputCollector.kt`),
  pointer and touch-surface event viewer (`ui/Diagnostics.kt`).
* Display and input geometry, camera capability report (characteristics only, no camera opened),
  sensor inventory, storage (StatFs, volumes; partition/slot in developer mode), launcher-visible app
  summary (no QUERY_ALL_PACKAGES; deep link to App Security & Privacy / App info).
* Radio, IMS visibility and FM status (`android/RadioCollector.kt`): **Sable Radio Diag folded in**
  (its `Reading`, `RadioNames`, `Consistency` and limits block moved to `core/Radio.kt` with their
  tests). FM shows status only and hands off to Sable Media (`FmStatus`, `SettingsLink.MEDIA_FM`).
* Network, IP, DNS and VPN summary (no SSID, no location permission).
* Critical text-entry test (`core/TextEntryTest.kt`, `TextEntryController`): letters, numbers,
  symbols, Alt, Sym, Fn (judged from key events), Bluetooth pairing code, Wi-Fi password and PIN
  simulations, software keyboard fallback. Only pass/fail is kept.
* Attention and SubScreen status: vibrator (with a one-shot test action), input-device lights, curated
  SubScreen companion modes available on this profile (`SubscreenModes`; none on a Q25).
* Hardware test categories and the dialer-code bridge (`core/DialerCodes.kt`): the Titan 2
  `*#*#3377#*#*` factory items mapped to Sable categories; each opens a Sable tool, names the owning
  Settings page, or is marked engineering-only (calibration, Mtklog, aging). Friendly queries
  ("factory test", "key test", "keyboard light", ...) resolve to the matching diagnostic; the raw code
  resolves through `BridgeDecision`, which always gives a reason when the bridge does not open. The
  `SecretCodeReceiver` is disabled in the manifest and enabled at runtime only when no vendor app
  answers the code; it never launches a raw vendor factory tool.
* Developer tier: Capability status (declared vs runtime vs visibility, mismatches) and the Factory
  test bridge explanation.

### TOOLS-I3: sensor utilities and local IR remote

Compass (magnetic, accuracy-driven calibration prompt), bubble level, picture hanging, plumb bob,
protractor (R sets the zero), height estimate (two sightings, always a range, eye height on C),
flashlight (torch, off on leave), magnifier (Camera2 preview with digital zoom, Up/Down, optional
light; nothing captured), noise meter (approximate dB, no audio stored), speedometer (GPS while open,
trip distance/max/average, km/h or mph), pedometer (step counter, or accelerometer estimate). Pure math
and state in `core/Measure.kt`; Android sensor/camera/mic/location glue in `ui/SensorTools.kt` and
`ui/Utilities.kt`. Calibration zero-points are app-private measurement offsets, not device settings.

IR remote (`core/IrRemote.kt`, `ui/IrRemoteController.kt`): NEC and Samsung32 pulse patterns for
`ConsumerIrManager.transmit`, a small built-in table of widely published TV codes (marked unverified),
user-made remotes stored in app preferences, carrier-range check, and a refusal with a reason when the
profile has no transmitter. No network, no learning mode (Android has no public IR receive API).

### TOOLS-I4: reports and hand-offs

* `core/Report.kt`: report categories with sensitivity; presets never pre-select a sensitive section
  (network addresses, app list, key session log); the export plan lists what is included, warnings
  for sensitive selections, what is not included, and the "never included" list from the design
  (message bodies, contacts, media, secrets, full private logs, location history, credentials);
  rendering drops unselected sections even if a caller passes them and passes every value through
  `Redactor` (long digit runs, e-mail, MAC, IPv4/IPv6 host part, phone numbers, coordinates,
  `password=`/`pin:`-style secrets).
* `ui/ReportController.kt`: section checklist (Space toggles), preview, copy, share through the
  Android share sheet only when chosen. "System bug report" (developer tier) hands off to Developer
  options instead of collecting logs.
* `core/SettingsLink.kt`: hand-offs with fallback chains to the owning surfaces (Keyboard & input,
  App display compatibility, Display, Sound, Battery, Notifications, the Settings-owned Network
  Manager, Mobile network, VPN, Storage, App Security & Privacy, App info, Developer options, About,
  Sable Media FM). Tools never writes a setting and keeps no policy store.
* `core/PrivilegePolicy.kt`: allowed/forbidden permission lists and manifest checks (no INTERNET, no
  services, no shared UID, no accessibility service, no settings write, no QUERY_ALL_PACKAGES); a unit
  test runs it against the real `AndroidManifest.xml`.

### Consolidation decisions

| Existing piece | Decision |
|---|---|
| Sable Radio Diag (`org.sableos.titan2.radiodiag`) | **Folded into Sable Tools** > Diagnostics > Radio, IMS and FM status. Row disabled; SableTools overrides it. Source kept for reference. |
| Sable Key Probe (launcher entry inside Sable Keyboard) | Launcher entry removed (SEPARATE_DIAGNOSTICS_LAUNCHER_APP=NO); activity kept, exported, reachable with `adb shell am start -n org.sableos.titan2.keyboard/.android.KeyProbeActivity`. Sable Tools' key viewer replaces it for users. |
| DisplayCompat (`org.sableos.titan2.displaycompat`) | **Stays separate.** It edits app display profiles, which the design assigns to Settings > Display > App display compatibility (configuration, not a tool). Sable Tools links to it. |
| Calculator / unit converter (`apps/r8/android/calculator`, `convert`) | **Stay in Sable Calculator.** They are not hardware-backed, so they are not Utilities under the design; Calculator also overrides ExactCalculator. |
| Separate Toolbox / Remote / diagnostics launcher apps | None created (SEPARATE_TOOLBOX_APP=PASS_ABSENT). |

## File map

```text
sable-src/apps/titan2/platform/settings.gradle.kts        + ":tools"
sable-src/apps/titan2/platform/keyboard/src/main/AndroidManifest.xml   Key Probe no longer a launcher entry
sable-src/apps/titan2/platform/tools/
  build.gradle.kts
  src/main/AndroidManifest.xml
  src/main/res/{values/strings.xml, drawable/ic_sable_tools.xml}
  src/main/java/org/sableos/tools/core/      pure Kotlin (no android.*)
    Capability.kt CapabilityGate.kt ToolsDeviceProfile.kt ToolCatalog.kt HomeModel.kt KeyCommands.kt
    KeyEventFormat.kt DialerCodes.kt SettingsLink.kt Report.kt Radio.kt Measure.kt IrRemote.kt
    TextEntryTest.kt PrivilegePolicy.kt
  src/main/java/org/sableos/tools/android/   platform glue (uncompiled)
    Platform.kt (probes, prefs, env, links, secret-code sync) Collectors.kt InputCollector.kt
    RadioCollector.kt SecretCodeReceiver.kt SysProps.kt
  src/main/java/org/sableos/tools/ui/        views (uncompiled)
    MainActivity.kt ToolActivity.kt Controllers.kt Ui.kt SensorTools.kt Utilities.kt
    IrRemoteController.kt Diagnostics.kt ReportController.kt
  src/test/java/org/sableos/tools/core/
    CapabilityGateTest.kt HomeModelTest.kt KeyCommandsTest.kt ReportTest.kt MeasureAndRemoteTest.kt RadioTest.kt
product/q25/apps.tsv                                      SableTools row added, SableRadioDiag disabled
docs/implementation/kf-c.md                               this file
```

## Acceptance keys

| Key | Covered by | Needs a device |
|---|---|---|
| SABLE_TOOLS_SINGLE_PRODUCT | one module/package `org.sableos.tools`; apps.tsv row | install check |
| SEPARATE_TOOLBOX_APP=PASS_ABSENT | no toolbox/remote package; Radio Diag disabled + overridden; Key Probe not a launcher entry | launcher check |
| TOOLS_UTILITIES/DIAGNOSTICS/REPORTS_SECTION | `HomeModel`, `HomeModelTest.threeSectionsInOrder` | screenshots |
| SETTINGS_OWNS_CONFIGURATION, TOOLS_DUPLICATE_POLICY_STORE=PASS_ABSENT | `SettingsLink`/`Handoffs`; prefs hold only recents, opt-in, measurement offsets, user remotes; `PrivilegePolicy` forbids settings-write | hand-off targets resolving on the Q25 build |
| TOOLS_READ_ONLY_DEFAULT, TOOLS_NO_ROOT_DAEMON | `PrivilegePolicy` + `ReportTest.manifestPolicy` (no services, INTERNET, shared UID, accessibility) | - |
| TOOLS_CAPABILITY_GATING | `CapabilityGate`, `CapabilityGateTest`, `HomeModelTest` | TOOLS-I5 evidence per profile |
| TOOLS_KEYBOARD_FIRST | `KeyCommands`, `KeyCommandsTest`; focus ring in `Ui` | keyboard walk-through on hardware |
| TOOLS_PERMISSION_JUST_IN_TIME | `PermissionPlan`, `HomeModelTest.permissionsAreJustInTime`; `ToolActivity` explainer | runtime prompt check |
| TOOLS_REPORT_PRIVACY | `ReportPolicy`, `Redactor`, `ReportTest` | review of a real report |
| FM_RADIO_OWNER_MEDIA | no FM tool in the catalog; `FmStatus`; `MEDIA_FM` hand-off | Sable Media has no FM mode yet |
| IR_REMOTE_LOCAL_FIRST | `IrRemote`, `MeasureAndRemoteTest`; no INTERNET | a Titan 2 with IR (not a Q25) |
| DEVICE_MODEL_BRANCHING_COMMON_UI=PASS_ABSENT | profile table only; `HomeModelTest.catalogHasNoDeviceModelNames` | - |
| Dialer-code bridge (HARDWARE_DIAGNOSTICS) | `DialerCodes`, tests | whether the dialer's secret-code broadcast may start the activity (background-activity-start rules) |
| Critical text-entry test | `TextEntryTest`, tests | running it on the Q25 keyboard (CRITICAL_TEXT_ENTRY is a Q3 gate) |

TOOLS-I5 (not done, needs hardware): for `zinwa-q25`, run each developer-only utility, compare the
Capability status screen with reality, then move proven entries in `ToolsDeviceProfile.ZinwaQ25` to
SUPPORTED with an evidence reference; confirm FM state and that no IR emitter is reported.

## Framework patches

None. No LineageOS project is changed, so there are no base commits to record.

## Integration

1. **`product/q25/apps.tsv`** (edited in this branch; `tests/run.sh` passes):

   ```text
   -SableRadioDiag	platform	apps/titan2/platform	:radiodiag:assembleRelease	radiodiag/build/outputs/apk/release/*.apk	org.sableos.titan2.radiodiag	unsigned	-	yes
   +SableRadioDiag	platform	apps/titan2/platform	:radiodiag:assembleRelease	radiodiag/build/outputs/apk/release/*.apk	org.sableos.titan2.radiodiag	unsigned	-	no
   +SableTools	platform	apps/titan2/platform	:tools:assembleRelease	tools/build/outputs/apk/release/*.apk	org.sableos.tools	unsigned	SableRadioDiag	yes
   ```

   plus a comment line explaining the fold. No other row changes (DisplayCompat and Calculator stay).
   `scripts/stage-product.sh` needs no change: SableTools is a normal unprivileged
   `android_app_import` with `overrides: ["SableRadioDiag"]` (harmless when RadioDiag is not built;
   when `apps --all` builds both, the override keeps only Sable Tools).
2. **`patches/sable-src/`**: regenerate to carry the new `tools/` module, the `settings.gradle.kts`
   include and the Sable Keyboard manifest change.
3. **Shared docs** (not edited here): `apps/README.md` says the platform set is five apps and "Every
   row is enabled"; it should list Keyboard, Camera, DisplayCompat, Setup and Tools, and note Radio
   Diag is disabled. `ROADMAP.md` (Radio Diag row) and `docs/SABLEOS_GAP_REVIEW.md` (KF-C) can point to
   this file. `docs/QUALIFICATION.md` could add a Q3 gate "TOOLS-I5: Sable Tools capability status
   matches hardware".
4. **Entry points for other packages** (Settings > Diagnostics, Sable Command search, Sable Start
   pins), all read-only:
   * `org.sableos.tools.action.OPEN_TOOL` + extra `org.sableos.tools.extra.TOOL` = a `Tool` enum name
     (for example `KEY_VIEWER`, `HARDWARE_TESTS`, `TEXT_ENTRY`).
   * `org.sableos.tools.action.SEARCH` + extra `org.sableos.tools.extra.QUERY` = a friendly query or the
     raw dialer code; resolution follows `DialerCodes.resolveQuery`.
   * Dynamic launcher shortcuts (ids = `Tool` names) for proven tools: IR remote, flashlight, compass,
     key viewer.
   * Optional Settings actions Tools tries first when present: `org.sableos.settings.NETWORK_MANAGER`,
     `org.sableos.settings.APP_SECURITY`, `org.sableos.settings.APP_DISPLAY_COMPATIBILITY`; Sable Media
     FM: `org.sableos.media.action.FM_RADIO`. Each falls back to a stock Android Settings action.

## Tests

```bash
K=<scratchpad with kotlinc and the JUnit shim>
cd sable-src/apps/titan2/platform/tools
$K/kotlinc/bin/kotlinc src/main/java/org/sableos/tools/core/*.kt src/test/java/org/sableos/tools/core/*.kt \
    $K/shim/Shim.kt -d /tmp/kf-c-out
java -cp /tmp/kf-c-out:$K/kotlinc/lib/kotlin-stdlib.jar org.junit.Run \
    org.sableos.tools.core.CapabilityGateTest org.sableos.tools.core.HomeModelTest \
    org.sableos.tools.core.KeyCommandsTest org.sableos.tools.core.ReportTest \
    org.sableos.tools.core.MeasureAndRemoteTest org.sableos.tools.core.RadioTest
# RAN=66 FAILED=0   (run from the module directory so the manifest-policy test reads the real manifest)

# With a working Android toolchain (not available here):
cd sable-src/apps/titan2/platform && ./gradlew :tools:testDebugUnitTest :tools:assembleRelease :tools:detekt :tools:ktlintCheck

bash tests/run.sh   # CI=PASS
```

Lint in this environment: `ktlint` 1.8.0 (`ktlint_code_style = android_studio`, as the Gradle plugin's
`android.set(true)`) and `detekt-cli` 1.23.8 with `--config sable-src/config/detekt.yml
--build-upon-default-config` both report nothing for `tools/src`.

## Not verified

* None of the Android code (`android/`, `ui/`, manifest, resources) has been compiled with the real
  SDK. It was type-checked against an old partial `android.jar`; the only remaining errors there are
  APIs newer than that jar (VibratorManager, SessionConfiguration, WindowMetrics, zoom ratio, etc.).
* Background activity start from `SecretCodeReceiver` may be blocked on Android 16 unless the dialer
  grants it; if so the bridge needs a notification instead (not implemented).
* `Settings.Secure.DEFAULT_INPUT_METHOD` readability for a normal app, keyboard-backlight light types,
  and how the Q25 trackpad reports (D-pad keys vs pointer) are unknown until run on the phone.
* Built-in IR codes are published values, not checked against real TVs; IR does not apply to the Q25.
* Soong accepting `overrides: ["SableRadioDiag"]` when that module is absent is expected but untested
  here.
