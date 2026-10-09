# Qualification gates

A phase closes only when its gates have evidence (a log, screenshot or command
output kept with the artifact directory). Status values follow SableOS:
`PASS`, `FAIL`, `BLOCKED`, `NOT_RUN`.

Support level today: **RESEARCH** (SableOS `DEVICE_SUPPORT_LEVELS.md`).
Promotion to **PORTABILITY** needs Q2 + Q3 gates passing.

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
| Q4-HOME | Sable Start presentation hosted in Trebuchet/Launcher3; user can pick another launcher | NOT_RUN |
| Q4-BRANDING | Sable branding in Settings > About and Setup | NOT_RUN |
| Q4-SYSTEMUI | Quick settings and notifications usable at 720x720 with keyboard | NOT_RUN |

## Q5: security and release

| Gate | Check | Status |
|---|---|---|
| Q5-KEYS | Release-key signed build; test keys and GSI developer keys removed | NOT_RUN |
| Q5-AVB | Custom AVB key accepted with relocked bootloader (or documented as impossible) | NOT_RUN |
| Q5-OTA | Updater points to a Sable OTA server and installs an incremental update | NOT_RUN |
| Q5-RESTORE | Return-to-stock procedure tested end to end | NOT_RUN |
