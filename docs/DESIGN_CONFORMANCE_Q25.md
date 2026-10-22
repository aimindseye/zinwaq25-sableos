# Q25 design conformance review

This review compares the 24 design artifacts in `sableos-project/platform_sable/docs/design/artifacts` with this port. Status as of 2026-10-22, after PR #25 and PR #28 were merged. Everything below is **in source only**: the PR #28 build has not been flashed yet, and the matching on-device checks are the `NOT_RUN` Q4-* rows in [`QUALIFICATION.md`](QUALIFICATION.md).

**Short answer:** 20 of the 24 artifacts are now done in code. Files and Media are partial, Utilities is still missing, and the rear-screen artifact doesn't apply to the Q25. PR #26 records the owner's earlier BB10 Start attempt in [`DESIGN_CONFORMANCE_TB2_BB10_IMPLEMENTATION.md`](DESIGN_CONFORMANCE_TB2_BB10_IMPLEMENTATION.md); PR #28 replaced that Start code, so this file is the current status.

Status words: **Done in code** (built and unit-tested, not yet seen on a device), **Partial**, **Missing**, **N/A** (Q25 hardware has no such thing).

## Booted on a Q25 (Q4, 2026-10-17, fixed in PR #25)

| Area | What changed |
|---|---|
| Lock screen PIN | Typing works wherever focus is. W E R S D F Z X C type 1 to 9, 0 types 0, Enter unlocks. |
| LineageOS leftovers | No LineageOS features step or wording in setup; Launcher3 settings and search provider hidden; LineageOS Updater replaced. |
| Settings > Sable | Holds Sable Setup only. |
| Keyboard light | Settings shows a note instead of controls: the PaBaLF firmware runs the light itself (Sym+0, Sym+Shift+1 to 9). |

## New in PR #28, not yet on a device

| Area | What changed |
|---|---|
| Sable Start | BlackBerry 10 layout: Hub on the left, Active Frames (real recent apps, Private Space excluded) as home, app grid pages on the right, Phone · Search · Camera bar. Status dials stay at the top; weather and date replace the clock. |
| Lock screen | The clock follows the doze tick, time zone and 12/24h (the "wrong time" report). Count row, Locked label, private card, Alt+Space emergency shortcut. |
| Notification LED | Amber blinks while charging; solid green only at 100 %. |
| Battery health | The kernel reports the fuel gauge's learned cycle count and capacity; Settings shows voltage. |
| Battery drain | No location prefetch on Doze; the keyboard driver no longer logs every key. |
| Apps | FM Radio inside Sable Media; Sable Games back as small release builds with keyboard play; microG and Aurora Store (`SABLE_Q25_MICROG`). |

## By artifact (updated for PR #28, source only, not yet on a device)

| Artifact | Status | Still open |
|---|---|---|
| start-titan2-3page | Replaced by the BB10 layout | Hub, Active Frames and the app grid, with a Phone · Search · Camera bar; dials stay at the top; weather and date replace the clock. Guessed BB10 colours and grid size; no swipe-up peek. |
| all-apps-titan2 | Done in code | |
| command-titan2 | Done in code | Alt+Space works only while Start is showing, not from inside other apps. |
| hub-titan2 | Done in code | |
| messages-conversation-titan2 | Done in code | |
| phone-dialer-titan2 | Done in code | Call details still show the contact name in private mode. |
| contacts-people-titan2 | Done in code | The Hub chip is a toolbar action; names stay visible when locked. |
| lockscreen-titan2 | Done in code | Emergency is Alt+Space. Large-clock fit needs a device check. |
| quick-settings-titan2 | Done in code | Three columns; tiles are 72dp high, not square. |
| settings-titan2 / -approved | Done in code | |
| setup-wizard-titan2 | Done in code | The Wi-Fi Alt/Sym hint is a toast, not inside the Wi-Fi dialog. |
| battery-health-usage-titan2 | Done in code | Cycles, capacity, temperature and voltage now come from the kernel. |
| mobile-manager-app-controls-titan2 | Done in code | |
| private-space-dual-work-titan2 | Done in code | App lock is hidden (Android has no launch gate). |
| connectivity-otg-nfc-cast-titan2 | Done in code | |
| camera-capture-titan2 | Done in code | The lock-screen camera and video requests still go to Aperture. |
| browser-webview-titan2 | Done in code | |
| files-documents-titan2 | Partial | No three-pane preview or F favourites (DocumentsUI has neither). |
| mail-calendar-titan2 | Done in code | No inline quick reply in Mail. |
| media-gallery-titan2 | Mostly done | Share moved from S to E; no I info on the grid. FM now plays inside Sable Media. |
| utilities-notes-recorder-titan2 | Missing | Not part of this round. |
| toolbox-hardware-titan2 | Done | |
| subscreen-shortcut-keyboard-titan2 | N/A | |

## Defaults chosen in PR #28 (change any of these on request)

1. **Lock-screen emergency shortcut:** Alt+Space (never types a digit; E types 2).
2. **Diagnostics:** under a new top-level Keyboard & input row; Settings > Sable stays Setup only.
3. **Quick Settings:** three columns, as in the artifact.
4. **Restore in Setup:** hidden until restore is validated.
5. **LineageOS rows:** version row, contributors and statistics hidden; Trust renamed Security status; legal notices kept.
6. **App lock:** hidden, because LineageOS has no launch gate to enforce it.
7. **Default camera:** Sable Camera answers still-photo requests from other apps; drop Aperture 0801 to undo.
8. **Photos:** Share moved from S to E (S is reserved for search).
9. **Battery health:** capacity, cycles, temperature and voltage marked as reported from kernel source, before device evidence.
10. **microG:** on by default; push and network location off until turned on in microG Settings.

## Still open

1. Files: three-pane preview and F favourites (DocumentsUI has neither).
2. Media: I info on the grid.
3. Utilities: Notes, Recorder, Clock and the Utilities home.
4. Command: Alt+Space from inside other apps needs a PhoneWindowManager change.
5. Everything above needs the device checks in `QUALIFICATION.md`.
