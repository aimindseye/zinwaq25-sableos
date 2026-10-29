# Architecture

```text
 Sable apps (common source, no Q25 fork)     sable-src/ (+ patches/sable-src) -> scripts/build-apps.sh
   Keyboard, Camera, DisplayCompat, Setup,          |   (also built and checked in GitHub Actions)
   Tools, Calculator, Hub, Weather, Mail, ...       v  hashed APKs
                                              vendor/sable/q25   (product/q25 template)
 Sable product layer  ----------------------> ro.sable.profile.id=zinwa-q25, updater off,
                                              android_app_import modules
 Q4 framework layer   ----------------------> SableLauncher (HOME), product/common/overlay,
                                              patches/framework applied to LineageOS projects
                                              (Launcher3, SystemUI, Settings, SetupWizard,
                                              Dialer, Contacts, Glimpse); reverted at Q1/Q2
                                                    ^
                                              vendor/extra/product.mk  (LineageOS hook)
                                                    ^
 LineageOS 23.2 (Android 16 QPR2)             vendor/lineage/config/common.mk
 lineage_Q25 product, unmodified              device/xelex/Q25, kernel/xelex/mt6789,
                                              hardware/mediatek, sepolicy_vndr,
                                              vendor/xelex/Q25 (TheMuppets blobs)
                                                    |
                                                    v
 Outputs                                      lineage-*-Q25.zip (renamed sableos-q25-*),
                                              boot, vendor_boot, dtbo, vbmeta images
```

## Principles (from SableOS portability rules)

1. **One common product core.** Apps are shared with the other SableOS devices.
   Device facts come from `ro.sable.profile.id` and the Q25 device profile, not
   from forked code.
2. **Bounded device adapter.** Everything Q25-specific is in `build/devices/q25.sh`,
   `device-profile/`, the local manifest, the product layer and the patch sets in
   `patches/`.
3. **Don't fork the device tree.** The official LineageOS tree is used as-is. If a
   change is needed, carry it as a reviewed patch in this repo (`patches/device/`)
   and try to upstream it to LineageOS.
4. **Fail closed.** Capabilities that aren't proven are `NO` in the adapter. There
   is no flash function until a flash contract is proven.
5. **Evidence over claims.** A phase is done when its gate has device evidence
   (`QUALIFICATION.md`).

## Why the `vendor/extra` hook

LineageOS's `vendor/lineage/config/common.mk` begins with
`$(call inherit-product-if-exists, vendor/extra/product.mk)`, so a downstream can
add packages and properties without touching LineageOS or the device tree. Our
`vendor/extra/product.mk` only acts when `TARGET_PRODUCT` is `lineage_Q25`. The
product keeps the name `lineage_Q25` because LineageOS's build (kernel, `bacon`
target, version naming) keys off a `lineage_` product name.

## Sable Notes

Sable Notes (`sable-src/apps/r8/android/notes`, built as `SableNotes` from
`:notes:assembleRelease`) is the Notes part of the daily-utility design
(platform_sable `docs/design/UTILITIES_NOTES_RECORDER_UX.md`).

```text
 MainActivity -> NotesController (state, saves on one background thread, share/export/import intents)
   ui: list (search, colour, delete), full-screen editor (Menu-key menu), Tasks
 core (pure Kotlin, unit tested): Note + NoteLabel, NoteCodec (one <id>.note file each),
   NoteStore (filesDir/notes, write-then-rename), Checklist ("- [ ] " lines), NoteSearch, Tasks, NotesKeys
```

Notes are plain text with a four-line header, so an export is the text itself.
Nothing leaves the app except through the share sheet or the file picker, and
the manifest asks for no permissions.

## Clipboard history in Sable Keyboard

Only the default keyboard (or the app in front) may read the clipboard, so
history lives in Sable Keyboard (`sable-src/apps/titan2/platform/keyboard`):

```text
 ClipboardWatcher (one per process; primary-clip listener while the IME runs, only when the setting is on)
   -> core ClipboardHistory (pure, tested: newest first, 20 entries, skips sensitive/blank/huge copies)
 SableImeService: held Alt+Shift+V opens core ClipPicker in the candidates strip (StripContent.Clips)
```

It is off by default and kept in memory only, never in storage: the keyboard's
settings live in device-protected storage, which is readable before first
unlock, so copies are not put there.

## Sable Reader

Sable Reader v2 (`sable-src/apps/common/reader`, built as `SableReader` from
`:leisure:assembleSableDebug` in `product/q25/apps.tsv`) follows
`docs/SABLE_READER_V2_ARCHITECTURE.md` in aimindseye/sableos and
sableos-project/platform_sable: one local library owns every publication kind,
each kind has its own engine, and nothing goes online.

```text
 leisure (app: library, tabs, settings, readers)
   Books tab = whole library; Comics and Audio tabs = the same shelf narrowed to one kind
   EPUB reader (Readium) | PDF reader | ComicReaderScreen | AudiobookReaderScreen
                                                              |
                                          AudiobookPlaybackService (Media3 MediaLibraryService,
                                          mediaPlayback foreground service, lock-screen controls)
 core (Room library, per-title settings, Settings defaults, importers for files and folders)
 reader-engine-pdf | reader-engine-comic (CBZ, image folders, ComicInfo.xml) | reader-engine-audio
 reader-model (pure Kotlin: PublicationKind, locators, library filters, key map, layout profile)
```

Per-title choices (comic reading mode and fit; audiobook speed and skip lengths)
are stored per title; Settings > Book > Comics & Audiobooks holds the defaults a
title starts with. Layout comes from the window size, never the device name:
`ReaderLayoutProfile.isShort` (under 480 dp tall; the Q25 is about 411 x 387 dp)
selects the compact library and audiobook layouts. The Q25's own Reader changes
are `patches/sable-src` 0079 and 0080.

## Moving off LineageOS later (Q6)

An AOSP- or GrapheneOS-derived base needs replacements for what the Q25 tree
takes from LineageOS: `hardware/lineage/compat`, Lineage power/light HALs,
Lineage's kernel build glue, `LineageSDK` overlays and Lineage's sepolicy
include. That's why it comes after the device is proven on LineageOS.
