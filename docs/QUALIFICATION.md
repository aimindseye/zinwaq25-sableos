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
| R0-BACKUP | `scripts/backup-device.sh` prints `BACKUP=PASS`; backup copied to two places off the machine; `nvram`, `nvdata`, `proinfo`, `persist`, `protect1/2` present | PASS (2026-10-16, board q20_v12_factory: all 60 non-userdata partitions read, `sha256sum -c` clean; second off-machine copy not yet confirmed) |
| R0-STOCK | Stock OS archive hashed and recorded in `STOCK_BASIS.md`; `restore-stock.sh` plan lists every required image | PASS (2026-10-16, With-GMS `SP1A.210812.016RELEASE-KEYS`, display `Q25_20.01.2026`, matches the phone) |
| R0-RESTORE | On the unlocked stock phone, `restore-stock.sh --execute` flashes the stock archive and stock Android boots with calls and data | PASS (2026-10-16: stock booted after the restore. The script stopped at `vbmeta_system` (bootloader reports it unslotted) and the rest was flashed by hand with `_a`/`_b` names, the fix in PR #22. Calls and SMS were checked on stock before unlocking, not after the restore) |

## Q1: LineageOS control build

| Gate | Check | Status |
|---|---|---|
| Q1-BUILD | `build/sable.sh q25 Q1 build` succeeds, artifacts hashed | PASS (2026-10-15, ai-g732) |
| Q1-BOOT | Control image boots to setup on a Q25 | PASS (2026-10-16, `23.2-20261015-UNOFFICIAL-Q25`) |
| Q1-BASIC | Wi-Fi, calls, SMS, keyboard typing work | PASS (2026-10-16) |

## Q2: Sable product layer

| Gate | Check | Status |
|---|---|---|
| Q2-BUILD | `apps`, `stage`, `build` succeed with the enabled app set | PASS (2026-10-15, ai-g732, with the three games disabled to fit the dynamic partitions) |
| Q2-BOOT | Boots; no boot loop; `sys.boot_completed=1` within 10 min | PASS (2026-10-17) |
| Q2-PROPS | `ro.sable.profile.id=zinwa-q25`, `ro.sable.release=Q2` | PASS (2026-10-17) |
| Q2-APPS | Each Sable app in `apps-manifest.tsv` installs and launches | PASS (2026-10-17: all 13 enabled apps installed and launched via `monkey`) |
| Q2-UPDATER | LineageOS Updater offers no LineageOS build (Q2/Q3 images; from Q4 the Updater is not installed, see Q4-NO-UPDATER) | PASS (2026-10-17: Updater shows "update check failed" and offers no build) |

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
| Q4-RECENTS | Recents opens from Sable Start (Launcher3QuickStep fallback Recents); swiping an app away works; no Launcher3 home screen appears; Settings > Apps lists `com.android.launcher3` as "Recents" (not Trebuchet) with no settings gear in App info, and Settings search finds no home settings (Launcher3 0002) | NOT_RUN |
| Q4-IME | Sable Keyboard is the only and default IME on a fresh install (LatinIME overridden); Q3-TEXT re-run passes. **Do not stage Q4 before Q3-TEXT passes** | NOT_RUN |
| Q4-MESSAGES | One Messages entry in Sable Start, and it opens Sable Hub (AOSP Messaging hidden, still the SMS app); Sable Messages is not installed (`pm list packages org.sableos.messages` empty: it is a demo, disabled in `apps.tsv`); sending and receiving SMS work from Hub (sableos #82) | NOT_RUN |
| Q4-BRANDING | About phone shows the SableOS version above the LineageOS version (LineageOS legal pages unchanged); the owner Setup flow shows no LineageOS name: "Welcome to SableOS", the Restore text names no OS, no LineageOS features step (SetupWizard 0408), and after setup LineageSettings.Secure `stats_collection` is still unset or 0 ([KF-B](implementation/kf-b.md)) | NOT_RUN |
| Q4-NO-UPDATER | `pm list packages org.lineageos.updater` is empty and `getprop lineage.updater.uri` is empty (SableNoUpdater overrides Updater; Sable OTA is Q5) | NOT_RUN |
| Q4-VISUAL | The three KF-B `org.sableos.overlay` overlays (framework, SystemUI, SetupWizard) and `org.sableos.overlay.settingsprovider` are enabled (`adb shell cmd overlay list`); Quick Settings shows two labelled tiles per row; Settings > Display > Accent changes Quick Settings and Sable app accents; App display compatibility > Sable app style is Compact on a fresh install, and switching it to Rounded and back restyles open Sable apps (8dp controls, 12dp cards) without a restart ([corners](implementation/corners.md)); the DESIGN-KF-B visual regression set captured at font scale 1.0 and 1.3; lockscreen PIN/password entry and emergency call unchanged | NOT_RUN |
| Q4-SYSTEMUI | Quick settings and notifications usable at 720x720 with keyboard | NOT_RUN |
| Q4-SHADE-KEYS | On a focused notification row: Space expands, R reply, D dismiss, Z snooze, M delivery options, C channel settings, H open in Hub; a focused text field always gets the keys ([KF-A](implementation/kf-a.md)) | NOT_RUN |
| Q4-HUB-PARITY | With Android notification access for Hub turned off, Sable Start hides nothing and Hub shows "Paused"; turning it back on restores the user's choices | NOT_RUN |
| Q4-HUB-PROFILES | Paused work profile shows "Work profile" with no text or reply; locked private space is hidden; Hub on a locked device follows the user's own choice in Settings > Notifications > lock screen (sensitive content shown or hidden; Sable's default is hidden, see Q4-LOCK-PRIVACY) | NOT_RUN |
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
| Q4-SETTINGS-APPS | Settings home keeps Android's order; Apps shows "N apps · permissions, network access and defaults" and N matches All apps; each All apps row shows the KF-D privacy row and updates on return after a permission or precise-location change; TalkBack reads the spoken form ([settings-ui5](implementation/settings-ui5.md)) | NOT_RUN |
| Q4-NETWORK-MANAGER | Settings > Apps > Network access: turning an app off blocks it on Wi-Fi, mobile data and VPN, and App info > Mobile data & Wi-Fi shows the same state; a messaging app asks first; App info per-network blocks show "(App info)"; type-ahead, Home/End, Page keys and `/` work; Sable Tools' Network Manager link opens it | NOT_RUN |
| Q4-APP-SECURITY | App security & privacy opens from Settings > Apps, App info and Sable Tools; every fact has an evidence tag; nothing on it changes the app; a large app opens without stalling the UI | NOT_RUN |
| Q4-PHONE-CONTACTS | Phone > Contacts and Contacts show one A-Z rail at the right (no second scroll thumb); each contact with a number shows "Type · number" under the name, matching the contact's default number; a letter key jumps to the first contact under it, pressing it again cycles, an unused letter says "No contacts under X" and focus stays; letters never call or open anyone; in Contacts `/` opens search and digits still start a search; letters typed in any search field stay text; Ctrl/Alt letters do nothing new ([phone-contacts](implementation/phone-contacts.md)) | NOT_RUN |
| Q4-PHONE-CONTACTS-OWNER | Adding, editing, linking, starring and deleting a contact (local and one synced account) behaves as on LineageOS; the subtitle updates after the edit; no new database or file appears under the Dialer or Contacts data directories; `dumpsys activity provider com.android.contacts` shows only reads from the two apps; with READ_CONTACTS denied both lists show the upstream permission screen | NOT_RUN |
| Q4-PHONE-CORE | Dialpad from the physical keys and by touch (digits, `*`, `#`, `+` by long-press 0, Backspace), emergency number entry, in-call DTMF and the Call/End keys behave exactly as on the Q1 control build (the patches do not touch them) | NOT_RUN |
| Q4-INCALL-KEYS | With the dialpad hidden during a call, M mutes, S switches the speaker (not with Bluetooth), D opens the dialpad (Dialer 0703); while a call rings and no other call is in progress, Space answers it, also from the lockscreen and with Decline focused, and holding Space after answering presses nothing; with Settings > Sounds and vibration > In-call > Space answers calls off, Space does nothing; Space never ends a call; with call waiting Space does nothing (Dialer 0704) | NOT_RUN |
| Q4-LOCK-PRIVACY | On a fresh install, a message notification on the locked screen shows the app but no sender or text until unlock; Settings > Notifications > lock screen offers showing all content, and choosing it shows content; `settings get secure lock_screen_allow_private_notifications` is 0 after first boot (`SableSettingsProviderOverlay`) | NOT_RUN |
| Q4-DAILY-DRIVER | Sable Start shows Photos (Glimpse), Camera, Files and Clock tiles with the Sable glyphs and identity accents, and the Live Photos row opens Glimpse; Glimpse is labelled "Photos" in All Apps (English) and its thumbnails and album tiles show a 2dp accent focus ring (no grey fill) when moved over with the keyboard and nothing on touch; the Gallery2 editor and crop use the Sable dark chrome and follow Settings > Display > Accent; Clock, Files, Camera and Browser follow the accent and Android's Dark theme as before; nothing clips at 720x720 and font scale 1.3 ([daily-driver](implementation/daily-driver.md)) | NOT_RUN |
| Q4-DAILY-DRIVER-BEHAVIOUR | Glimpse, Gallery2, Clock, Files, Camera and Browser keep their behaviour, permissions and storage: `dumpsys package` shows the same requested permissions as an unpatched build, and opening, sharing, deleting (with confirmation), alarms, timers and capture work as on LineageOS | NOT_RUN |
| Q4-SHADE-ALT | Alt+Down opens the shade and again expands Quick Settings; Alt+Up goes back one level only while the shade is open; Alt+Enter on a notification opens its actions; in Quick Settings Space toggles a tile and Alt+Enter opens its details; nothing fires on the keyguard or before setup (frameworks/base 0302-0303) | NOT_RUN |
| Q4-LOCK-TYPEAHEAD | With the keyboard, the lockscreen shows "Type PIN · Enter unlocks"; typing on a secure lockscreen opens the PIN or password field and fills it; Alt+letter digits work in the PIN field; Enter alone submits; nothing is checked or submitted without Enter (frameworks/base 0304) | NOT_RUN |
| Q4-LOCK-PIN-KEYS | On the PIN bouncer with the keyboard, without touching the screen or trackpad first: W E R S D F Z X C type 1-9 (plain and with Alt) and 0 types 0; the same after moving the trackpad or pressing Tab (focus stays on the PIN field; the on-screen keys never take a focus ring); Shift+W types nothing; Backspace deletes one digit; Enter and the trackpad click submit, never type a digit; a held key types one digit; touch on the on-screen keys still works; the bouncer shows "Enter PIN" with "Type PIN · Enter unlocks" under it; wrong PINs show the usual error and lockout countdown. Also with Settings > System > Keyboard > physical keyboard layout set to English (US) (frameworks/base 0304) | NOT_RUN |
| Q4-LOCK-LOOK | Lock screen at 720x720: the large clock stays centered (owner design; `config_sableKeyguardClockStartAligned` true moves it to the start edge with a 28dp margin), 28dp from the top area; the unlock hint reads "Swipe up to unlock" and sits 36dp from the bottom; "Type PIN · Enter unlocks" fits on one line beside it (frameworks/base 0305, `SableSystemUIOverlay`) | NOT_RUN |
| Q4-HUB-SMS | In an SMS thread Enter sends and Shift+Enter adds a line; drafts survive leaving the thread and a reboot; a new SMS appears without pulling to refresh; Inbox filters A/M/C/N switch All, Messages, Calls, Notifications; Calls need READ_CALL_LOG with Hub as a system app; Sable Start search "#term" opens Hub filtered | NOT_RUN |
| Q4-HUB-DOCK | Hub dock shows unread counts for SMS, missed calls and each included app (mail apps too) and its total matches the inbox; D focuses it, Enter narrows the inbox; adjacent rows from the same person roll up with +N and a double divider, Right/Space expand and Left collapses; Combine conversations off restores the flat list; Mail filter (E) lists Gmail/Outlook/Sable Mail rows from their notifications; history from before the update survives | NOT_RUN |
| Q4-CALENDAR-EDIT | Enter opens an event's detail, E edits, Del asks before deleting (focus on Cancel; repeating events warn); edits of repeating and all-day events change only title, location and notes; read-only calendars refuse edits | NOT_RUN |
| Q4-MEDIA-MAIL-KEYS | Sable Media: Space, J/K, Shift+J/K seek, +/- volume, `/` search, Esc back, and shortcuts off in text fields; Sable Mail: C compose, J/K next and previous, R/A/F reply, D delete after a confirmation, `/` search, Alt+Enter sends in compose | NOT_RUN |
| Q4-STOCK-KEYS | Files: Alt+C/M/R/N/I/O/A, Del after confirmation, Esc up a folder, read-only files refuse rename and delete; Photos: J/K, Space, D after confirmation, S share; Browser: Alt+L/T/F/R and single keys only when no form or the address bar has focus (DocumentsUI 1001-1002, Glimpse 1021-1022, Jelly 1041-1042) | NOT_RUN |
| Q4-MOBILE-MANAGER | Settings > Apps > Mobile Manager opens; Network access matches Network Manager; App Lock says "not enforced"; Focus mode pauses chosen apps and releases only those when it ends; Private Space row opens Android's own setup; Dual Apps says why it is unavailable when the overlay is unset (Settings 1101-1102) | NOT_RUN |
| Q4-DISPLAY-COMPAT | `getprop persist.sable.displaycompat.backend` is `validated` and App display compatibility shows "Enforcement backend: OPEN"; `dumpsys package org.sableos.titan2.displaycompat` shows `userId=1000` and the privileged permissions granted; saving 16:9 letterbox for a test app reports "platform reports aspect 4" and, after the app restarts, it opens in a 405x720 column; Mini mode gives a 480x720 window (`dumpsys activity activities` bounds); Orientation Portrait/Landscape take effect on the next start (`dumpsys platform_compat` lists 265464455 plus 265452344 or 266124927 for the package); Reset to Native clears both; keyboards, Settings and Sable Start stay blocked | NOT_RUN |
| Q4-SECURE-VAULT | With Private Space set up and unlocked, Secure Vault appears only in the Private Space section of All apps (`pm list packages --user <private> org.sableos.titan2.vault` lists it, `--user 0` does not); I imports copies from the picker and offers to delete the originals (Back keeps them); P/D/R switch Photos, Documents, Recordings; E and Enter each ask first (Back cancels and nothing is written or opened); X deletes after Y; L locks Private Space (the vault and private apps disappear until unlocked) and the files are there again after unlock; screenshots of the vault are blocked and Recents shows no preview | NOT_RUN |
| Q4-TOOLS-SECRET-CODE | Dialling `*#*#3377#*#*` opens Sable Tools now that its receiver requires `MODIFY_PHONE_STATE` (sent only by the telephony stack); an app broadcasting the code itself is refused | NOT_RUN |
| Q4-LED-BB | BlackBerry-style notification LED, not charging, Settings > Notifications > Notification light at its defaults: with the screen off, a new message from an app that sets no light (for example an SMS, a Hub-delivered or a test notification at Default importance) gives a short red flash every ~3 s; turning the screen on stops it and unlocking turns it off (it flashes again at the next screen-off until the shade is opened or the notification dismissed); a Low or Silent channel never lights; turning that app channel's "Blink light" on and then off stops it lighting; flashing still runs after more than 1 minute of screen-off (GPIO LED timer in suspend; `adb shell dumpsys notification` lists the key under "Lights List"); with no notification lit: charging shows amber, green from 90 %, off when full; low battery not charging flashes amber; a new notification while charging flashes red over the battery color (`SableLedOverlay`, frameworks/base 0501, lineage-sdk 0502, LineageParts 0503; [KF-A](implementation/kf-a.md#notification-led-q25-blackberry-style)) | NOT_RUN |

Crash rule (sableos #84): after any first-boot or launcher crash, run
`scripts/capture-crash-evidence.sh` before clearing anything and keep its
`SUMMARY.txt` with the gate evidence ([`CRASH_EVIDENCE.md`](CRASH_EVIDENCE.md)).

## Q5: security and release

| Gate | Check | Status |
|---|---|---|
| Q5-KEYS | Release-key signed build; test keys and GSI developer keys removed | NOT_RUN |
| Q5-AVB | Custom AVB key accepted with relocked bootloader (or documented as impossible) | NOT_RUN |
| Q5-OTA | A Sable OTA updater (Q4 ships none; Q2/Q3 keep the LineageOS Updater pointed away) installs an incremental update from a Sable server | NOT_RUN |
| Q5-RESTORE | Return-to-stock procedure tested end to end | NOT_RUN |
