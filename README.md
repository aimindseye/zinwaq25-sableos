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

> **Status: the next release is SableOS 1.0, built from this main. The latest published build so far is the test build
> [`alpha-20261029`](https://github.com/aimindseye/zinwaq25-sableos/releases/tag/alpha-20261029),
> built from main after PR #38; it runs on a Q25.** On 2026-10-15/17 a real
> Q25 (board `q20_v12_factory`, stock `Q25_20.01.2026`) was backed up, restored
> to stock, unlocked, and flashed with the Q1 control image, the Q2 image and
> then the first Q4 image. The device reviews on 2026-10-22 and 2026-10-28
> ([review notes](docs/implementation/q4-review.md)) were answered by PRs #32,
> #34-#36 and #38. The first alpha, `alpha-20261028`, booted; PR #38 answered
> its review: the weather fix, Sound Profiles restyled, BlackBerry 7 home dials,
> swipes and home screen icons, one pure black palette, and seven new games
> (Bricks, Snake, Blocks, Reversi, Solitaire, Mahjong and Words).
> On `alpha-20261029` the seven games install under /product and the Pure
> black setting defaults to on. Two problems found on it are fixed in PR #40
> (in the next alpha): the system black theme overlay was not applied
> (frameworks/base 0103), and returning from Hub on the BlackBerry 7 home showed
> the BlackBerry 10 home ([#39](https://github.com/aimindseye/zinwaq25-sableos/issues/39)).
> PR #41 (also for the next alpha) finishes Sable Reader for the Q25: the
> Comics and Audio tabs list comics and audiobooks instead of "Coming Soon",
> N/P change audiobook chapters, Settings sets the comic and audiobook
> defaults, and the library, audiobook player and dialogs fit the 411 dp
> square screen.
> Sable Notes (also for the next alpha, sable-src 0081) adds notes and
> checklists in the spirit of BlackBerry Remember and Color Notes: colour
> labels, a Tasks view of every open item, and share, export and import, all
> kept on the phone.
> Clipboard history (sable-src 0083) is an opt-in Sable Keyboard setting:
> held Alt+Shift+V lists your last 20 copies in the keyboard strip, kept in
> memory only and never on the lock screen.
> Sable Terminal (sable-src 0084) is ConnectBot 1.10.9 for SSH, Telnet and a
> local shell; on the Q25 the Menu key is Ctrl and Menu then Back is Esc.
> Every Sable app now keeps its English in `strings.xml` (sable-src 0085) and has
> draft translations in 18 languages; see [`docs/LANGUAGES.md`](docs/LANGUAGES.md).
> **Disclaimer:** Sable app translations (18 languages) are machine-assisted drafts: they have not been reviewed by fluent speakers and may contain errors or awkward wording. English is the reference text. Corrections are welcome.
> Most Q4-* device gates are still `NOT_RUN`
> ([#27](https://github.com/aimindseye/zinwaq25-sableos/issues/27) tracks them).
> **Using SableOS:** [`docs/USER_GUIDE.md`](docs/USER_GUIDE.md) covers setup,
> the keys, the apps, the privacy and security defaults and what to change.
> Install steps: [`docs/INSTALL.md`](docs/INSTALL.md), or for a downloaded
> release [`docs/FLASHING_RELEASE.md`](docs/FLASHING_RELEASE.md).
> Gates and evidence: [`docs/QUALIFICATION.md`](docs/QUALIFICATION.md). Design
> status per artifact: [`docs/DESIGN_CONFORMANCE_Q25.md`](docs/DESIGN_CONFORMANCE_Q25.md).
> What is still open: [`docs/SABLEOS_GAP_REVIEW.md`](docs/SABLEOS_GAP_REVIEW.md).

```text
DEVICE=Q25
PHASE=R0, Q1, Q2 PASS on a Q25; Q4 alpha-20261029 (main after PR #38) flashed 2026-10-29, device gates in progress
BASE=LineageOS 23.2 (Android 16 QPR2), official lineage_Q25 device (not a GSI)
SABLE_LAYER=vendor/extra/product.mk -> vendor/sable/q25 (+ patches/framework at Q4)
APPS_BUILD=PASS in GitHub Actions (Gradle build, unit tests, Lint, detekt, ktlint)
IMAGE_BUILD_TESTED=Q1, Q2 (ai-g732, 2026-10-15); Q4 (2026-10-17); PR #28 Q4 (2026-10-22); PR #33 Q4 (2026-10-28); alpha-20261028; alpha-20261029
FRAMEWORK_PATCHES=104 patch files; all apply in order (check with scripts/apply-framework-patches.sh check)
BOOT_TESTED=Q1, Q2 on a Q25 (2026-10-16/17); Q4 (2026-10-17, 2026-10-22, 2026-10-28); alpha-20261028; alpha-20261029 (2026-10-29)
FLASH_SCRIPT=NONE (manual, documented steps in docs/INSTALL.md and docs/FLASHING_RELEASE.md)
RELEASES=1.0 (the first SableOS release, built from this main), earlier test builds alpha-20261029, alpha-20261028; GitHub releases made with build/sable.sh q25 full release
```

## Install a release

Every release is built on **LineageOS 23.2 (Android 16 QPR2)** and says so in its
release notes. Download `sableos-q25-full-1.0.zip` from
[Releases](https://github.com/aimindseye/zinwaq25-sableos/releases), then, with
`adb` and `fastboot` installed and the phone's USB-C cable plugged in:

```bash
unzip sableos-q25-full-1.0.zip
cd sableos-q25-full-1.0
sha256sum -c SHA256SUMS.txt                 # every line must say OK
adb reboot bootloader
fastboot flashing unlock                    # erases the phone; confirm on it
fastboot flash boot boot.img
fastboot flash dtbo dtbo.img
fastboot flash vbmeta vbmeta.img
fastboot flash vendor_boot vendor_boot.img
fastboot reboot recovery
# in recovery: Factory reset > Format data, then Apply update > Apply from ADB
adb sideload sableos-q25-full-*-userdebug.zip
```

Back up first and read [`docs/FLASHING_RELEASE.md`](docs/FLASHING_RELEASE.md) before
you start: unlocking erases everything, and SableOS is an unofficial community build that you flash at your own risk.

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
| SableOS base | LineageOS 23.2 (Android 16 QPR2) |

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
| [Patches](patches/README.md) | Q25 changes to the Sable app sources (`sable-src` 0001-0085) and the LineageOS framework patches |
| [Stock basis](docs/STOCK_BASIS.md) | Firmware facts to record before flashing |
| [Sources](docs/SOURCES.md) | Every upstream with its pin and licence |
| [Lessons from Titan 2](docs/LESSONS_FROM_TITAN2.md) | Mistakes not to repeat |

### Special boot modes

* **Fastboot / bootloader:** power off, hold **Volume Up + Power**, release when
  the boot menu shows, choose *fastboot*. Or `adb reboot bootloader`.
* **Recovery:** same key combo, choose *recovery*.

## Build the image yourself

You need a Linux computer (Ubuntu 22.04 or newer, 32 GB RAM, 300 GB free disk)
and the Android SDK (platform 36). The package list is in
[`docs/BUILD.md`](docs/BUILD.md) section 1. No other background is needed.

```bash
git clone https://github.com/aimindseye/zinwaq25-sableos
cd zinwaq25-sableos
export ANDROID_HOME=/path/to/android-sdk
bash build/sable.sh q25 all --yes
```

That one command checks your computer and says what is missing, downloads
LineageOS 23.2 (about 200 GB, first run only), builds the Sable apps, adds the
Sable layer, builds the image and collects the files. The first run takes many
hours. If it stops, read the message, fix that, and run the same command again:
finished steps are reused, and LineageOS and microG that are already downloaded
are never downloaded twice (`bootstrap --update` forces a new LineageOS sync). Add `--release` to also zip the result for a GitHub
release. The files end up in `~/sable-q25/artifacts/q25/full/`; to use another
disk, `export SABLE_BUILD_ROOT=/big/disk/sable-q25` first. Then install them
with [`docs/INSTALL.md`](docs/INSTALL.md).

Advanced: `bash build/sable.sh q25 <function>` runs one step (`doctor`,
`bootstrap`, `apps`, `microg`, `stage`, `build`, `artifacts`, `release`). The
default release `full` is the complete SableOS image. `Q2` (Sable apps on stock
LineageOS) and `Q1` (plain `lineage_Q25`, the control image) can be put before
the function, as in `bash build/sable.sh q25 Q1 build`. The Sable app sources are
in `sable-src/` (see [`apps/README.md`](apps/README.md)). Full guide:
[`docs/BUILD.md`](docs/BUILD.md).

## Repository layout

```text
build/sable.sh            entry point: bash build/sable.sh q25 <release> <function>
build/devices/q25.sh      device adapter (capabilities, fail-closed flags)
build/config/             storage, host tools and source pins
scripts/                  bootstrap, apps, stage, build, artifacts, release, import
releases/                 hand-written notes for GitHub releases (1.0.md)
manifests/local_manifests Q25 device/kernel/blob projects at pinned commits
product/q25/              Sable product layer copied to vendor/sable/q25
device-profile/           Q25 hardware capability profile and key map
apps/                     how the Sable application sources are managed
sable-src/                Sable app sources imported from SableOS (pinned commit)
patches/sable-src/        Q25 changes re-applied to sable-src/ after every import (0001-0085)
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
