# KF-B: SystemUI visual convergence and Sable branding

> **Update 2026-10-10:** the Sable app code from this package (the `sable-src`
> side) now builds in GitHub Actions, with unit tests, Android Lint, detekt and
> ktlint passing (PR #9; the fixes those gates needed are in `sable-src` patch
> 0011). The LineageOS framework patches are still uncompiled, and nothing has
> run on a device. Statements below about uncompiled app code describe the
> state when the package was written.

Package `kf-b` implements DESIGN-KF-B, *SystemUI Visual Convergence Contract*
(`platform_sable/docs/design/SYSTEMUI_VISUAL_CONVERGENCE_CONTRACT.md`), on the
LineageOS 23.2 (Android 16 QPR2) base. It also covers the T3 branding item
(Sable branding in Settings > About and Setup). Where they apply, it follows
`QUICK_SETTINGS_NOTIFICATION_SHADE_UX.md`,
`LOCKSCREEN_SECURE_ENTRY_UX.md`/`LOCKSCREEN_VISUAL_CONFIRMATION.md` and
`KEYBOARD_FIRST_SETTINGS_UX.md`.

Status: **written and statically checked, not built, not run on a device.**
No Android build ran here (Google Maven and the 200 GB tree are not
available). The framework patches apply cleanly to their base commits
(`git apply --check`), but the Kotlin and Java inside them is **uncompiled**
against the Android SDK. Only the pure-logic parts were compiled and tested on
the host.

## Approach

Overlays are tried first. A framework patch is used only where an overlay
can't reach the value.

| Need | Mechanism |
|---|---|
| One token table (colors, accents, radii, spacing, type, motion, focus) | `SableSystemTokens.kt` in shared `sabledesign`, pure Kotlin, unit-tested |
| Contrast rules for the palette | `SableTokenRules` (JVM test) and `tests/check-sable-design.py` (CI) |
| Card, panel and media radii in shade, lockscreen and QS | RRO `SableSystemUIOverlay` (dimens) |
| Default QS tiles, high-value controls first | RRO `SableSystemUIOverlay` (`quick_settings_tiles_default`) |
| Three-column grid of labeled small tiles, compact row of four (`sable-quick-settings-titan2.svg`; replaced the earlier two-column large tiles) | RRO `values/sable_quick_settings.xml` + frameworks/base patches 0101 (large-tile default is a resource, set empty) and 0310 (compact columns, small-tile labels) |
| QS tile shapes (pill and morph replaced by 12dp/8dp) | frameworks/base patch 0102 (constants are hard-coded in Compose) |
| System accent equals Sable accent | RRO `SableFrameworkOverlay` (`theming_defaults`, first boot) + Settings patch 0101 (accent choice seeds the system palette) |
| One light/dark source | Settings patch 0101: the Sable authority always reports `follow-system`; light and dark belong to Android's Dark theme |
| Sable branding in Setup | RRO `SableSetupWizardOverlay` (welcome title in 49 locales, restore and recovery text, Sable logo) and SetupWizard patch 0408 (no LineageOS features step) |
| Sable branding in About | Settings patch 0102 (SableOS version row) |
| Density | One value, `SABLE_LCD_DENSITY` in `product/q25/sable-q25.mk` |

Nothing branches on a device model. The overlay is product-wide and the
Quick Settings grid follows the owner's Titan 2 artifact (three columns);
`SableDisplayClass` still classifies square screens for the Sable apps.
Density changes the dp size, and the layouts follow from it.

## What was implemented

### 1. Token table (shared Sable design)

`sable-src/src/android/shared/sabledesign/src/main/java/org/sableos/design/SableSystemTokens.kt`
(pure Kotlin, compiled into SableLauncher through the existing
`sable_design_shared_srcs` filegroup):

* `SableColorRoles` for dark and light: `surface.base/raised/overlay`,
  `text.primary/secondary/disabled`, `success`, `warning`, `danger` and
  `privacy`. The surface and text values equal the existing `SableColorTokens`.
* `SableAccentTokens`: the five Sable presets. Each has its existing seed (equal
  to `AccentPreset`) plus a dark-surface tone and a light-surface tone. Sable
  Blue, Green and Orange fall below 3:1 on white (Blue is 2.79:1), so in the
  light theme, focus and accent use a darker tone. `focus = accent tone for the
  theme`, drawn as an outline with a 2dp gap and never as a fill. This keeps
  focus distinguishable from a selected (accent-filled) control.
* `SableGeometryTokens`: 4dp grid, `space.1..8`, 48dp rows, 8/12/16dp shapes,
  12dp card radius, 2dp focus stroke and 2dp focus gap.
  `SableTypeRole` gives 28/22/18/16/14/12 sp. `SableMotionTokens` clamps
  transitions to 120-180 ms and returns 0 when animations are off.
* `SableDisplayClass` (square keyboard, compact portrait, tall) is computed from
  pixels and density with Android's own "long" rule.
  `SableQuickSettingsLayout` and `SableQuickSettingsTiles` define the
  square-screen QS layout and the default tiles.
* `SableContrast` (WCAG relative luminance) and `SableTokenRules`: primary text
  ≥ 7:1, secondary text ≥ 4.5:1 and disabled labels ≥ 3:1 on every surface;
  status, privacy, accent and focus ≥ 3:1 non-text contrast; text on an accent
  fill ≥ 4.5:1; privacy never equal to danger; spaces on the 4dp grid.

Sable app corner shapes are a user setting, added after this package by
[corners](corners.md): App display compatibility > Sable app style offers
Compact (the original 2-6dp app `Shapes`, the default, so Sable apps look the
same until the user picks) and Rounded (these 8dp control / 12dp card tokens).
`SableTheme` builds its `Shapes` from `SableShapeTable` for the style read from
`org.sableos.appearance` (column `corner_style`, Settings patch 0103), and
`MAX_STANDARD_CORNER_RADIUS_DP` is now the larger of the two allowed styles
(`MAX_COMPACT_CORNER_RADIUS_DP = 6`, `MAX_ROUNDED_CORNER_RADIUS_DP = 12`).

### 2. Runtime resource overlays (`product/common/overlay`)

Three static, product-partition, resource-only overlays, with an `Android.bp`
of `runtime_resource_overlay` modules. They have no code, no platform
certificate and no privilege. Their targets declare no `<overlayable>`, so any
preinstalled overlay may set these values.

* `SableFrameworkOverlay` → `android`: `theming_defaults =
  *|TONAL_SPOT|#4D9CFF`. On LineageOS 23.2 the SystemUI flag
  `hardware_color_styles` is enabled (build/release `bp3a`), so before setup
  completes `ThemeOverlayController` seeds the Material palette from this value
  instead of the wallpaper.
* `SableSystemUIOverlay` → `com.android.systemui`:
  `notification_corner_radius` is 12dp. It shapes notification cards in the
  shade and on the lockscreen, the media card (`qs_media_background`) and the
  controls popups.
  `notification_scrim_corner_radius` and `qs_corner_radius` are 16dp, and
  `qs_media_album_radius` is 8dp.
  `quick_settings_tiles_default` = `internet,bt,dark,dnd,battery,flashlight,hotspot,location,airplane`.
  `values/sable_quick_settings.xml` (sable-quick-settings-titan2): a 3-column
  grid, one compact row of 4, small tiles labeled under the icon, no default
  large tile, and the shade header, Hub summary row and key hint
  (frameworks/base 0310-0312, [kf-a](kf-a.md)). The earlier `values-notlong`
  two-column large tiles were removed.
* `SableSetupWizardOverlay` → `org.lineageos.setupwizard`:
  `setup_welcome_message` becomes "Welcome to SableOS" in the default locale
  and in the 48 translated locales. These are generated from the LineageOS
  translations by `scripts/gen-sable-setupwizard-strings.py`; two broken
  translations (a self-closing `<xliff:g/>` in Persian, `%1s` in Icelandic)
  are handled.
  `@drawable/logo` becomes the Sable shield
  (`sableos_shield.png`, SHA-256 `eed1b127…5aea7`, the SableOS D2 SetupWizard
  icon, copied unchanged). It is padded 25% on each side, so the
  2:1 LineageOS layout never shows a square logo that fills the screen.
  Rule (as on SableOS Panther): **no visible LineageOS in the owner Setup
  flow**; package ids, legal and licence text and upstream URLs stay.
  `intro_restore_subtitle` is the Panther text ("If you have a compatible
  Seedvault backup from a previous device, you can restore it here.") in
  English (default and `en-r*`); the 36 other locales LineageOS translates
  get their translation with SableOS in place of the OS name, and locales
  without a translation fall back to the default. The Update recovery step's
  title, description and switch say "recovery" instead of "Lineage
  Recovery" in English only (no Sable translations yet; other translated
  locales keep the LineageOS text). That step only appears when
  `/vendor/bin/install-recovery.sh` exists, which the A/B Q25 does not
  ship. The "LineageOS features" step is not renamed but removed from the
  owner script by SetupWizard patch 0408, because its metrics opt-in sends
  data to LineageOS (checked by default); LineageOS statistics keep the
  lineage-sdk default `def_stats_collection=false`. `os_name`, the features
  page's own strings and its privacy-policy text are never overlaid, and no
  overlay touches legal, licence or notice resources (check).

### 3. Framework patches (range 0101-0199)

| Patch | Base (lineage-23.2) | What it does |
|---|---|---|
| `framework/frameworks/base/0101-SableOS-read-default-large-Quick-Settings-tiles-from.patch` | `f4ed08a03b518772ba77c3c0a1b4185fa1712c9b` | `DefaultLargeTilesRepositoryImpl` reads `R.array.quick_settings_default_large_tiles` (new `res/values/config_qs_large_tiles.xml`, default = upstream internet, bt, dnd, cast) instead of a hard-coded set. The Kosmos test fixture passes `mainResources`. Only first-boot defaults change; user resizing, restore and tile behaviour are unchanged. |
| `framework/frameworks/base/0102-SableOS-Quick-Settings-tiles-use-the-Sable-card-and-.patch` | same | Tile shape constants: tiles use 12dp (the card radius) instead of a 50dp pill when inactive and 24dp when active. The active icon background uses 8dp instead of 16dp. The focus ring (`borderOnFocus`, which follows the tile's corner) becomes a stable rectangle. State is shown by color, not by a shape change. |
| `framework/packages/apps/Settings/0101-SableOS-Sable-appearance-authority-and-Display-Accen.patch` | `8d8f6486b274bcf0aa6e5d0cbba52c0b05ae5c65` | Adds `SableAppearanceProvider` (`org.sableos.appearance`, exported, direct-boot aware), which Sable apps and Sable Start already read but nothing on LineageOS provided. `mode` always reads `follow-system`. A light/dark write maps to `UiModeManager.setNightMode`. An accent write stores the accent (device-protected prefs) and seeds `THEME_CUSTOMIZATION_OVERLAY_PACKAGES` as a preset color (`color_source=preset`, `system_palette`, `accent_color`, `TONAL_SPOT`). Only Settings and `org.sableos.launcher` may write. Also adds an **Accent** list next to Dark theme in Settings > Display. The information architecture is unchanged: no new top-level entry. The pure policy is in `SableAppearancePolicy`. |
| `framework/packages/apps/Settings/0102-SableOS-SableOS-version-row-in-About-phone.patch` | same | Adds **SableOS version** (order 41, above Android version) to About phone, built from `ro.sable.release`, `ro.sable.base` and `ro.sable.build_source`. The LineageOS version row and LineageOS legal information are untouched. The pure formatter is in `SableBuildInfo`. |

Settings patch `0103-SableOS-Sable-app-corner-style-in-the-appearance-aut.patch`
(package [corners](corners.md)) stacks on 0101 and 0102: it adds the
`corner_style` column to the same provider and lets App display compatibility
write only that column. Mode and accent keep the writers listed above.

All four patches were checked with `git apply --check` and applied in order
on a checkout of their base. They were produced with `git format-patch` from
commits in `/home/claude/lineage-kf-b/{frameworks_base,Settings}`.

LineageOS release flags that the design depends on were read from
`LineageOS/android_build_release` lineage-23.2 `d76088e5`:
`qs_ui_refactor_compose_fragment` and `hardware_color_styles` are ENABLED from
`bp3a` (and `bp4a` inherits `bp3a`). `media_controls_in_compose` and
`scene_container` are not set, so they keep their declared default and the
legacy media views are used. If LineageOS turns those flags on, recheck the
media rows below.

### 4. Density: one value

`product/q25/sable-q25.mk` has `SABLE_LCD_DENSITY :=`. Empty keeps the
LineageOS device value, 193 (about 596 dp wide). Any value in 120-640 is
written as `PRODUCT_PRODUCT_PROPERTIES += ro.sf.lcd_density=<value>`.
`device-profile/DISPLAY.md` points to it. Choosing the value is still a
hardware decision. That a product-partition `ro.sf.lcd_density` replaces the
vendor value (init loads product's build.prop last) is standard Android
behaviour, but on the first build confirm it with `adb shell getprop
ro.sf.lcd_density` and `adb shell wm density`.

### 5. Staging (implemented)

`scripts/stage-product.sh`, `FRAMEWORK=YES` branch (Q4 and later) copies
`product/common/overlay` to `vendor/sable/q25/overlay` and adds
`SableFrameworkOverlay SableSystemUIOverlay SableSetupWizardOverlay` to
`PRODUCT_PACKAGES` in the generated `sable-q25-framework.mk`. Staging an
earlier release removes them along with the rest of `vendor/sable/q25`.
`tests/run.sh` checks both directions.

## Design acceptance keys

| Key | State | Evidence / what is left |
|---|---|---|
| `SYSTEMUI_SABLE_VISUAL_CONVERGENCE` | Code | Token table, SystemUI overlay radii, tile shapes, accent → system palette. Screenshots need a device. |
| `SYSTEM_THEME_SINGLE_SOURCE` | Code + host test | The authority reports `follow-system`, and light/dark writes go to `UiModeManager`. The Dark theme QS tile is a default tile (`dark`). `SableSettingsPolicyTest`. Runtime propagation needs a device. |
| `VISIBLE_KEYBOARD_FOCUS` | Upstream + device | Upstream already draws focus in compose QS tiles and the brightness slider (`borderOnFocus`, 3dp ≥ the 2dp token, `colorScheme.secondary`, now Sable-seeded), in notification rows (`notification_focus_overlay_color`) and through Settings' focus states. No patch was needed. Check on the Q25 trackpad and keyboard. |
| `HOVER_DOES_NOT_STEAL_FOCUS` | Upstream + device | No code path in compose QS (`combinedClickable`, hover-only indication) or in the View-based shade or Settings moves focus on hover. No patch was needed. Check with a USB/BT mouse and the trackpad. |
| `SYSTEM_MEDIA_METADATA_VISIBLE` | Upstream + device | On this base, QS uses the legacy media views. The collapsed constraint set (`media_session_collapsed.xml`) shows title, artist and play/pause, and prev/next only when the session offers them. |
| `SOLID_ACCENT_MEDIA_BAR` (absent) | Upstream + device | The Panther bar came from the GrapheneOS compose media path, which is not enabled on LineageOS 23.2. The card has artwork or app colors plus metadata, and its radius is now the 12dp card radius. |
| `QUICK_SETTINGS_COMPACT_SQUARE` | Code + static check | Artifact grid: 3 labeled columns, compact row of 4 (patches 0101, 0310 + overlay), default tiles. `SableSystemTokensTest`, `check-sable-design.py`. |
| `PROFILE_GATED_HARDWARE_CONTROLS` | Code + static check | No AOD, SubScreen or keyboard-backlight tile in the defaults (check). Adding one waits for a validated Q25 capability. |
| `LOCKSCREEN_SECURITY_BEHAVIOR_UNCHANGED` | Code + static check | No Keyguard, bouncer or biometric file is touched (check). The lockscreen changes only through the shared notification card radius and accent. |
| `SETTINGS_ANDROID_IA_PRESERVED` | Code | Only an Accent row in Display and a version row in About. No new top-level entries and no moved settings. |
| `SETUP_CRITICAL_TEXT_ENTRY` | Device | No SetupWizard layout or input change from this package: strings and the logo drawable only. Check Wi-Fi password entry on the Q25 keyboard. |
| `SABLE_PRODUCT_BRANDING` | Code + static check | The owner Setup flow shows no LineageOS name (welcome, restore and recovery strings; features step removed by 0408) and About shows the SableOS version. LineageOS version, legal and licence text stay (check). |
| `FONT_SCALE_1_3_CORE_CONTROLS` | Device | Large labeled tiles help here, and upstream switches to extra-large tiles at 1.8x. Screenshots are needed. |
| `VISUAL_REGRESSION_SET` | Device | The eleven screenshots (compact shade light/dark, expanded QS, notification actions, focused brightness slider, compact media, locked redaction, PIN entry, Settings top level, Setup text entry, confirmation dialog) at 1.0x and 1.3x need a booted build. |

## Not done, and why

* **Sable app shapes.** Not forced to 8/12dp here. The owner chose a user
  setting instead: App display compatibility > Sable app style (Compact by
  default, or Rounded = 8/12dp), implemented by [corners](corners.md).
* **Sliders.** Upstream Material3 `Slider` already gives bounded arrow-key steps
  and Home/End, plus an accessible role and value. The brightness slider has an
  icon and an a11y label but no visible text label or percentage. Adding one is
  a Compose change in `BrightnessSlider.kt` that couldn't be checked here, so it
  was left out.
* **Focus ring width and color.** Upstream draws 3dp in `secondary`, against the
  2dp `focus` token. It was left as upstream because a wider ring is allowed
  ("may increase").
* **Motion.** The framework animation durations were not changed. A global
  `config_shortAnimTime` override would affect every app.
* **Accent drift.** If the user later picks colors in the wallpaper picker, the
  system palette changes but the Sable accent does not. Sable apps keep their
  accent until it is chosen again in Settings > Display > Accent.
* **Keyboard shortcuts for the shade** (Fn+Down and others) were not part of
  this package. The Q25 has no Fn key; frameworks/base patches 0302-0304 map
  them to Alt (Alt+Down, Alt+Up, Alt+Enter), add Space and Alt+Enter on Quick
  Settings tiles and lock screen keys ([kf-a](kf-a.md)).
* **Boot animation.** LineageOS ships its own boot animation, which was not
  replaced. The GrapheneOS-era `android-logo-mask.png` change does not apply.

## Files

```text
sable-src/src/android/shared/sabledesign/src/main/java/org/sableos/design/SableSystemTokens.kt   new, pure
sable-src/src/android/shared/sabledesign/src/test/java/org/sableos/design/SableSystemTokensTest.kt new, 13 tests
product/common/overlay/Android.bp                                                                 new
product/common/overlay/SableFrameworkOverlay/{AndroidManifest.xml,res/values/config.xml}         new
product/common/overlay/SableSystemUIOverlay/{AndroidManifest.xml,res/values/{dimens,config,sable_quick_settings}.xml} new
product/common/overlay/SableSetupWizardOverlay/{AndroidManifest.xml,res/drawable/logo.xml,res/drawable-nodpi/sableos_shield.png,res/values*/strings.xml} new
patches/framework/frameworks/base/0101-*.patch, 0102-*.patch                                       new
patches/framework/packages/apps/Settings/0101-*.patch, 0102-*.patch                               new
scripts/gen-sable-setupwizard-strings.py                                                          new
scripts/stage-product.sh                                                                          FRAMEWORK=YES branch stages overlays
product/q25/sable-q25.mk                                                                          SABLE_LCD_DENSITY
device-profile/DISPLAY.md                                                                         density note points at SABLE_LCD_DENSITY
tests/check-sable-design.py                                                                       new, 29 static checks
tests/java/SableSettingsPolicyTest.java                                                           new, 32 checks
tests/run.sh                                                                                      stage Q4/Q2 overlay checks, design check, Settings policy test
docs/implementation/kf-b.md                                                                       this file
```

## Tests

```bash
bash tests/run.sh                       # includes the two checks below
python3 tests/check-sable-design.py     # tokens, contrast, overlays, branding, density

# Token table unit tests (kotlinc + JUnit shim)
K=<scratchpad>
D=sable-src/src/android/shared/sabledesign/src
$K/kotlinc/bin/kotlinc $D/main/java/org/sableos/design/SableSystemTokens.kt \
    $D/test/java/org/sableos/design/SableSystemTokensTest.kt $K/shim/Shim.kt -d /tmp/kf-b-out
java -cp /tmp/kf-b-out:$K/kotlinc/lib/kotlin-stdlib.jar org.junit.Run org.sableos.design.SableSystemTokensTest

# Regenerate the SetupWizard strings after a LineageOS update
python3 scripts/gen-sable-setupwizard-strings.py <LineageOS packages/apps/SetupWizard checkout>
```

Results here: `SableSystemTokensTest` RAN=13 FAILED=0;
`check-sable-design.py` 29/29 PASS; `SableSettingsPolicyTest` 32 checks,
0 failed; `tests/run.sh` CI=PASS.

## Integration

1. Merge `q25/kf-b`. Regenerate `patches/sable-src/` for the two new
   `sabledesign` files (`SableSystemTokens.kt`, `src/test/.../SableSystemTokensTest.kt`).
2. Add rows to the table in `patches/README.md`:

   | Patch | Base (lineage-23.2) | What it does |
   |---|---|---|
   | `framework/frameworks/base/0101-SableOS-read-default-large-Quick-Settings-tiles-from.patch` | `f4ed08a03b518772ba77c3c0a1b4185fa1712c9b` | Default large QS tiles come from an overlayable resource (DESIGN-KF-B two-column grid on square screens). |
   | `framework/frameworks/base/0102-SableOS-Quick-Settings-tiles-use-the-Sable-card-and-.patch` | same | QS tiles use the 12dp card and 8dp control radii. |
   | `framework/packages/apps/Settings/0101-SableOS-Sable-appearance-authority-and-Display-Accen.patch` | `8d8f6486b274bcf0aa6e5d0cbba52c0b05ae5c65` | `org.sableos.appearance` provider and Display > Accent; accent seeds the system palette. |
   | `framework/packages/apps/Settings/0102-SableOS-SableOS-version-row-in-About-phone.patch` | same | SableOS version row in About phone. |

3. In `product/q25/README.md`, add `overlay/` (from `product/common/overlay`,
   Q4+) to the staged-tree diagram.
4. `docs/QUALIFICATION.md`, proposed Q4 gate **Q4-VISUAL**: the overlays are
   enabled (`adb shell cmd overlay list | grep org.sableos.overlay`, three
   `[x]`); QS shows two labeled tiles per row; Settings > Display > Accent
   changes QS and Sable app accents; About phone shows the SableOS version
   above the LineageOS version; Setup says "Welcome to SableOS"; the DESIGN-KF-B
   visual regression set captured at font scale 1.0 and 1.3; lockscreen
   PIN/password entry, emergency call and notification redaction unchanged.
5. First operator build: run `apply-framework-patches.sh check` (expect
   `APPLIES` for all four), then build `SableSystemUIOverlay`, `Settings` and
   `SystemUI` and fix any compile error in the uncompiled patch code.

## Expected overlaps with other packages

* **Settings patches.** KF-A (Hub), KF-C (Sable Tools), BH (Battery) and T3 Setup
  may also patch `packages/apps/Settings`. These patches add new files plus
  three single-hunk insertions: `AndroidManifest.xml` after
  `org.apache.http.legacy`, `res/xml/display_settings.xml` after Dark theme,
  and `res/xml/my_device_info.xml` before Android version. Conflicts are
  possible only if another package edits the same anchors. Numbering 0101/0102
  is within this package's range.
* **`org.sableos.appearance`.** If another package also ports the Settings
  appearance provider (the GrapheneOS-era `r9_settings_cohesion` one), keep
  one. This one deliberately drops the stored light/dark mode and the
  override bookkeeping in favour of the system theme. Settings patch 0103
  (corners) adds the `corner_style` column on top of it.
* **SystemUI.** KF-A attention controls in QS may add tiles. Profile-gated tiles
  should be appended through a validated profile, not added to
  `quick_settings_tiles_default`.
* **sabledesign.** KF-A, KF-D and T3 may edit `SableTheme.kt`.
  `check-sable-design.py` fails if `AccentPreset` seeds drift from
  `SableAccentTokens`.
* **SetupWizard.** T3 IR-014 (keyboard-first Setup steps) patches the same
  LineageOS SetupWizard. This package overlays the welcome, restore and
  recovery strings and the logo; 0408 drops the LineageOS features step.
* **`stage-product.sh`** `FRAMEWORK=YES` block: other packages adding modules to
  the same `PRODUCT_PACKAGES` heredoc will need a trivial merge.
