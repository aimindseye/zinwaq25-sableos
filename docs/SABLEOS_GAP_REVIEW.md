# SableOS gaps for the Zinwa Q25

Status: 2026-10-09. This page lists only what is still open. What each part
does, and how it was tested, is in [`implementation/`](implementation/).

## Where the port stands

Every planned SableOS feature for the Q25 is written in this repository:

* **Q2, Sable apps:** 17 apps in [`../product/q25/apps.tsv`](../product/q25/apps.tsv),
  including Reader v2, Mail, Text Reader and Sable Tools (which replaces Radio
  Diag).
* **Q4, framework layer** (`bash build/sable.sh q25 Q4 stage`):
  * Sable Start is the home screen and Launcher3 is kept only for Recents.
  * Sable Keyboard is the only keyboard.
  * Keyboard-first setup steps.
  * Sable look and branding.
  * Attention and Hub follow Android's notification policy.
  * All Apps privacy row.
  * Battery health and usage in Settings.
  * Settings > Apps privacy rows, Network Manager and App security & privacy.

  The framework patches are listed in [`../patches/README.md`](../patches/README.md).

Nothing below is a missing feature in the plan. What remains is compiling,
running on the phone, a few owner decisions, and known limits.

## 1. Needs a build machine

Google's Maven repository is unreachable where this code was written, so no
Android code has been built yet. Pure logic is unit-tested. The app UI code was
type-checked against desktop Compose and Android 16 framework jars, which
catches most errors, but it is not an Android build.

| What to build | Things to watch |
|---|---|
| Apps (`bash build/sable.sh q25 Q2 apps --all`) | Sable Start, Hub, Media, Calendar, Weather, Tools and the design module were edited without a compiler. Run each module's unit tests too. Lint may flag `LauncherApps.getLauncherUserInfo` in Hub (guarded by an SDK check). |
| Flavor apps (Mail, Text Reader) | First build from the pinned upstreams; needs `NETWORK_FETCH_AUTHORIZED=YES`. |
| Framework patches | After `repo sync`, `bash scripts/apply-framework-patches.sh check` must say `APPLIES` for Launcher3, frameworks/base, Settings and SetupWizard. Then build SystemUI, Settings, SetupWizard, `SableLauncher` and the three overlays. |
| Settings Apps screens (0601-0604) | Type-checked against the Android 16 jar with stubbed androidx and SettingsLib; check resource linking, the `tests/sable-apps` host test module and the LineageOS `NetworkPolicyManager` calls. |
| Settings battery screens | `ViewTreeOnBackPressedDispatcherOwner.get` and the `PreferenceGroupAdapter` assumption in the battery keyboard support. |
| Packaging | Soong accepting Sable Tools' `overrides: ["SableRadioDiag"]` when Radio Diag isn't built. |

## 2. Needs the phone

Each item has a gate in [`QUALIFICATION.md`](QUALIFICATION.md); none has run.

* **Before any flash:**
  * Backup and the stock-restore rehearsal (R0).
  * Keyboard firmware update.
* **Device profile (Q3):**
  * Key scan codes and the Sym layer.
  * Trackpad behaviour (keys or pointer).
  * Camera ids.
  * Radio, IMS and VoLTE.
  * Sensors and power.
  * The final display density.
* **Framework layer (Q4):**
  * Home, Recents and keyboard: Q4-HOME, Q4-RECENTS, Q4-IME (only after Q3-TEXT), Q4-MESSAGES.
  * Look and branding: Q4-BRANDING, Q4-VISUAL, Q4-SYSTEMUI.
  * Notifications and Hub: Q4-SHADE-KEYS, Q4-HUB-PARITY, Q4-HUB-PROFILES, Q4-HUB-SETTINGS.
  * Sable Start and apps: Q4-ALLAPPS-PRIVACY, Q4-RESPONSIVE.
  * Setup: Q4-SETUP-KEYS, Q4-SETUP-FALLBACK.
  * Q4-WEATHER-CITIES.
  * Battery: Q4-BATTERY, Q4-BATTERY-EVIDENCE.
  * Tools: Q4-TOOLS, Q4-TOOLS-I5.
  * Settings apps: Q4-SETTINGS-APPS, Q4-NETWORK-MANAGER, Q4-APP-SECURITY.
* **Facts only the phone can give:**
  * The battery facts (capacity health, cycle count, temperature source, charging limits). Each stays Unavailable until [`../device-profile/BATTERY.md`](../device-profile/BATTERY.md) evidence exists.
  * Which Sable Tools utilities really work.
  * Which attention outputs (status LED, keyboard backlight) are real.
  * The measured battery capacity for `power_profile.xml`.
* **Runtime unknowns:**
  * Whether Hub may read the user's lock-screen notification setting. If it can't, it assumes "show, no private content".
  * Whether SELinux allows Settings to see the LineageOS health service. If it doesn't, charging controls stay hidden.
  * Whether Android 16 lets the Sable Tools dialer code open its screen.
  * Whether a normal app can read the default keyboard setting.
* **Visuals and accessibility:**
  * Screenshots of the design's regression and capture sets.
  * Font scale 1.3.
  * TalkBack.
  * Crash evidence after any first-boot crash, captured with [`../scripts/capture-crash-evidence.sh`](../scripts/capture-crash-evidence.sh).

## 3. Needs an owner decision

| Decision | Today |
|---|---|
| Attention outputs for the Q25 | `ro.sable.attention.outputs` is not set, so every attention output is off. Set it once outputs are proven on the phone. |
| Display density | `SABLE_LCD_DENSITY` is empty, so the vendor's 193 stays. One value in `product/q25/sable-q25.mk`. |
| Charging-limit controls (BH6) | Not allowed by the design without a device-verified backend. They stay hidden. |

## 4. Known limits

* No attention output driver ships yet: Hub decides what should alert, but
  nothing drives a light until a device output is validated.
* If the user picks colours in the wallpaper picker, the system palette moves
  away from the Sable accent until it is chosen again in Settings > Display.
* The brightness slider has no visible text label, and the focus ring stays at
  Android's 3dp.
* LineageOS's boot animation is kept.
* Sable app corners are a user setting (App display compatibility > Sable app
  style: Compact, the default, or Rounded). Shapes drawn through the Sable theme
  follow it. Views that hard-code a radius (parts of Sable Start, Reader, Hub,
  Calendar and Messages), the keyboard-first platform apps with their own
  palette (Camera, DisplayCompat, Tools) and Sable Mail keep their own corners.
* Keyboard type-ahead in Settings covers the Battery screens only.
* Battery and Sable Tools strings are English only.
* Connected apps in Hub still calls its ordering list "favorites", while the
  toggle is now "Hub priority".
* Titan 2 GSI, cellular and IMS work does not apply: the Q25 runs a full
  LineageOS device build with the vendor's own radio stack.
