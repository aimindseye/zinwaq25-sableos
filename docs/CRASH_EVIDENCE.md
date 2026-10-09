# Crash evidence capture

`scripts/capture-crash-evidence.sh` saves what a crash, an ANR or a bad
first boot left on the phone, before anything clears it (SableOS #84). It
needs only adb with USB debugging authorized; it does not need root and does
not change the phone.

## When to run it

Run it first, before you:

* reboot the phone (the `crash` and `main` log buffers are in RAM);
* run `adb logcat -c`, reinstall or update an app, or clear its data;
* factory reset or reflash.

If the phone is stuck in a boot loop with adb up, run it during the loop.

## Use

```sh
scripts/capture-crash-evidence.sh                       # one device, default folder
scripts/capture-crash-evidence.sh --out /media/usb/evidence --label first-boot
scripts/capture-crash-evidence.sh --serial ABC123       # several devices attached
SABLE_ADB=/opt/platform-tools/adb scripts/capture-crash-evidence.sh
```

The output goes to a new folder `q25-crash-evidence-<UTC stamp>[-label]`
under `--out` (default `$HOME/sable-q25-evidence`). The script refuses an
`--out` inside this repository, so evidence cannot be committed by accident.

## What it captures

| File | Source (all read-only) |
|---|---|
| `logcat-crash.txt` | `adb logcat -d -b crash -v threadtime` |
| `logcat-all.txt` | `adb logcat -d -b main,system,events,crash -v threadtime` |
| `logcat-kernel.txt` | `adb logcat -d -b kernel` (empty without permission) |
| `dropbox/list.txt` | `dumpsys dropbox` |
| `dropbox/<tag>.txt` | `dumpsys dropbox --print <tag>` for app/system crashes, ANRs, native crashes, `SYSTEM_TOMBSTONE`, watchdog, boot, last kmsg, restarts and WTFs |
| `exit-info/all.txt`, `exit-info/<package>.txt` | `dumpsys activity exit-info [package]` |
| `tombstones/ls.txt`, `tombstones/*` | `ls -la /data/tombstones`; each file is pulled when the shell may read it (userdebug/eng builds usually yes, user builds usually no; `SYSTEM_TOMBSTONE` in DropBox covers user builds) |
| `anr/ls.txt`, `anr/*` | the same for `/data/anr` |
| `getprop.txt` | `getprop` |
| `uptime.txt`, `devices.txt` | `uptime; date -u`, `adb devices -l` |
| `packages/versions.txt` | `pm list packages --show-versioncode -U` |
| `packages/sable-apps.tsv` | `dumpsys package` for every package in `product/q25/apps.tsv`, `org.sableos.launcher` and any other `org.sableos.*` package on the phone: installed, versionCode, versionName, lastUpdateTime |
| `INDEX.tsv` | each file with the adb command and its exit status (non-zero is normal for permission-denied reads) |
| `SUMMARY.txt` | fingerprint, `ro.sable.release`, count of `FATAL EXCEPTION`/`Fatal signal`/`ANR in` lines, tombstones pulled, `DEVICE_MODIFIED=NO` |
| `SHA256SUMS.txt` | SHA-256 of every other file |

Logs and DropBox are captured first because they rotate.

## What it never does

No `adb root`, `remount`, `reboot`, `install`/`uninstall`, `push`,
`logcat -c`, `pm clear`, `rm` or settings writes. `tests/run.sh` runs the
script against a fake adb (`SABLE_ADB`) and fails if any such command is
issued, if the hashes do not verify, or if an output path inside the repo or
an ambiguous device choice is accepted.

## Privacy

The folder holds the serial, build fingerprint, package list and app log
lines, which may include personal data. It is created `chmod 700`. Share
`SUMMARY.txt` and the relevant excerpts, not the whole folder, unless the
person debugging needs it.

## Status

Tested here only against the fake adb in `tests/run.sh`. It has not yet run
against a Q25.
