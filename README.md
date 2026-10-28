# SableOS for the Zinwa Q25

[![Sable apps](https://github.com/aimindseye/zinwaq25-sableos/actions/workflows/android.yml/badge.svg)](https://github.com/aimindseye/zinwaq25-sableos/actions/workflows/android.yml)
[![Checks](https://github.com/aimindseye/zinwaq25-sableos/actions/workflows/checks.yml/badge.svg)](https://github.com/aimindseye/zinwaq25-sableos/actions/workflows/checks.yml)
[![Rust](https://github.com/aimindseye/zinwaq25-sableos/actions/workflows/rust.yml/badge.svg)](https://github.com/aimindseye/zinwaq25-sableos/actions/workflows/rust.yml)
[![Security](https://github.com/aimindseye/zinwaq25-sableos/actions/workflows/security.yml/badge.svg)](https://github.com/aimindseye/zinwaq25-sableos/actions/workflows/security.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=aimindseye_zinwaq25-sableos&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=aimindseye_zinwaq25-sableos)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Platform: LineageOS 23.2](https://img.shields.io/badge/Platform-LineageOS_23.2_(Android_16)-167C80.svg)](https://wiki.lineageos.org/devices/Q25/)
[![Device: Zinwa Q25](https://img.shields.io/badge/Device-Zinwa_Q25-black.svg)](https://wiki.lineageos.org/devices/Q25/)

SableOS is a privacy-focused, keyboard-first Android-compatible OS. This
repository holds everything needed to build SableOS for the **Zinwa Q25** (the
BlackBerry Classic restomod, codename `Q25`), without needing the Pixel 7,
Titan 2 or Q27 SableOS lanes.

> **Status: the image built after PR #33 runs on a Q25; PRs #34-#36 are not built
> yet.** On 2026-10-15/17 a real Q25 (board `q20_v12_factory`, stock
> `Q25_20.01.2026`) was backed up, restored to stock, unlocked, and flashed with
> the Q1 control image, the Q2 image and then the first Q4 image. The PR #28 Q4
> image was reviewed on 2026-10-22 and PR #32 fixed that review. The image built
> after PR #33 was flashed and reviewed on 2026-10-28
> ([second device review](docs/implementation/q4-review.md#second-device-review-2026-10-28-sable-src-0074)).
> PR #34 answers it (one palette, square-screen layouts, a BlackBerry 7 home
> style, Setup choices, the Sable boot animation). PR #35 makes the $ key type
> '$', makes Sable Setup translatable and adds a keyboard firmware screen. PR #36
> adds BlackBerry AutoText, single-key list movement, speed dial on held letters
> and the Sound Profiles app, and the tool that packages a build as a GitHub
> alpha release. That build, alpha-20261028, booted on the Q25; PR #38 answers
> its review (the weather fix, Sound Profiles restyled, BlackBerry 7 home dials,
> swipes and home screen icons, pure black, and seven new games: Bricks, Snake,
> Blocks, Reversi, Solitaire, Mahjong and Words). Their Q4-* gates are `NOT_RUN` until the next image.
> **Using SableOS:** [`docs/USER_GUIDE.md`](docs/USER_GUIDE.md) covers setup,
> the keys, the apps, the privacy and security defaults and what to change.
> Install steps: [`docs/INSTALL.md`](docs/INSTALL.md), or for a downloaded
> release [`docs/FLASHING_RELEASE.md`](docs/FLASHING_RELEASE.md).
> Gates and evidence: [`docs/QUALIFICATION.md`](docs/QUALIFICATION.md). Design
> status per artifact: [`docs/DESIGN_CONFORMANCE_Q25.md`](docs/DESIGN_CONFORMANCE_Q25.md).
> What is still open: [`docs/SABLEOS_GAP_REVIEW.md`](docs/SABLEOS_GAP_REVIEW.md).

```text
DEVICE=Q25
PHASE=R0, Q1, Q2 PASS on a Q25; Q4 image after PR #33 flashed and reviewed 2026-10-28; PRs #34-#36 not built yet
BASE=LineageOS 23.2 (Android 16 QPR2), official lineage_Q25 device (not a GSI)
SABLE_LAYER=vendor/extra/product.mk -> vendor/sable/q25 (+ patches/framework at Q4)
APPS_BUILD=PASS in GitHub Actions (Gradle build, unit tests, Lint, detekt, ktlint)
IMAGE_BUILD_TESTED=Q1, Q2 (ai-g732, 2026-10-15); Q4 (2026-10-17); PR #28 Q4 (2026-10-22, with PR #31); PR #33 Q4 (2026-10-28)
FRAMEWORK_PATCHES=102 patch files; all apply in order (check with scripts/apply-framework-patches.sh check)
BOOT_TESTED=Q1, Q2 on a Q25 (2026-10-16/17); Q4 (2026-10-17); PR #28 Q4 (2026-10-22); PR #33 Q4 (2026-10-28)
FLASH_SCRIPT=NONE (manual, documented steps in docs/INSTALL.md; tested for Q1, Q2 and Q4)
RELEASES=none published yet; build/sable.sh q25 Q4 release packages one as a GitHub pre-release
```

## Device info

| | |
|---|---|
| Vendor / name | Zinwa Q25 (Q25 Pro) |
| Codename | `Q25` |
| Released | 2025 |
| SoC | MediaTek Helio G99 (MT6789) |
| CPU | 2x Cortex-A76 2.2 GHz + 6x Cortex-A55 2.0 GHz |
| GPU | Mali-G57 MC2 |
| RAM | 12 GB |
| Storage | 256 GB UFS 2.x, microSD |
| Display | 3.5" 720x720 IPS (square, 1:1) |
| Input | Physical QWERTY keyboard, optical trackpad, touchscreen |
| Cameras | 50 MP rear with LED flash, 8 MP front |
| Battery | 3000 mAh, non-removable |
| Network | 2G/3G/4G LTE, no 5G |
| Connectivity | Wi-Fi 6 (a/b/g/n/ac/ax), Bluetooth 5.2, NFC, USB-C, 3.5 mm jack, FM |
| Stock OS | Android 14 |
| SableOS base | LineageOS 23.2 (Android 16) |

More: [`docs/DEVICE_INFO.md`](docs/DEVICE_INFO.md).

## Guides

| Guide | What it covers |
|---|---|
| [Roadmap](ROADMAP.md) | What's reused from SableOS, what's Q25-specific, and the phases |
| [Build](docs/BUILD.md) | Host setup, source sync, blobs, building the image, GitHub Actions |
| [Install](docs/INSTALL.md) | Unlock, flash, sideload (tested for Q1 and Q2) |
| [Flash a release](docs/FLASHING_RELEASE.md) | For a downloaded alpha release: back up, flash, and go back to stock |
| [Play Store apps](docs/MICROG.md) | microG and Aurora Store (Q4, on by default): installing LinkedIn and other apps, battery and location defaults, removing it |
| [SableOS gaps](docs/SABLEOS_GAP_REVIEW.md) | What is still open: build, device checks, owner decisions, known limits |
| [Return to stock](docs/RETURN_TO_STOCK.md) | Backup first, rehearse the restore, recovery ladder back to Zinwa firmware |
| [Architecture](docs/ARCHITECTURE.md) | How the LineageOS base and Sable layer fit together |
| [Keyboard and input](docs/KEYBOARD_AND_INPUT.md) | Q25 keyboard, trackpad, key profile, AutoText and speed dial |
| [Languages](docs/LANGUAGES.md) | What is translated and how to translate a Sable app |
| [Qualification](docs/QUALIFICATION.md) | Gates each phase must pass |
| [Crash evidence](docs/CRASH_EVIDENCE.md) | Capture crash logs over adb before anything is cleared |
| [Implementation notes](docs/implementation/) | What each Sable design package (KF-A..D, Battery, T3, corners, icons, settings-ui5, Phone + Contacts, daily driver, Hub dock, power, Q4 review fixes) changed and what still needs the phone |
| [Patches](patches/README.md) | Q25 changes to the Sable app sources (`sable-src` 0001-0076) and the LineageOS framework patches |
| [Stock basis](docs/STOCK_BASIS.md) | Firmware facts to record before flashing |
| [Sources](docs/SOURCES.md) | Every upstream with its pin and licence |
| [Lessons from Titan 2](docs/LESSONS_FROM_TITAN2.md) | Mistakes not to repeat |

### Special boot modes

* **Fastboot / bootloader:** power off, hold **Volume Up + Power**, release when
  the boot menu shows, choose *fastboot*. Or `adb reboot bootloader`.
* **Recovery:** same key combo, choose *recovery*.

## Quick start (build)

```bash
git clone https://github.com/aimindseye/zinwaq25-sableos
cd zinwaq25-sableos
# optional, tree on another disk: export SABLE_BUILD_ROOT=/big/disk/sable-q25
export ANDROID_HOME=/path/to/android-sdk                          # platform 36, build-tools 36.0.0
bash build/sable.sh q25 Q4 doctor                                  # check host
NETWORK_FETCH_AUTHORIZED=YES bash build/sable.sh q25 Q4 bootstrap   # ~200 GB sync
bash scripts/apply-framework-patches.sh check                      # patches still apply?
bash build/sable.sh q25 Q4 apps                                    # build Sable APKs (always before stage)
NETWORK_FETCH_AUTHORIZED=YES bash build/sable.sh q25 Q4 microg      # microG + Aurora APKs
bash build/sable.sh q25 Q4 stage                                   # Sable layer + framework patches
bash build/sable.sh q25 Q4 build                                   # build image
bash build/sable.sh q25 Q4 artifacts                               # hash outputs
bash build/sable.sh q25 Q4 release                                 # optional: zip + notes for a GitHub pre-release
```

`Q4` is the full SableOS build: the Sable layer plus the framework layer (Sable
Start as HOME, Sable Keyboard as the only IME, `patches/framework`, microG unless
`SABLE_Q25_MICROG=NO`). `Q2` adds only the Sable apps and props on stock LineageOS,
and `Q1` builds plain `lineage_Q25` (the control image). For those, replace `Q4`
above and skip `microg`. `stage` packages the APKs already in `apps-out`, so run
`apps` again after pulling new sources. The Sable app sources are in `sable-src/`
(see [`apps/README.md`](apps/README.md)). Full guide: [`docs/BUILD.md`](docs/BUILD.md).

## Repository layout

```text
build/sable.sh            entry point: bash build/sable.sh q25 <release> <function>
build/devices/q25.sh      device adapter (capabilities, fail-closed flags)
build/config/             storage, host tools and source pins
scripts/                  bootstrap, apps, stage, build, artifacts, release, import
releases/                 hand-written notes for GitHub releases (ALPHA_NOTES.md)
manifests/local_manifests Q25 device/kernel/blob projects at pinned commits
product/q25/              Sable product layer copied to vendor/sable/q25
device-profile/           Q25 hardware capability profile and key map
apps/                     how the Sable application sources are managed
sable-src/                Sable app sources imported from SableOS (pinned commit)
patches/sable-src/        Q25 changes re-applied to sable-src/ after every import (0001-0076)
patches/framework/        Q4 patches to LineageOS 23.2 projects (Settings, SystemUI, Dialer, ...)
product/common/overlay/   Q4 static overlays (framework, SystemUI, SetupWizard, Glimpse, Gallery2)
docs/                     guides
tests/run.sh              static checks and host unit tests
sonar-project.properties  SonarCloud analysis scope (scripts/sonar-compile-commands.sh feeds the C shim)
.github/workflows/        GitHub Actions: checks, Sable apps, Rust, Security, SonarCloud
```

## Continuous integration

GitHub Actions runs on every pull request and on `main`:

| Workflow | What it runs |
|---|---|
| `checks` | `tests/run.sh`: shellcheck, pins, staging dry runs, framework patch apply/revert, design and policy checks, host unit tests |
| `Sable apps` | `scripts/build-apps.sh` for each Gradle project (platform apps, r8 apps, Reader, Mail, Text Reader) with unit tests, Android Lint, detekt, ktlint and Kover |
| `Rust` | fmt, clippy, tests, `cargo audit`, `cargo deny`, coverage for `sable-src/apps/r8/rust` |
| `Security` | Gitleaks history scan and MobSF source scan (also weekly) |
| `SonarCloud` | SonarCloud scan of the whole repository (Kotlin, Java, C, Python, shell, XML), waiting on the "Sonar way" quality gate |

`Sable apps` and `Rust` only run when their sources change. SonarCloud runs as
CI-based analysis, so Automatic Analysis must stay off in the SonarCloud
project. The only open SonarCloud findings on `main` are the 25 `restrict`
qualifiers in the camera shim, kept on purpose. The LineageOS image
is too large for hosted runners and is built on a build host. Details:
[`docs/BUILD.md`](docs/BUILD.md#7-github-actions).

## Boundaries

This repository must never contain stock firmware, OTA packages, partition
images, extracted proprietary blobs, signing keys, or device identifiers (serial,
IMEI). Proprietary blobs come from `TheMuppets/proprietary_vendor_xelex_Q25` at
sync time or from your own device with `scripts/extract-blobs.sh`.

## Licence

Apache License 2.0 ([`LICENSE`](LICENSE), [`NOTICE`](NOTICE)). Third-party
components keep their own licences: see
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

## Credits

* LineageOS Q25 maintainers (electimon, basamaryan, npjohnson, Androbots) for the
  device tree, kernel and blobs this port builds on.
* SableOS (`aimindseye/sableos`, `sableos-project/*`) for the product, apps and
  design.

Other projects this port uses or learned from:

* [nozerorma/q25toolbox](https://github.com/nozerorma/q25toolbox): the GMS Sensor
  Guard recipe, the Q25 battery facts (static cycle count, FULL never reported,
  health near 100%) and the per-app resolution, resolution hotkey and rotation
  ideas behind Display compatibility's tall canvas. Ideas and facts only; no code
  copied, as the repo has no licence.
* [benjamindaines/q25-led](https://github.com/benjamindaines/q25-led) and
  [mybabysexy/q25-led](https://github.com/mybabysexy/q25-led): checked for LED
  control. They drive the same three `/sys/class/leds` switches the Lights HAL
  uses, so they add nothing the port lacks ([kf-a](docs/implementation/kf-a.md)).
  IvanTheTolerable (Discord) first found the GMS Sensor Guard fix.
* [patproport/Q25EdgeTrackpad](https://github.com/patproport/Q25EdgeTrackpad)
  (GPL-3.0): the `/proc/q20_switch_key_mouse` switch, edge scrolling and the touch
  guard. Ideas only; no code copied.
* [bellhopsw/q25-50mp](https://github.com/bellhopsw/q25-50mp) (MIT and GPL): the
  optional 50 MP rear camera in `product/q25/camera-50mp`.
* [MarathonOS/marathon-zinwa-q25](https://github.com/MarathonOS/marathon-zinwa-q25)
  and [JamiKettunen/droidian-zinwa-q25](https://github.com/JamiKettunen/droidian-zinwa-q25):
  partition, GPT and multiboot research.
* [asmtronic/Zinwa-Q25-Guide](https://github.com/asmtronic/Zinwa-Q25-Guide): the SP
  Flash Tool return-to-stock steps.
* [bkerler/mtkclient](https://github.com/bkerler/mtkclient): full-device backups.
* [ovehbe/i2c_puppet](https://github.com/ovehbe/i2c_puppet) (PaBaLF firmware): the
  keyboard backlight.
* [eqbirvin/Conduit](https://github.com/eqbirvin/Conduit) (GPL-3.0): the Hub dock
  idea.
* Ripe Launcher (Ripe 7 and Ripe 10): the BB7-style unread bar on Start's Hub
  page and the 5-column app grid. Looked at, not copied.
* [TheMuppets/proprietary_vendor_xelex_Q25](https://github.com/TheMuppets/proprietary_vendor_xelex_Q25):
  the vendor blobs.
* [microG](https://github.com/microg) (GmsCore, FakeStore),
  [Aurora Store](https://gitlab.com/AuroraOSS/AuroraStore) and
  [lineageos4microg](https://github.com/lineageos4microg): microG and the Play
  Store client.
* [thunderbird/thunderbird-android](https://github.com/thunderbird/thunderbird-android):
  the base of Sable Mail.
* [vaachak-platform](https://github.com/vaachak-platform) (vaachak-textreader,
  vaachak-mobile): the base of Text Reader.
* [Phosphor Icons](https://github.com/phosphor-icons/core),
  [iA Fonts](https://github.com/iaolo/iA-Fonts) and
  [Accessible DfA](https://github.com/Orange-OpenSource/font-accessible-dfa): icons
  and fonts.
