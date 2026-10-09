# Sable application sources

The Sable apps are common SableOS source. The Q25 does not fork them; it builds
them unchanged (plus `zinwa-q25` profile entries) and imports the APKs into the
image.

They come from the private `aimindseye/sableos` repository. The owner approved
publishing them here, so they are committed under `sable-src/` at the commit in
`sable-src/SOURCE_IMPORT.txt`. You don't need sableos access to build.

## Refreshing `sable-src/` (maintainers)

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
sable-src/apps/titan2/platform     Keyboard, Camera, DisplayCompat, Setup, RadioDiag
sable-src/apps/r8/android          Calculator, Games, Media, Hub, Weather, Calendar, Messages
sable-src/apps/common/reader       Sable Reader v2 (EPUB, PDF, comics, audiobooks)      [titan2-temp]
sable-src/reference                Start type-to-find, Setup flow, Weather cities logic [titan2-temp]
sable-src/apps/r8/rust             native code used by the R8 apps
sable-src/src/android/...          Sable design tokens, Sable Start
sable-src/config, third_party, tools   lint config, icons, pinyin data, generators
```

The script leaves out files listed in its `EXCLUDE` array (for example a
mislabelled "font" that was really a saved web page) and refuses to finish if
anything looks like web-page account data. Review the diff before committing.

## Which apps go into the image

`product/q25/apps.tsv` lists them. Every row is enabled: the five
keyboard-first platform apps and the common set (Calculator, the three games,
Media, Hub, Weather, Calendar, Messages, Reader v2). `apps --all` also builds
rows someone has set to `enabled=no`.
