# SableOS for the Zinwa Q25

SableOS is a privacy-focused, keyboard-first Android-compatible OS. This
repository holds everything needed to build SableOS for the **Zinwa Q25** (the
BlackBerry Classic restomod, codename `Q25`), without needing the Pixel 7,
Titan 2 or Q27 SableOS lanes.

> **Status: engineering, not yet booted.** No SableOS image has been built and
> booted on a Q25 from this repository yet. The build scripts are written; the
> install steps are derived from the LineageOS Q25 guide and are **untested for
> SableOS**. Read [`ROADMAP.md`](ROADMAP.md) before flashing anything.

```text
DEVICE=Q25
PHASE=Q0_FOUNDATION
BASE=LineageOS 23.2 (Android 16 QPR2), official lineage_Q25 device
SABLE_LAYER=vendor/extra/product.mk -> vendor/sable/q25
BUILD_TESTED=NO
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
| [Build](docs/BUILD.md) | Host setup, source sync, blobs, building the image |
| [Install](docs/INSTALL.md) | Unlock, flash, sideload (untested for SableOS) |
| [Return to stock](docs/RETURN_TO_STOCK.md) | Getting back to Zinwa firmware |
| [Architecture](docs/ARCHITECTURE.md) | How the LineageOS base and Sable layer fit together |
| [Keyboard and input](docs/KEYBOARD_AND_INPUT.md) | Q25 keyboard, trackpad and key profile |
| [Qualification](docs/QUALIFICATION.md) | Gates each phase must pass |
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
the Sable layer. The Sable app sources are in `sable-src/`
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
patches/                  reserved for device-tree and framework patches
docs/                     guides
tests/run.sh              static checks (also run by GitHub Actions)
```

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
