# Settings: Applications privacy, Network Manager, App security (settings-ui5)

Port of the GrapheneOS-era Settings work (ui5a, ui5b, the Network Manager part
of r9, and what ui5c still lacked) to LineageOS 23.2 `packages/apps/Settings`.

```text
PACKAGE=settings-ui5
SURFACE=Settings > Apps, Settings > Apps > Network access, App info (patches 0601-0604)
BASE=8d8f6486b274bcf0aa6e5d0cbba52c0b05ae5c65 + 0101 0102 0103 0201 0202 0203 0300
SETTINGS_ANDROID_IA_PRESERVED=YES (Apps keeps its upstream place and name)
TOOLS_ACTIONS=org.sableos.settings.NETWORK_MANAGER, org.sableos.settings.APP_SECURITY (action only, no extras, no data)
NETWORK_CONTROL=LineageOS per-UID policy POLICY_REJECT_ALL (no INTERNET revocation on LineageOS)
FRAMEWORKS_BASE_CHANGES=NONE
MODEL_HOST_TESTS=40 PASS
ANDROID_GLUE=TYPE-CHECKED against Android 16 android-all + LineageOS Settings sources (0 errors)
BUILD_TESTED=NO   DEVICE_TESTED=NO
```

Nothing here has been built with Soong or run on a phone. The pure model is
compiled with `javac` and its host tests pass. The Android code was
type-checked (method below), which catches wrong names and signatures but is
not a build: resources, manifest merging and lint have not run.

## What a user gets

Settings home keeps Android's order. The Apps row now says how many apps are
installed:

```text
Apps    34 apps · permissions, network access and defaults
```

**Settings > Apps > All apps** (ManageApplications, main list) shows the KF-D
privacy row under each app name instead of the size line:

```text
Maps        permissions · Location · Camera · Microphone +2
Keyboard    permissions · ⚠ Accessibility
Clock       permissions · none sensitive
Work app    permissions · see app info
```

The rules are KF-D's (`implementation/kf-d.md`): a permission counts only when
it is granted in the app's own user and its app-op is not ignored or errored;
an app-op that cannot be read makes the group UNKNOWN and it is left out
rather than guessed; location granted only as approximate reads "Location
approximate", photos granted as selected photos reads "Photos limited";
the five special-access groups (Accessibility, Device admin, Install apps,
Overlay, Usage access) carry "⚠"; three labels, then "+N". When
nothing can be read the row says "permissions · unavailable", never "none".
TalkBack reads "Permissions: …" and "Special access: …" without symbols.

**Settings > Apps > Network access** (Network Manager) lists every app that
requests `INTERNET`, one switch per Linux UID (apps that share a UID are one
row, "shared with N other apps"), sorted by label with the locale collator.
Switching an app off adds `POLICY_REJECT_ALL`, the same store App info >
Mobile data & Wi-Fi uses, so both screens always agree. Turning off an app
that sends messages, mail or calls asks for confirmation first. Rows say
exactly what is in force: "Allowed on all networks", "Blocked on all
networks", or "Blocked on mobile data · VPN (App info)" when App info's
per-transport switches are set (Network Manager does not change those),
plus "background mobile data off" when set. System UIDs that cannot be
restricted show "Managed by the system" and are disabled. Keyboard: type a
name to jump, Home/End, Page Up/Down, `/` or Ctrl+K for Settings search.

**App security & privacy** (Settings > Apps > App security & privacy, and an
entry in every App info under App permissions) is read-only. Each fact is
tagged with where it comes from: DECLARED (package metadata), ENFORCED
(current Android policy), OBSERVED (recorded by the system), INFERRED, or
UNKNOWN. Sections: identity and provenance (package, version, SDK, installer
chain, SHA-256 signer, signing history, install type, debuggable/test-only),
effective access (same evaluator as the list row, link to App permissions),
components and exposure (counts, exported names, instrumentation, powerful
bindings such as accessibility, notification listener, VPN, IME, autofill,
device admin), code (native libraries and ABIs from a bounded ZIP scan,
shared libraries; trackers say "No tracker signature list is installed;
nothing is claimed either way"), network and background (links to Network
Manager, battery optimization, stopped state), history (install and update
time; permission change history "Not kept on this device").

## Decisions and deviations

* **Android's information architecture stays** (KEYBOARD_FIRST_SETTINGS_UX,
  KF-B SETTINGS_ANDROID_IA_PRESERVED). Not ported from the GrapheneOS work:
  the custom Settings home header, the top-level Hub entry, moving Apps into a
  privacy group or renaming it "Applications", renaming About, and the
  SetupWizard and Keyguard edits.
* **ui5c identity row** is already covered by KF-B 0102 (SableOS version in
  About phone); nothing added.
* **Network Manager uses `POLICY_REJECT_ALL`.** GrapheneOS revoked the
  `INTERNET` permission; LineageOS has no revocable `INTERNET`, but
  frameworks/base `f4ed08a0` already has per-UID REJECT_ALL / CELLULAR /
  WIFI / VPN policies. Network Manager only toggles REJECT_ALL; the
  per-transport switches stay in App info.
* **Read-only security page.** Nothing on it changes the app; changes go
  through Android's own App permissions and Network Manager.
* **No network services, analytics, daemons or new permissions.** Everything
  is read from PackageManager, AppOps, AccessibilityManager settings,
  DevicePolicyManager and NetworkPolicyManager in the Settings process.

## Files

Pure model (patch 0601), `src/com/android/settings/applications/sable/model/`,
filegroup `SettingsSableAppsModel-srcs`:
`PrivacyGroup`, `OpMode`, `PermissionFact`, `PrivacyFacts`,
`PrivacyPermissions`, `GroupState`, `AppPrivacy`, `PrivacyEvaluator`,
`PrivacyRow`, `NetworkAccessPolicy`, `NetworkAppRows`, `Evidence`,
`ComponentExposure`, `NativeCodeScan`, `AppSecurityFacts`.
Host tests: `tests/sable-apps/` (`java_test_host SettingsSableAppsModelTests`,
`TEST_MAPPING`).

Android glue, `src/com/android/settings/applications/sable/`:

| Patch | Files |
|---|---|
| 0602 | `SablePrivacyFactsReader`, `SablePrivacyLabels`, `AppStateSablePrivacyBridge`, `TopLevelAppsPreferenceController`; `strings_sable_apps.xml`; controller on `top_level_apps` in both top-level XMLs; 17 lines in `ManageApplications` (bridge for the main list, summary and content description) |
| 0603 | `SableAppListKeyboard`, `SableNetworkManagerFragment`; `sable_network_manager.xml`, `strings_sable_network.xml`; `apps.xml` row; `Settings.SableNetworkManagerActivity`; manifest activity for `org.sableos.settings.NETWORK_MANAGER`; `SettingsGateway` |
| 0604 | `SableAppSecurityFragment`, `SableAppSecurityAppsFragment`, `SableAppSecurityPreferenceController`; `sable_app_security*.xml`, `strings_sable_app_security.xml`; `app_info_settings.xml` entry; `AppInfoDashboardFragment` (2 lines); `apps.xml` row; `Settings.SableAppSecurityActivity`; manifest activity for `org.sableos.settings.APP_SECURITY` (plain, and with a `package:` URI for one app); `SettingsGateway` |

`SableAppListKeyboard` reuses the battery keyboard model from 0201/0202
(`TypeAheadMatcher`, `ListFocusNavigator`, `SableBatteryKeys`), so 0603 needs
those patches.

## Tests and checks

```text
bash tests/run.sh
  PASS  Settings Applications model host tests (RAN=40)
  PASS  Sable Tools Settings links land on exported Settings actions
bash scripts/apply-framework-patches.sh check     (against a Settings tree at the base)
```

`tests/run.sh` step 6f extracts the model and its tests from 0601, compiles
them with `javac` and a small JUnit shim (`tests/java/junit-shim/`), runs
them, and checks that both actions Sable Tools sends (`SettingsLink.kt`) are
exported by a 060x manifest. In a LineageOS tree run
`atest SettingsSableAppsModelTests`.

The series 0101…0604 was applied in order with `git apply` to a clone of
Settings at the base; the result matches the commits it was generated from.

**Type-check of the Android code.** Every added or changed Java file
(model, 0602-0604 glue, the `ManageApplications` and
`AppInfoDashboardFragment` edits) was compiled with `javac` against:

* the Android 16 framework jar (Robolectric `android-all` API 36), with
  `ACC_FINAL` restored on static constants that the instrumented jar strips;
* the real LineageOS Settings sources it uses: `AppStateBaseBridge`,
  `AppCounter`, `InstalledAppCounter`, `AppInfoPreferenceControllerBase`, and
  the battery keyboard classes;
* an `R` class generated from the patched `res/`;
* stubs, with real signatures, only for what is not in that jar: LineageOS
  `NetworkPolicyManager` constants and `getUidPolicy`/`addUidPolicy`/
  `removeUidPolicy`, androidx (preference, fragment, appcompat, recyclerview,
  annotation), SettingsLib (`ApplicationsState`, `AccessibilityUtils`,
  `ThreadUtils`, lifecycle), and Settings base classes
  (`SettingsPreferenceFragment`, `BasePreferenceController`, `AppInfoBase`,
  `SubSettingLauncher`, `FeatureFactory`, `SearchFeatureProvider`).

Result: 0 errors at each of 0602, 0603 and 0604. The only warnings are
deprecations that match upstream use. The `ManageApplications` lines were
checked in a harness that holds them verbatim. XML is well formed and every
`@string` the patches use is defined.

Not checked by any of this: AAPT resource linking, manifest merge, Soong
module wiring for `tests/sable-apps`, lint, Kotlin interop with the real
androidx versions, and runtime behaviour.

## Not done

* Searching All apps by permission words, and a "sensitive access" filter in
  ManageApplications.
* "Show system apps" in Network Manager (system apps that request
  `INTERNET` are listed; those that cannot be restricted are disabled).
* Fitting the privacy row to the row width; it is cut at three labels plus
  "+N" instead.
* Checking device-admin or work-profile restrictions before allowing the
  Network Manager switch (Android's `NetworkPolicyManager` still enforces its
  own rules; a refused change is re-read and shown as it is).

## Q4 gates (proposed)

| Gate | Check |
|---|---|
| Q4-SETTINGS-APPS | Settings home keeps Android's order; Apps shows "N apps · permissions, network access and defaults" and N matches All apps; each All apps row shows the KF-D privacy row; granting, denying and approximate-location changes show on return; TalkBack reads the spoken form |
| Q4-NETWORK-MANAGER | Settings > Apps > Network access lists apps requesting network access; turning one off blocks it on Wi-Fi, mobile data and VPN and App info > Mobile data & Wi-Fi shows the same state; a messaging app asks for confirmation; per-transport blocks set in App info show "(App info)"; type-ahead, Home/End, Page keys and `/` work; the "Settings > Network Manager" link in Sable Tools' Network tool opens this screen |
| Q4-APP-SECURITY | App security & privacy opens from Settings > Apps, from App info and from Sable Tools' Apps tool; every fact carries an evidence tag; nothing on the page changes the app; a large app (many APKs or native libraries) opens without stalling the UI |

## Integration

1. Settings patches apply in name order: 0101-0103, 0201-0203, 0300, then
   0601-0604. 0603 needs 0201/0202 (battery keyboard model).
2. After `repo sync`, `bash scripts/apply-framework-patches.sh check` must say
   `APPLIES` for 0601-0604; otherwise regenerate them in a synced tree with
   `git format-patch` and update the base in `patches/README.md`.
3. Build Settings and run `atest SettingsSableAppsModelTests`.
4. Run the gates above on the phone.
