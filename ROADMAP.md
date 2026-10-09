# SableOS for Zinwa Q25: port roadmap

Status: **Q0 repository foundation. Nothing in this repository has been built or booted on a Q25 yet.**

Date: 2026-10-09

This roadmap says what this port reuses from existing SableOS work, what has to
be written for the Zinwa Q25, and the order the work happens in. It is meant to
be read on its own: you don't need to know the Pixel 7, Titan 2 or Q27 SableOS
lanes to follow it.

---

## 1. Starting point

### 1.1 The device

| Item | Q25 value | Where it comes from |
|---|---|---|
| Codename | `Q25` (board names `q20_v12_factory`, `q20_v1_factory`) | LineageOS device tree `board-info.txt` |
| Vendor namespace | `xelex` (`device/xelex/Q25`) | LineageOS device tree |
| SoC | MediaTek Helio G99 (`mt6789`), 2x A76 + 6x A55, Mali-G57 MC2 | LineageOS wiki, device tree |
| RAM / storage | 12 GB / 256 GB UFS 2.x, microSD | Zinwa product page, Wikipedia |
| Display | 3.5" 720x720 square LCD (the original BlackBerry Classic panel), density 193 | device tree `BoardConfig.mk` |
| Input | BlackBerry Classic QWERTY keyboard, optical trackpad, touchscreen, Call/End keys | device tree keylayout and kcm |
| Stock OS | Android 14 (shipping API 34), "Xelex10_Ultra" fingerprint, 2026-03-26 FOTA | device tree `lineage_Q25.mk`, `system_ext.prop` |
| Partitions | Virtual A/B, dynamic `super` (9 GiB), boot header v4, GKI, `vendor_boot` recovery | device tree `BoardConfig.mk` |
| Bootloader | Unlockable with standard `fastboot flashing unlock` | LineageOS install guide |

Full details: [`docs/DEVICE_INFO.md`](docs/DEVICE_INFO.md).

### 1.2 Why the Q25 path differs from Titan 2

Titan 2 (Dimensity 7300, MT6878) has no public device tree or kernel source, so
SableOS for Titan 2 is a Treble **system image (GSI) on top of the stock vendor
partitions**. That lane has a reboot loop on its latest image and an open
bootclasspath failure, so it is a structural reference here, not proven code.

The Q25 is in a much better position:

* LineageOS ships an **official** Q25 device tree, kernel source and extracted
  vendor blobs, maintained on `lineage-23.2` (Android 16 QPR2) and already
  branched for `lineage-24.0` (Android 17).
* So the Q25 can be built as a **full device image** (boot, vendor_boot, dtbo,
  vbmeta, super) from source, the same way LineageOS does it, instead of
  layering a GSI on an unknown stock vendor.

**Decision for this port (default, revisable):** SableOS Q25 is built on the
LineageOS 23.2 source tree with the official `lineage_Q25` device, and the Sable
product layer is injected through LineageOS's supported
`vendor/extra/product.mk` hook. The device tree itself is not forked. A GSI lane
is kept only as a fallback (section 5, lane G).

Why LineageOS rather than the GrapheneOS base the Pixel build uses: GrapheneOS
only supports Pixels and the Q25 tree depends on LineageOS components
(`hardware/lineage/compat`, `hardware/mediatek`, `device/mediatek/sepolicy_vndr`,
Lineage's kernel build). Moving to an AOSP or GrapheneOS-derived base is phase Q6,
after the device is proven.

---

## 2. Reuse matrix

Legend for **Action**:

* **Copy**: bring across unchanged.
* **Adapt**: bring across and change for the Q25 (path, device id, base OS).
* **Pattern**: re-implement the same idea; the source is too tied to its device.
* **Reference**: read it for design rules; nothing is copied.
* **Drop**: does not apply to the Q25.

Paths starting with `sableos/` are in the private `aimindseye/sableos` repo
(pinned at `c538fc0e57e4592e11b27c93870b79ee48f7fbfc` for this roadmap).
Paths starting with `platform_sable/` are in the public
`sableos-project/platform_sable` repo.

### 2.1 Build system and scripts

| Source | Action | Q25 destination | Notes |
|---|---|---|---|
| `sableos/build/sable.sh` (device/release/function entry point) | Adapt | `build/sable.sh` | Same `<device> <release> <function>` shape, single device `q25`, functions trimmed to `doctor`, `bootstrap`, `apps`, `stage`, `build`, `artifacts`, `ci`. |
| `sableos/build/devices/*.sh` (fail-closed adapter pattern) | Adapt | `build/devices/q25.sh` | Capability flags (`BUILD_SUPPORTED`, `FLASH_SUPPORTED=NO`, ...) kept; values set for Q25. |
| `sableos/build/config/storage.env`, `host-tools.env` | Adapt | `build/config/storage.env`, `build/config/host-tools.env` | Same variable names; defaults moved under `$HOME/sable-q25` so a single-user workstation works. |
| `sableos/build/panther/bootstrap.sh` (`NETWORK_FETCH_AUTHORIZED` gate, pinned manifest evidence) | Adapt | `scripts/bootstrap.sh` | `repo init` points at LineageOS `lineage-23.2` plus our local manifest, not GrapheneOS. |
| `sableos/build/titan2/build-n1d-c3b-e2-product-apps.sh` (Gradle -> unsigned APK bundle) | Pattern | `scripts/build-apps.sh` | Same flow: build from admitted source, hash, stage as `android_app_import`. |
| `sableos/build/titan2/stage-n1d-c3b-e2-product-composition.sh` | Pattern | `scripts/stage-product.sh` | Stages `product/q25` into `vendor/sable/q25` and writes `vendor/extra/product.mk`. |
| `sableos/build/deploy/artifact_registry.py` | Adapt (Q2) | `scripts/` | Its `full-device-images` artifact kind fits the Q25. Not needed until artifacts are registered. |
| `sableos/build/deploy/flash.sh`, `target_files_plan.py` | Pattern (Q5) | n/a yet | Panther's A/B flash assumptions must not be reused for MediaTek until the Q25 flash contract is proven. |
| `sableos/build/local-ci/run.sh` | Pattern | `tests/run.sh` | Static checks only for now. |
| `sableos/build/gates/**`, `build/titan2/**` (N1B/N1D/N1I/C3A gate scripts) | Drop | none | Titan 2 GSI/fastbootd/LP-resize specific. The *lessons* are kept in `docs/LESSONS_FROM_TITAN2.md`. |
| `sableos/build/panther/**`, `build/macos/**` | Drop | none | Pixel-only. |
| `sableos/tools/sablectl` (host validation CLI) | Adapt (Q3) | `tools/sablectl` | Device-agnostic parts (doctor, baseline capture, preserved-data) port; Panther target-files checks don't. |
| `sableos/tools/validation/**` | Reference | none | Panther/R1 specific. |

### 2.2 Sable applications (common product, no Q25 fork)

SableOS rule ([`platform_sable/docs/PORTABILITY_RULES.md`](https://github.com/sableos-project/platform_sable/blob/main/docs/PORTABILITY_RULES.md)):
one common app source, device behaviour through profiles. The Q25 does not fork
any app; it adds a `zinwa-q25` profile where an app needs device facts.

| App | Source | Action | Q25 work |
|---|---|---|---|
| Sable Keyboard (IME) | `sableos/apps/titan2/platform/keyboard` | Copy + profile | Add `zinwa-q25` to `KeyLayout.forProfile`. BlackBerry Alt legends are already in the Q25 `Q25_keyboard.kcm`; transcribe, keep `verified=false` until a device run. |
| Sable Camera (Control Deck) | `sableos/apps/titan2/platform/camera` | Copy + profile | Add `CameraDeviceProfile("zinwa-q25", EvidenceLevel.None)`; capture 50 MP / 8 MP ids and sizes on hardware. |
| Display Compat | `sableos/apps/titan2/platform/displaycompat` | Copy | `SquareSafe` profile already exists (Titan 2 is also square). Tune for 720x720 at 193 dpi. |
| Setup (first-boot readiness) | `sableos/apps/titan2/platform/setup` | Copy | Reads `ro.sable.profile.id`; nothing Q25-specific. |
| Radio Diag | `sableos/apps/titan2/platform/radiodiag` | Copy | Validates MTK IMS/VoLTE state; MT6789 IMS comes from `hardware/mediatek` in the Lineage tree. |
| Calculator, Convert, Games (Sudoku, Minesweeper, 2048) | `sableos/apps/r8/android/{calculator,convert,games}` | Copy | Check 720x720 layouts. |
| Sable Hub / Messages | `sableos/apps/r8/android/{hub,messages}` | Copy | Must keep the Hub portability contract (`platform_sable/docs/SABLE_HUB_PORTABILITY_CONTRACT.md`). |
| Sable Media, Weather, Calendar | `sableos/apps/r8/android/{media,weather,calendar}` | Copy | Keyboard-first pass. |
| Sable Reader v2, Text Reader | `sableos/apps/r8/android/{reader,textreader}` | Copy | Reader v2 is a separate P5 train; take the frozen version. |
| Sable Mail | `sableos/apps/r8/mail` (Thunderbird 23.0 based) | Copy | Large; optional for Q2. |
| Sable design tokens | `sableos/apps/r8/android/design`, `sableos/src/android/shared/sabledesign` | Copy | Shared by all apps. |
| Sable Start (HOME presentation) | `sableos/src/android/packages/apps/SableStart` + Launcher3 patch | Adapt (Q4) | Today it is hosted inside a patched GrapheneOS Launcher3. On LineageOS the launcher is Trebuchet, so the host patch must be re-done (section 2.3). |

How the apps reach the image is the Titan 2 pattern: Gradle builds unsigned
APKs, they're hashed, and `product/q25/Android.bp` imports them as
non-privileged `android_app_import` modules signed by the build.

### 2.3 Framework patches

| Source | Action | Notes |
|---|---|---|
| `sableos/patches/android-17-grapheneos-2026081300/apply_*.py` (branding, Launcher3 host, Settings cohesion, SystemUI, SetupWizard) | Adapt (Q4) | Written against GrapheneOS Android 17 trees. Lineage 23.2 is Android 16 with different Settings/SystemUI/Trebuchet code, so each patcher needs a Lineage target. Do branding first; Launcher3 host second. |
| `sableos/patches/android-16-n1d-trebledroid/**` | Drop | TrebleDroid/GSI compatibility fixes; not needed on a real device tree. |
| `sableos/patches/android-12.1`, `android-14.0.0_r28`, `android-16-bp4a`, `android-16-n1i-c1` | Drop | Historical Pixel/Titan baselines. |

### 2.4 Product and device files

| Source | Action | Q25 destination |
|---|---|---|
| `sableos/product/titan2/c3b/{sable_titan2.mk,sable-titan2-apps.mk,Android.bp}` | Adapt | `product/q25/{sable-q25.mk,sable-q25-apps.mk,Android.bp}` |
| `sableos/product/r8/panther/permissions/*` | Reference | Only if a Sable app becomes privileged (none are in Q2). |
| `sableos/device/sable/titan2/n0/{STOCK_BASIS,DEPLOYMENT_GATE,ARTIFACT_DECISION}.md` | Adapt | `docs/STOCK_BASIS.md`, `docs/QUALIFICATION.md` |
| `sableos/device/sable/bramble/**` (powerd, oxynoded, sepolicy) | Drop | Pixel 4a 5G research daemons. |

### 2.5 Documentation and design (public, reference only)

| Source | Use |
|---|---|
| `platform_sable/docs/PORTABILITY_RULES.md`, `DEVICE_SUPPORT_LEVELS.md` | Support-level vocabulary used in `docs/QUALIFICATION.md`. |
| `platform_sable/docs/KEYBOARD_FIRST_DEVICE_PROFILE_MODEL.md`, `KEYBOARD_AND_POINTER_PROFILE_MODEL.md`, `NORMALIZED_KEY_INPUT_CONTRACT.md`, `CRITICAL_TEXT_ENTRY_GATES.md` | Q25 keyboard and trackpad profile (`docs/KEYBOARD_AND_INPUT.md`). |
| `platform_sable/docs/DISPLAY_AND_ATTENTION_PROFILE_MODEL.md` | 720x720 square display profile. |
| `platform_sable/docs/design/*` | All keyboard-first UX specs (Start, Hub, Settings, Setup, Camera...). Used as-is. |
| `platform_sable/docs/device-capabilities/ZINWA_Q27.md` | Template for `device-profile/CAPABILITIES.md`. Q25 must not inherit Q27 evidence. |
| `sableos-project/device_sable_titan2` | Structure for this repo's README, boundaries and evidence docs. |

### 2.6 External sources this port depends on

| Upstream | Role | Pin |
|---|---|---|
| `LineageOS/android` manifest, `lineage-23.2` | OS base | branch, recorded per build by `repo manifest -r` |
| `LineageOS/android_device_xelex_Q25` | Device tree | `1f4e295b5b972bd5e5ea0ec01f8a87154973b464` |
| `LineageOS/android_kernel_xelex_mt6789` | Kernel | `2a873a3511ee0eeead1442e60145093192a4535d` |
| `LineageOS/android_hardware_mediatek` | MTK HALs, IMS | `68f9be72a32bca66e7c63d69e9739b18f13c8b48` |
| `LineageOS/android_device_mediatek_sepolicy_vndr` | Vendor sepolicy | `b1c50f4903504168e7bd0e32b44c72d139d38d09` |
| `TheMuppets/proprietary_vendor_xelex_Q25` | Proprietary blobs | `b8c2c8f90a211dbe16d14b198743e4ba8ea000e4` |
| `MarathonOS/marathon-zinwa-q25` | Droidian port: GPT layouts, multiboot notes | reference only |
| duc1607 Notion "Q25 Tutorials" | Community guides | reference only (page needs JavaScript; not machine-read for this roadmap) |

---

## 3. What has to be created specifically for the Q25

1. **Local manifest** that adds the device tree, kernel, MTK hardware, sepolicy and
   blobs at pinned commits ([`manifests/local_manifests/sable_q25.xml`](manifests/local_manifests/sable_q25.xml)).
2. **Sable product layer for a LineageOS base** ([`product/q25`](product/q25)):
   `vendor/extra/product.mk` hook, app imports, `ro.sable.profile.id=zinwa-q25`,
   branding props, LineageOS Updater pointed away from LineageOS OTAs.
3. **Q25 device profile** ([`device-profile/`](device-profile)): keyboard (scan
   codes from `Q25_keyboard.kl`, Alt layer from `Q25_keyboard.kcm`, Call/End,
   trackpad as DPAD/pointer), square 720x720 display, cameras, radio.
4. **Q25 entries in common apps**: `zinwa-q25` in Keyboard `KeyLayout`, Camera
   `CameraDeviceProfile`, and DisplayCompat defaults.
5. **Framework patch retarget** from GrapheneOS Android 17 to LineageOS 23.2
   (branding, Trebuchet host for Sable Start, Settings, SystemUI, SetupWizard).
6. **Q25 install and recovery docs**: unlock, flash, sideload, return to stock.
   Based on the LineageOS Q25 install flow; marked untested until a device run.
7. **Q25 qualification gates** ([`docs/QUALIFICATION.md`](docs/QUALIFICATION.md)):
   boot, keyboard, trackpad, telephony/IMS, camera, Wi-Fi/BT/NFC, FM, sensors.
8. **Signing and AVB** for a non-Pixel MediaTek device (own release keys, removing
   the GSI developer keys the Lineage tree installs, relock feasibility).

---

## 4. Phases

Each phase has an exit condition. A phase is not done because its scripts exist;
it's done when its exit condition has evidence.

| Phase | Goal | Exit condition |
|---|---|---|
| **Q0 Foundation** (this PR) | Repo layout, roadmap, build scripts, product layer, docs | Static checks pass (`tests/run.sh`). |
| **Q1 Control build** | Build unmodified `lineage_Q25` with our scripts | `bash build/sable.sh q25 Q1 build` produces a LineageOS zip whose boot on a Q25 is recorded. This is the boot-qualified baseline, so later failures can be bisected (the lesson Titan 2 learned the hard way). |
| **Q2 Sable product layer** | Add the Sable app set and props via `vendor/extra` | Image boots; every Sable app launches; `ro.sable.profile.id=zinwa-q25`; no LineageOS OTA offered. |
| **Q3 Q25 device profile** | Keyboard, trackpad, display, camera, radio profiles | Keyboard-first gates in `docs/QUALIFICATION.md` pass on device: critical text entry, Alt layer, Call/End, trackpad navigation, camera shutter, calls/SMS/VoLTE. |
| **Q4 Framework integration** | Retarget GrapheneOS patches to Lineage 23.2 | Sable Start hosted as HOME, Sable branding in Settings/Setup, SystemUI convergence on the 720x720 display. |
| **Q5 Security and release** | Release keys, own AVB key, OTA channel, relock study | Signed build; GSI developer keys removed; documented relock result (pass or fail); OTA from our own server. |
| **Q6 Base evolution** | Android 17 (`lineage-24.0`, already branched for Q25) and/or AOSP/GrapheneOS-derived base | Same gates pass on the new base. |

### Lane G (fallback only): Treble GSI

If a full device build becomes blocked (for example blobs stop matching new stock
firmware), a Sable GSI can be flashed to `system` over stock vendor, using the
Titan 2 GSI pattern. Q25's `fstab` already trusts GSI developer AVB keys, which
makes this possible. It is not the primary path because it inherits every Titan
2 GSI problem.

---

## 5. Risks and open questions

* **Licensing.** The owner approved publishing the Sable app sources here
  (`sable-src/`), but neither sableos nor this repo has a LICENSE file yet, so
  others have no stated right to reuse them. Third-party inputs keep their own
  licences (`sable-src/third_party/*/licenses`).
* **LineageOS vs SableOS security model.** LineageOS is not GrapheneOS. Q2-Q4
  images are engineering builds and must say so. Hardening is Q5/Q6.
* **Blob drift.** TheMuppets blobs come from the 2026-03-26 stock release. A newer
  stock firmware (modem, `vendor_boot`) may need matching blobs.
* **Bootloader relock.** Unknown whether the Q25's MediaTek bootloader accepts a
  custom AVB key. Until proven, Sable Q25 runs unlocked.
* **IMS/VoLTE** depends on MediaTek IMS patches in the Lineage tree
  (`ims-patches/`); carrier coverage must be tested per region.
* **Two board names** (`q20_v12_factory`, `q20_v1_factory`). Both are accepted by
  `board-info.txt`; we don't know if hardware differs.

---

## 6. Lessons carried over from Titan 2

See [`docs/LESSONS_FROM_TITAN2.md`](docs/LESSONS_FROM_TITAN2.md). Short version:

1. Establish a boot-qualified control image before adding anything (Q1).
2. Retain every image, target-files and pinned manifest you flash; Titan 2 lost
   its control image (N1B) because they weren't kept.
3. Add one layer at a time and keep each layer revertable.
4. Never flash an image whose vendor/firmware basis wasn't recorded.
