# SableOS for the Zinwa Q25

SableOS is a privacy-focused, keyboard-first Android-compatible OS. This
repository holds everything needed to build SableOS for the **Zinwa Q25** (the
BlackBerry Classic restomod, codename `Q25`), without needing the Pixel 7,
Titan 2 or Q27 SableOS lanes.

> **Status: engineering, not yet booted.** Every planned SableOS feature for the
> Q25 is written (Q2 apps and the Q4 framework layer), and GitHub Actions builds
> and checks all 17 Sable apps. No LineageOS image has been built, and nothing
> has run on a Q25 yet. The install steps are derived from the LineageOS Q25
> guide and are **untested for SableOS**. Read [`ROADMAP.md`](ROADMAP.md) and
> [`docs/SABLEOS_GAP_REVIEW.md`](docs/SABLEOS_GAP_REVIEW.md) before flashing anything.

```text
DEVICE=Q25
PHASE=Q2 + Q4 written; Q1 (control build) is next
BASE=LineageOS 23.2 (Android 16 QPR2), official lineage_Q25 device (not a GSI)
SABLE_LAYER=vendor/extra/product.mk -> vendor/sable/q25 (+ patches/framework at Q4)
APPS_BUILD=PASS in GitHub Actions (Gradle build, unit tests, Lint, detekt, ktlint)
IMAGE_BUILD_TESTED=NO   (LineageOS tree; needs a build host)
FRAMEWORK_PATCHES=checked with git apply against their base commits; uncompiled
BOOT_TESTED=NO
FLASH_SCRIPT=NONE (manual, documented steps only)
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
| [Install](docs/INSTALL.md) | Unlock, flash, sideload (untested for SableOS) |
| [SableOS gaps](docs/SABLEOS_GAP_REVIEW.md) | What is still open: build, device checks, owner decisions, known limits |
| [Return to stock](docs/RETURN_TO_STOCK.md) | Backup first, rehearse the restore, recovery ladder back to Zinwa firmware |
| [Architecture](docs/ARCHITECTURE.md) | How the LineageOS base and Sable layer fit together |
| [Keyboard and input](docs/KEYBOARD_AND_INPUT.md) | Q25 keyboard, trackpad and key profile |
| [Qualification](docs/QUALIFICATION.md) | Gates each phase must pass |
| [Crash evidence](docs/CRASH_EVIDENCE.md) | Capture crash logs over adb before anything is cleared |
| [Implementation notes](docs/implementation/) | What each Sable design package (KF-A..D, Battery, T3, corners, icons, settings-ui5, Phone + Contacts, daily driver) changed and what still needs the phone |
| [Patches](patches/README.md) | Q25 changes to the Sable app sources (`sable-src` 0001-0011) and the LineageOS framework patches |
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
bash build/sable.sh q25 Q2 doctor                                  # check host
NETWORK_FETCH_AUTHORIZED=YES bash build/sable.sh q25 Q2 bootstrap   # ~200 GB sync
bash build/sable.sh q25 Q2 apps                                    # build Sable APKs
bash build/sable.sh q25 Q2 stage                                   # inject Sable layer
bash build/sable.sh q25 Q2 build                                   # build image
bash build/sable.sh q25 Q2 artifacts                               # hash outputs
```

`Q1` builds plain `lineage_Q25` (the control image, no Sable layer). `Q2` adds
the Sable layer. `Q4` adds the framework layer (Sable Start as HOME, Sable
Keyboard as the only IME, `patches/framework`); stage it only after gate
Q3-TEXT passes. The Sable app sources are in `sable-src/`
(see [`apps/README.md`](apps/README.md)). Full guide: [`docs/BUILD.md`](docs/BUILD.md).

## Repository layout

```text
build/sable.sh            entry point: bash build/sable.sh q25 <release> <function>
build/devices/q25.sh      device adapter (capabilities, fail-closed flags)
build/config/             storage, host tools and source pins
scripts/                  bootstrap, apps, stage, build, artifacts, import
manifests/local_manifests Q25 device/kernel/blob projects at pinned commits
product/q25/              Sable product layer copied to vendor/sable/q25
device-profile/           Q25 hardware capability profile and key map
apps/                     how the Sable application sources are managed
sable-src/                Sable app sources imported from SableOS (pinned commit)
patches/sable-src/        Q25 changes re-applied to sable-src/ after every import (0001-0011)
patches/framework/        Q4 patches to LineageOS 23.2 projects (Settings, SystemUI, Dialer, ...)
product/common/overlay/   Q4 static overlays (framework, SystemUI, SetupWizard, Glimpse, Gallery2)
docs/                     guides
tests/run.sh              static checks and host unit tests
.github/workflows/        GitHub Actions: checks, Sable apps, Rust, Security
```

## Continuous integration

GitHub Actions runs on every pull request and on `main`:

| Workflow | What it runs |
|---|---|
| `checks` | `tests/run.sh`: shellcheck, pins, staging dry runs, framework patch apply/revert, design and policy checks, host unit tests |
| `Sable apps` | `scripts/build-apps.sh` for each Gradle project (platform apps, r8 apps, Reader, Mail, Text Reader) with unit tests, Android Lint, detekt, ktlint and Kover |
| `Rust` | fmt, clippy, tests, `cargo audit`, `cargo deny`, coverage for `sable-src/apps/r8/rust` |
| `Security` | Gitleaks history scan and MobSF source scan (also weekly) |

`Sable apps` and `Rust` only run when their sources change. The LineageOS image
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
* MarathonOS / Droidian Q25 port for partition and multiboot research.
* SableOS (`aimindseye/sableos`, `sableos-project/*`) for the product, apps and
  design.
