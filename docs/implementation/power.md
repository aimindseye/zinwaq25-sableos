# Power: battery drain audit, charge light and learnings (Q25)

```text
PACKAGE=power
CHANGES=Doze location prefetch off (SableFrameworkOverlay sable_power.xml);
        MediaTek userdebug pr_debug off (product/q25/init/sable-power.rc);
        bbqX0kbd per-event logging off (kernel 0202);
        charge light amber blink / green at 100 % (lineage-sdk 0504, LineageParts 0504);
        real battery health (kernel 0201, Settings 1301)
SENSORS_DISABLED=NONE by default (see "Sensors")
BUILD_TESTED=NO   DEVICE_TESTED=NO   (Q4-POWER-IDLE, Q4-LED-BB, Q4-BATTERY-HEALTH-Q25)
```

Everything below was read from source: the device tree `device/xelex/Q25`
(1f4e295), the vendor blobs `vendor/xelex/Q25` (b8c2c8f), the kernel at
`2a873a35`, frameworks/base `f4ed08a`, vendor/lineage `lineage-23.2` (ca37c45)
and hardware/lineage/interfaces (805d253). None of it has been measured on a
phone yet; each row says how to measure it.

## What changed

| Change | Evidence | Why it is safe |
|---|---|---|
| `config_autoPowerModePrefetchLocation=false` (`product/common/overlay/SableFrameworkOverlay/res/values/sable_power.xml`) | frameworks/base `core/res/res/values/config.xml:282` ships true; `DeviceIdleController.java:4000-4019` then asks the fused provider and GPS (1 s, 5 m) for a fix for up to `locating_to` (30 s, `:1128`) each time the phone enters deep Doze. The upstream `remove_idle_location` flag (`apex/jobscheduler/service/aconfig/device_idle.aconfig:5`) drops this but its value in this release is not known | The fix is only a prefetch for apps. Doze still uses the motion sensor (`config_autoPowerModeUseMotionSensor` stays true), so idle entry and exit are unchanged |
| `write /proc/dynamic_debug/control "file *mediatek* -p"` at `sys.boot_completed=1` (`product/q25/init/sable-power.rc`, installed to `/product/etc/init`) | vendor `etc/init/mtklog.rc:3-4` turns on every pr_debug in `*mediatek*` files on userdebug builds; SableOS builds userdebug by default (`docs/BUILD.md:68`). init parses `/product` after `/vendor` and runs actions for one property change in parse order, so this runs after mtklog.rc. init may write `proc_dynamic_debug_control` (sepolicy_vndr) | Debug-only logging. With GKI's `CONFIG_DYNAMIC_DEBUG_CORE=y`, only modules built with `DYNAMIC_DEBUG_MODULE` have entries, so it may match nothing; it is a guard |
| `BBQX0KBD_KEYBOARD_LOG` → `pr_debug` (kernel `0202`) | `drivers/input/keyboard/bbqX0kbd/debug_levels.h:31` sets `DEBUG_LEVEL` to FE\|RW\|LD and `:36` maps the trace macro to `pr_err`, so `bbqX0kbd_main.c` prints several error-level lines for every interrupt (`:615`, `:626`), key (`:355`, `:387`, `:506`, `:538`) and trackpad move (`:417`, `:552`, `:563-584`) | Trace lines only; `BBQX0KBD_KEYBOARD_ERR` still prints real errors. Costs CPU while typing and using the trackpad (console, logd's kernel reader) and floods `dmesg` |

## Checked and left alone

| Item | Evidence | Verdict |
|---|---|---|
| Doze | vendor/lineage `overlay/common/frameworks/base/core/res/res/values/config.xml:63` `config_enableAutoPowerModes=true`; device `FrameworksResOverlay/res/values/config.xml:130` `config_dozeAfterScreenOffByDefault=true`, `:19` `config_suspendWhenScreenOffDueToProximity=true` | Good as shipped |
| Wi-Fi and Bluetooth scanning with Wi-Fi/BT off | frameworks/base SettingsProvider `defaults.xml:164` `def_wifi_scan_always_available=0`; there is no BLE scan-always default, so `ble_scan_always_enabled` starts at 0 | Off by default; the user can turn it on in Location services |
| Assisted GPS | vendor/lineage SettingsProvider overlay `defaults.xml:31` `assisted_gps_enabled=false` | Off |
| Location mode | SettingsProvider `defaults.xml:45` `def_location_mode=3` (on); no network location provider unless microG is installed | Left to the user and the microG layer |
| GNSS daemons | device `rootdir/etc/init.connectivity.rc` `mnld` (class main); vendor `mtk_agpsd_p.rc:4-5` (class main); `lbs_hidl_service` (class hal) | Idle until a fix is requested |
| `lbs_dbg` "GPS Debug Process" | device `init.connectivity.rc:83` starts `/system/bin/lbs_dbg` | Not in `proprietary-files.txt`, so the binary does not exist and init never starts it |
| Connsys logging | device `vendor.prop:65-66` `ro.vendor.connsys.dedicated.log=1`, `:71` `ro.vendor.wlan.standalone.log=y` | No MediaTek logger (connsyslogger, mobile_log_d) ships, so nothing reads it. Measure before touching |
| Sensors HAL | vendor `etc/sensors/hals.conf:1` loads `android.hardware.sensors@2.0-subhal-impl-1.0`, the hardware/lineage pass-through to `sensors.mt6789.so` (`SensorsSubHal.cpp:183` batch, `:306` wake lock only for wake-up sensors) | No always-on sensor of its own; sensors run only while an app holds a listener |
| Fuel gauge daemon | vendor `fuelgauged_init.rc:1-2` class core | Needed for the learned battery health (kernel 0201) |

## Sensors

No sensor is disabled by default. The Q25 declares accelerometer, compass,
gyroscope, light, proximity, step counter and step detector (`device.mk:184-190`),
and turning one off would break apps (compass, rotation, the proximity call
screen) without saving anything while nobody listens.

The known drain is an app holding a sensor in the background. q25toolbox's
**GMS Sensor Guard** (v4.4.1, found by IvanTheTolerable on Discord) found that the Google Play
services location library keeps the accelerometer at 50 Hz and the light sensor
in bursts. Revoking `ACTIVITY_RECOGNITION` for the GMS **UID** stops it while
location fixes keep working. On LineageOS the package form of `cmd appops`
is accepted but has no effect, and the mode does not survive a reboot.
SableOS ships microG, not Google Play services, so this is not applied by
default; for a user who sees it:

```sh
uid=$(adb shell cmd package list packages -U com.google.android.gms | sed 's/.*uid://')
adb shell cmd appops set --uid "$uid" ACTIVITY_RECOGNITION ignore
adb shell am force-stop com.google.android.gms   # drop listeners already registered
# redo after each reboot
```

Play Store (or Aurora) asking for location in the background shows up the same
way in `dumpsys location`; `adb shell cmd appops set com.android.vending
COARSE_LOCATION ignore` stops it; whether every store feature still works
without location has not been checked, so this is not a default either.

## Measuring

```sh
adb shell dumpsys batterystats --reset            # then unplug, screen off, wait 1-8 h
adb shell dumpsys batterystats --charged | grep -A30 'Estimated power use'
adb shell dumpsys batterystats | grep -E 'Wake lock|Kernel Wake lock|Wakeup reason' | head -40
adb shell dumpsys sensorservice | sed -n '/Active sensors/,/Socket Buffer/p'   # who holds which sensor
adb shell dumpsys alarm | grep -E 'RTC_WAKEUP|ELAPSED_WAKEUP' | head -40        # wakeup alarms per app
adb shell dumpsys location | grep -A5 -E 'gps provider|fused provider'          # live location requests
adb shell dumpsys deviceidle | grep -E 'mState|mLocating|mHasGps'                # mLocating stays false
adb shell grep -c '=p' /proc/dynamic_debug/control                               # 0 after boot
adb shell dmesg | grep -c BBQX0KBD                                               # stays low while typing
```

Compare a night's screen-off drain (%/h) and the `Kernel Wake lock` list with and
without the change.

## Learnings from community projects

Facts and ideas only; no code was copied.

**[q25toolbox](https://github.com/nozerorma/q25toolbox)** by nozerorma (no licence file, so facts only):

* GMS Sensor Guard (above).
* beta12: the battery cycle count is stuck at 1. The kernel hard-coded it
  (`mtk_battery.c`); kernel 0201 now reports the gauge's learned count.
* beta11: Android's battery usage graph never fills on the Q25 because the
  charger never reports FULL. lineage-sdk 0504 treats level 100 while plugged in
  as full, so the green light does not depend on FULL.
* The toolbox's "health %" is always about 100 % because both numbers come from
  the static Q_MAX. Kernel 0201 reports full capacity as design × aging factor.
* The LED "no timer trigger" fixes in its history are for the Key2 (Qualcomm).
  The Q25 kernel has `CONFIG_LEDS_TRIGGER_TIMER=y`.

**[Q25EdgeTrackpad](https://github.com/patproport/Q25EdgeTrackpad)** by patproport, a derivative of
Shroyer Tech's Q25 Trackpad Customizer (GPL-3.0, so ideas only):

* The trackpad mode is the `bbqX0kbd` driver's `/proc/q20_switch_key_mouse`
  (kernel `bbqX0kbd_main.c:56-75`, `:1117`): `0` = mouse (REL_X/REL_Y, click is
  BTN_LEFT), `1` = keyboard (arrow keys after 26 counts, `config.h:23`; click
  is ENTER). The node is mode 0777, so SELinux is the only thing keeping apps from writing it.
* Edge scrolling: a pointer held at a screen edge (about 3 px) turns into
  scroll events, so mouse mode can still scroll lists.
* Bottom Touch Guard: while the trackpad is in use, the bottom of the screen
  ignores touches for a moment, because the thumb rests on the screen edge
  next to the trackpad.
* Per-app mode and keyboard mode while a text field is focused.

These are candidates for a SableOS trackpad setting; see the trackpad row in
[Keyboard and input](../KEYBOARD_AND_INPUT.md).
