# Q25 design conformance review

This review compares the 24 design artifacts in `sableos-project/platform_sable/docs/design/artifacts` with this port. Status as of 2026-10-23, after PR #25 and PR #28 were merged and with PR #32 open. The PR #28 image was flashed and reviewed on a Q25 on 2026-10-22; PR #32 answers that review ([q4-review](implementation/q4-review.md)) and is **in source only**. The matching on-device checks are the `NOT_RUN` Q4-* rows in [`QUALIFICATION.md`](QUALIFICATION.md).

**Short answer:** 20 of the 24 artifacts are done in code, now sized for the Q25 (density 280, about 411 dp square) rather than the Titan 2. Files and Media are partial, Utilities is still missing, and the rear-screen artifact doesn't apply to the Q25. PR #26 records the owner's earlier BB10 Start attempt in [`DESIGN_CONFORMANCE_TB2_BB10_IMPLEMENTATION.md`](DESIGN_CONFORMANCE_TB2_BB10_IMPLEMENTATION.md); PR #28 replaced that Start code, so this file is the current status.

Status words: **Done in code** (built and unit-tested, not yet seen on a device), **Partial**, **Missing**, **N/A** (Q25 hardware has no such thing).

## Booted on a Q25 (Q4, 2026-10-17, fixed in PR #25)

| Area | What changed |
|---|---|
| Lock screen PIN | Typing works wherever focus is. W E R S D F Z X C type 1 to 9, 0 types 0, Enter unlocks. |
| LineageOS leftovers | No LineageOS features step or wording in setup; Launcher3 settings and search provider hidden; LineageOS Updater replaced. |
| Settings > Sable | Holds Sable Setup only. |
| Keyboard light | Settings shows a note instead of controls: the PaBaLF firmware runs the light itself (Sym+0, Sym+Shift+1 to 9). |

## Reviewed on a Q25 (PR #28 image, 2026-10-22) and fixed in PR #32, not yet on a device

| Area | What changed |
|---|---|
| Sizing | Density 280 (411 dp square); game boards fit the screen; Start's grid has 5 columns and one tile-free glyph style. |
| LineageOS leftovers | Sable wallpaper; Sable Calendar and Media replace Etar and Twelve; AudioFX hidden; Sable Messages replaces AOSP Messaging. |
| Recents | The Recents key opens Active Frames. |
| Weather | Background refresh and a city step in setup. |
| Keyboard | The compact strip is off; modifier state shows in the status bar. |
| Notification LED | The Q25 LED shows green and blue only and the board blinks it green while charging, so the charging light is off, full is solid green and notifications are blue. Amber is impossible. |
| Phone and Contacts | Sable dark look: focus outline, Phone header, pill dial-pad keys, in-call panels. |
| Hub | Start's Hub page is the one Hub: All · Messages · Calls · Contacts and a Ripe-style unread bar; the Hub app has no launcher entry. |
| Messages | Sable Messages is the default SMS app: send, receive SMS and MMS. |
| Display compatibility | Tall canvas per app, Alt+Shift+R resolution hotkey, system rotation override; aspect ratios now take effect on Android 16. |

## New in PR #28, not yet on a device

| Area | What changed |
|---|---|
| Sable Start | BlackBerry 10 layout: Hub on the left, Active Frames (real recent apps, Private Space excluded) as home, app grid pages on the right, Phone · Search · Camera bar. Status dials stay at the top; weather and date replace the clock. |
| Lock screen | The clock follows the doze tick, time zone and 12/24h (the "wrong time" report). Count row, Locked label, private card, Alt+Space emergency shortcut. |
| Notification LED | Amber blinks while charging; solid green only at 100 %. (Replaced in PR #32: the Q25 LED can't show amber.) |
| Battery health | The kernel reports the fuel gauge's learned cycle count and capacity; Settings shows voltage. |
| Battery drain | No location prefetch on Doze; the keyboard driver no longer logs every key. |
| Apps | FM Radio inside Sable Media; Sable Games back as small release builds with keyboard play; microG and Aurora Store (`SABLE_Q25_MICROG`). |

## Sable Notes, not yet on a device

Sable Notes follows the Notes part of platform_sable `docs/design/UTILITIES_NOTES_RECORDER_UX.md`.
- **Local first, explicit export.** Notes live in app storage and nothing syncs. Share, Export and Import happen only when asked, through the share sheet and the file picker, and the app holds no permissions.
- **Latest first.** The list puts the latest-edited note first, with the open checklist count in the header.
- **Full-screen editor.** The 411 x 387 dp window has no room for a list beside the editor (FULL_SCREEN_EDITOR_MODE).
- **Checklists and tasks.** Checklist lines are plain text, and Tasks gathers the open items, with done items only on request (COMPLETED_TASKS_EXPLICIT_HISTORY).
- **Text input wins.** Typing owns every printable key.

One difference from the contract: the Q25 has no Ctrl or Esc key, so the contract's Ctrl+N/S/F/Enter and Esc become C in the list, the Menu key's lettered menu, Alt+Enter and Back on the Q25. The Ctrl chords still work on keyboards that have them. Locked-note and private-mode redaction are not built, because Notes has no lock-screen surface or widget yet.

## Sable Terminal, not yet on a device

- **Keyboard first on a keyboard without Ctrl or Esc.** Menu is a sticky Ctrl and Menu then Back is Esc, with the same lock cycle as the on-screen Ctrl; plain Back keeps its Back meaning.
- **Upstream unchanged elsewhere.** ConnectBot's screens, settings and translations are kept, so it does not yet follow the Sable list keys (T/B/Space) or the pure black theme.
- **Local first.** Keys and host settings stay in app storage and backup is off.

## Sable Keyboard clipboard history, not yet on a device

- **Off by default.** Persistent keyboard clipboard history must not be on by default, so it is a Keyboard settings switch; turning it off forgets every entry.
- **Memory only.** The history is never written to storage; a reboot or keyboard restart empties it, and nothing is readable before first unlock.
- **Private copies stay out.** Clips flagged sensitive are skipped and the picker never opens on the lock screen.
- **Keyboard first.** Held Alt+Shift+V opens it in the strip; Space, the trackpad, Enter, Backspace and Back drive it, and any other key types as usual. Touch works too.
- **No Pastiera code.** The behaviour is written from scratch.

## Translatable Sable apps, not yet on a device

- **Every line in a strings file.** Each Sable app reads its text from `res/values/strings.xml`, so the phone's language picks a translation when one is added and English otherwise (sable-src 0085, [`LANGUAGES.md`](LANGUAGES.md)).
- **Key legends stay keys.** Hints keep the Q25's printed key names and letters; translators change only the words.
- **Support reports stay English.** Sable Tools reports and Radio diagnostics are not translated, so a report reads the same from any phone.

## Sable Reader in PR #41, not yet on a device

| Area | What changed |
|---|---|
| Tabs | Books, Comics, Audio and Settings: Comics and Audio list only that kind of title (they said "Coming Soon"); an empty tab says how to add one. |
| Sizing | On windows under 480 dp tall (the Q25 is about 411 x 387 dp) the tab row is one line of labels, the title row is hidden unless profiles are in use, and the library fits four covers across. The audiobook player puts the cover beside the chapter so the controls stay on screen; comic and audiobook dialogs scroll. |
| Settings | Book > Comics & Audiobooks: default comic reading mode and fit, default audiobook speed and skip lengths. A on a title still overrides them. |
| Keys | N/P change audiobook chapters (no PageUp/PageDown on the Q25). |

## By artifact (updated for PR #28, source only, not yet on a device)

| Artifact | Status | Still open |
|---|---|---|
| start-titan2-3page | Replaced by the BB10 layout | Hub, Active Frames and the app grid (5 columns, tile-free glyphs), with a Phone · Search · Camera bar; dials stay at the top; weather and date replace the clock. No swipe-up peek. |
| all-apps-titan2 | Done in code | |
| command-titan2 | Done in code | Alt+Space works only while Start is showing, not from inside other apps. |
| hub-titan2 | Done in code | Start's Hub page is the one Hub (PR #32): Contacts tab and Ripe-style unread bar. The Hub app's own inbox and dock are no longer reachable. |
| messages-conversation-titan2 | Done in code | Sable Messages is the default SMS app (PR #32). No MMS sending. |
| phone-dialer-titan2 | Done in code | Sable look in PR #32. Call details still show the contact name in private mode; the Call button stays round. |
| contacts-people-titan2 | Done in code | Sable look in PR #32; the Message action opens Sable Messages. Names stay visible when locked; the title is still "Contacts". |
| lockscreen-titan2 | Done in code | Emergency is Alt+Space. The Sable glance needs a PIN or password; without one the upstream lock screen shows. |
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
5. Lock screen without a PIN keeps upstream's design.
6. Everything above needs the device checks in `QUALIFICATION.md`.
