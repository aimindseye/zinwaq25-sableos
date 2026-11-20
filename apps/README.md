# Sable application sources

The Sable apps are built from **`sableos-project/sableos-android`**, the one source of truth
for the app code (one folder per app under `apps/`). This repository holds no app code that
ships; it builds the APKs from a checkout of that repo and imports them into the image.
Fix an app by changing it there, not here. CI in sableos-android is the only place the apps
are compiled and tested before merge.

| `apps.tsv` gradle_root | Folder in sableos-android |
|---|---|
| `apps/titan2/platform` | `apps/titan2-platform` (keyboard, camera, setup, vault, profiles, spaces, compass, phone, tools) |
| `apps/r8/android` | `apps/android` (Hub, Browser, Calendar, Tasks, Notes, Calculator, Weather, Media, Messages, Assistant) |
| `apps/common/reader` | `apps/reader` (Sable Reader: books, comics, PDF, audiobooks) |
| `flavor:launcher` | `apps/launcher` (Sable Launcher, the home app) |
| `flavor:mail`, `textreader`, `connectbot`, `espeak`, `davx5`, `icsx5`, `keymapper` | `apps/mail`, `apps/textreader`, `apps/connectbot`, `apps/espeak`, `apps/davx5`, `apps/icsx5`, `apps/keymapper` (patched third-party sources) |

Point the build at your checkout with `SABLE_APPS_REPO`:

```bash
git clone https://github.com/sableos-project/sableos-android
export SABLE_APPS_REPO=$PWD/sableos-android
bash build/sable.sh q25 Q2 apps
```

The built APKs, a manifest with sha256 values and the checkout's commit (`SOURCE_IMPORT.txt`)
land in `apps-out/<stamp>/`. To ship a newer app version, `git pull` the checkout and run `apps`
again; `stage` packages the last build and does not rebuild it.

## The old `sable-src/` copy

`sable-src/` is the earlier import of the app sources from `aimindseye/sableos` (commit in
`sable-src/SOURCE_IMPORT.txt`), kept for the shared design sources that `stage` still copies
(`sable-src/src/android/shared/sabledesign`) and for the static checks in `tests/`. The
upstream flavor scripts were removed, so the `flavor:` apps cannot be built from it. It is no longer
refreshed for app changes. The text below is how it was imported and is kept for reference.

### Refreshing `sable-src/` (historical)

To move to a newer sableos commit, update `SABLE_Q25_SABLEOS_COMMIT` in
`build/config/q25.env`, then with a local clone of `aimindseye/sableos`:

```bash
bash build/sable.sh q25 Q2 import --sableos /path/to/sableos \
    --titan2-temp /path/to/titan2-temp --authorize-public-copy
```

Add `--titan2-temp /path/to/titan2-temp` to layer the newer product source from
`aimindseye/titan2-temp` (commit `SABLE_Q25_TITAN2_TEMP_COMMIT`) over it: the
keyboard-first platform apps (Keyboard provisioning, P2), Reader v2 (P5A-P5F,
replacing the older `apps/r8/android/reader`) and the P1/P3/P4 reference
sources. The script then applies the Q25's own changes from
`patches/sable-src/*.patch`, so the result is reproducible.

This copies, at the commit pinned in `build/config/q25.env`, exactly the paths
the app build needs, keeping the sableos layout so Gradle's relative paths work:

```text
sable-src/apps/titan2/platform     Keyboard, Camera, DisplayCompat, Setup, Tools, RadioDiag
sable-src/apps/r8/android          Calculator, Media, Hub, Weather, Calendar, Messages, Notes, Tasks (all Kotlin)
sable-src/apps/common/reader       Sable Reader v2 (EPUB, PDF, comics, audiobooks; Books/Comics/Audio tabs) [titan2-temp]
sable-src/reference                Start type-to-find, Setup flow, Weather cities logic [titan2-temp]
sable-src/src/android/...          Sable design tokens, Sable Start
sable-src/config, third_party, tools   lint config, icons, pinyin data, generators
```

The script leaves out files listed in its `EXCLUDE` array (for example a
mislabelled "font" that was really a saved web page) and refuses to finish if
anything looks like web-page account data. Review the diff before committing.

## Which apps go into the image

`product/q25/apps.tsv` lists them. Enabled: the five keyboard-first platform
apps (Keyboard, Camera, DisplayCompat, Setup, Sable Tools) and the common set (Calculator,
Media, Hub, Weather, Calendar, Messages, Notes, Reader v2, Mail, Text Reader, Terminal).
The games moved to their own repo, aimindseye/sablegames (owner, 2026-11-14), and SableOS no longer ships them.
Mail, Text Reader, Terminal, Speech, DAVx5, ICSx5, Key Mapper and the Launcher are `flavor:` rows.
They are patched third-party sources that now live in sableos-android, so the build needs no
upstream download and no flavor script for them. `apps --all` also builds
rows someone has set to `enabled=no`. Radio Diag is disabled because Sable
Tools (DESIGN-KF-C) carries it as Diagnostics > Radio, IMS and FM status and
overrides the old package.

Every Sable app keeps its English in `res/values/strings.xml` (Sable Start: `res/values` next to
its sources), read through one `<App>Text` table per app; a translation is a
`values-<lang>/strings.xml` beside it ([`docs/LANGUAGES.md`](../docs/LANGUAGES.md)). Mail, Text
Reader, Reader and Terminal keep their upstream translations.
