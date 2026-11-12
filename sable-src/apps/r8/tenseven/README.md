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

The APK is platform-signed but not privileged (`platformapp`), as Sable Start was. The framework
overlay grants it notification listener access on first boot, so Hub fills without a setup step.

Active Frames on SableOS (owner, 2026-11-12) never ask for screen capture. The flavor gives the home
Sable Start's platform permissions, so frames follow the system's recent tasks and show the system task
snapshot (`SableTaskSnapshots.kt`, Android 17's TaskSnapshotManager, then TenSeven's older call);
`adb logcat -s SableFrames` says why a frame has no picture. Media frames come from every app's media
session through the notification listener that the framework overlay grants on first boot.

Sable Start's source stays in `sable-src/src/android/packages/apps/SableStart` until the home is
qualified on a Q25. To go back to it, set the `SableHome` row's `enabled` column to `no`.

To move to a newer TenSeven, change `TENSEVEN_COMMIT` and `EXPECTED_COMMIT` together. Each edit
in `apply_sable_flavor.py` checks that its anchor occurs once, so the build stops if upstream moved it.
