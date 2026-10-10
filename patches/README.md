# Patches

* `sable-src/` Q25 changes to the imported Sable app sources, applied by
  `scripts/import-sable-sources.sh` in name order after every import:
  0001 keyboard and camera profiles, 0002 Weather cities and the Key Probe
  launcher entry (T3), 0003 system tokens (KF-B), 0004 All Apps privacy and
  responsive polish (KF-D), 0005 Hub notification policy (KF-A), 0006 Sable
  Tools (KF-C), 0007 fixes found while integrating them, 0008 adaptive
  Phosphor launcher icons, 0009 the App display corner style, 0010 R9 daily-driver core apps in
  Sable Start (Photos, Camera, Files, Clock tiles; Camera prefers Sable Camera).
  Applied in order to a
  fresh import they reproduce `sable-src/` exactly. Regenerate a patch with `git diff` against a
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
| `framework/packages/apps/Settings/0103-SableOS-Sable-app-corner-style-in-the-appearance-aut.patch` | `8d8f6486b274bcf0aa6e5d0cbba52c0b05ae5c65` + 0101, 0102 | Corner style: `corner_style` column (compact default, rounded) in `org.sableos.appearance`, written only by Settings and the system-image App display compatibility; mode and accent keep their writers. Stacks on 0101. |
| `framework/packages/apps/Settings/0201-SableOS-battery-health-and-usage-model-with-host-tes.patch` | `8d8f6486b274bcf0aa6e5d0cbba52c0b05ae5c65` | Battery: pure Java health/usage model (availability, source, confidence; capacity math; history ring buffer; chart cursor; capability profile and the closed BH6 gate) and its host tests. |
| `framework/packages/apps/Settings/0202-SableOS-Battery-health-and-Charging-protection-in-Se.patch` | `8d8f6486b274bcf0aa6e5d0cbba52c0b05ae5c65` | Battery: Battery health and Charging & protection under Settings > Battery; LineageOS charging controls and the legacy Battery information page hidden; history sampled only on existing jobs; keyboard type-ahead, Home/End/Page and focus ring. |
| `framework/packages/apps/Settings/0203-SableOS-keyboard-inspection-for-the-battery-usage-ch.patch` | `8d8f6486b274bcf0aa6e5d0cbba52c0b05ae5c65` | Battery: keyboard inspection mode for the Battery usage chart. |
| `framework/packages/apps/Settings/0300-SableOS-Sable-Attention-and-per-app-Sable-Hub-entrie.patch` | `8d8f6486b274bcf0aa6e5d0cbba52c0b05ae5c65` | KF-A: adds Settings > Notifications > Sable Attention and a per-app "Sable Hub and Attention" link into Sable Hub; both hidden when Hub is absent. |
| `framework/packages/apps/Settings/0601-SableOS-Applications-privacy-Network-Manager-and-App.patch` | `8d8f6486b274bcf0aa6e5d0cbba52c0b05ae5c65` + 0101-0300 | settings-ui5: pure Java model (KF-D privacy evaluator and row text, per-UID network access policy on LineageOS `POLICY_REJECT_ALL`, app security facts with evidence tags, bounded native-code scan) and its 40 host tests (`tests/sable-apps`). |
| `framework/packages/apps/Settings/0602-SableOS-sensitive-access-under-app-names-and-the-App.patch` | `8d8f6486b274bcf0aa6e5d0cbba52c0b05ae5c65` + 0101-0601 | settings-ui5: the KF-D privacy row under each name in Settings > Apps > All apps (read in the background; spoken form for TalkBack) and the app count on the Apps row; Android's Settings order is unchanged. |
| `framework/packages/apps/Settings/0603-SableOS-Network-Manager-in-Settings-Apps-Network-acc.patch` | `8d8f6486b274bcf0aa6e5d0cbba52c0b05ae5c65` + 0101-0602 | settings-ui5: Settings > Apps > Network access, one switch per UID that adds or removes `POLICY_REJECT_ALL` (the store App info uses), confirmation for messaging apps, keyboard type-ahead; exported action `org.sableos.settings.NETWORK_MANAGER`. Needs 0201/0202. |
| `framework/packages/apps/Settings/0604-SableOS-read-only-App-security-privacy-page.patch` | `8d8f6486b274bcf0aa6e5d0cbba52c0b05ae5c65` + 0101-0603 | settings-ui5: read-only App security & privacy page (identity, effective access, components, code, network, history; each fact tagged DECLARED/ENFORCED/OBSERVED/INFERRED/UNKNOWN), from Settings > Apps and App info; exported action `org.sableos.settings.APP_SECURITY` (plain or with a `package:` URI). |
| `framework/frameworks/base/0301-SableOS-keyboard-notification-commands-in-the-shade.patch` | `f4ed08a03b518772ba77c3c0a1b4185fa1712c9b` | KF-A: contextual single-key commands on a focused notification row (Space, R, D, Z, M, C, H); text input always wins. New router, command glue and a router unit test; three small hooks in `ExpandableNotificationRow`. |
| `framework/packages/apps/SetupWizard/0401-SableOS-keyboard-first-focus-in-setup-steps.patch` | `715b772f07c52ce0fdb83b9891765f3de992ffb5` | IR-014: on a hardware keyboard the first key with nothing focused lands on the step's first text field, else Start/Next; MoveHome/MoveEnd jump to the first/last control; a held Enter/Space/Tab does not repeat; Enter in the last text field runs the primary action. Every other key keeps its platform meaning, and failures fall back to it. Pure policy: `sable/SableKeyPolicy.java`. |
| `framework/packages/apps/SetupWizard/0402-SableOS-Sable-Keyboard-readiness-step-before-Wi-Fi-a.patch` | `715b772f07c52ce0fdb83b9891765f3de992ffb5` | IR-014: a Keyboard step after Locale (owner script) that reports whether Sable Keyboard is installed, enabled and selected and offers one system screen to fix it, before Wi-Fi passwords and the screen-lock PIN. Read-only; Next is always enabled. Pure state: `sable/SableKeyboardState.java`. |
| `framework/packages/apps/Dialer/0701-SableOS-pure-contact-list-letter-and-number-model-fo.patch` | `6da8042323a97d5b3cba1fd975709cc42f29916f` | Phone + Contacts: pure Java for the Phone contacts list (`sable/`): letter navigation over the existing address-book index (accents fold, repeat cycles), the bounded read-only subtitle number map (default number first; 5000 contacts / 20000 rows), the subtitle text and the list key policy. |
| `framework/packages/apps/Dialer/0702-SableOS-Phone-contacts-list-with-A-Z-rail-letter-key.patch` | `6da8042323a97d5b3cba1fd975709cc42f29916f` + 0701 | Phone + Contacts: one A-Z rail on the contacts tab (replaces the scroll thumb while shown), A-Z keys jump and cycle (never call or open), "Type · number" under each name from one read-only Phone query off the main thread, flat bottom bar. Telephony, dialpad, in-call untouched. |
| `framework/packages/apps/Contacts/0751-SableOS-pure-contact-list-letter-and-number-model-fo.patch` | `02bbe49b4ed8e8094d9573ff52747ff8050d9813` | Phone + Contacts: the same pure classes in `com.android.contacts.sable`. |
| `framework/packages/apps/Contacts/0752-SableOS-Contacts-list-with-A-Z-rail-letter-keys-and-.patch` | `02bbe49b4ed8e8094d9573ff52747ff8050d9813` + 0751 | Phone + Contacts: one A-Z rail on the list (fast-scroll thumb off while shown, hidden in search), A-Z keys jump and cycle, `/` opens search, other keys keep type-to-search, default number and type under local contacts, 22sp light section letters, flat toolbar. ContactsProvider stays the owner. |
| `framework/packages/apps/Glimpse/0801-SableOS-Sable-keyboard-focus-ring-on-gallery-thumbna.patch` | `c7b5e8cfbb4e941473f3179322ec8513d83b4ca9` | R9 daily driver: photo thumbnails and album tiles draw the Sable focus ring (2dp `colorPrimary` outline, 2dp gap, no fill) instead of the platform's grey focus highlight, and are explicitly focusable. Resources only; touch, selection and media access unchanged. The rest of the R9 daily-driver port is overlays (`SableGlimpseOverlay`, `SableGallery2Overlay`) and Sable Start ([daily-driver](../docs/implementation/daily-driver.md)). |

Patch ranges: 0001-0099 HOME/Recents, 0100-0199 KF-B (SystemUI styling and
branding), 0200-0299 Battery, 0300-0399 KF-A (notification policy, attention,
Hub), 0400-0499 setup wizard (IR-014), 0600-0699 settings-ui5 (Applications
privacy, Network Manager, App security), 0700-0799 Phone + Contacts (0701-0749
Dialer, 0751-0799 Contacts), 0800-0899 R9 daily-driver core apps (Clock,
Files, Photos, Camera, Browser presentation). Each patch was checked against its base
commit; patches of one project are applied in name order and may build on each
other (`apply-framework-patches.sh check` tries them in sequence). No LineageOS
build ran here. `tests/framework/run-pure-tests.sh` unit-tests the pure classes
the SetupWizard, Dialer and Contacts patches add; `tests/run.sh` runs the Settings 0601 model tests. When LineageOS moves, regenerate the patch in a
synced tree with `git format-patch -1` and update the base above.

Reserved:

* `device/` changes to the LineageOS Q25 device tree. Prefer upstreaming to
  LineageOS.
