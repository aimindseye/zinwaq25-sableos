# Battery usage, health and charging (BH1-BH5)

Implementation of `platform_sable/docs/design/BATTERY_HEALTH_USAGE_UX.md` and
`BATTERY_HEALTH_USAGE_VISUAL_CONFIRMATION.md`, following the plan in
`aimindseye/sableos` `docs/BATTERY_HEALTH_USAGE_IMPLEMENTATION_PLAN.md`.

```text
PACKAGE=battery (BH)
SURFACE=Settings > Battery (LineageOS packages/apps/Settings patches 0201-0203)
SEPARATE_BATTERY_APP=NO
BH1_OVERVIEW_KEYBOARD=IMPLEMENTED (uncompiled Android glue)
BH2_USAGE_ACCOUNTING=IMPLEMENTED on Android's BatteryUsageStats (uncompiled glue)
BH3_HEALTH_MODEL=IMPLEMENTED, host tests PASS
BH4_LOCAL_HISTORY=IMPLEMENTED, model host tests PASS, glue uncompiled
BH5_CAPABILITY_DISCOVERY=IMPLEMENTED, read-only
BH6_CHARGING_MUTATION=NOT_AUTHORIZED, not implemented (gate is a compile-time false)
BH7_RUNTIME_QUALIFICATION=NOT_RUN (needs a Q25)
FRAMEWORKS_BASE_CHANGES=NONE (every API used already exists in lineage-23.2)
BUILD_TESTED=NO   DEVICE_TESTED=NO
```

Nothing here has been built with AOSP or run on a phone. The pure model was
compiled with `javac` and its 75 host tests pass. The Android code was
parse-checked with `javac` and type-checked against hand-written API stubs
(below), which catches typos and wrong signatures but is not a real build.

## What a user gets

Settings > Battery keeps Android's header (level, charging state, time
estimate) and gains, in the design's order:

```text
Battery usage           Since Oct 9, 07:12 · Screen 24% · Mobile network 17%
Battery health          Partial data · temperature available
Charging & protection   Device controls not yet qualified
Battery Saver           (Android's control)
```

* **Battery usage** (BH2) is Android's own usage screen (BatteryUsageStats,
  daily and hourly charts, app and system consumers, background-restriction
  handoff through the existing app detail page). The overview summary is new:
  the top two system consumers as shares of the same device-wide total, with
  the start of the accounting window. The usage chart gains keyboard
  inspection (patch 0203).
* **Battery health** (BH3, BH4) lists maximum capacity, cycle count,
  temperature, condition, full-charge and design capacity, remaining charge,
  and (only when trustworthy) manufacture and first-use month. Every value
  carries "Source: ... · ..." (Android, battery hardware (Health HAL), local
  estimate; measured / reported by the platform / calculated, high confidence /
  sources disagree, low confidence). Anything else reads "Not reported by this
  device", "Not available on this device", "Reported, but not yet verified on
  this device" or "Reported value failed a consistency check": never a
  placeholder number. Below: a local history chart (Today / 24 hours / 7 days,
  temperature and battery level).
* **Charging & protection** (BH5) shows Charge limit, Adaptive charging and
  Overnight charging as disabled-looking rows that stay keyboard-focusable
  ("Not available on this device yet" / "Found on this device, not yet
  qualified for SableOS" / "Not available on this device"), thermal protection
  as platform status (with the framework's shutdown temperature when
  configured), the Android-reported charging policy when there is one, and a
  link to Battery Saver.
* LineageOS's own **Charging control** and **Charging speed** rows (mutable,
  backed by `lineagehealth`) are hidden while the BH6 gate is closed, and
  Android's ungated **Battery information** page is hidden and removed from the
  search index (it showed a capacity percentage without device evidence).

## How the rules are enforced

* **Fail closed.** `BatteryHealthEvaluator` applies, per fact: profile says
  `NO` -> unavailable; Android reported nothing -> unavailable; value fails a
  sanity check (range, full above 125% of design, design more than 25% from
  the vendor rating, first use before manufacture, dates before 2000 or in the
  future, cycle count 0 without evidence) -> unavailable; *strict* fact
  (capacity, cycle count, charge counter, dates) without profile evidence ->
  unavailable. An unavailable fact carries no value, source or confidence, so
  no renderer can print a number for it.
* **No fabricated percentage.** Capacity health is either the Health HAL's
  `BATTERY_PROPERTY_STATE_OF_HEALTH` (platform reported) or
  `floor(full / design)` from two HAL values (calculated, high confidence); if
  the two disagree by more than 10 points the confidence drops to low. Nothing
  is inferred from charge samples.
* **No sysfs in common UI.** Facts come from the sticky
  `ACTION_BATTERY_CHANGED` extras (`EXTRA_MAXIMUM_CAPACITY`,
  `EXTRA_DESIGN_CAPACITY`, `EXTRA_CYCLE_COUNT`, `EXTRA_TEMPERATURE`,
  `EXTRA_HEALTH`, `EXTRA_CHARGE_COUNTER`) and `BatteryManager.getLongProperty`
  (`STATE_OF_HEALTH`, `MANUFACTURING_DATE`, `FIRST_USAGE_DATE`,
  `CHARGING_POLICY`; Settings holds `BATTERY_STATS`). The only files Settings
  reads are its own history file and the profile under `/system_ext/etc`.
* **Device differences are data.** `ro.sable.profile.id` selects
  `/system_ext/etc/sable/battery/<id>.conf` (or `/product/etc/...`). The parser
  rejects a file whose `profile_id` differs from the file name, unknown schema
  versions, duplicate keys and bad values, all toward UNKNOWN. No profile
  means every strict fact and every charging control is unavailable.
* **BH6 closed in code.** `ChargingControlPolicy.MUTATION_AUTHORIZED` is a
  compile-time `false`; `ENABLED` is unreachable for every mutable control
  whatever a profile claims. Nothing in the patches writes to a charging
  backend.
* **History is cheap and local** (BH4). One sample (time, level, temperature
  only if the TEMPERATURE fact is available, charging flag) is taken by the
  existing hourly `PeriodicJobReceiver` alarm that Settings already runs for
  Android's battery-usage snapshot, and when a Battery screen opens. There is
  no new alarm, wakelock, service, observer or polling. Samples closer than 30
  minutes are dropped, history older than 7 days is pruned, the ring holds at
  most 336 samples (about 10 KB) in Settings' credential-encrypted files dir,
  a big backwards clock jump resets it, and there is no export. No per-app data
  is stored.
* **One denominator.** The usage summary is built from one BatteryUsageStats
  window; shares are of that window's device-wide consumed power.

## Keyboard and touch

| Contract | Where |
|---|---|
| Up/Down, Enter | Platform focus search and Preference activation, unchanged |
| Visible focus | `SableFocusDecoration`: a stroke in the primary text colour around the focused row (not accent colour alone); range and chart draw their own outline |
| Type-ahead | `TypeAheadMatcher` over row titles plus aliases (`sable_battery_type_ahead_aliases`): U usage, H health, C charging, B saver; 800 ms buffer; repeat cycles; case and accent insensitive; wraps. Implemented for the four Battery screens via `View#addOnUnhandledKeyEventListener`, so it only sees keys no view used |
| MoveHome/MoveEnd, PageUp/PageDown | `ListFocusNavigator` (System HOME is never mapped) |
| `/`, Search key, Ctrl/Meta+K | Open Settings search (`SearchFeatureProvider.buildSearchIntent`) |
| Segmented range | `SegmentedRangeGroup` in `SableRangeSelectorView`: Enter enters, Left/Right move (RTL mirrored), Enter/Space confirm, Escape/Back restore, Up/Down leave; arrows not consumed when idle |
| Chart inspection | `ChartInspectionCursor` in `SableHistoryChartView` and in Android's `BatteryChartView`: Enter, Left/Right (skips empty buckets), PageUp/PageDown, MoveHome/MoveEnd, Up/Down between series only when there are two, Escape/Back exit; no key consumed outside inspection |
| Back in temporary modes | Settings uses OnBackInvokedCallback, so Back doesn't reach views; `SableBackCallback` registers an androidx back callback only while a mode is active |
| Text for charts | Selected bucket as text (series, time span, value, "charging") in a polite live region; the usage chart announces the slot's existing content description |
| Touch fallback | Tap a segment or bar; rows behave as before |
| Unavailable controls | `SableUnavailablePreference`: looks disabled and reports `enabled=false` to accessibility, but stays focusable (a disabled View cannot take focus) |
| Search aliases | `sable_keywords_battery_usage/_health/_charging/_saver`; unavailable controls indexed only as "Charge limit (not available on this device)" |

## File map

Repository (`q25/battery`):

| Path | What |
|---|---|
| `patches/framework/packages/apps/Settings/0201-SableOS-battery-health-and-usage-model-with-host-tes.patch` | Pure Java model + host tests + `SettingsSableBatteryModel-srcs` filegroup |
| `patches/framework/packages/apps/Settings/0202-SableOS-Battery-health-and-Charging-protection-in-Se.patch` | Settings glue, resources, overview rows, gates, history hook, keyboard support |
| `patches/framework/packages/apps/Settings/0203-SableOS-keyboard-inspection-for-the-battery-usage-ch.patch` | `BatteryChartView` keyboard inspection |
| `device-profile/battery/zinwa-q25.conf` | Q25 battery capability profile: every fact UNKNOWN, `mutable_backend_qualified=NO`, rated 3000 mAh as a sanity bound |
| `device-profile/BATTERY.md` | Known Q25 battery facts with sources, and the evidence needed per key |
| `device-profile/CAPABILITIES.md` | Battery rows added |
| `docs/implementation/battery.md` | This file |

Inside the Settings patches (`src/com/android/settings/fuelgauge/sable/`):

| File | Role |
|---|---|
| `model/FactKind, HealthFact, FactSource, FactConfidence, FactAvailability, UnavailableReason, Capability` | Fact model |
| `model/BatteryReadings, BatteryHealthEvaluator, HealthReport, CapacityMath` | Evaluation and capacity math |
| `model/HealthFactPresenter, FactText, TemperatureClass` | Locale-free presentation, month-precision dates, coarse age, warm/hot |
| `model/BatteryCapabilityProfile, BatteryProfileParser` | Capability schema per device profile |
| `model/ChargingControl, ChargingDiscovery, ChargingControlPolicy` | BH5 states, BH6 gate |
| `model/HistorySample, HistoryRingBuffer, HistoryRange, HistoryBuckets` | Bounded local history |
| `model/NavKey, ChartInspectionCursor, SegmentedRangeGroup, TypeAheadMatcher, ListFocusNavigator` | Keyboard state machines |
| `model/UsageSummary` | Top consumers over one window |
| `SableBatteryState, SableBatteryProfileLoader, SableBatteryHistoryStore` | Framework reads, profile load, history file |
| `SableBatteryHealthFragment, SableHealthFactPreferenceController, SableBatteryHistoryPreference, SableHistoryChartView, SableRangeSelectorView` | Health screen |
| `SableChargingProtectionFragment, SableChargingControlPreferenceController, SableUnavailablePreference, SableChargingGate` | Charging and protection |
| `SableBatteryHealthEntryController, SableChargingEntryController, SableBatteryUsageSummary, SableLegacyBatteryInfoController, SableVendorChargingGateController` | Overview rows and gates |
| `SableBatteryKeyboardSupport, SableFocusDecoration, SableBatteryKeys, SableBackCallback, SableBatteryText` | Keyboard, focus, key mapping, Back, localized text |
| `res/values/sable_battery_{strings,arrays,config}.xml`, `res/xml/sable_battery_{health,charging}.xml`, `res/layout/sable_battery_history_preference.xml` | Resources |
| `tests/sable-battery/` | `SettingsSableBatteryModelTests` (`java_test_host`, `TEST_MAPPING` presubmit) |

Existing Settings files touched (small, marked `SableOS:`):
`Android.bp` (filegroup), `res/xml/power_usage_summary.xml` (two rows,
keywords, two gate controllers), `PowerUsageSummary.java` (usage summary,
keyboard, history sample on resume), `PowerUsageAdvanced.java` (keyboard),
`PeriodicJobReceiver.java` (history sample), `ChargingSpeedPreferenceController.java`
(gate), `BatteryInfoFragment.java` (search gate), `BatteryChartView.java`
(inspection).

## Patch bases

| Patch | Project | Base (lineage-23.2) |
|---|---|---|
| 0201, 0202, 0203 | `packages/apps/Settings` (`LineageOS/android_packages_apps_Settings`) | `8d8f6486b274bcf0aa6e5d0cbba52c0b05ae5c65` |

Each patch applies on its own to that base (`git apply --check`), and the
series reproduces the patched tree exactly. 0202 and 0203 need 0201 to
compile; 0203 needs 0202. No `frameworks/base` change: the APIs used
(`EXTRA_MAXIMUM_CAPACITY`, `EXTRA_DESIGN_CAPACITY`, `EXTRA_CYCLE_COUNT`,
`BATTERY_PROPERTY_STATE_OF_HEALTH`, `_MANUFACTURING_DATE`,
`_FIRST_USAGE_DATE`, `_CHARGING_POLICY`, `BatteryUsageStats`) are present in
`LineageOS/android_frameworks_base` lineage-23.2 `f4ed08a0`, and Settings is a
platform app.

Device evidence sources: `LineageOS/android_device_xelex_Q25` `1f4e295`,
`LineageOS/android_hardware_mediatek` `68f9be7` (the pins in
`build/config/q25.env`).

## Acceptance coverage

`code+test` = enforced by the pure model with a host test; `code` = Android
glue written but uncompiled and unrun; `device` = needs a Q25 (BH7).

| Key | Status |
|---|---|
| BATTERY_SETTINGS_LOCATION_ANDROID_COMPATIBLE | code (rows in `power_usage_summary.xml`) |
| SEPARATE_BATTERY_APP=PASS_ABSENT | code (no package added) |
| BATTERY_KEYBOARD_NAVIGATION / ARROW / ENTER / BACK_ESCAPE | code+test (state machines), device |
| BATTERY_VISIBLE_FOCUS | code, device |
| BATTERY_TYPE_AHEAD | code+test (`TypeAheadMatcherTest` incl. U/H/C/B), device |
| BATTERY_SETTINGS_SEARCH | code (keywords, dynamic raw entries), device |
| BATTERY_CHART_KEYBOARD_INSPECTION | code+test (`ChartInspectionCursorTest`), device |
| BATTERY_NO_FOCUS_TRAPS | code+test (idle keys not consumed, empty chart not enterable), device |
| BATTERY_SPACE_TOGGLE_SUPPORTED_CONTROLS | n/a on these screens (no Sable switches; Battery Saver keeps Android's) |
| BATTERY_TOUCH_FALLBACK | code, device |
| BATTERY_USAGE_PLATFORM_ACCOUNTING | code+test (`UsageSummaryTest`); Android accounting unchanged |
| BATTERY_HEALTH_UNAVAILABLE_STATE | code+test (`BatteryHealthEvaluatorTest`, `HealthFactPresenterTest`) |
| FABRICATED_HEALTH_PERCENTAGE / MAX_CAPACITY / CYCLE_COUNT / BATTERY_AGE = PASS_ABSENT | code+test |
| DERIVED_VALUES_LABEL_SOURCE_CONFIDENCE | code+test |
| RAW_SYSFS_COMMON_UI_ACCESS = PASS_ABSENT | code (review: no sysfs path in the patches) |
| CHARGING_CONTROL_CAPABILITY_GATED / UNQUALIFIED_CHARGING_MUTATION=NO | code+test (`ChargingControlPolicyTest`, `ProfileFileCheckTest`) |
| BATTERY_CHARGING_UNQUALIFIED_STATE | code+test |
| NETWORK_REQUIRED / CLOUD_ANALYTICS = PASS_ABSENT | code (no network code) |
| DEVICE_PROFILE_SEPARATION / DEVICE_CAPABILITY_INHERITANCE=PASS_ABSENT | code+test (profile id must match file name) |
| S1_7_FROZEN_API_MUTATION = PASS_ABSENT | code (IPowerService untouched) |
| BATTERY_LIST_FIRST_SQUARE_LAYOUT / USAGE_CHART_BOUNDED | code (single column, 112 dp chart), device screenshots at 720x720 |
| EXPORT_USER_INITIATED / EXPORT_REDACTION | n/a: nothing is exported |

## Tests

Host tests (75) for the model, with `javac` and the JUnit shim used in this
environment:

```bash
K=/tmp/claude-0/-home-claude/17ecbda4-c8ea-50fb-b812-754223248d5c/scratchpad
S=<Settings checkout with 0201 applied>
OUT=/tmp/battery-out; mkdir -p "$OUT"
"$K/kotlinc/bin/kotlinc" "$K/shim/Shim.kt" -d "$OUT"
javac -Xlint:all -Werror -cp "$OUT:$K/kotlinc/lib/kotlin-stdlib.jar" -d "$OUT" \
    $(find "$S/src/com/android/settings/fuelgauge/sable/model" "$S/tests/sable-battery/src" -name '*.java')
java -Dsable.battery.profile=$PWD/device-profile/battery/zinwa-q25.conf \
    -cp "$OUT:$K/kotlinc/lib/kotlin-stdlib.jar" org.junit.Run \
    $(cd "$S/tests/sable-battery/src" && find . -name '*Test.java' | sed 's#^\./##; s#\.java$##; s#/#.#g')
# RAN=75 FAILED=0
```

`ProfileFileCheckTest` checks the named profile (every schema key stated, no
parse warnings, BH6 closed, strict facts unavailable without evidence); it
fails if, for example, a key is set to `maybe`.

In a LineageOS tree: `atest SettingsSableBatteryModelTests` (plain JUnit4 host
test). Not run here.

`bash tests/run.sh` passes (the new patches parse with `git apply --stat`).

## Not done, and why

* **No AOSP build or device run.** All Android code is uncompiled. Things to
  watch in the first build: `ViewTreeOnBackPressedDispatcherOwner.get` (static
  Java name of the androidx.activity Kotlin extension), `PreferenceGroupAdapter`
  being the list adapter (Settings' `RoundCornerPreferenceAdapter` extends it),
  lint on custom views.
* **BH6** (charge limit, adaptive and overnight charging as working controls)
  is not authorized and not implemented.
* **BH7**: keyboard, touch, TalkBack, font scale and focus restoration on a Q25;
  screenshots at 720x720; whether a letter key in touch mode focuses a row
  (type-ahead only sees keys once something in the list has focus).
* **Q25 evidence**: every health key in `zinwa-q25.conf` is UNKNOWN; the
  procedure is in `device-profile/BATTERY.md`.
* Type-ahead is implemented for the Battery screens only, as reusable classes;
  a Settings-wide type-ahead is outside this package.
* `ServiceManager.checkService("lineagehealth")` from `system_app` may be denied
  by SELinux on some trees; it then reads as "no backend" (fail closed).
* Translations: English only.
* The Q25 `power_profile.xml` capacity placeholder (1000 mAh) should be
  corrected once measured; Settings shows only ratios, so it is not blocking.

## Integration

1. **Install the profile at Q4.** In `scripts/stage-product.sh`, inside the
   `if [[ "$FRAMEWORK" == YES ]]` block, after the SableStart copy:

   ```bash
   mkdir -p "$VENDOR_SABLE/etc/battery"
   cp "$SABLE_REPO_ROOT/device-profile/battery/zinwa-q25.conf" "$VENDOR_SABLE/etc/battery/"
   cat >> "$fw_mk" <<'MK'
   PRODUCT_COPY_FILES += \
       vendor/sable/q25/etc/battery/zinwa-q25.conf:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/sable/battery/zinwa-q25.conf
   MK
   ```

   and in `tests/run.sh` step 6b, with the other Q4 assertions:
   `grep -q 'etc/sable/battery/zinwa-q25.conf' "$v/sable-q25-framework.mk" || ok=no`.
   The Settings patches apply through the existing
   `scripts/apply-framework-patches.sh` (no change needed).
2. **patches/README.md** table rows:

   | Patch | Base (lineage-23.2) | What it does |
   |---|---|---|
   | `framework/packages/apps/Settings/0201-...` | `8d8f6486b274bcf0aa6e5d0cbba52c0b05ae5c65` | Pure Java battery health/usage model and its host tests |
   | `framework/packages/apps/Settings/0202-...` | same | Battery health and Charging & protection under Settings > Battery; gates LineageOS charging controls and the legacy Battery information page; local history; keyboard support |
   | `framework/packages/apps/Settings/0203-...` | same | Keyboard inspection for the Battery usage chart |

3. **QUALIFICATION.md** gates to add for Q4 (all NOT_RUN):
   `BATTERY_HEALTH_UNAVAILABLE_STATE`, `BATTERY_CHART_KEYBOARD_INSPECTION`,
   `BATTERY_TYPE_AHEAD`, `BATTERY_VISIBLE_FOCUS`, `BATTERY_TOUCH_FALLBACK`,
   `CHARGING_CONTROL_CAPABILITY_GATED` (LineageOS charging control hidden),
   `BATTERY_HISTORY_NO_EXTRA_WAKEUPS` (no new alarm in `dumpsys alarm`),
   and the per-key evidence in `device-profile/BATTERY.md`.
4. **SABLEOS_GAP_REVIEW.md**: §6 row and "T4 ... BH1 Battery Settings" ->
   "BH1-BH5 written as Settings patches 0201-0203 (uncompiled); BH6 not
   authorized; BH7 and Q25 evidence wait for the device".
5. No `apps.tsv` change: there is no Battery app.

### Expected conflicts with other packages

* Any package patching `packages/apps/Settings` (Settings keyboard-first or
  search work, KF-D All Apps privacy if it lands in Settings, T3 setup/tools)
  should use a different number range. Overlap is only likely in
  `res/xml/power_usage_summary.xml`, `PowerUsageSummary.java` or a
  Settings-wide type-ahead: if another package adds a global type-ahead or
  focus ring, `SableBatteryKeyboardSupport` and `SableFocusDecoration` should
  defer to it (they are attached per screen and easy to drop).
* KF-B (SystemUI convergence) does not touch these files; a SystemUI battery
  indicator is separate.
* `device-profile/CAPABILITIES.md` gains rows at the end of its table; merge
  trivially with other packages' rows.
