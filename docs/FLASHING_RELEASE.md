# Flash a SableOS release on the Zinwa Q25

These steps are for a release zip downloaded from
[GitHub Releases](https://github.com/aimindseye/zinwaq25-sableos/releases). They
follow [`INSTALL.md`](INSTALL.md), which covers building your own image.

> [!WARNING]
> **This is alpha software. You flash it entirely at your own risk.**
>
> SableOS is an unofficial community build, not made or supported by Zinwa. It
> is provided "as is", without warranty of any kind (Apache License 2.0,
> sections 7 and 8). The owner of this repository and its contributors are not
> responsible for anything that happens to your phone or your data: a phone
> that no longer starts, lost data, lost IMEI or radio calibration, carrier,
> banking or warranty problems, or any other damage.
>
> Unlocking the bootloader erases everything on the phone. Do not continue
> unless you have made the backups in step 1 and can follow the restore steps
> at the end of this guide yourself.

## In short

<!-- short:start -->
1. Back up first: your files, and the phone's partitions with mtkclient
   (FLASHING.md step 1). Keep the stock firmware package for your phone.
2. Unpack the zip and run `sha256sum -c SHA256SUMS.txt`.
3. Unlock the bootloader (this erases the phone): `adb reboot bootloader`, then
   `fastboot flashing unlock` and confirm on the phone.
4. Flash the boot images from fastboot: `boot`, `dtbo`, `vbmeta` and
   `vendor_boot` (`fastboot flash boot boot.img`, and so on), then
   `fastboot reboot recovery`.
5. In recovery: *Factory reset* > *Format data / factory reset*, then
   *Apply update* > *Apply from ADB* and `adb sideload sableos-q25-*.zip`.
6. *Reboot system now*. The first boot takes several minutes.

Full steps, the backup commands and the way back to stock are in `FLASHING.md`
in the zip.
<!-- short:end -->

## What you need

- A Zinwa Q25, charged above 50%, with your data backed up.
- A computer with `adb` and `fastboot` (Android SDK Platform-Tools) and a USB-C cable.
- Google accounts removed from the phone (avoids factory reset protection).
- On the phone: Settings > About phone, tap Build number seven times, then
  Developer options > turn on **OEM unlocking** and **USB debugging**.

## 1. Back up the phone first

Do this while the phone still runs stock Android, before you unlock anything.

**Your own files.** Unlocking erases photos, messages, app data and accounts.
Copy what you want to keep to a computer or a cloud service first.

**The phone's partitions.** This saves everything on the phone except your
data, including the partitions that can never be downloaded again (`nvram`,
`nvdata`, `proinfo`, `persist`, `protect1`, `protect2`: your IMEI, radio
calibration and keys). It uses [mtkclient](https://github.com/bkerler/mtkclient)
on a Linux computer, only reads, and does not need the bootloader unlocked.

```bash
python3 -m pip install --user mtkclient     # or follow the mtkclient README
mkdir -p ~/q25-backup && cd ~/q25-backup
mtk printgpt | tee printgpt.txt              # when it waits: power the phone off, plug in USB
mtk gpt gpt                                  # both partition tables
mtk rl partitions --skip=userdata            # every partition except your data
sha256sum printgpt.txt gpt/* partitions/* > SHA256SUMS.txt
```

If mtkclient does not see the phone, unplug it, hold Volume Up and Volume Down,
and plug it in again. Expect 15 to 20 GB. Keep two copies off the phone and
never share them: `nvram`, `nvdata` and `proinfo` hold your IMEI and serial.
From a checkout of this repository, `bash scripts/backup-device.sh --out
~/q25-backup` does the same.

**The stock firmware.** Download the full stock package for your phone (not an
OTA) and keep it with the backup; the restore steps below use it. Where to find
it and how to check it is in
[`RETURN_TO_STOCK.md`](https://github.com/aimindseye/zinwaq25-sableos/blob/main/docs/RETURN_TO_STOCK.md#stock-images).
Note your build (Settings > About phone > Build number) and board
(`fastboot getvar product`, once in fastboot).

## 2. Unpack and check the download

```bash
unzip sableos-q25-*.zip
cd sableos-q25-*/
sha256sum -c SHA256SUMS.txt        # every line must say OK
```

Stop if any line fails, and download again.

## 3. Pick the Q25

If another Android phone is connected, name the Q25 in every command. Set its
serial once in this shell; `adb` and `fastboot` both read it:

```bash
adb devices                        # note the Q25's serial
export ANDROID_SERIAL=<q25-serial>
```

## 4. Unlock the bootloader (first install only)

```bash
adb reboot bootloader
sleep 10
fastboot devices                   # the Q25 must be listed
fastboot flashing unlock           # confirm with the volume keys and power; this wipes the phone
```

The phone restarts into stock Android. Skip the setup screens, then turn on USB
debugging again as above.

## 5. Flash the boot images

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

## 6. Install SableOS

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
*Format data*: flash the boot images (step 5), then sideload the new package from
recovery (step 6 without the factory reset). Updates that keep your data are not
qualified yet, so back up first.

## Going back to stock

Use the first way that works. All of them erase SableOS and your data.

### A. Fastboot, with the stock package

Use this when the phone still reaches fastboot (power off, then hold Volume Up
and Power and choose fastboot). Unzip the stock package, then from its folder:

```bash
fastboot devices                                  # the Q25 must be listed
for p in boot dtbo vendor_boot vbmeta vbmeta_system vbmeta_vendor; do
    fastboot flash ${p}_a $p.img
    fastboot flash ${p}_b $p.img
done
fastboot flash super super.img
fastboot -w                                       # erase data so stock starts clean
fastboot reboot
```

Name both slots as shown: the Q25's bootloader rejects `--slot=all` for
`vbmeta_system` and `vbmeta_vendor`. If the package has no `super.img`, use
`restore-stock.sh` from a checkout of this repository, which flashes the
separate system images from fastbootd:
`bash scripts/restore-stock.sh --images <stock folder> --serial <q25-serial>`
prints the plan, and adding `--execute` runs it.

The bootloader stays unlocked. Lock it (`fastboot flashing lock`) only once the
phone runs fully stock, or it may not start.

### B. Your own mtkclient backup

Use this when fastboot works but A does not help, or the phone lost its IMEI
or signal. Copy only the partitions SableOS changes into a new folder and write
them back (phone off, then plug in USB):

```bash
cd ~/q25-backup
sha256sum -c SHA256SUMS.txt
mkdir -p restore
for p in boot dtbo vendor_boot vbmeta vbmeta_system vbmeta_vendor; do
    cp partitions/${p}_a.bin partitions/${p}_b.bin restore/
done
cp partitions/super.bin restore/
mtk wl restore                                    # writes every image in the folder
```

For lost IMEI or signal, write back `nvram`, `nvdata` and `proinfo` the same way
from your backup. Then start stock recovery (Volume Up and Power, choose
recovery) and choose *Wipe data / factory reset*.

### C. The phone does not reach fastboot

Use Zinwa's own SP Flash Tool method with the stock package on a Windows PC, as
described in
[`RETURN_TO_STOCK.md`](https://github.com/aimindseye/zinwaq25-sableos/blob/main/docs/RETURN_TO_STOCK.md#zinwas-official-flash-procedure-rung-3).
Pick the package made for your board: a package for another board can leave the
phone unable to start.

## Reporting a problem

Open an issue at
<https://github.com/aimindseye/zinwaq25-sableos/issues> with the release tag,
what you did and what happened. For a crash or boot loop, attach the output of
`bash scripts/capture-crash-evidence.sh` from a checkout of the repository
([`CRASH_EVIDENCE.md`](https://github.com/aimindseye/zinwaq25-sableos/blob/main/docs/CRASH_EVIDENCE.md)).
