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
- **Clipboard history** (off until you turn it on in Keyboard settings): hold Alt and Shift and press V to see your recent copies in the strip above the keyboard. Space or the trackpad moves, Enter pastes, Backspace removes the entry and Back closes. It keeps your last 20 copies in memory only, so a restart empties it; it skips anything an app marks as sensitive (such as a copied password), never opens on the lock screen, and Keyboard settings > Clear clipboard history empties it at once.

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
| Notes | Notes and checklists with colour labels, kept on the phone | See [Notes](#notes) below; C new, S find, K tasks |
| Weather | Forecast for your city, refreshed in the background | Feeds Start's header |
| Camera | Sable Camera for stills; video uses Aperture | Tap the viewfinder for touch controls |
| Media | Music, photos, FM radio | E shares |
| Sound Profiles | Normal, Loud, Vibrate only, Phone calls only, All alerts off | Enter uses, E edits, Menu for more; Quick Settings tile |
| Reader | Books (EPUB), PDF, comics and audiobooks, all stored on the phone | See [Comics and audiobooks](#comics-and-audiobooks) below; B bookmark, T contents, A view settings |
| Calculator | Keyboard-first | Digits and operators from the keys |
| Games | Ten games, each its own app: Sudoku, Minesweeper, 2048, Bricks, Snake, Blocks, Reversi, Solitaire, Mahjong, Words | See [Games](#games) below |
| Tools | Device reports you can save to Download/SableTools | C copy, S save, M more |
| Vault | Private Space and app controls | |
| App display compatibility | Taller canvas per app for apps that don't fit a square screen | Alt+Shift+R |
| Aurora Store + microG | Install Play Store apps without Google Play | See [MICROG.md](MICROG.md) |

### Games

Every game plays from the keyboard or the trackpad (which sends arrow keys), and also by touch. N starts a new game in all of them except Words, where every letter types; there Enter starts the next round once one is done.

| Game | How to play | Keys |
| --- | --- | --- |
| Sudoku | Fill the 9 x 9 grid | Arrows move; W E R S D F Z X C (or Alt+digits) enter 1-9; Del erases; U undoes |
| Minesweeper | Clear the field without hitting a mine | Arrows move; Space reveals; F flags |
| 2048 | Slide tiles to merge them up to 2048 | Arrows slide |
| Bricks | Break the wall with the ball; three lives, each level faster | Left/right move the paddle; Space launches; P pauses |
| Snake | Eat to grow; don't hit a wall or yourself | Arrows turn; P pauses; Space restarts after a crash |
| Blocks | Falling blocks: fill whole rows to clear them | Left/right move; Up rotates; Down drops one row; Space drops; P pauses |
| Reversi | Play dark against the phone; flip its pieces by closing lines | Arrows move; Space or Enter plays |
| Solitaire | Klondike, one card at a time | Arrows pick a pile; Enter picks up and puts down; Space turns the stock; A sends a card to its foundation; Del lets go |
| Mahjong | Remove matching free tiles in pairs; every deal can be cleared | Arrows move; Space or Enter picks; H shows a pair |
| Words | Guess the five-letter word in six tries: green is right, amber is in the word elsewhere | Type the letters; Del erases; Enter guesses |

### Notes

Sable Notes is for quick notes and lists, in the spirit of BlackBerry Remember and Color Notes. Notes stay on the phone: there is no account and no sync. To move notes elsewhere, use Share or Export, and use Import to bring a text file in. Text shared from another app (Share > Sable Notes) becomes a new note.

The first line of a note is its title. The list shows the latest-edited note first, with its colour, when it was edited and how many checklist items are done.

| Where | Key | What it does |
| --- | --- | --- |
| List | C (or Ctrl+N) | New note |
| List | S or / | Find in every note; Enter opens the first match |
| List | K | Tasks: the open checklist items from every note. Enter ticks one, O opens its note, H shows the done ones |
| List | L | Change the focused note's colour |
| List | D | Delete the focused note (asks first) |
| List | I | Import a text file as a note |
| List | T, B, Space, N, P | Top, bottom, page, next, previous |
| Note | Menu | The note menu. Each item has a letter: L makes the line a checklist item, T ticks it, F finds, G finds the next match, C changes the colour, S shares, E exports to a file, D deletes |
| Note | Enter on a checklist item | Starts the next item; Enter on an empty item ends the list |
| Note | Alt+Enter | Ticks or unticks the item the cursor is on |
| Note | Back | Saves and goes back to the list. A note left empty is not kept |

Notes save themselves as you type. On a keyboard with Ctrl, Ctrl+S, Ctrl+F, Ctrl+G, Ctrl+T, Ctrl+L, Ctrl+E and Ctrl+Enter do the same as the menu letters, and Esc goes back. A checklist item is a line that starts with `- [ ] `, or `- [x] ` once it's done, so an exported note reads the same in any text editor.

### Comics and audiobooks

Reader has four tabs. Books shows the whole library, Comics shows only comics, Audio shows only audiobooks, and Settings holds the defaults. Each shelf has + to add one file and the folder button to add a folder.

**Comics.** Add a CBZ file, or a folder of page images (one folder is one comic). Comics open one whole page at a time. Left and Right turn pages, and A picks the reading mode: pages left to right, right to left (manga), top to bottom, continuous scroll or webtoon, plus whole-page or page-width fit. A comic marked as manga or webtoon in its own ComicInfo.xml opens that way automatically.

**Defaults.** Settings > Book > Comics & Audiobooks sets the reading mode and fit that new comics start with, and the speed and skip lengths that new audiobooks start with. Pressing A while reading changes just that one title, and it keeps that choice.

#### Audiobooks

Sable Reader plays DRM-free audiobooks from the phone's storage: MP3, AAC/M4A, M4B (with its chapters), Ogg/Opus, FLAC and WAV. A folder of audio files counts as one book, with each file as a chapter.

1. Copy the book to the phone (for example to Download or Audiobooks).
2. In Reader's Audio tab, tap + to add one file, or the folder button to add a folder of audio files.
3. Open it. Playback carries on with the screen off and shows in the notification shade and on the lock screen. Headset buttons work too.

| Key | Does |
| --- | --- |
| Space | Play / pause |
| Left / Right | Skip back / forward |
| N / P | Next / previous chapter |
| T | Chapter list |
| B | Bookmark this moment |
| A | Speed, skip length and sleep timer (minutes or end of chapter) |
| Esc / Back | Close a panel, then leave the book |

Reader remembers where you stopped in each book. It never goes online, and needs no account.

### Making other apps dark

Sable apps, Phone, Contacts, Settings and the system screens all use the same pure black. Other apps only go dark if they support Android's Dark theme, and many use their own dark grey. The clean fix is the app's own setting: look for Dark mode, Black or AMOLED theme in its settings (most chat apps and browsers have one). Developer options > Override force-dark exists, but it recolours apps that were not made for it and can leave wrong colours, invisible icons or inverted images, so SableOS leaves it off.

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
- [ ] Keep the Dark theme: from PR #38 a fresh install has Pure black on (Settings > Display > Dark theme), and Start's pages, Hub and All Apps are pure black too, which suits the Q25.
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
