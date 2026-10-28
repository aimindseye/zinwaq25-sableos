# Q4 device review fixes and additions (PR #32)

Status: **written and checked in source (`tests/run.sh`, Gradle unit tests, Lint, detekt,
ktlint, aapt2 links for the Phone and Contacts look), not built into an image or run on a
device.** Each item has a `NOT_RUN` gate in [`QUALIFICATION.md`](../QUALIFICATION.md).

The PR #28 Q4 image was flashed on a Q25 and reviewed on 2026-10-22. The review found that
every design artifact had been drawn for the Titan 2 (1440x1440), that LineageOS apps and the
LineageOS wallpaper were still showing, and that some Sable features only appear in one state
(the lock-screen glance needs a PIN, the weather needs a first fetch). This page lists what
PR #32 changed in answer, and what the owner asked for after the review.

## Review fixes

| Area | Change | Where | Gate |
|---|---|---|---|
| Density | `SABLE_LCD_DENSITY := 280` (about 411 dp square) replaces the vendor 193 | `product/q25/sable-q25.mk`, [`DISPLAY.md`](../../device-profile/DISPLAY.md) | Q4-REVIEW-DENSITY |
| Games | Sudoku, Minesweeper and 2048 boards fit the shorter side (60 % of the window height) | sable-src 0067 | Q4-REVIEW-GAMES |
| Keyboard strip | The compact "EN Shift Alt Sym Nav" strip is off on the Q25; the modifier state shows in the status bar | sable-src 0067, `ro.sable.keyboard.compact_strip=false` | Q4-REVIEW-KEYBOARD-STRIP |
| Wallpaper | A dark Sable wallpaper (720x720, ordered dither) replaces LineageOS's | `product/q25/wallpaper/` | Q4-REVIEW-WALLPAPER |
| LineageOS apps | Sable Calendar replaces Etar, Sable Media replaces Twelve; Sable Start hides AudioFX | `apps.tsv`, sable-src 0066 | Q4-REVIEW-APPS |
| Weather | Sable Weather refreshes in the background (about every 3 hours); setup asks for the city | sable-src 0066, 0068 | Q4-REVIEW-WEATHER, Q4-REVIEW-WEATHER-CITY |
| Display compatibility | App rows open on a tap | sable-src 0066 | Q4-REVIEW-DISPLAYCOMPAT |
| Recents | The Recents key and button open Start's Active Frames (swipe-up and Alt+Tab keep Android's overview) | Launcher3 0003, sable-src 0069 | Q4-REVIEW-RECENTS |
| Start icons | One tile-free glyph style for every shipped app, 5 columns, rows from the window height | sable-src 0069 | Q4-REVIEW-START-ICONS |
| Lock screen | Not changed: the Sable glance still needs a PIN or password, to avoid an overlap risk without the phone | | |

## Status LED

Measured on the phone on 2026-10-22: the `red` (GPIO46) and `green` (GPIO48) switches both
light green, and `blue` (GPIO49) lights blue. While charging, the board blinks the LED green by
itself: it kept blinking with the charger's STAT pin off, the MT6366 current sink off and
charging control stopped. The kernel's LED setup is copied from the Q20 board
(`q20_v12_factory.dts`, triggers `battery_red` and `battery_green` that this kernel doesn't
have). So yellow and red can't be shown, and the charging blink can't be stopped from software.

The LED overlay (`product/common/overlay/SableLedOverlay`) now uses what the Q25 can show:
Android's charging light is off (the board blinks anyway), the LED is solid green when full,
and blue for notifications and low battery. Details: [kf-a](kf-a.md).

Checked and not useful: `benjamindaines/q25-led` and `mybabysexy/q25-led` drive the same three
sysfs switches from a notification listener, and the LineageOS Q25 device tree only sets the
Lights HAL to round colours to pure red, green or blue.

## Phone and Contacts look

Dialer 0707-0709 and Contacts 0755-0756 give both apps the Sable look from
`sable-phone-dialer-titan2` and `sable-contacts-people-titan2`: light or dark with the system
Dark theme like every Sable app (the call screen stays dark), an
accent outline on the focused row, a "Phone" header, accent section labels, pill dial-pad keys,
in-call panels and an End pill. The accent comes from the system palette, so it follows the
user's Sable accent. Details and what stays upstream: [phone-contacts](phone-contacts.md).
Gate: Q4-PHONE-CONTACTS-LOOK.

## One Hub

The image had two Hub screens: Sable Start's Hub page ("Sable Hub", All / Messages / Calls) and
the Sable Hub app (priority / people / more). The owner kept Start's page (sable-src 0070):

* Tabs: All · Messages · Calls · Contacts.
* A Ripe BB7-style unread bar under the title: each app with unread notifications as its icon
  and count, most first, "+N" for the rest. Enter opens the app, or switches tab for Sable
  Messages, Phone and Contacts. Locked or in private mode it shows one padlock total.
* Contacts tab: favourites first, then A to Z; letter keys jump; Enter opens the contact, call
  opens the dialer with the number, text opens Sable Messages. Start holds READ_CONTACTS
  (granted at first boot, revocable).
* The Sable Hub app has no launcher entry. It keeps running for the counts (its notification
  listener feeds `content://org.sableos.hub.snapshot/apps`, package and count only), the
  connected-apps policy and Hub settings. Its SMS, contacts and call-log access is granted at
  first boot in `product/q25/permissions/default-permissions-sable-q25.xml`.
* Framework links follow (frameworks/base 0301 and 0311, Settings 1001, Contacts 0754): the
  shade's H key opens the Hub page on the tab for that notification, the Quick Settings Hub row
  opens the page, Settings' Sable Hub row opens Hub settings, and Contacts' Message action opens
  that person's Sable Messages conversation.

The [Hub dock](hub-dock.md) is part of the Hub app's own screen, which users no longer reach.

Gates: Q4-HUB-ONE, Q4-HUB-LINKS.

## Sable Messages

Sable Messages was a demo with hard-coded threads. It is now a real SMS app and the default SMS
app (sable-src 0071):

* Threads and conversations come from the SMS/MMS provider, with contact names.
* The conversation opens at the latest message and follows new ones; End or Esc returns to the
  latest; Enter sends, Shift+Enter adds a line.
* Sending uses the default SMS SIM, splits long texts, and shows Sending, Sent or "Not sent"
  with Enter to retry.
* Receiving: SMS (including multipart) is saved and notified (private on the lock screen, with
  a mark-read action); MMS is downloaded through MmsService, acknowledged, saved and shown with
  its pictures. The PDU reader is the app's own small Kotlin parser.
* `sms:`, `smsto:`, `mms:` and `mmsto:` links and text shares open the right thread.
* `config_defaultSms` (SableFrameworkOverlay) makes it the SMS role default, and it overrides
  AOSP Messaging (`messaging`) in `apps.tsv`. It ships as a release build (about 2 MB).
* Not done: MMS sending, so group threads can't be answered.

Gate: Q4-MESSAGES.

## Display tools

After [q25toolbox](https://github.com/nozerorma/q25toolbox) (ideas only; that repository has
no licence), App display compatibility gains (sable-src 0072, frameworks/base 0313):

* **Tall canvas** per app: 720x772, 720x960 (3:4), 720x1280 (9:16) or 720x1440. While that app
  is in front, Display Compat sets the forced display size as the system user (no root), so the
  app lays out like a taller phone, shrunk into the square with side bars. The native size comes
  back when another app comes forward, the screen locks or turns off, or the app dies, and at
  boot before the lock screen. Sable Start, keyboards, Settings, SystemUI, setup, Recents and
  the phone app are never resized.
* **Resolution hotkey:** Alt+Shift+R (or Sym+Shift+R) cycles the app in front through Native and
  the favourite sizes, with a toast, and saves the choice.
* **System rotation:** Follow apps (default), Auto-rotate, Portrait or Landscape, plus per-app
  orientation held live while that app is in front.
* Android 16 applies user aspect ratios (16:9, 4:3, Mini) only when the display ignores apps'
  orientation requests. Display Compat now turns that on while any aspect profile is stored;
  without it those profiles did nothing on the Q25.

Gates: Q4-DISPLAY-TALL-CANVAS, Q4-DISPLAY-HOTKEY, Q4-DISPLAY-ROTATION. Hotkey:
[KEYBOARD_AND_INPUT](../KEYBOARD_AND_INPUT.md).

## Defaults chosen (change any on request)

1. Sable Messages is the default SMS app and AOSP Messaging is removed.
2. Hub's SMS, contacts and call-log access is granted at first boot, because Hub has no screen
   to ask from.
3. Settings 1103 still removes Settings > Sable > Sable Hub; Hub settings open from Start's Hub
   page.
4. Any stored aspect profile makes the display ignore apps' orientation requests everywhere.
5. Landscape is 270 degrees, matching the per-app override.
6. The Contacts app keeps the name "Contacts" (the design says "People").

## Second device review (2026-10-28, sable-src 0074)

The main image after PR #33 was flashed and reviewed. The review found that the theme was mixed
(some apps on the old navy Sable palette, others on the neutral dark one), that Calculator and
Camera were not laid out for the square screen, and that Start still showed "Weather · no forecast
yet".

| Area | Change | Gate |
|---|---|---|
| One palette | Setup, Vault, Tools, App display compatibility and the on-screen keyboard use the SableSystemTokens surfaces (#05070A, #0D1117, #161C24 dark; #F5F7F9, #FFFFFF, #E9EDF2 light) and the per-theme accent tones, the same as Start, Settings and Quick Settings. The old navy (#06121D, #0C1E2D) is gone | Q4-REVIEW2-PALETTE |
| Square screen | In a window under 600 dp tall, every sabledesign app (Calculator, Calendar, Games, Hub, Media, Messages, Weather) gets one-line page headers, a smaller type scale and 12/8 dp screen padding | Q4-REVIEW2-SHORT |
| Calculator | Square layout: mode tabs on one line, the display beside a keypad that fills the height, no scrolling | Q4-REVIEW2-CALCULATOR |
| Camera | Square windows get the Control Deck: the viewfinder is clear, a tap shows the touch controls, which wrap instead of running off the right edge | Q4-REVIEW2-CAMERA |
| Weather on Start | Sable Weather, Calendar and Media can now see Start (`<queries>`), so their providers' caller check no longer refuses it; Start says what to do when there is no forecast | Q4-REVIEW2-WEATHER |
| BlackBerry 7 style Start | A second home style next to BlackBerry 10: clock, date, weather and Hub count on the wallpaper over a tray of Favorites that opens into All and Downloads; arrows, Enter, Space or Alt+Left/Right, typing to search, Menu for options. Hub, Active Frames and Edit Home open the BlackBerry 10 screens until Home is pressed. Chosen in Sable Setup or Start's Menu | Q4-REVIEW2-BB7 |
| Sable Setup in Settings | The Settings homepage row "Sable Setup" opens Sable Setup directly (framework 1401). Setup now leads with choices: Home screen style, Home app (another launcher), Keyboard (any keyboard is valid), Add a keyboard (Gboard and others), Default apps | Q4-REVIEW2-SETUP |
| Sable Tools reports | "Save to phone" writes the report as a text file in Download/SableTools, ready to attach or share. The Q25 has no Ctrl, Esc or Tab key, so every tool screen now has a visible Actions button (Copy, Save to phone, About this tool, hand-offs) and plain-letter keys: C copy, S save, M more, Back to leave. App display compatibility's hints say Back instead of Esc | Q4-REVIEW2-TOOLS-SAVE |
| Active Frames | Each frame is the app's picture with a strip under it (icon, name, close ×), a 2 dp accent border and accent strip on focus, and the higher resolution snapshot | Q4-REVIEW2-FRAMES |

Light or dark is the Theme step's Dark/Light choice in setup (Settings > Display > Dark theme
later); the Sable accent only sets the accent colour.

### App audit for the Q25 screen

| App | Colours | Square-screen layout |
|---|---|---|
| Sable Start | Sable tokens, accent | Designed for the Q25 (BB10 layout, 5-column grid) |
| Sable Games | Sable tokens, accent | Square layout (0067) |
| Sable Calculator | Sable tokens, accent | Square layout (0074) |
| Sable Camera | Always dark viewfinder | Square deck (0074) |
| Sable Calendar, Hub, Media, Messages, Weather | Sable tokens, accent | Shared compact headers, type and padding (0074); screens are lists that scroll, no app-specific square layout yet |
| Sable Setup, Vault, Tools, App display compatibility | Sable tokens, accent (0074) | Lists that scroll; fit at 411 dp |
| Sable Keyboard | Sable tokens, accent (0074) | Hardware keyboard first; the on-screen keyboard is the Titan 2 layout |
| Sable Reader | Its own copy of the Sable theme | Its own responsive layout |
| Sable Mail, Text Reader | Upstream apps with the Sable flavour | Upstream layouts, not redesigned |
| Settings, Phone, Contacts, Files | Android theme, Sable accent and Dark theme | AOSP/LineageOS layouts |
