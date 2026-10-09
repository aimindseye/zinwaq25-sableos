# Patches

* `sable-src/` Q25 changes to the imported Sable app sources (the `zinwa-q25`
  keyboard and camera profiles). `scripts/import-sable-sources.sh` applies them
  in name order after every import. Regenerate a patch with `git diff` against a
  fresh import when you change these files.

* `framework/<project path>/` phase Q4 changes to LineageOS 23.2 projects, as
  `git format-patch` files. `scripts/apply-framework-patches.sh` applies them
  with `git apply` when `stage` runs for Q4 or later, records them in
  `$SABLE_ANDROID_ROOT/.sable-q25-framework-patches`, and reverts them when an
  earlier release is staged. `apply-framework-patches.sh check` reports
  `APPLIES`, `APPLIED` or `CONFLICT` for each patch after a `repo sync`.

| Patch | Base (lineage-23.2) | What it does |
|---|---|---|
| `framework/packages/apps/Launcher3/0001-SableOS-keep-Launcher3QuickStep-for-Recents-only-not.patch` | `ee25ab865b59ecda9c8fd6f901e80b48d7b789ea` | Removes `QuickstepLauncher`'s HOME intent filter, so `SableLauncher` is the only HOME app, and makes `OverviewComponentObserver` name its own activity explicitly instead of resolving HOME. Launcher3QuickStep then serves Recents through its fallback `RecentsActivity`, as in SableOS R9. |

The patch was checked against that commit (`check` says `APPLIES`); no
LineageOS build ran here. When LineageOS moves, regenerate the patch in a
synced tree with `git format-patch -1` and update the base above.

Reserved:

* `device/` changes to the LineageOS Q25 device tree. Prefer upstreaming to
  LineageOS.
