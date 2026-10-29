# Install SableOS on the Zinwa Q25

> **Tested for Q1, Q2 (2026-10-16/17), the first Q4 image (2026-10-17) and the
> PR #28 Q4 image (2026-10-22).** These
> steps follow the official LineageOS Q25 install flow, which is the same image
> layout this build produces. Do not follow them unless you can recover the device yourself (see
> [`RETURN_TO_STOCK.md`](RETURN_TO_STOCK.md)).
> Unlocking the bootloader erases all data.
> Built on LineageOS 23.2 (Android 16 QPR2).

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
6. Have the build outputs from `bash build/sable.sh q25 Q4 artifacts` (or Q1/Q2)
   and check them: `sha256sum -c SHA256SUMS.txt`. They are in
   `$SABLE_BUILD_ROOT/artifacts/q25/<release>/<stamp>/` (`~/sable-q25` unless you
   set `SABLE_BUILD_ROOT`). `artifacts` refuses to copy a build whose last
   `build` step failed.

## Pick the Q25 first

If another phone is plugged into the same computer, every `adb` and `fastboot`
command must name the Q25. Set its serial once per shell; both tools read it:

```bash
adb devices                       # note the Q25's serial
export ANDROID_SERIAL=<q25-serial>  # e.g. 36022310232425 on the test phone
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
adb sideload sableos-q25-Q4-*-userdebug.zip     # or the Q1/Q2 package you built
```

4. *Reboot system now*. The first boot can take several minutes. A Q4 image
   opens the Sable setup wizard, then Sable Start as HOME; Sable Keyboard is the
   only keyboard, and microG and Aurora Store are installed (see
   [`MICROG.md`](MICROG.md) to sign in or skip).

## 4. After first boot

Record the result in [`QUALIFICATION.md`](QUALIFICATION.md) (Q1/Q2-BOOT, or the
Q4-* gates for a Q4 image), including `adb shell getprop ro.sable.profile.id`
(expect `zinwa-q25`), `adb shell getprop ro.sable.release` and
`adb shell getprop ro.build.fingerprint`. For Q4 also run
`adb shell aflags list | grep scene_container` (expect disabled, or no line
because read-only flags may not be listed; gate Q4-LOCK-SCENE-CONTAINER) and the microG checks in gate Q4-MICROG.

## Updating

Sideload a newer package from recovery the same way, without the factory reset.
Preserved-data updates are not qualified yet.

Moving to the 2026-10-22 Q4 image from Q1, Q2 or an earlier Q4 image, do the
*Format data* step as well: Q4 changes the keyboard, HOME and adds microG, and
Sable Calculator is now signed by the platform build instead of the debug key,
so its old data would not match.
