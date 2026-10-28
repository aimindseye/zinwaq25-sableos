# SableOS on the Zinwa Q25: User Guide

As of 2026-10-28. Editable copy: [SableOS User Guide (Claude Doc)](https://claude.ai/artifact/KmYo2jeSg6d4z5dXuvzvMf).

## About this guide

SableOS is a BlackBerry-style Android for the Zinwa Q25. It is built on LineageOS 23.2 (Android 16), and almost everything can be done from the keyboard. This guide covers the alpha-20261028 release plus the changes in PR #38 (pure black Dark theme, BlackBerry 7 home icons and swipes, Sound Profiles look, weather fix).

This is alpha software. Features whose gate in [QUALIFICATION.md](QUALIFICATION.md) says `NOT_RUN` have not been checked on a Q25 yet. Settings marked "default" below are what a fresh install ships with.

## Install and first boot

Installing erases the phone. Back up first, and keep the stock firmware package. Full steps and the way back to stock are in [FLASHING_RELEASE.md](FLASHING_RELEASE.md).

1. Back up your files, and the phone's partitions with mtkclient. The `nvram`, `nvdata` and `proinfo` partitions hold your IMEI and can never be downloaded again.
2. On stock Android, turn on Developer options > OEM unlocking and USB debugging, and remove Google accounts.
3. Unpack the release zip and run `sha256sum -c SHA256SUMS.txt`. Every line must say OK.
4. If another phone is connected, run `export ANDROID_SERIAL=<q25-serial>` first.
5. `adb reboot bootloader`, then `fastboot flashing unlock`. This wipes the phone.
6. Flash `boot`, `dtbo`, `vbmeta` and `vendor_boot`, then `fastboot reboot recovery`.
7. In recovery: Factory reset > Format data, then Apply update > Apply from ADB, and `adb sideload sableos-q25-*.zip`. Stopping at 47% is normal.
8. Reboot system now. The first boot takes several minutes.

### First-boot setup and Sable Setup

The first-boot setup walks through language, Wi-Fi, theme and Weather city. Every step fits the square screen with Next on screen. Sable Setup opens next, and later from Settings > Sable Setup. Its first rows are the choices most people change:

| Setup row | Default | What it does |
| --- | --- | --- |
| Home screen style | BlackBerry 10 | BlackBerry 10 (Hub, Active Frames, app pages) or BlackBerry 7 (banner and tray of icons) |
| Home app | Sable Start | Pick another launcher if you prefer |
| Keyboard | Sable Keyboard | Any keyboard works; Gboard shows as "In use: Gboard" |
| Add a keyboard | none | Install Gboard or another keyboard |
| Default apps | Sable apps | Browser, SMS (Sable Messages), Phone, Camera |
| Weather city | asked at setup | Start's header shows that city's weather |

## Getting around

The keyboard does almost everything. Typing on the home screen searches, arrows and the trackpad move focus, Enter opens, and Menu (the BlackBerry key) shows what you can do.

### The keyboard

- **Alt layer:** Alt types the printed legend on each key. W E R = 1 2 3, S D F = 4 5 6, Z X C = 7 8 9, and 0 is 0.
- **Sticky keys:** press Shift or Alt once for the next key, twice to lock. Holding a key types its Alt character.
- **The $ key** types $ in every app and keyboard.
- **AutoText** (on by default) fixes typos and contractions (`dont` becomes "don't"). It also expands `ld` to the date, `lt` to the time and `myver` to the build. Backspace right after a change undoes it. Add your own in Keyboard settings > AutoText entries.
- **Languages:** Sable Keyboard has 35 typing languages, switched from Keyboard settings.

### Sable Start, BlackBerry 10 style (default)

| Page | What it shows | Keys |
| --- | --- | --- |
| Hub (left) | All · Messages · Calls · Contacts, and an unread bar per app | T/B top and bottom, Enter opens |
| Active Frames (home) | Your recent apps as live frames | Arrows move, Enter resumes, × closes |
| App pages (right) | 5-column grid of every app | Arrows move, a letter jumps |
| Bottom bar | Phone · Search · Camera | Alt+1 to Alt+3 |

Alt+Left and Alt+Right move between pages. Alt+Space opens Command, and any letter starts a search of apps, contacts and commands. Menu opens Edit Home, where you choose Home apps, the quick bar and widgets. The status dials at the top show Wi-Fi, Mobile, CPU, RAM, battery and Cell.

### Sable Start, BlackBerry 7 style

- The banner shows the status dials, the date, the weather and the Hub unread count. The status bar shows the time.
- Swipe right or press Alt+Left to open Hub. Swipe left or press Alt+Right to open All apps.
- The tray at the bottom shows one to three rows of Favorites. Drag it up or down to change the rows. Up opens the whole tray and Esc closes it.
- Space switches panels (Favorites, All, Downloads), and typing searches apps.
- Menu on an app: Open, Add to Favorites or Remove from Favorites, Move (arrows carry it, Enter drops it) and App Info. Then Hub, Home Screen Style, Settings and Close.

### Keys that work everywhere

| Key | Does |
| --- | --- |
| Menu | The screen's full menu, with Close last |
| T / B | Top / bottom of a list (Messages, Hub, Mail, Sound Profiles) |
| Space / Shift+Space | Page down / up in lists |
| N / P | Next / previous conversation in Messages |
| Recents key | Active Frames |
| Alt+Shift+R | Next display size for the app in front (App display compatibility) |
| Alt+Space on the lock screen | Emergency dialer |

## Apps

Every Sable app follows the Dark theme and your Sable accent, and has single-key shortcuts.

| App | What it's for | Keyboard tips |
| --- | --- | --- |
| Phone | Calls, recents, contacts | Type a name to jump. In a call: M mute, S speaker, D dial pad. Space answers. Hold a letter to speed dial (set in Menu > Speed dial) |
| Sable Messages | Default SMS app: sends SMS, receives SMS and MMS (no MMS sending yet) | T/B, N/P, Space pages |
| Hub (in Start) | One inbox for messages, calls and notifications | H on a notification opens it in Hub |
| Mail | Email; Gmail sign-in is covered in [MAIL_GMAIL_SIGNIN.md](MAIL_GMAIL_SIGNIN.md) | B to bottom |
| Calendar | Week view with a NOW line | C, T, V, J, K, R |
| Weather | Forecast for your city, refreshed in the background | Feeds Start's header |
| Camera | Sable Camera for stills; video uses Aperture | Tap the viewfinder for touch controls |
| Media | Music, photos, FM radio | E shares |
| Sound Profiles | Normal, Loud, Vibrate only, Phone calls only, All alerts off | Enter uses, E edits, Menu for more; Quick Settings tile |
| Calculator, Games | Keyboard-first | Digits and operators from the keys |
| Tools | Device reports you can save to Download/SableTools | C copy, S save, M more |
| Vault | Private Space and app controls | |
| App display compatibility | Taller canvas per app for apps that don't fit a square screen | Alt+Shift+R |
| Aurora Store + microG | Install Play Store apps without Google Play | See [MICROG.md](MICROG.md) |

## Privacy and security defaults

The alpha favours privacy over Google compatibility, but it is not hardened yet. The bootloader stays unlocked, and the build is not release-key signed (gate Q5-KEYS is `NOT_RUN`). Treat it as a development phone, not a phone for banking or work secrets.

| Area | Default on a fresh install | Why |
| --- | --- | --- |
| Google services | None. microG Services and Companion and Aurora Store are installed instead | No Google Play services polling location or syncing in the background |
| microG SafetyNet / Play Integrity | Off | Apps that require it (many banking apps) may refuse to run |
| Signature spoofing | Only for official microG builds, and only on this image | No other app can pretend to be Google |
| Data encryption | On (Android file-based encryption, set up by Format data) | Your data needs your PIN to unlock after a restart |
| Lock screen | No PIN until you set one in setup | You choose; the keyboard types the PIN |
| Private Space | Available, off until you create it | Keeps chosen apps behind a separate lock; Hub and Active Frames leave them out |
| App lock | Hidden | Android has no real way to block an app from opening, so a fake lock is not offered |
| Hub access | SMS, contacts and call log granted at first boot (revocable) | Hub has no screen of its own to ask from |
| Location | Android default, asked per app | Weather asks when you choose "current location" |
| Bootloader | Unlocked | Needed to install SableOS. Locking it on SableOS may stop the phone from starting |
| Build type | Debuggable build: `adb root` works when USB debugging is on | Lets you collect logs; it also lets anyone with your unlocked phone and a cable read everything |
| Updates | No over-the-air updates; you sideload new releases | Sable OTAs are planned for phase Q5 |

## Recommended changes for everyday use

Do these once after setup. The first group protects you; the second makes the Q25 faster to use.

### Security and privacy

- [ ] Set a PIN or password: Settings > Security > Screen lock. Without one the Sable lock glance is not shown and encryption only protects the phone while it is off.
- [ ] Hide sensitive notification content on the lock screen: Settings > Notifications > Notifications on lock screen.
- [ ] Turn off USB debugging when you are not collecting logs: Settings > System > Developer options. Debugging gives root over a cable on this build.
- [ ] In microG Settings, turn on only what you use: Google account (for Gmail and apps that sign in), Cloud Messaging (for push in apps like Signal or WhatsApp), and Location (for maps). Leave SafetyNet off unless an app needs it.
- [ ] Sign in to Aurora Store anonymously unless you need apps you bought.
- [ ] Review app permissions after installing apps: Settings > Security & privacy > Privacy > Permission manager.
- [ ] Put sensitive apps in Private Space (Vault) so they stay out of Hub, Active Frames and search.
- [ ] Keep your mtkclient backup and stock firmware off the phone. It is the only copy of your IMEI partitions.

### Using the Q25 efficiently

- [ ] Pick a home style in Sable Setup (BlackBerry 10 or 7) and add your apps: Edit Home on BB10, Menu > Add to Favorites on BB7.
- [ ] Keep the Dark theme: Start's pages, Hub and All Apps are pure black from PR #38, which suits the Q25.
- [ ] Set your Weather city in Sable Setup, so Start's header shows it.
- [ ] Assign speed dial letters in Phone > Menu > Speed dial, then hold a letter on the home screen to call.
- [ ] Add your own AutoText shortcuts (Keyboard settings > AutoText entries), such as `addr` for your address.
- [ ] Add the Sound profile tile in Quick Settings > Edit, and set each profile's tones and vibration in Sound Profiles.
- [ ] Give apps that look cramped on the square screen a taller canvas in App display compatibility, or press Alt+Shift+R while the app is open.
- [ ] Set the time zone in Settings > System > Date & time if you have no SIM; without one the phone stays on the zone from setup.
- [ ] If you prefer Gboard or another keyboard, add it in Sable Setup > Add a keyboard. The $ key and Alt shortcuts still work.

## Troubleshooting and getting help

| Problem | What to do |
| --- | --- |
| Sideload stops at 47% | Normal. Wait for recovery to say the install finished |
| Start says "open Sable Weather to set it up" | Open Weather and choose a city. On alpha-20261028 this was a bug fixed by PR #38 |
| Keyboard light stays dark | The stock keyboard firmware ignores the light; see the Keyboard firmware notes in [KEYBOARD_AND_INPUT.md](KEYBOARD_AND_INPUT.md) |
| Lock screen time is wrong | Set the time zone in Settings > System > Date & time |
| An app needs Google Play services | Turn on the matching microG feature; some apps that require Play Integrity will not run |
| Phone won't start, or lost signal | Go back to stock with [FLASHING_RELEASE.md](FLASHING_RELEASE.md#going-back-to-stock) |
| Start or an app crashes on first boot | Run `scripts/capture-crash-evidence.sh` before clearing anything, and attach its `SUMMARY.txt` to a GitHub issue |

Report problems as [GitHub issues](https://github.com/aimindseye/zinwaq25-sableos/issues). Include the release tag, what you pressed, and a photo or log.
