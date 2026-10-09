# KF-D: All Apps privacy row and responsive app polish

Package `kf-d`, branch `q25/kf-d`. Implements DESIGN-KF-D (KF-D-I in the plan):
`docs/design/ALL_APPS_PRIVACY_RESPONSIVE_POLISH.md` and its predecessor
`ALL_APPS_PRIVACY_UX.md` in `platform_sable`.

Status: the pure logic is written and unit tested off-device. The Android and
Compose code is **uncompiled**: Google Maven is blocked here, so neither Gradle
nor Soong ran. Nothing here has run on a device or an emulator, and none of the
visual-confirmation captures the design asks for exist yet.

No framework patch was needed, so none of the 0500-0599 range is used. The
frameworks/base sources checked for the AppOps and permission behaviour were
LineageOS `lineage-23.2` at `f4ed08a03b518772ba77c3c0a1b4185fa1712c9b` (read
only).

## Part 1: All Apps privacy/security row (Sable Start)

### What the row does now

Each All Apps row is `[icon] App name [work]` with one line under it:

```text
permissions · Location · Contacts · Nearby · +1      (at most 3 labels, whole labels only)
permissions · none sensitive                          (every requested group known and denied)
permissions · see app info                            (nothing allowed, but some state unreadable)
permissions · unavailable                             (package state could not be read)
⚠ permissions · Camera                                (special access as a badge with a spoken label)
```

- **Truthfulness.** `PrivacyEvaluator` shows a group only when the permission
  is granted in the app's own user, and its App-op, if it has one, is
  allowed/foreground/default. A requested-only permission shows nothing. If
  the op is ignored or errored, the grant is cancelled. If an op can't be read
  the group becomes `Unknown`: it is left out and never shown as access.
- **Partial access.** `Location approximate` is used only when precise
  location is known to be off. If location is certainly on but precision can't
  be read, the row says plain `Location`. `Photos limited` (Android 14+, the
  selected-photos grant only) is shown only when full photo access is known to
  be off.
- **Notifications.** On Android 13+ the POST_NOTIFICATIONS grant is the
  app-level notification switch, legacy apps included, so the grant is used.
  Its mere presence in the manifest is not enough. Before Android 13 a
  launcher can't read another app's switch, so the group is omitted.
- **Legacy storage.** READ_EXTERNAL_STORAGE counts as Photos and Audio only for
  apps that target API 32 or lower.
- **Groups.** There are 11 everyday groups, kept in design order. The 5
  special-access groups (Accessibility, Device admin, Install apps, Overlay,
  Usage access) go into a warning badge with a spoken label, plus the expanded
  detail. They appear inline only when there is nothing everyday to show.
- **Density.** The row shows at most 3 inline labels, folds the rest into
  `+N`, and is one line. Whole labels move into `+N` until the text, measured
  with the row's real width and font scale, fits. The view then ellipsizes,
  which the design allows. Raw permission names and package names never appear.
- **Current user and work profile.** Each app instance is read in its own user
  (`createContextAsUser`). It is cached by `(userId, package)`, never by
  package alone, and is never merged across profiles. Rows outside the
  launcher's user carry a `work`, `private` or `profile` badge that has a
  spoken label.
- **No blocking at row bind.** `LauncherAppsRepository.loadApps()` already runs
  on the inventory executor and attaches the snapshot to `AppEntry.privacy`.
  Rows only format it. The in-memory `PrivacySnapshotCache` is invalidated
  - per package by LauncherApps add/change/remove/available/unavailable,
  - per uid by `PackageManager.OnPermissionsChangedListener`,
  - per user by the managed/private profile broadcasts,
  - and by a 60 s max age, for App-op-only changes such as overlay or usage
    access that have no signal a launcher can hear.

  Launcher icons, which used to be a LauncherApps binder call inside
  composition, now load on `Dispatchers.IO` into a process LRU cache.
- **No permission database.** Nothing is persisted.
- **Keyboard** (`AllAppsKeyPolicy`):
  - Up/Down use platform focus traversal.
  - Enter opens the app.
  - Space expands the privacy detail inline.
  - Menu or Fn+Enter opens the actions (Peek).
  - `/` or Search opens search.
  - Letters and digits type-to-jump: a multi-letter prefix, a repeated letter
    cycles through matches, and with no match the jump goes to the next
    alphabet section.
  - Held keys don't repeat one-shot actions.
  - Ctrl/Meta shortcuts and text fields pass through.

  Every row has a visible focus ring, and actions also open from the `›`
  button and by long-press. No action needs touch.
- **Actions** (`AppActionPolicy`, shown in Peek): open, app info +
  permissions, notification settings (launcher user only), pin to/remove from
  Start, and uninstall (removable apps in the launcher user only).
  - Uninstall first arms, then needs `confirm uninstall`; the system
    uninstaller then confirms again.
  - Add to base bar exists in the policy but is not offered, because this Sable
    Start source has no base-bar surface.
  - Disable is not offered, because the launcher holds no
    component-enable permission. App info covers it.
- **Search.** Name matches come first, then apps whose *effective* access
  matches privacy words: `camera`, `mic`, `location`, `bluetooth`, ...
- **One alphabet index.** All Apps used to have a hidden 22 dp drag strip on
  the left as well as the visible rail on the right. Now section headers, the
  right rail (only letters that exist) and keyboard letters all come from one
  `SableAlphabetIndex`.
- **Focus restoration.** Coming back from Peek, App info or Search refocuses
  the same app, or the app now at its old position if it was removed. This
  happens once per visit, so inventory refreshes never move focus.
- **Peek** shows the full privacy detail ("App info has the full permission
  detail."). The `open` button gets focus first.

### SableLauncher manifest additions

```xml
<uses-permission android:name="android.permission.INTERACT_ACROSS_USERS" />          <!-- read work-profile apps' own state -->
<uses-permission android:name="android.permission.OBSERVE_GRANT_REVOKE_PERMISSIONS" /> <!-- permission-change signal -->
<uses-permission android:name="android.permission.REQUEST_DELETE_PACKAGES" />        <!-- hand off to system uninstaller -->
```

SableLauncher is `certificate: "platform"`, which satisfies the first two
(`signature|privileged|...`), so no privapp allowlist entry is needed.
`REQUEST_DELETE_PACKAGES` is a normal permission. Without INTERACT_ACROSS_USERS,
work-profile rows would read `permissions · unavailable`, which is safe but
less useful.

## Part 2: responsive polish (shared design system + apps)

Pure rules live in `sable-src/src/android/shared/sabledesign`, which Soong
(`sable_design_shared_srcs`) and the Gradle `:design` module both compile. The
Compose renderers sit next to them.

| Rule | Pure policy | Compose block |
|---|---|---|
| Breakpoints, font scale | `SableLayoutMetrics` (compact under 600 dp, or text width = width/fontScale under 420 dp; square ratio; large font at 1.3 or more) | `rememberSableLayoutMetrics()` |
| No tab clipping; at most 3 compact destinations | `SableNavigationPolicy.layout` (fits measured labels, otherwise N + "more", and the selected destination always stays visible) | `SableAdaptiveTopNav` (one-line labels, keyboard-reachable "more" dropdown, `Role.Tab`) |
| Dense rows | `SableDenseRowPolicy.split` (1 primary trailing on compact and at large font; destructive actions never trailing on compact; nothing lost) | `SableDenseRow` (title 2 lines, subtitle 1 line, 48 dp targets, "⋯" menu also on the Menu key, focus ring) |
| Alphabet index | `SableAlphabetIndex` (only existing sections, accent folding, `#`, pinned-favourites section, keyboard jump to letter or next section, touch slot) and `SableTypeToJump` | `SableAlphabetRail` (never takes focus) and `Modifier.sableLetterJump` |
| Mini-player | `SableMiniPlayerPolicy` (never blank; always has a state label; labelled play/pause; progress only when the duration is known and not live; hidden when idle) | `SableMiniPlayer` (secondary actions in a menu) |
| Latest/current content | `SableLatestContentPolicy` | `SableReturnToCurrent` |
| Long labels | `SableLabelPolicy` (word-boundary ellipsis, full label for a11y) | text uses `maxLines` + `Ellipsis` |
| Focus restoration | `SableFocusMemory` | `Modifier.sableFocusRing()` (hover never moves focus) |

`SableListRow` now uses a minimum height instead of a fixed 58 dp, so large
font scales grow the row instead of clipping it. Its title allows 2 lines and
it has a focus ring.

### Adoption

- **Sable Media**
  - Top destinations (music, podcasts, radio, now playing) use
    `SableAdaptiveTopNav`, so a compact screen shows 2 plus "more" instead of
    a horizontally scrolling row. The library pivots (5) and podcast pivots
    (3) work the same way.
  - The mini-player is `SableMiniPlayer`. It is pinned below the list in
    Collection, Podcasts and Radio, so the current state is visible on open.
    It has a play/pause toggle, a state label and progress, with previous,
    next and up next in its menu.
  - Track, station, playlist, podcast-episode and queue rows are
    `SableDenseRow`: play stays visible and favourite, queue, save, edit,
    move and remove go to the row menu.
  - Removing a playlist now asks for confirmation.
  - The collection A–Z uses `SableAlphabetIndex`, with the rail plus keyboard
    letter jump.
- **Sable Hub**
  - The pivot row (priority, messages, email, people) and the Connected Apps
    pivots use `SableAdaptiveTopNav`.
  - The People index uses `SableAlphabetIndex`: starred contacts form one
    section and the rail lists only letters that exist. Before, all 26 letters
    were shown and the dead ones were disabled. Keyboard letters jump.
  - In a conversation, newest messages are visible on open, and a "latest"
    button appears when history is scrolled.
  - Back from a conversation returns to the pivot it was opened from (it used
    to always go to Messages), and Compose returns to where it came from. The
    Messages list restores focus to the opened conversation.
- **Sable Calendar.** The mode pivot uses `SableAdaptiveTopNav`. Agenda
  already opens on today.
- **Sable Messages** already has a latest-first interaction model with tests,
  so it is unchanged. **Weather** has no tab row or indexed list, so it is
  unchanged too.

## File map

```text
sable-src/src/android/packages/apps/SableStart/
  Android.bp                                        presentation srcs += privacy/*, PrivacyFactsReader
  AndroidManifest.xml                               3 permissions (above)
  src/com/sable/start/privacy/PrivacyModel.kt       groups, facts, PrivacyEvaluator            (pure)
  src/com/sable/start/privacy/PrivacySummaryPolicy.kt  compact row, detail, search terms       (pure)
  src/com/sable/start/privacy/PrivacySnapshotCache.kt  (user, package) cache + invalidation    (pure)
  src/com/sable/start/privacy/AllAppsInteraction.kt    keys, app actions                       (pure)
  src/com/sable/start/platform/PrivacyFactsReader.kt   PackageManager/AppOps/a11y/DPM reader  (uncompiled)
  src/com/sable/start/platform/LauncherAppsRepository.kt  snapshot attach, invalidation, uninstall, notif settings
  src/com/sable/start/model/AppEntry.kt             privacy, profileLabel, canUninstall (privacySummary removed)
  src/com/sable/start/SableStartActivity.kt         permission listener, profile receiver, new callbacks
  src/com/sable/start/ui/SableStartScreen.kt        All Apps, row, Peek, search, async icons (uncompiled)
sable-src/src/android/shared/sabledesign/src/main/java/org/sableos/design/
  SableResponsive.kt  SableAlphabetIndex.kt  SableMiniPlayerPolicy.kt               (pure)
  SableResponsiveComponents.kt                                                   (Compose, uncompiled)
  SableComponents.kt                                                             (SableListRow min height)
sable-src/apps/r8/android/
  design/src/test/.../SableResponsivePolicyTest.kt, SableAlphabetAndMiniPlayerTest.kt, SableDesignContractTest.kt
  sablestart-presentation-check/build.gradle.kts    syncs the new sources; testImplementation junit
  sablestart-presentation-check/src/test/.../privacy/*Test.kt
  sablestart-visual-review/.../R9LauncherVisualReviewActivity.kt   uses PrivacyFactsReader; privacyFixtures extra
  media/.../MediaResponsive.kt (new), MediaScreen.kt, PodcastScreen.kt, MediaC2Screens.kt
  hub/.../ui/HubScreen.kt, ui/ConnectedAppsScreen.kt
  calendar/.../MainActivity.kt
```

## Acceptance keys

| Key | Covered by | Still needs |
|---|---|---|
| ALL_APPS_EFFECTIVE_PRIVACY_SUMMARY | `PrivacyEvaluatorTest` (grant + op, unknown omitted, partial, notifications, legacy storage, special access) | device check of real AppOps modes |
| ALL_APPS_CURRENT_USER_SCOPED | reader uses the app's user context; `PrivacyCacheAndInteractionTest` | device with a second profile |
| ALL_APPS_RAW_PERMISSION_NAMES=ABSENT | `PrivacySummaryPolicyTest.rowTextNeverContainsRawPermissionNames`, `groupListMatchesDesign` | — |
| ALL_APPS_PERMISSION_DB=ABSENT | in-memory cache only (no file, DB or prefs written) | — |
| ALL_APPS_MAX_INLINE_PERMISSION_LABELS=3 | `PrivacySummaryPolicyTest` (cap, +N, width folding) | capture |
| ALL_APPS_WORK_PROFILE_SEPARATION | per-(user, package) keys test; work badge | device with work profile |
| ALL_APPS_ROW_BIND_BLOCKING_QUERY=ABSENT | snapshot on inventory thread; icons on IO | profiling on device |
| RESPONSIVE_TAB_CLIPPING=ABSENT | `SableResponsivePolicyTest` (4 to "more", selected visible, large-font slot fit) | capture |
| MEDIA_MINI_PLAYER_MEANINGFUL | `SableAlphabetAndMiniPlayerTest` mini-player cases | capture |
| DENSE_ROW_ACTION_OVERFLOW | `SableResponsivePolicyTest` dense-row cases | capture |
| CONTACTS_DUPLICATE_ALPHABET_RAIL=ABSENT | single index in Hub People and All Apps (left strip removed) | capture |
| VISIBLE_ALPHABET_RAIL_FUNCTIONAL | `railShowsOnlySectionsThatExist`, pinned section test | capture |
| FOCUS_RESTORATION | `SableFocusMemory` test; wired in All Apps and Hub Messages | device keyboard run |
| FONT_SCALE_COMPACT_LAYOUT | text-width metrics, nav fit test, measured privacy line, min-height rows | capture at font scale 1.3–2.0 |

Visual confirmation (design list) still needed on an emulator or device. The
review APK can show the All Apps states with
`--ez privacyFixtures true --es initialScreen apps`: no sensitive, 1, 3, more
than 3, special access, partial, unknown, unreadable, and a work-profile
duplicate with a different state. The Media, Hub and Calendar captures need
the apps themselves.

## Tests

The pure tests run with the kotlinc + JUnit shim (52 tests: 31 Start privacy
and 21 design responsive):

```bash
K=<scratch>/kotlinc-dir-with-shim; W=sable-src
kotlinc \
  $W/src/android/packages/apps/SableStart/src/com/sable/start/privacy/*.kt \
  $W/apps/r8/android/sablestart-presentation-check/src/test/java/org/sableos/start/privacy/*.kt \
  $W/src/android/shared/sabledesign/src/main/java/org/sableos/design/{SableResponsive,SableAlphabetIndex,SableMiniPlayerPolicy}.kt \
  $W/apps/r8/android/design/src/test/java/org/sableos/design/{SableResponsivePolicyTest,SableAlphabetAndMiniPlayerTest}.kt \
  $K/shim/Shim.kt -d /tmp/kf-d-out
java -cp /tmp/kf-d-out:$K/kotlinc/lib/kotlin-stdlib.jar org.junit.Run \
  org.sableos.start.privacy.PrivacyEvaluatorTest org.sableos.start.privacy.PrivacySummaryPolicyTest \
  org.sableos.start.privacy.PrivacyCacheAndInteractionTest \
  org.sableos.design.SableResponsivePolicyTest org.sableos.design.SableAlphabetAndMiniPlayerTest
# RAN=52 FAILED=0
```

The existing `reference/sable-start-type-to-find` suite is unchanged and still
passes (77 tests, run from `sable-src/`). `bash tests/run.sh` passes. With
Gradle and network the same tests run as `:design:testDebugUnitTest` and
`:sablestart-presentation-check:testDebugUnitTest`. The second one also syncs
and compiles the canonical Start sources, which checks the uncompiled Compose
code.

## Integration

- No `product/q25/apps.tsv` or `scripts/stage-product.sh` change is needed.
  Stage Q4 already copies `SableStart` and `sabledesign` whole. The new files
  are listed in `SableStart/Android.bp`, and the design filegroup globs
  `**/*.kt`.
- Regenerate `patches/sable-src/*.patch` from this branch as usual.
- Suggested QUALIFICATION gate (not added): `Q4-ALLAPPS-PRIVACY`. On device
  with a work profile:
  - capture the design's 11 visual-confirmation states;
  - check that toggling a runtime permission or precise location in Settings
    updates the row on return;
  - check that keyboard Enter, Space, Fn+Enter, `/` and letters behave as
    listed;
  - check that the uninstall flow needs two confirmations.
- First compile risk: `SableStartScreen.kt`, `SableResponsiveComponents.kt`,
  `MediaScreen.kt` and `HubScreen.kt` were edited without a compiler. Run
  `:sablestart-presentation-check:compileDebugKotlin`, `:media`, `:hub` and
  `:calendar` assemble first.
