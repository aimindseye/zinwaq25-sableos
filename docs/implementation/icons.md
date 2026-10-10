# Icons: adaptive and monochrome launcher icons from the pinned Phosphor glyphs

> **Update 2026-10-10:** the app code from this package now builds in GitHub
> Actions, with unit tests, Android Lint, detekt and ktlint passing (PR #9; the
> fixes those gates needed are in `sable-src` patch 0011). Nothing has run on a
> device. Statements below about uncompiled code describe the state when the
> package was written.

This gives every enabled app in `product/q25/apps.tsv` (17 apps, plus the disabled SableRadioDiag) an adaptive
launcher icon with a monochrome layer and an adaptive round icon. All of it is generated from the app's pinned
Phosphor glyph. The design and per-app table are in [t3.md, "Launcher icons"](t3.md#launcher-icons-adaptive--monochrome).

Nothing here was built with AGP or run on a device (Google Maven is blocked here). The XML is well-formed and follows
the platform `<adaptive-icon>` schema. The one Kotlin change (Sable Tools shortcut icon) is uncompiled.

## What changed

* `sable-src/tools/gen_first_party_icons.py`: now covers all apps. It writes the legacy R9 vector plus
  `_background`, `_foreground`, `_monochrome` drawables and `mipmap-anydpi-v26/<name>{,_round}.xml` per manifest
  row. It writes a `mipmap/` legacy fallback only where minSdk < 26 (Sable Mail). It checks the safe zone, the
  manifest schema, and each glyph's sha256 and single-path shape. `--check` exits 1 on any drift.
* `sable-src/third_party/phosphor-icons/ICON_MANIFEST.tsv`: two new columns (`res_dir`, `legacy_fallback`) and
  13 new rows (Tools, the 11 R9 common apps and Reader, Messages, Mail, Text Reader). `glyphs/` gains 13 verbatim
  regular SVGs. `PROVENANCE.md` is updated.
* Manifests (14 in-tree modules): `android:icon="@mipmap/<name>"`, `android:roundIcon="@mipmap/<name>_round"`.
* `sable-src/apps/r8/mail/apply_sable_flavor.py`: copies `apps/r8/mail/res/` into Thunderbird and sets icon and
  roundIcon. Mail had no round icon before. `verify_sable_mail.py` checks the new wiring and the monochrome layer.
* `sable-src/apps/r8/textreader/apply_sable_flavor.py`: copies `apps/r8/textreader/res/`, wires mipmap icons and
  fails if the anchors are missing.
* `sable-src/apps/titan2/platform/tools/.../ui/MainActivity.kt`: shortcut icon uses `R.mipmap.ic_sable_tools`.
* Legacy drawables whose content changed: `ic_sable_tools` (Phosphor wrench instead of hand-drawn),
  `ic_sable_messages` in `messages/` (R9 chat-text tile instead of the old 108dp bubble), `ic_sable_calendar`
  (same calendar-dots geometry, now the verbatim path), `ic_sable_reader` (formatting only). The flavor
  Text Reader legacy icon is now text-align-left instead of hand-drawn lines.
* `scripts/audit-app-icons.py`: all six checks are enforced by default. An adaptive icon counts only if its
  layers resolve, and round must be adaptive when the icon is. Flavor apps are resolved in the copied `res/` tree.
  Shared-icon detection compares the resolved layers.
* `tests/run.sh`: step 12 enforces round, adaptive and mono; new step 12b runs the generator `--check`.
* Docs: `docs/implementation/t3.md` icon section, `docs/SABLEOS_GAP_REVIEW.md` (owner decision and Mail round-icon
  limit removed), `THIRD_PARTY_NOTICES.md`.

Sable Start needed no change: `AdaptiveIconDrawable.draw` applies the system mask in its `getBadgedIcon` path, and
its own glyph tiles are separate (details in t3.md).

## Tests

```
python3 -I sable-src/tools/gen_first_party_icons.py --check   # ICON_GENERATOR_CHECK=PASS files=110 drift=0
python3 -I scripts/audit-app-icons.py                          # AUDIT=PASS (enforced: launcher,label,icon,round,adaptive,mono), 17/17 each
bash tests/run.sh                                              # CI=PASS
```

Negative checks were run by hand. Pointing a roundIcon back at the plain drawable fails `round` and `mono`.
Deleting a monochrome drawable fails the audit `mono` check and the generator `--check`.

## Needs a device

* Icon shape under the Q25's configured mask, in Launcher3, Settings and Sable Start.
* Themed-icon (monochrome) tinting on Android 16.
* Sable Mail on API 23-25 using the `mipmap/` fallback (not a Q25 case; the Q25 runs Android 16).

## Integration

1. Regenerate `patches/sable-src/` (new files: glyph SVGs, generated `res` files, `apps/r8/{mail,textreader}/res/`).
2. No `apps.tsv` or stage changes.
3. Expected conflicts: KF-C (Sable Tools `MainActivity.kt` shortcut line, `ic_sable_tools.xml`, tools manifest
   icon lines). T3 (`docs/implementation/t3.md` icon section, audit script, `tests/run.sh` step 12). Anyone editing
   the Mail or Text Reader `apply_sable_flavor.py` icon blocks. If another package edits an app manifest's
   `<application>` attributes, keep the `@mipmap/...` icon refs.
