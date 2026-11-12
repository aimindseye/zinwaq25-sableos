# SableOS home: TenSeven Launcher flavor

Owner decision 2026-11-12: TenSeven Launcher is the SableOS 2.0 home and Hub, replacing Sable Start.
TenSeven was cut from Sable Start, keeps its intent contract and reads the Sable apps' providers, and
follows the approved BB10 design review (TenSeven PR 124, `docs/design/bb10-device-review`).

`scripts/build-apps.sh` builds the `SableHome` row of `product/q25/apps.tsv` from the pinned commit
in `upstream.env` (TenSeven PR 127), after `apply_sable_flavor.py` has changed:

- the package id to `org.sableos.launcher`, so the Soong import overrides `SableLauncher` and every
  framework patch, Setup step, Settings link and permission file that names Sable Start still works;
- the entry points TenSeven lacked: `org.sableos.start.action.COMMAND` (Alt+Space from any app,
  frameworks/base 0319) and `org.sableos.start.action.SET_LOOK` (SetupWizard 0417);
- the colours, to the approved tokens: blue `#2E9BD6`, black, raised `#141414`, dividers `#262626`.
- the widget picker's list, from a fixed 360 dp to at most 360 dp, so the dialog fits the Q25's
  411 dp square screen (about 383 dp under the status bar).

The flavor also removes the TenSeven code that SableOS does not ship (`patch_leftovers`), and
`verify_sable_tenseven.py` fails the build if any of it comes back:

- TenSeven Phone, its in-call screen, call screening and dialer-role prompt. Sable Phone is the Phone
  app; with an InCallService and DIAL activities in the home APK, Android counted the home as a Phone app.
- TenSeven Calendar (Sable Calendar). Hub still reads calendars.
- The bundled extras: Calc Plus, Notes, Sound Profiles, the Tasks launcher entry and ten games. Sable
  Calculator, Notes, Profiles, Tasks and Games cover them; the ringer button opens Sable Profiles.
- Screen capture for Active Frames, and the CALL_PHONE, full-screen intent, foreground service and
  WRITE_SETTINGS permissions only that code used.
- TenSeven's own setup opening over the home on first boot; SableOS Setup covers it, and it stays
  in Command and Launcher Settings.

TenSeven's Phone and Calendar are also what PR 127's quality ratchet flags (LargeClass), so none of
that code reaches SableOS. Hub reminders keep TenSeven's task store; the Tasks app itself is Sable Tasks.

Hub email (owner, 2026-11-12) comes from Sable Mail, not from TenSeven. `patch_hub_mail` adds
`SableMailFeed.kt`: the Hub reads the newest inbox messages of every Sable Mail account from
`content://org.sableos.mail.hub/messages` (sender, subject, preview, date, read), in memory only, and
re-reads when Sable Mail says the inboxes changed. While that feed answers, Sable Mail's own
notifications are left out of the Hub, so each email shows once. Enter on an email opens it in Sable
Mail (`org.sableos.mail.action.OPEN_MESSAGE`). Mail read in Sable Mail shows but is not counted as new, and marking an email read in the Hub marks
it read in Sable Mail too.

Back at an app's root (2.0 plan, BB10): Android moves the app's task back and brings the home forward
without a new intent. `patch_back_to_frames` adds `SableBackToFrames.kt`: as the home stops it notes
whether another app took the screen (REAL_GET_TASKS), and when it comes back for that reason it shows
Active Frames, as Home does. Returning from the home's own screens or from screen off changes nothing.

Hub over any app (2.0 plan, BB10's peek): `patch_hub_peek` adds `SableHubPeekActivity`, a panel over the
app in front with the newest conversations, unread first (`SableHubPeek`, unit-tested). Holding the
Recents key opens it (frameworks/base 0326), and so does Hub, the first row of the Alt+BlackBerry key
menu. Enter opens a conversation in its app, Open Hub shows the full Hub, and Back returns to the app.

Hub after the JL10 review (owner, 2026-11-12; ideas only, no JLauncher code or art): `patch_hub_keep`
adds `SableHubKeep.kt` (unit-tested in `SableHubKeepTest`). The Hub keeps what the notification shade
drops until you delete it, on the phone only (`noBackupFilesDir/sable_hub_kept.txt`, at most 1000 items;
ongoing notices and Sable Mail are not kept). The list has an Unread section, then Today, Yesterday and
dated sections, with "All accounts · N unread" under the title. Views adds Priority Hub, each app with
its unread count, and Email accounts…; the Hub bar gets a ⋮ menu (Hub settings, Email accounts,
Calendar accounts, Connected apps). The row menu adds Mark unread, Add to Priority Hub for the person and
for the app, and Delete. Hub settings add Keep messages, Every message on its own and System
notifications, and links to Sable Mail (email accounts) and DAVx⁵ (calendar accounts).

Home status (owner, 2026-11-12): `patch_status_icons` renames TenSeven's "Text" status choice to
Icons, so Launcher settings > Home status reads Dials, Icons, Hidden everywhere. The saved value stays
`text`, so a phone keeps its choice.

Extra dim (owner, 2026-11-12): the Q25 backlight has about 25 hardware steps and its lowest is still
bright, which only the kernel can change. `patch_extra_dim` adds `SableExtraDim` (unit-tested): with manual
brightness at the slider's bottom (0..255 value at or below 2) the home turns on Android's Extra dim
(`reduce_bright_colors_activated`, strength 50 when never set) and turns it off when the slider moves up.
Extra dim the person turned on themselves is left alone. Launcher settings > Home readability > Extra dim at
lowest brightness switches it off. The home holds WRITE_SECURE_SETTINGS for this (platform certificate).

Home access (owner, 2026-11-12): the home is part of SableOS, so `patch_home_access` adds
`SableHomeAccess` (unit-tested): on its first start the home grants itself Contacts, Calendar, Call log,
Photos and Notifications (`grantRuntimePermission`), notification access
(`setNotificationListenerAccessGranted`), usage access and display over other apps (`AppOpsManager.setMode`),
with the platform certificate's GRANT_RUNTIME_PERMISSIONS, MANAGE_NOTIFICATION_LISTENERS and
MANAGE_APP_OPS_MODES. Each item is tried until it is granted once; after that, an "off" from the person stays
off. Location is left to the person. Launcher settings drop the Active Frames page and its review card.

Cover API (2.0 plan, BB10 Active Frame covers): `patch_frame_covers` adds the
`content://org.sableos.launcher.covers` provider. An app says what its own Active Frame shows:

```kotlin
context.contentResolver.call(
    Uri.parse("content://org.sableos.launcher.covers"), "setCover", null,
    bundleOf("title" to "Messages", "detail" to "Anna: see you at six"),
)
// and "clearCover" with no extras to remove it
```

The cover always belongs to the calling package (Android's checked calling package, never a request
field), is kept in memory only, is bounded to a 60-character title and 240 characters of text, is hidden
while the phone is locked, and lapses after 24 hours unless set again. It shows on the app's frame ahead
of notification and media summaries. An app targeting Android 11 or later lists
`<provider android:authorities="org.sableos.launcher.covers" />` under `<queries>`.

The APK is platform-signed but not privileged (`platformapp`), as Sable Start was. The framework
overlay grants it notification listener access on first boot, so Hub fills without a setup step.

Active Frames on SableOS (owner, 2026-11-12) never ask for screen capture. The flavor gives the home
Sable Start's platform permissions, so frames follow the system's recent tasks and show the system task
snapshot (`SableTaskSnapshots.kt`, Android 17's TaskSnapshotManager, then TenSeven's older call);
`adb logcat -s SableFrames` says why a frame has no picture. Media frames come from every app's media
session through the notification listener that the framework overlay grants on first boot.
The name bar under each frame is slim (22 dp), so more of the app shows; X and Backspace close the
focused frame. The first frame refresh logs one `SableFrames` line with the snapshot permissions the home
holds and whether TaskSnapshotManager was found.

Sable Start's source stays in `sable-src/src/android/packages/apps/SableStart` until the home is
qualified on a Q25. To go back to it, set the `SableHome` row's `enabled` column to `no`.

To move to a newer TenSeven, change `TENSEVEN_COMMIT` and `EXPECTED_COMMIT` together. Each edit
in `apply_sable_flavor.py` checks that its anchor occurs once, so the build stops if upstream moved it.
