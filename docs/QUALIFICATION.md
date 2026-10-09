# Qualification gates

A phase closes only when its gates have evidence (a log, screenshot or command
output kept with the artifact directory). Status values follow SableOS:
`PASS`, `FAIL`, `BLOCKED`, `NOT_RUN`.

Support level today: **RESEARCH** (SableOS `DEVICE_SUPPORT_LEVELS.md`).
Promotion to **PORTABILITY** needs Q2 + Q3 gates passing.

## R0: way back to stock (before any flash)

Nothing other than the stock archive is flashed on a phone until all three pass
on that phone. Procedure: [`RETURN_TO_STOCK.md`](RETURN_TO_STOCK.md).

| Gate | Check | Status |
|---|---|---|
| R0-BACKUP | `scripts/backup-device.sh` prints `BACKUP=PASS`; backup copied to two places off the machine; `nvram`, `nvdata`, `proinfo`, `persist`, `protect1/2` present | NOT_RUN |
| R0-STOCK | Stock OS archive hashed and recorded in `STOCK_BASIS.md`; `restore-stock.sh` plan lists every required image | NOT_RUN |
| R0-RESTORE | On the unlocked stock phone, `restore-stock.sh --execute` flashes the stock archive and stock Android boots with calls and data | NOT_RUN |

## Q1: LineageOS control build

| Gate | Check | Status |
|---|---|---|
| Q1-BUILD | `build/sable.sh q25 Q1 build` succeeds, artifacts hashed | NOT_RUN |
| Q1-BOOT | Control image boots to setup on a Q25 | NOT_RUN |
| Q1-BASIC | Wi-Fi, calls, SMS, keyboard typing work | NOT_RUN |

## Q2: Sable product layer

| Gate | Check | Status |
|---|---|---|
| Q2-BUILD | `apps`, `stage`, `build` succeed with the enabled app set | NOT_RUN |
| Q2-BOOT | Boots; no boot loop; `sys.boot_completed=1` within 10 min | NOT_RUN |
| Q2-PROPS | `ro.sable.profile.id=zinwa-q25`, `ro.sable.release=Q2` | NOT_RUN |
| Q2-APPS | Each Sable app in `apps-manifest.tsv` installs and launches | NOT_RUN |
| Q2-UPDATER | LineageOS Updater offers no LineageOS build | NOT_RUN |

## Q3: Q25 device profile

| Gate | Check | Status |
|---|---|---|
| Q3-KEYS | Every physical key's scan code captured in `device-profile/KEYMAP.md` | NOT_RUN |
| Q3-ALT | Alt layer types the printed legends in Sable Keyboard | NOT_RUN |
| Q3-TEXT | Critical text entry: lock PIN/password, Wi-Fi password, SIM PIN from hardware keys and soft fallback | NOT_RUN |
| Q3-TRACKPAD | Trackpad moves focus in Settings and Sable apps; click activates | NOT_RUN |
| Q3-DISPLAY | 720x720 layouts: no clipped dialogs in Setup, Settings, Sable apps | NOT_RUN |
| Q3-CAMERA | Rear/front ids, max JPEG size, flash, shutter key recorded in camera profile | NOT_RUN |
| Q3-RADIO | Calls, SMS, mobile data, VoLTE state shown correctly by Radio Diag | NOT_RUN |
| Q3-CONNECTIVITY | Wi-Fi, Bluetooth audio, NFC, GPS, FM, USB OTG | NOT_RUN |
| Q3-SENSORS | Proximity during calls, light, accelerometer, compass | NOT_RUN |
| Q3-POWER | Suspend/resume, overnight idle drain, charging | NOT_RUN |

## Q4: framework integration

| Gate | Check | Status |
|---|---|---|
| Q4-HOME | Sable Start (SableLauncher) is HOME after first boot; Home key and gesture return to it; user can pick another launcher in Settings > Apps > Default apps | NOT_RUN |
| Q4-RECENTS | Recents opens from Sable Start (Launcher3QuickStep fallback Recents); swiping an app away works; no Launcher3 home screen appears | NOT_RUN |
| Q4-IME | Sable Keyboard is the only and default IME on a fresh install (LatinIME overridden); Q3-TEXT re-run passes. **Do not stage Q4 before Q3-TEXT passes** | NOT_RUN |
| Q4-MESSAGES | One Messages entry in Sable Start (AOSP Messaging hidden, still the SMS app); sending and receiving SMS work (sableos #82) | NOT_RUN |
| Q4-BRANDING | About phone shows the SableOS version above the LineageOS version (LineageOS legal pages unchanged); Setup says "Welcome to SableOS" ([KF-B](implementation/kf-b.md)) | NOT_RUN |
| Q4-VISUAL | The three `org.sableos.overlay` overlays are enabled (`adb shell cmd overlay list`); Quick Settings shows two labelled tiles per row; Settings > Display > Accent changes Quick Settings and Sable app accents; the DESIGN-KF-B visual regression set captured at font scale 1.0 and 1.3; lockscreen PIN/password entry, emergency call and notification redaction unchanged | NOT_RUN |
| Q4-SYSTEMUI | Quick settings and notifications usable at 720x720 with keyboard | NOT_RUN |
| Q4-SHADE-KEYS | On a focused notification row: Space expands, R reply, D dismiss, Z snooze, M delivery options, C channel settings, H open in Hub; a focused text field always gets the keys ([KF-A](implementation/kf-a.md)) | NOT_RUN |
| Q4-HUB-PARITY | With Android notification access for Hub turned off, Sable Start hides nothing and Hub shows "Paused"; turning it back on restores the user's choices | NOT_RUN |
| Q4-HUB-PROFILES | Paused work profile shows "Work profile" with no text or reply; locked private space is hidden; Hub on a locked device follows the user's own choice in Settings > Notifications > lock screen (sensitive content shown or hidden; Android's default is kept) | NOT_RUN |
| Q4-HUB-SETTINGS | Settings > Notifications > Sable Attention and the per-app "Sable Hub and Attention" link open Hub; Hub's "Delivery" links open Android's own pages | NOT_RUN |
| Q4-ALLAPPS-PRIVACY | With a work profile: the 11 DESIGN-KF-D capture states; toggling a permission or precise location in Settings updates the row on return; Enter, Space, Fn+Enter, `/` and letters behave as listed; uninstall needs a confirm step ([KF-D](implementation/kf-d.md)) | NOT_RUN |
| Q4-RESPONSIVE | Media, Hub and Calendar navigation doesn't clip at 720x720 and font scale 1.3; the media mini-player shows state and play/pause; the alphabet index jumps from the keyboard; focus returns to the item after Back | NOT_RUN |
| Q4-SETUP-KEYS | Factory-reset phone, no touch: Welcome to home screen with the keyboard only; first key focuses Start; the Keyboard step says Sable Keyboard is ready; Wi-Fi password and screen-lock PIN type from the physical keys; Back works on every step. Repeat with touch only ([T3](implementation/t3.md)) | NOT_RUN |
| Q4-SETUP-FALLBACK | With Sable Keyboard disabled on a test build, the Keyboard step offers to turn it on and Next still completes setup | NOT_RUN |
| Q4-WEATHER-CITIES | Add a city by name and by `Name, lat, lon, Area/Zone`, select, remove, reset; list and selection survive a reboot; switching city never shows the previous city's forecast; the Sable Start weather line follows | NOT_RUN |
| Q4-BATTERY | Settings > Battery shows usage, Battery health, Charging & protection, saver in that order; every Q25 health fact shows Unavailable (no fabricated percentage); LineageOS charging control is hidden; chart inspection, type-ahead (U/H/C/B), visible focus and touch all work; `dumpsys alarm` shows no new Settings alarm ([Battery](implementation/battery.md)) | NOT_RUN |
| Q4-BATTERY-EVIDENCE | Each key in `device-profile/battery/zinwa-q25.conf` moved from UNKNOWN only with the evidence `device-profile/BATTERY.md` asks for | NOT_RUN |
| Q4-TOOLS | Sable Tools opens from the launcher; Radio Diag is not installed separately; on the Q25 a normal user sees no Utilities and no IR or SubScreen entries; reports start with sensitive sections unselected ([KF-C](implementation/kf-c.md)) | NOT_RUN |
| Q4-TOOLS-I5 | Each developer-only tool run on the phone and compared with the capability status screen; proven entries moved to SUPPORTED in `ToolsDeviceProfile` with evidence | NOT_RUN |

Crash rule (sableos #84): after any first-boot or launcher crash, run
`scripts/capture-crash-evidence.sh` before clearing anything and keep its
`SUMMARY.txt` with the gate evidence ([`CRASH_EVIDENCE.md`](CRASH_EVIDENCE.md)).

## Q5: security and release

| Gate | Check | Status |
|---|---|---|
| Q5-KEYS | Release-key signed build; test keys and GSI developer keys removed | NOT_RUN |
| Q5-AVB | Custom AVB key accepted with relocked bootloader (or documented as impossible) | NOT_RUN |
| Q5-OTA | Updater points to a Sable OTA server and installs an incremental update | NOT_RUN |
| Q5-RESTORE | Return-to-stock procedure tested end to end | NOT_RUN |
