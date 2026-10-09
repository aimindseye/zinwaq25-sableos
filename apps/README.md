# Sable application sources

The Sable apps are common SableOS source. The Q25 does not fork them; it builds
them unchanged (plus `zinwa-q25` profile entries) and imports the APKs into the
image.

They live in the private `aimindseye/sableos` repository. This repository is
public, so bringing them here publishes them. That is the owner's call.

## Getting the sources into `sable-src/`

With a local clone of `aimindseye/sableos`:

```bash
bash build/sable.sh q25 Q2 import --sableos /path/to/sableos --authorize-public-copy
```

This copies, at the commit pinned in `build/config/q25.env`, exactly the paths
the app build needs, keeping the sableos layout so Gradle's relative paths work:

```text
sable-src/apps/titan2/platform     Keyboard, Camera, DisplayCompat, Setup, RadioDiag
sable-src/apps/r8/android          Calculator, Games, Media, Hub, Weather, Calendar, ...
sable-src/apps/r8/rust             native code used by the R8 apps
sable-src/src/android/...          Sable design tokens, Sable Start
sable-src/config, third_party, tools   lint config, icons, pinyin data, generators
```

`sable-src/` is git-ignored until the owner decides to commit it.

## Which apps go into the image

`product/q25/apps.tsv` lists them. Rows with `enabled=yes` (the five
keyboard-first platform apps) build by default; `apps --all` adds the common set.
