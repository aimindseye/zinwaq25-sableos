# Flash a SableOS release on the Zinwa Q25

These steps are for a release zip downloaded from
[GitHub Releases](https://github.com/aimindseye/zinwaq25-sableos/releases). They
follow [`INSTALL.md`](INSTALL.md), which covers building your own image.

> **Alpha builds are for testers who can recover the phone themselves.**
> Unlocking the bootloader erases everything on the phone. Before you start,
> take the stock backup and learn the way back in
> [`RETURN_TO_STOCK.md`](https://github.com/aimindseye/zinwaq25-sableos/blob/main/docs/RETURN_TO_STOCK.md).

## What you need

- A Zinwa Q25, charged above 50%, with your data backed up.
- A computer with `adb` and `fastboot` (Android SDK Platform-Tools) and a USB-C cable.
- Google accounts removed from the phone (avoids factory reset protection).
- On the phone: Settings > About phone, tap Build number seven times, then
  Developer options > turn on **OEM unlocking** and **USB debugging**.

## 1. Unpack and check the download

```bash
unzip sableos-q25-*.zip
cd sableos-q25-*/
sha256sum -c SHA256SUMS.txt        # every line must say OK
```

Stop if any line fails, and download again.

## 2. Pick the Q25

If another Android phone is connected, name the Q25 in every command. Set its
serial once in this shell; `adb` and `fastboot` both read it:

```bash
adb devices                        # note the Q25's serial
export ANDROID_SERIAL=<q25-serial>
```

<!-- short:start -->
1. Unpack the zip and run `sha256sum -c SHA256SUMS.txt`.
2. Unlock the bootloader (this erases the phone): `adb reboot bootloader`, then
   `fastboot flashing unlock` and confirm on the phone.
3. Flash the boot images from fastboot: `boot`, `dtbo`, `vbmeta` and
   `vendor_boot` (`fastboot flash boot boot.img`, and so on), then
   `fastboot reboot recovery`.
4. In recovery: *Factory reset* > *Format data / factory reset*, then
   *Apply update* > *Apply from ADB* and `adb sideload sableos-q25-*.zip`.
5. *Reboot system now*. The first boot takes several minutes.

Full steps, checks and the way back to stock are in `FLASHING.md` in the zip.
<!-- short:end -->

## 3. Unlock the bootloader (first install only)

```bash
adb reboot bootloader
sleep 10
fastboot devices                   # the Q25 must be listed
fastboot flashing unlock           # confirm with the volume keys and power; this wipes the phone
```

The phone restarts into stock Android. Skip the setup screens, then turn on USB
debugging again as above.

## 4. Flash the boot images

```bash
adb reboot bootloader
sleep 10
fastboot flash boot boot.img
fastboot flash dtbo dtbo.img
fastboot flash vbmeta vbmeta.img
fastboot flash vendor_boot vendor_boot.img   # holds recovery on this phone
fastboot reboot recovery
```

The phone should show LineageOS recovery. If it shows anything else, stop and
restore stock.

## 5. Install SableOS

1. In recovery choose *Factory reset* > *Format data / factory reset* and confirm.
2. Go back and choose *Apply update* > *Apply from ADB*.
3. On the computer, from the unpacked folder:

   ```bash
   adb sideload sableos-q25-*.zip
   ```

   The computer may stop at 47% or say "Total xfer: 1.00x"; both are normal. Wait
   for recovery to say the install finished.
4. Choose *Reboot system now*. The first boot takes several minutes.

The phone opens Sable Setup, then Sable Start. Sable Keyboard is the keyboard,
and microG with Aurora Store replaces Google Play
([`MICROG.md`](https://github.com/aimindseye/zinwaq25-sableos/blob/main/docs/MICROG.md)).

## Updating from an earlier SableOS release

Unless the release notes ask for a clean install, skip the bootloader unlock and
*Format data*: flash the boot images (step 4), then sideload the new package from
recovery (step 5 without the factory reset). Updates that keep your data are not
qualified yet, so back up first.

## Reporting a problem

Open an issue at
<https://github.com/aimindseye/zinwaq25-sableos/issues> with the release tag,
what you did and what happened. For a crash or boot loop, attach the output of
`bash scripts/capture-crash-evidence.sh` from a checkout of the repository
([`CRASH_EVIDENCE.md`](https://github.com/aimindseye/zinwaq25-sableos/blob/main/docs/CRASH_EVIDENCE.md)).
