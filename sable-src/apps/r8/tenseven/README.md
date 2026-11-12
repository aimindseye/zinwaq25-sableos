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

The APK is platform-signed but not privileged (`platformapp`), as Sable Start was. The framework
overlay grants it notification listener access on first boot, so Hub fills without a setup step.

Not done yet, and still Sable Start features: live Active Frame pictures from the system task
snapshot (TenSeven uses screen-capture consent), and Sable Start's private-counts view. Sable
Start's source stays in `sable-src/src/android/packages/apps/SableStart` until the home is
qualified on a Q25. To go back to it, set the `SableHome` row's `enabled` column to `no`.

To move to a newer TenSeven, change `TENSEVEN_COMMIT` and `EXPECTED_COMMIT` together. Each edit
in `apply_sable_flavor.py` checks that its anchor occurs once, so the build stops if upstream moved it.
