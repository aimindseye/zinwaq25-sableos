# Return to stock

Status: **plan written from LineageOS's install flow and Q25 community reports;
not yet run by this project.** Gate R0 in [`QUALIFICATION.md`](QUALIFICATION.md)
turns it into evidence, and **nobody flashes SableOS until R0 passes.**

## The short version

1. **Before anything else, back up the phone** while it is still stock
   (`scripts/backup-device.sh`). This saves every partition except `userdata`,
   including the ones that can never be re-downloaded (`nvram`, `nvdata`,
   `proinfo`, `persist`, `protect1/2`: IMEI, radio calibration, keys).
2. **Get the stock "OS" archive** that matches your phone (not an OTA zip) and
   record its hash in [`STOCK_BASIS.md`](STOCK_BASIS.md).
3. **Rehearse the restore on stock** (gate R0): flash the stock archive back with
   `scripts/restore-stock.sh` and confirm the phone boots. Now you know the way
   back works before you ever leave.
4. Only then install SableOS ([`INSTALL.md`](INSTALL.md)).

## Why this is low risk on the Q25

Installing SableOS (like LineageOS) only writes `boot`, `dtbo`, `vbmeta`,
`vendor_boot` and the contents of `super` (system, system_ext, product, vendor,
vendor_dlkm, odm_dlkm). It never writes the boot chain (`preloader`, `lk`,
`tee`, ...), the modem, or the calibration partitions. As long as the boot chain
is intact, the phone can always reach **fastboot** (Volume Up + Power, choose
fastboot), and everything SableOS changed can be overwritten from there.

Below fastboot, MediaTek phones have a ROM-level download mode (BROM) that works
even when nothing on the storage boots. Community guides report both
**SP Flash Tool** and **mtkclient** working on the Q25 (sources below), which is
the last line of defence.

## Recovery ladder

Use the first rung that works.

| Rung | When | How | Needs |
|---|---|---|---|
| **1. Reflash what SableOS touched** | SableOS doesn't boot, boot loop, recovery broken | `bash scripts/restore-stock.sh --images DIR` (default scope `sable`: boot, dtbo, vendor_boot, vbmeta*, super, both slots) | fastboot works; stock OS archive |
| **2. Full fastboot restore** | Rung 1 boots but something is still wrong, or slots are mixed | `bash scripts/restore-stock.sh --images DIR --scope full` (every image in the archive except `userdata`, both slots) | fastboot works; stock OS archive |
| **3. SP Flash Tool "Firmware Upgrade"** | Fastboot unreachable; phone dead or stuck in a loop before fastboot | Power off, open SP Flash Tool V6, load the stock firmware package, *Firmware Upgrade*, connect USB. Leave `userdata` unticked for a normal update. Reported to also relock the bootloader | Windows/Linux PC; SP Flash firmware package with scatter |
| **4. mtkclient write-back of your own backup** | Radio/IMEI lost, or rungs 1-3 don't restore the device | `mtk wl <backup-dir>` (writes every image in the folder; remove images you don't want written first) | mtkclient; your rung-0 backup |

After rung 1 or 2 the bootloader is still unlocked; relocking (`fastboot
flashing lock`) only on a fully stock device, because locking with non-stock
images can make it unbootable.

## Step details

### Back up (do this first, on stock)

```bash
pip install mtkclient          # or follow https://github.com/bkerler/mtkclient
bash scripts/backup-device.sh --out ~/q25-backup-$(date +%Y%m%d)
```

The script only reads: it prints the partition table, saves both GPT copies and
reads back every partition except `userdata` with mtkclient, then writes
`SHA256SUMS.txt`. When it says `waiting for device`, power the phone off and
plug in USB (if it isn't detected, hold Volume Up + Volume Down while plugging
in). Expect roughly 15-20 GB without `userdata`.

Keep at least two copies off the phone. **Never** put them in this repository:
`nvram`/`nvdata`/`proinfo` contain your IMEI and serial.

SP Flash Tool's *Readback* (phone off, automatic mode) is an alternative.

### Stock images

* Phone owners' Drive folder ("With-GMS"):
  https://drive.google.com/drive/folders/1RlhjXInYh7t_ITkWS6fqKY4quZAAKmuT
* Community Q25 files folder (from the asmtronic Q25 guide):
  https://drive.google.com/drive/folders/1dQ3V04yze6P7fXzjs7HB1Plg4L-vDGyQ

Neither has been checked by this project. Use the full firmware package, not an
OTA. Before use: unzip it, run `sha256sum` over the files, and record the build
in `STOCK_BASIS.md`. `restore-stock.sh` lists what it found and stops if
something it needs is missing.

#### The "With-GMS" package (file list only, contents not inspected)

The With-GMS folder holds a folder named `SP1A.210812.016RELEASE-KEYS`, which is
an **SP Flash Tool package**. It has `MT6789_Android_scatter.xml` and
`download_agent/`, so the same files work for rung 3. It contains:

* everything rung 1 needs: `boot`, `dtbo`, `vendor_boot`, `vbmeta`,
  `vbmeta_system`, `vbmeta_vendor` and a single `super.img`;
* rung 2 firmware: `lk tee scp sspm dpm mcupm md1img gz spmfw pi_img logo`;
* `preloader_q20_v12_factory.bin`, so the package targets the
  **q20_v12_factory** board. Check `fastboot getvar product` on your phone
  before you use it;
* `userdata.img`, `boot-debug.img` and `vendor_boot-debug.img`, which
  `restore-stock.sh` ignores.

Its exact layout is tested in `tests/run.sh` (fixture
`tests/fixtures/stock-gms-sp1a.files`).

**Check the version before using rung 2 or 3.** `SP1A` is an Android 12 build
tag. The stock build this port's blobs came from reports Android 14
(`STOCK_BASIS.md`). If this package is older than the firmware on your phone,
only use rung 1, which writes just the partitions SableOS changed. Don't use the
full restore or SP Flash with it: writing an older boot chain (`lk`, `tee`,
preloader) over newer firmware can be refused or brick the phone on some
MediaTek devices. Compare `ro.build.fingerprint` in the package's
`mssi_64_cn_armv82/build.prop` and `q20_v12_factory/build.prop` with
Settings > About. `mssi_64_cn` also suggests a China-region system build;
check the region before relying on it.

### Rehearsal (gate R0)

On the stock phone, after the backup and after unlocking:

```bash
bash scripts/restore-stock.sh --images ~/q25-stock/OS-...   # prints the plan only
bash scripts/restore-stock.sh --images ~/q25-stock/OS-... --execute
```

The phone should boot stock Android with calls and data working. Record the
result in `QUALIFICATION.md` (R0-RESTORE).

## Sources

* LineageOS Q25 install guide: https://wiki.lineageos.org/devices/Q25/install/
  (partitions written by an install).
* asmtronic Q25 guide: https://github.com/asmtronic/Zinwa-Q25-Guide (SP Flash
  Tool V6 readback and firmware download on the Q25).
* Droidian Q25 wiki: https://github.com/JamiKettunen/droidian-zinwa-q25/wiki
  (stock OS archive flashed per partition with fastboot, SP Flash "Firmware
  Upgrade" full restore, mtkclient GPT backup, original GPT in
  https://github.com/MarathonOS/marathon-zinwa-q25/tree/main/gpt).
* mtkclient: https://github.com/bkerler/mtkclient
