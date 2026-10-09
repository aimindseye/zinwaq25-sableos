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
| `framework/packages/apps/Settings/0300-SableOS-Sable-Attention-and-per-app-Sable-Hub-entrie.patch` | `8d8f6486b274bcf0aa6e5d0cbba52c0b05ae5c65` | KF-A: adds Settings > Notifications > Sable Attention and a per-app "Sable Hub and Attention" link into Sable Hub; both hidden when Hub is absent. |
| `framework/frameworks/base/0301-SableOS-keyboard-notification-commands-in-the-shade.patch` | `f4ed08a03b518772ba77c3c0a1b4185fa1712c9b` | KF-A: contextual single-key commands on a focused notification row (Space, R, D, Z, M, C, H); text input always wins. New router, command glue and a router unit test; three small hooks in `ExpandableNotificationRow`. |

Each patch was checked against its base commit (`git apply --check`); no
LineageOS build ran here. Range 0300-0399 is KF-A (notification policy,
attention, Hub); 0100-0199 is KF-B (SystemUI styling). When LineageOS moves, regenerate the patch in a
synced tree with `git format-patch -1` and update the base above.

Reserved:

* `device/` changes to the LineageOS Q25 device tree. Prefer upstreaming to
  LineageOS.
