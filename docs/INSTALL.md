# Install SableOS on the Zinwa Q25

> **Untested for SableOS.** These steps follow the official LineageOS Q25 install
> flow, which is the same image layout this build produces. No SableOS build has
> been installed on a Q25 yet. Do not follow them unless you can recover the
> device yourself (see [`RETURN_TO_STOCK.md`](RETURN_TO_STOCK.md)).
> Unlocking the bootloader erases all data.

## Before you start

1. Boot stock once and check calls, SMS and (if your carrier has it) VoLTE/VoWiFi
   work. Some carriers provision IMS on first use.
2. Record your stock build (Settings > About) in the
   [`STOCK_BASIS.md`](STOCK_BASIS.md) fields.
3. Remove Google accounts to avoid factory reset protection.
4. Install `adb` and `fastboot`; enable Developer options > USB debugging and OEM
   unlocking.
5. Have the build outputs from `bash build/sable.sh q25 Q2 artifacts` and check
   them: `sha256sum -c SHA256SUMS.txt`.

## 1. Unlock the bootloader

```bash
adb -d reboot bootloader          # or power off, then Volume Up + Power, choose fastboot
fastboot devices
fastboot flashing unlock          # confirm on the device; this wipes data
```

Set the device up again (skip accounts) and re-enable USB debugging.

## 2. Flash boot images

```bash
adb -d reboot bootloader
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
adb -d sideload sableos-q25-Q2-YYYYMMDD-userdebug.zip
```

4. *Reboot system now*. The first boot can take several minutes.

## 4. After first boot

Record the result in [`QUALIFICATION.md`](QUALIFICATION.md) gate Q1/Q2-BOOT,
including `adb shell getprop ro.sable.profile.id` (expect `zinwa-q25`) and
`adb shell getprop ro.build.fingerprint`.

## Updating

Sideload a newer package from recovery the same way, without the factory reset.
Preserved-data updates are not qualified yet.
