# KF-A: notification policy, attention and Hub ownership

Implements DESIGN-KF-A (`platform_sable/docs/design/NOTIFICATION_POLICY_ATTENTION_HUB_OWNERSHIP.md`)
for all Sable devices, in common Sable Hub source plus two small LineageOS framework patches.
Device differences come only from the device profile (`ro.sable.profile.id` and the new
`ro.sable.attention.outputs` declaration); common code does not branch on device model.

Nothing here has run on a device, and none of the Android code (Hub activities, listener,
providers, Compose UI, SystemUI and Settings patches) has been compiled: Google Maven is blocked
in this environment and there is no android.jar or LineageOS tree. The pure Kotlin policy code
and its tests were compiled and run with kotlinc (see Test commands).

## What was implemented

**Ownership model.** Android NotificationManager/NotificationChannel stay the only delivery
policy. `policy/NotificationOwnership.kt` labels every notification concept as Android-, Hub- or
Attention-owned. A test maps every persisted field of Hub's `ConnectedAppPolicy` to a Hub-owned
concept and rejects delivery-like field names (importance, sound, vibration, heads-up, badge,
lockscreen, snooze, DND, channel). Every Android-owned concept resolves to an Android Settings
deep link (`policy/AndroidSettingsRoutes.kt`), with fallbacks that always exist.

**No delivery or DND ownership in Hub.** The listener only reads. `tests/kf-a-notification-policy-check.sh`
(run by `tests/run.sh`) fails if Hub code calls cancel/snooze/notify/channel/interruption-filter
APIs or its manifest asks for a notification-policy permission. Hub priority (`favorite`) is now
labelled and documented as ordering only and is not an input to attention decisions.

**Connected-apps parity with notification access.** `policy/ConnectedAppsParity.kt`: when Android
notification access for Hub's listener is off, the Sable Start hidden-apps provider
(`content://org.sableos.hub.connected_apps/hidden`) returns nothing, so no app vanishes from Start
while Hub cannot show it. Policies are kept, so restoring access restores the choices. The listener
notifies the hidden URI on connect and disconnect. Each connected app shows its state (In Hub /
Paused: notification access off / Paused: profile locked). "Enable notification access" opens
Hub's own listener detail page.

**RemoteInput-safe replies.** `policy/ReplyEligibility.kt`: only a source-published action with a
PendingIntent and a free-form RemoteInput counts. System-generated contextual (smart) actions are
skipped, and semantic REPLY is preferred. Sending a reply needs the device and the source profile
unlocked, and marks the result as `SOURCE_FREE_FORM_INPUT`. The reply registry is cleared when the
listener disconnects.

**Bounded derived history.** `policy/HubHistoryBounds.kt` replaces the store's own pruning. Only
included sources are kept, of profiles that still exist. Retention is per source (1, 7 or 30 days,
with a hard 30-day cap), at most 300 records per source and 1,200 in total, and future timestamps
are dropped. The unbounded "Until deleted" option is gone; stored v1/v2 records that name it decode
to 30 days.

**Attention capability model.** `policy/AttentionCapabilities.kt` covers six outputs (audio,
haptic, status LED, keyboard backlight, secondary display, AOD). Each has an owner: Android channel,
Android platform or Sable. Evidence per output is Absent, Candidate or Validated.
- Sable-relevant outputs come only from `ro.sable.attention.outputs=<profile-id>:<output>=<evidence>,...`.
  A declaration for another profile id is ignored, so families never inherit PASS. A malformed or
  missing declaration fails closed.
- Audio and haptic follow Android's hardware facts.
- Only Validated outputs are visible. Candidates are hidden, not shown disabled.

Defaults: the keyboard backlight is off; the secondary display is profile- and privacy-gated; the
status LED and AOD use the platform default. `policy/AttentionPolicy.kt` emits a Sable output only
when all of these hold:
- the user selected it;
- Android importance is DEFAULT or higher;
- `Ranking.matchesInterruptionFilter()` is true, so DND is never bypassed;
- it is not an alert-once update;
- privacy leaves something to show.

Patterns are bounded by construction (at most 3 pulses and 6 s). The keyboard backlight never
carries content. Android-owned outputs are delegated, never driven. `notifications/AttentionDispatcher.kt`
wires this to the listener's Ranking. No output driver (sink) ships, because no Sable profile
has a validated Sable-driven output yet.

**Lockscreen, private mode, work profile.** `policy/PrivacyPosture.kt` encodes the design's
privacy table. Hub levels are Full, SenderOnly, SourceAndCount, GenericProfile and Hidden.
Attention levels are Full, CategoryCount, Generic and None. The inputs, all read and never written:
- Android lock state;
- `lock_screen_show_notifications` / `lock_screen_allow_private_notifications` (conservative if
  unreadable);
- the effective visibility (the more restrictive of the notification's and the channel's);
- profile kind and lock (work paused or locked, private space locked);
- Hub private mode, a new switch in Connected apps.

`policy/HubRedaction.kt` applies this to the snapshot before any screen sees it:
- A locked work profile shows "Work profile" with no text and no reply.
- A locked private space disappears.
- Orphan messages are dropped.

Work rows carry a "Work" badge. Notifications marked SECRET by the source or its channel are
cached without title or text ("source restricted"). Keys stay Android user serial + package; none
is package-only.

**Hub preview preference.** `ConnectedAppPolicy.previewPolicy`: sender and message, sender only,
or app and count only (codec v3; v1/v2 still decode). It narrows list previews and never widens
privacy.

**Settings IA handoff.**
- Hub: each connected app has "Delivery: Android notification settings". Opened conversations
  have a "Notifications" button for the source's Android page.
- New `AttentionSettingsActivity` (Sable Attention) lists only validated outputs. Android-owned
  outputs and DND, history and conversations link to Android Settings.
- Settings patch 0300 adds Settings > Notifications > Sable Attention and, on each app's
  notification page, "Sable Hub and Attention". That link carries package + uid, so work-profile
  apps keep their own entry. Both are hidden when Hub is absent. `ConnectedAppsActivity` handles
  that intent and shows just that app.

**Keyboard notification commands (shade).** SystemUI patch 0301 adds a pure
`SableNotificationKeyRouter` and `SableNotificationKeyCommands`, and hooks them into
`ExpandableNotificationRow.onKeyDown/onKeyUp`. On a focused row:

| Key | Action |
| --- | --- |
| Space | Expand/collapse (no longer opens the notification) |
| R | Source RemoteInput button (SystemUI's own RemoteInput flow, keyguard auth included) |
| D | Dismiss, if dismissible |
| Z | Snooze menu |
| M | Delivery options (Android's NotificationInfo guts) |
| C | Android channel/conversation settings |
| H | `org.sableos.hub.action.OPEN_NOTIFICATION` |

C and H go through `NotificationActivityStarter`, which dismisses the keyguard first. Arrows,
Enter and Back keep their existing paths. Commands act only when the row itself has focus, never
with a modifier held. A focused text editor always passes through. Unavailable or repeated letters
are swallowed, not passed on. For H, Hub decides eligibility: an excluded source opens its
Connected apps entry, so Hub never includes a source silently (`policy/HubHandoffRouter.kt`).

## File map

Hub (`sable-src/apps/r8/android/hub/src/main/java/org/sableos/hub/`):

| File | Kind |
| --- | --- |
| `policy/NotificationOwnership.kt`, `AndroidSettingsRoutes.kt`, `AttentionCapabilities.kt`, `AttentionPolicy.kt`, `AttentionSelection.kt`, `PrivacyPosture.kt`, `HubRedaction.kt`, `HubHistoryBounds.kt`, `ReplyEligibility.kt`, `ConnectedAppsParity.kt`, `HubHandoffRouter.kt`, `HubIntents.kt` | pure Kotlin, unit tested |
| `ConnectedAppPolicy.kt` (bounded retention, preview policy, codec v3), `HubModels.kt` (`profileBadge`, `hubPriority`) | pure, tested |
| `platform/ProfileDirectory.kt`, `PrivacyReader.kt`, `DeviceAttention.kt`, `AndroidSettingsLauncher.kt`, `SysProps.kt` | Android, uncompiled |
| `notifications/AttentionDispatcher.kt`, `SableNotificationListenerService.kt`, `ConnectedReplyRegistry.kt` | Android, uncompiled |
| `ConnectedAppsPolicyProvider.kt`, `ConnectedNotificationHistoryStore.kt`, `ConnectedAppsInventory.kt`, `ConnectedAppsRepository.kt`, `HubPreferences.kt`, `HubRepository.kt` | Android, uncompiled |
| `MainActivity.kt`, `ConnectedAppsActivity.kt`, `AttentionSettingsActivity.kt`, `ui/HubScreen.kt`, `ui/ConnectedAppsScreen.kt`, `ui/AttentionSettingsScreen.kt`, `AndroidManifest.xml` | Android/Compose, uncompiled |

Other files:
- Tests: `hub/src/test/java/org/sableos/hub/policy/*Test.kt` (11 files), plus updates to
  `ConnectedAppPolicyTest.kt`.
- `sable-src/apps/r8/android/config/detekt.yml`: FunctionNaming exclude for the new Compose file,
  as for the other Hub screens.
- `patches/framework/packages/apps/Settings/0300-SableOS-Sable-Attention-and-per-app-Sable-Hub-entrie.patch`
- `patches/framework/frameworks/base/0301-SableOS-keyboard-notification-commands-in-the-shade.patch`
- `tests/kf-a-notification-policy-check.sh` (called from `tests/run.sh`).
- Rows added to the `patches/README.md` table.

## Framework patch bases (lineage-23.2)

| Patch | Project | Base commit |
| --- | --- | --- |
| 0300 | packages/apps/Settings | `8d8f6486b274bcf0aa6e5d0cbba52c0b05ae5c65` |
| 0301 | frameworks/base | `f4ed08a03b518772ba77c3c0a1b4185fa1712c9b` |

Both pass `git apply --check` on their base. Neither was built.

## Acceptance keys

| Gate | Covered by | Needs device |
| --- | --- | --- |
| NOTIFICATION_POLICY_ANDROID_OWNED | `NotificationOwnershipTest`, `AndroidSettingsRoutesTest`, static check | Settings deep links open the right pages |
| DUPLICATE_NOTIFICATION_POLICY_STORE=PASS_ABSENT | `NotificationOwnershipTest` (field reflection) | - |
| HUB_DELIVERY_POLICY_OWNERSHIP=PASS_ABSENT | static check (APIs, permissions) | - |
| HUB_CONNECTED_APPS_PARITY | `ConnectedAppsParityTest`, static check | revoke access, confirm Start shows hidden apps again |
| HUB_REMOTEINPUT_PROVIDER_SAFE | `ReplyEligibilityTest`, static check | reply against real providers (Signal, WhatsApp...) |
| HUB_DND_BYPASS=PASS_ABSENT | `AttentionPolicyTest.doNotDisturbIsNeverBypassed`, static check | - |
| ATTENTION_CAPABILITY_GATING | `AttentionCapabilitiesTest`, `AttentionPolicyTest` | per-profile validation of each output (none validated yet) |
| ATTENTION_LOCKSCREEN_REDACTION | `PrivacyPostureTest`, `AttentionPolicyTest` | needs a validated output and driver |
| WORK_PROFILE_SEPARATION | `HubRedactionTest`, `PrivacyPostureTest`, `ProfileKindTest` | work profile and private space on device |
| SHADE_IS_NOTIFICATION_CENTER | ownership test (history is Android's), static check | - |
| HUB_HISTORY_BOUNDED_DERIVED | `HubHistoryBoundsTest`, static check | - |
| KEYBOARD_NOTIFICATION_COMMANDS_CONTEXTUAL | `SableNotificationKeyRouterTest` (in patch 0301), static check | key handling in the real shade |
| TEXT_INPUT_ALWAYS_WINS | `SableNotificationKeyRouterTest.textInputAlwaysWins` | inline reply typing on device |

## Test commands

Hub pure tests (75 tests, all pass). kotlinc and the JUnit shim are as in the shared brief; the shim
copy adds `@Test(expected)`, `@Before`/`@After` and `assertNotEquals`, which the existing Hub tests
use.

```bash
K=<scratchpad>; H=sable-src/apps/r8/android/hub; M=$H/src/main/java/org/sableos/hub
# CONNECTED_LOCAL_REPLY_PREFIX lives in the (Android) history store; a one-line stub provides it.
$K/kotlinc/bin/kotlinc $M/ConnectedAppPolicy.kt $M/ConnectedNotificationModels.kt $M/HubModels.kt \
  $M/notifications/ConnectedNotificationActivityRegistry.kt $M/policy/*.kt prefix-stub.kt \
  $(find $H/src/test -name '*.kt') Shim.kt -d /tmp/kf-a-out
java -cp /tmp/kf-a-out:$K/kotlinc/lib/kotlin-stdlib.jar org.junit.Run <test classes>
```

Patch 0301 router test (8 tests, all pass): compile `SableNotificationKeyRouter.kt` and
`SableNotificationKeyRouterTest.kt` from the patched tree with stubs for `SysuiTestCase`,
`SmallTest`, `RunWith` and `AndroidJUnit4`. On a LineageOS tree:
`atest SystemUITests:SableNotificationKeyRouterTest`.

Repository static checks: `bash tests/run.sh` (includes the KF-A gates).

## Left for the device / later

- Build Hub with Gradle and the framework patches in a LineageOS tree; fix any compile issues
  (lint may also flag `LauncherApps.getLauncherUserInfo`, a FlaggedApi, guarded by an SDK check).
- No attention output driver ships. When a profile validates one (for example a Titan 2 rear
  SubScreen glance), add an `AttentionSink` and declare it in that profile's
  `ro.sable.attention.outputs`.
- `lock_screen_*` secure settings may not be readable by Hub; Hub then assumes "show, but no
  private content". If that is too conservative, grant Hub read access or pass the state through
  a platform API.
- Runtime checks: shade keys on a focused row, inline reply typing, Settings entries, Hub
  handoff from H, revoked notification access, work profile pause, private space lock.

## Integration

1. Merge branch `q25/kf-a`. No `apps.tsv` or stage change is needed (Hub is already a row; the
   framework patches are picked up by `apply-framework-patches.sh` at Q4).
2. Proposed device-profile property, to add to `product/q25/sable-q25.mk` next to
   `ro.sable.profile.id`. It is optional, because omitting it has the same fail-closed effect. Q25
   values stay unvalidated:
   ```make
   PRODUCT_SYSTEM_EXT_PROPERTIES += \
       ro.sable.attention.outputs=zinwa-q25:status_led=candidate,keyboard_backlight=candidate,secondary_display=absent,aod=absent
   ```
3. Proposed default (Sable posture, "locked: redacted by default"): ship
   `lock_screen_allow_private_notifications=0` through the Settings provider defaults overlay.
   This is not done here.
4. Proposed QUALIFICATION gates: KF-A-SHADE-KEYS, KF-A-HUB-PARITY, KF-A-WORK-PROFILE,
   KF-A-SETTINGS-LINKS (the device checks listed above).
