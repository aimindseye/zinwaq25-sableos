# Install SableOS on the Zinwa Q25

> **Tested on a Q25 for every image from Q1 (2026-10-16) to SableOS 1.0
> (2026-10-29).** These
> steps follow the official LineageOS Q25 install flow, which is the same image
> layout this build produces. Do not follow them unless you can recover the device yourself (see
> [`RETURN_TO_STOCK.md`](RETURN_TO_STOCK.md)).
> Unlocking the bootloader erases all data.
> SableOS 2.0 is built on LineageOS 24.0 (Android 17). SableOS 1.0 was built on LineageOS 23.2 (Android 16 QPR2).

## Before you start

0. **Gate R0 must pass on this phone first**: a full backup taken while stock and
   a rehearsed stock restore ([`RETURN_TO_STOCK.md`](RETURN_TO_STOCK.md),
   [`QUALIFICATION.md`](QUALIFICATION.md)). Don't skip it: it's what makes every
   step below undoable.
1. Update the keyboard firmware while still on stock, if Zinwa has a newer one
   ([`KEYBOARD_AND_INPUT.md`](KEYBOARD_AND_INPUT.md#keyboard-firmware)).
   SableOS has no updater for it.
2. Boot stock once and check calls, SMS and (if your carrier has it) VoLTE/VoWiFi
   work. Some carriers provision IMS on first use.
3. Record your stock build (Settings > About) in the
   [`STOCK_BASIS.md`](STOCK_BASIS.md) fields.
4. Remove Google accounts to avoid factory reset protection.
5. Install `adb` and `fastboot`; enable Developer options > USB debugging and OEM
   unlocking.
6. Have the build outputs from `bash build/sable.sh q25 full artifacts` (or Q1/Q2)
   and check them: `sha256sum -c SHA256SUMS.txt`. They are in
   `$SABLE_BUILD_ROOT/artifacts/q25/<release>/<stamp>/` (`~/sable-q25` unless you
   set `SABLE_BUILD_ROOT`). `artifacts` refuses to copy a build whose last
   `build` step failed.

## Pick the Q25 first

If another phone is plugged into the same computer, every `adb` and `fastboot`
command must name the Q25. Set its serial once per shell; both tools read it:

```bash
adb devices                       # note the Q25's serial
export ANDROID_SERIAL=<q25-serial>  # e.g. 3602XXXXXXXXXX (14 digits; the rest of the test phone's serial is hidden)
```

`restore-stock.sh --execute` and `capture-crash-evidence.sh` refuse to run
without it (`--serial`, `SABLE_Q25_SERIAL` or `ANDROID_SERIAL`).
`backup-device.sh` talks to the boot ROM over mtkclient, which can't pick a
device: unplug any other MediaTek phone before running it.

## 1. Unlock the bootloader

```bash
adb reboot bootloader          # or power off, then Volume Up + Power, choose fastboot
fastboot devices
fastboot flashing unlock          # confirm on the device; this wipes data
```

Set the device up again (skip accounts) and re-enable USB debugging.

## 2. Flash boot images

```bash
adb reboot bootloader
fastboot flash boot boot.img
fastboot flash dtbo dtbo.img
fastboot flash vbmeta vbmeta.img
fastboot flash vendor_boot vendor_boot.img   # contains recovery on this device
fastboot reboot recovery
```

Recovery should show the LineageOS recovery from this build. If it shows anything
else, stop.

## 3. Install SableOS

1. In recovery: *Factory reset* > *Format data / factory reset*.
2. *Apply update* > *Apply from ADB*.
3. On the computer:

```bash
adb sideload sableos-q25-full-*.zip     # or the Q1/Q2 package you built
```

The computer may stop at 47% or say "Total xfer: 1.00x" while the phone keeps installing; both are normal, so do not unplug or cancel. Wait for recovery to say the install finished.

The fastboot step only puts the boot images on the phone (kernel, recovery, verified-boot data). The operating system itself (system, system_ext, product, vendor, the apps) is inside the install package, and only recovery can write it, which is why you sideload it as well.

4. *Reboot system now*. The first boot can take several minutes. A full (Q4) image
   opens the Sable setup wizard, then Sable Start as HOME; Sable Keyboard is the
   only keyboard. From SableOS 2.0 the image has no Google software and no app
   store: microG and Aurora Store are not installed. If you need apps that use
   Google Play services, install the microG add-on after setup (section 5).

## 4. After first boot

Record the result in [`QUALIFICATION.md`](QUALIFICATION.md) (Q1/Q2-BOOT, or the
Q4-* gates for a full image), including `adb shell getprop ro.sable.profile.id`
(expect `zinwa-q25`), `adb shell getprop ro.sable.release` and
`adb shell getprop ro.build.fingerprint`. For the full image also run
`adb shell aflags list | grep scene_container` (expect disabled, or no line
because read-only flags may not be listed; gate Q4-LOCK-SCENE-CONTAINER) and, if you install the microG add-on, the checks in gate Q4-MICROG.

## 5. Optional: the microG add-on (Play Store apps)

SableOS 2.0 ships without Google Play services. To run apps that need them, finish
setup, turn on USB debugging (Settings > System > Developer options), connect the
phone and run from this repository:

```bash
NETWORK_FETCH_AUTHORIZED=YES bash scripts/fetch-microg.sh
SABLE_MICROG_PREBUILTS=product/q25/microg/addon.tsv NETWORK_FETCH_AUTHORIZED=YES bash scripts/fetch-microg.sh
bash scripts/install-microg-addon.sh --with-aurora    # GsfProxy is always installed
```

This installs microG Services, microG Companion and Aurora Store as normal apps,
each checked against its pinned sha256. Then open microG Settings > Self-Check.
Every APK, the defaults and how to remove it are in [`MICROG.md`](MICROG.md).

## Updating

Sideload a newer package from recovery the same way, without the factory reset.
Preserved-data updates are not qualified yet.

Moving from SableOS 1.0 (Android 16) to 2.0 (Android 17), back up and do the
*Format data* step: the Android version changes and microG is no longer built in,
so reinstall the add-on afterwards if you used it.

Moving to the 2026-10-22 Q4 image from Q1, Q2 or an earlier Q4 image, do the
*Format data* step as well: Q4 changes the keyboard, HOME and adds microG, and
Sable Calculator is now signed by the platform build instead of the debug key,
so its old data would not match.
