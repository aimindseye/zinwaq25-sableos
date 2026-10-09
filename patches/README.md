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
| `framework/frameworks/base/0101-SableOS-read-default-large-Quick-Settings-tiles-from.patch` | `f4ed08a03b518772ba77c3c0a1b4185fa1712c9b` | KF-B: the default large Quick Settings tiles come from a resource (upstream set by default; the SystemUI overlay sets Sable's), test fixture updated. |
| `framework/frameworks/base/0102-SableOS-Quick-Settings-tiles-use-the-Sable-card-and-.patch` | `f4ed08a03b518772ba77c3c0a1b4185fa1712c9b` | KF-B: Quick Settings tile corners 12dp and active icon 8dp instead of the pill shape. |
| `framework/packages/apps/Settings/0101-SableOS-Sable-appearance-authority-and-Display-Accen.patch` | `8d8f6486b274bcf0aa6e5d0cbba52c0b05ae5c65` | KF-B: the `org.sableos.appearance` provider Sable apps read (mode always follows Android's dark theme; accent stored and applied to the system palette) and an Accent list in Settings > Display. |
| `framework/packages/apps/Settings/0102-SableOS-SableOS-version-row-in-About-phone.patch` | `8d8f6486b274bcf0aa6e5d0cbba52c0b05ae5c65` | KF-B: "SableOS version" row in About phone from the `ro.sable.*` properties; the LineageOS version row and legal pages stay. |
| `framework/packages/apps/Settings/0201-SableOS-battery-health-and-usage-model-with-host-tes.patch` | `8d8f6486b274bcf0aa6e5d0cbba52c0b05ae5c65` | Battery: pure Java health/usage model (availability, source, confidence; capacity math; history ring buffer; chart cursor; capability profile and the closed BH6 gate) and its host tests. |
| `framework/packages/apps/Settings/0202-SableOS-Battery-health-and-Charging-protection-in-Se.patch` | `8d8f6486b274bcf0aa6e5d0cbba52c0b05ae5c65` | Battery: Battery health and Charging & protection under Settings > Battery; LineageOS charging controls and the legacy Battery information page hidden; history sampled only on existing jobs; keyboard type-ahead, Home/End/Page and focus ring. |
| `framework/packages/apps/Settings/0203-SableOS-keyboard-inspection-for-the-battery-usage-ch.patch` | `8d8f6486b274bcf0aa6e5d0cbba52c0b05ae5c65` | Battery: keyboard inspection mode for the Battery usage chart. |
| `framework/packages/apps/Settings/0300-SableOS-Sable-Attention-and-per-app-Sable-Hub-entrie.patch` | `8d8f6486b274bcf0aa6e5d0cbba52c0b05ae5c65` | KF-A: adds Settings > Notifications > Sable Attention and a per-app "Sable Hub and Attention" link into Sable Hub; both hidden when Hub is absent. |
| `framework/frameworks/base/0301-SableOS-keyboard-notification-commands-in-the-shade.patch` | `f4ed08a03b518772ba77c3c0a1b4185fa1712c9b` | KF-A: contextual single-key commands on a focused notification row (Space, R, D, Z, M, C, H); text input always wins. New router, command glue and a router unit test; three small hooks in `ExpandableNotificationRow`. |
| `framework/packages/apps/SetupWizard/0401-SableOS-keyboard-first-focus-in-setup-steps.patch` | `715b772f07c52ce0fdb83b9891765f3de992ffb5` | IR-014: on a hardware keyboard the first key with nothing focused lands on the step's first text field, else Start/Next; MoveHome/MoveEnd jump to the first/last control; a held Enter/Space/Tab does not repeat; Enter in the last text field runs the primary action. Every other key keeps its platform meaning, and failures fall back to it. Pure policy: `sable/SableKeyPolicy.java`. |
| `framework/packages/apps/SetupWizard/0402-SableOS-Sable-Keyboard-readiness-step-before-Wi-Fi-a.patch` | `715b772f07c52ce0fdb83b9891765f3de992ffb5` | IR-014: a Keyboard step after Locale (owner script) that reports whether Sable Keyboard is installed, enabled and selected and offers one system screen to fix it, before Wi-Fi passwords and the screen-lock PIN. Read-only; Next is always enabled. Pure state: `sable/SableKeyboardState.java`. |

Patch ranges: 0001-0099 HOME/Recents, 0100-0199 KF-B (SystemUI styling and
branding), 0200-0299 Battery, 0300-0399 KF-A (notification policy, attention,
Hub), 0400-0499 setup wizard (IR-014). Each patch was checked against its base
commit; patches of one project are applied in name order and may build on each
other (`apply-framework-patches.sh check` tries them in sequence). No LineageOS
build ran here. `tests/framework/run-pure-tests.sh` unit-tests the pure classes
the SetupWizard patches add. When LineageOS moves, regenerate the patch in a
synced tree with `git format-patch -1` and update the base above.

Reserved:

* `device/` changes to the LineageOS Q25 device tree. Prefer upstreaming to
  LineageOS.
