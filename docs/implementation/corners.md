# Corners: Sable app corner style as a user setting

Package `corners` resolves the open KF-B item "Sable app corner shapes". KF-B
left Sable app shapes at 2-6dp while the SystemUI design
(`SYSTEMUI_VISUAL_CONVERGENCE_CONTRACT.md`) uses 8dp controls and 12dp cards.
The owner decided that the corner style is a user setting in the App display
screen, not a fixed restyle.

Status: **written and statically checked, not built, not run on a device.**
The pure logic is compiled and unit-tested on the host. The Compose code was
type-checked against the jar-based Compose/Android stubs in the scratchpad
`cc` setup, which is not an Android build. The Java in Settings patch 0103 is
**uncompiled** against the Android SDK; only its pure policy class was compiled
and tested.

## What the user sees

App display compatibility (Settings > Display > App display compatibility,
also linked from Sable Tools) gets a **Sable app style** row above the filter
field:

* Two options, **Compact** and **Rounded**. Each option is drawn with its own
  corner shape as a preview. The line under them says what the selected style
  does ("8dp controls, 12dp cards and sheets, like Quick Settings").
* **Compact** is the default: the original 2-6dp Sable app shapes. Nothing
  changes until the user picks Rounded.
* Keyboard: Up from the first app selects the row (2dp accent border, as for
  app rows). Left/Right choose a style (they stop at the ends), Enter switches
  to the other one, Down returns to the app list. Left/Right reach the filter
  field again as soon as the row is not selected, so typing still wins. The
  footer shows the keys for whichever part is selected. Pointer and touch can
  click an option.
* After a change the status line says "Sable apps now use Rounded corners." A
  refused write says "could not be saved" in the warning tint, using the
  screen's existing message convention.
* On a build without the Settings authority (before release Q4), the row shows
  Compact, is read-only, and says the setting needs the SableOS appearance
  service in Settings.

The row follows changes made elsewhere through a content observer.

## One source of truth

| Piece | What it does |
|---|---|
| `org.sableos.appearance` (Settings, patch 0103) | New column `corner_style` (`compact`, `rounded`), stored next to the accent in Settings' device-protected prefs. The default is `compact`. A change notifies `content://org.sableos.appearance/appearance`. |
| Writers | Each column is checked against its own writers. `mode`/`accent`: Settings and `org.sableos.launcher`, as before. `corner_style`: Settings and `org.sableos.titan2.displaycompat`, but only when DisplayCompat is the copy on the system image (`FLAG_SYSTEM` set, `FLAG_UPDATED_SYSTEM_APP` clear). DisplayCompat can't write the accent, and Sable Start can't write the corner style. |
| `SableGlobalAppearance.kt` (shared, reader copy) | Reads `corner_style` into `SableAppearance.cornerStyle`. A missing column reads as Compact, so an older provider still works. `writeGlobalSableAppearance` still writes only mode and accent, so Sable Start's "reset appearance" leaves the corner style alone. The new `writeGlobalSableCornerStyle` is there for Settings-side or future callers. |
| `SableCornerStyle.kt` (new, pure; shared and reader copy) | `SableCornerStyle` codec and `SableShapeTable`: Compact = 2/3/4/6/6 dp, Rounded = 8/8/12/12/12 dp for extraSmall/small/medium/large/extraLarge. |
| `SableTheme.kt` (shared, reader copy) | `sableShapes(style)` builds Material `Shapes` from the table, and `SableTheme` uses the shapes for `appearance.cornerStyle`. `SableGlobalTheme` and `rememberGlobalSableAppearance` already observe the authority, so open apps restyle live, the same way the accent does. |
| `SableDesignContract` | `MAX_COMPACT_CORNER_RADIUS_DP = 6`, `MAX_ROUNDED_CORNER_RADIUS_DP = 12`, and `MAX_STANDARD_CORNER_RADIUS_DP = MAX_ROUNDED_CORNER_RADIUS_DP` (the bound over the two allowed styles). New key name `KEY_CORNER_STYLE`. |

### Why a package check and not a signature permission

The provider already used a package-name list (`org.sableos.launcher`), not a
permission. A `signature` permission defined by Settings is granted only to
apps signed with Settings' certificate, which is the platform key. DisplayCompat
is staged as `android_app_import` with `default_dev_cert: true` and
`privileged: false` (`product/q25/apps.tsv`, signing `unsigned`;
`scripts/stage-product.sh`). So neither `signature` nor `signature|privileged`
could be granted to it. The write is limited instead to the DisplayCompat
package as installed on the system image. A sideloaded update signed with the
same (default dev) key would set `FLAG_UPDATED_SYSTEM_APP` and be refused. Its
reach is also only the corner style. If DisplayCompat is ever platform-signed,
the check can become a signature permission.

## Where the style applies

Every Sable app that themes through `SableTheme`/`SableGlobalTheme` and draws
shapes through `MaterialTheme.shapes` (the shared `SableComponents` and
`SableResponsiveComponents` cards, rows and focus borders) follows the setting.
These are Sable Start, Hub, Media, Calculator, Weather, Messages, Games,
Calendar, the Reader (own design copy) and the Text Reader flavor (it copies
`SableCornerStyle.kt` too).

These keep their own corners (listed in `SABLEOS_GAP_REVIEW.md` known limits):

* Views that hard-code `RoundedCornerShape(N.dp)`: parts of Sable Start, Reader,
  Hub, Calendar and Messages.
* The keyboard-first platform apps with their own palette: Camera,
  DisplayCompat and Tools.
* The Sable Mail flavor, which has its own appearance reader.

Moving those to theme shapes is per-app follow-up work.

## Files

```text
sable-src/src/android/shared/sabledesign/src/main/java/org/sableos/design/SableCornerStyle.kt        new, pure
sable-src/src/android/shared/sabledesign/src/main/java/org/sableos/design/SableTheme.kt              shapes from the style; contract maxima
sable-src/src/android/shared/sabledesign/src/main/java/org/sableos/design/SableGlobalAppearance.kt   corner_style column, reader, writer
sable-src/src/android/shared/sabledesign/src/test/java/org/sableos/design/SableSystemTokensTest.kt   +1 test (Rounded = token radii)
sable-src/apps/r8/android/design/src/test/java/org/sableos/design/SableDesignContractTest.kt         two-style corner rule, +3 tests
sable-src/apps/common/reader/leisure/src/main/java/org/sableos/design/SableCornerStyle.kt            new, copy
sable-src/apps/common/reader/leisure/src/main/java/org/sableos/design/{SableTheme,SableGlobalAppearance}.kt  same changes
sable-src/apps/r8/textreader/apply_sable_flavor.py                                                   copies SableCornerStyle.kt
sable-src/apps/titan2/platform/displaycompat/src/main/java/org/sableos/titan2/displaycompat/core/AppStyle.kt          new, pure
sable-src/apps/titan2/platform/displaycompat/src/main/java/org/sableos/titan2/displaycompat/android/AppearanceStyleStore.kt  new
sable-src/apps/titan2/platform/displaycompat/src/main/java/org/sableos/titan2/displaycompat/android/DisplayCompatApp.kt      wires the controller
sable-src/apps/titan2/platform/displaycompat/src/main/java/org/sableos/titan2/displaycompat/ui/AppListScreen.kt              Sable app style row
sable-src/apps/titan2/platform/displaycompat/src/main/AndroidManifest.xml                           <queries> provider
sable-src/apps/titan2/platform/displaycompat/src/test/java/org/sableos/titan2/displaycompat/core/AppStyleTest.kt              new, 8 tests
patches/framework/packages/apps/Settings/0103-SableOS-Sable-app-corner-style-in-the-appearance-aut.patch  new
tests/check-sable-design.py              +10 corner style checks
tests/java/SableSettingsPolicyTest.java  +27 checks for 0103
tests/run.sh                             policy test also applies 0103
patches/README.md                        0103 row
docs/implementation/{corners,kf-b}.md, docs/QUALIFICATION.md (Q4-VISUAL), docs/SABLEOS_GAP_REVIEW.md
```

## Framework patch

| Patch | Base | Notes |
|---|---|---|
| `packages/apps/Settings/0103-SableOS-Sable-app-corner-style-in-the-appearance-aut.patch` | lineage-23.2 `8d8f6486b274bcf0aa6e5d0cbba52c0b05ae5c65` with 0101 and 0102 applied | Made with `git format-patch` from commit `7a5b9bdac93` in `/home/claude/lineage-corners/Settings`. Touches only files 0101 added: the manifest comment, `SableAppearancePolicy.java` and `SableAppearanceProvider.java`. Checked: the whole Settings stack (0101, 0102, 0103, 0201, 0202, 0203, 0300) applies in order with `git am` on the base. |

## Design and acceptance coverage

| Item | State |
|---|---|
| Corner style is a user setting in App display, Compact by default | Code + host tests (`AppStyleTest`, `SableDesignContractTest`, check script). |
| Rounded = design 8dp controls / 12dp cards and sheets | Host tests compare the table with `SableGeometryTokens` (`SableSystemTokensTest`, `SableDesignContractTest`, check script). |
| One authority, readable by every Sable app; only Settings and DisplayCompat write it | Code + host tests (`SableSettingsPolicyTest`, 0103). Needs a device to confirm that the provider sees DisplayCompat as a non-updated system app. |
| Live restyle of open apps | Code: the existing content observer in `rememberGlobalSableAppearance` and `notifyChange` on write. Needs a device (Q4-VISUAL). |
| Keyboard-first row | Code (type-checked against stubs). Needs a device for focus and visual checks. |

## Tests

```bash
bash tests/run.sh                        # includes the check script and the Settings policy test
python3 tests/check-sable-design.py      # 39 checks (10 new corner checks)

K=<scratchpad>
# Pure: token table + corner style
D=sable-src/src/android/shared/sabledesign/src
$K/kotlinc/bin/kotlinc $D/main/java/org/sableos/design/SableSystemTokens.kt \
    $D/main/java/org/sableos/design/SableCornerStyle.kt \
    $D/test/java/org/sableos/design/SableSystemTokensTest.kt $K/shim/Shim.kt -d /tmp/corners-out
java -cp /tmp/corners-out:$K/kotlinc/lib/kotlin-stdlib.jar org.junit.Run org.sableos.design.SableSystemTokensTest

# Pure: DisplayCompat core (AppStyle + existing tests)
C=sable-src/apps/titan2/platform/displaycompat/src
$K/kotlinc/bin/kotlinc $C/main/java/org/sableos/titan2/displaycompat/core/*.kt \
    $C/test/java/org/sableos/titan2/displaycompat/core/*.kt $K/shim/Shim.kt -d /tmp/corners-dc
java -cp /tmp/corners-dc:$K/kotlinc/lib/kotlin-stdlib.jar org.junit.Run \
    org.sableos.titan2.displaycompat.core.AppStyleTest \
    org.sableos.titan2.displaycompat.core.DisplayCompatCoreTest \
    org.sableos.titan2.displaycompat.core.DisplayCompatHardeningTest

# Type-check (scratchpad cc setup: kc.sh/rt.sh): sabledesign, the reader's design
# copy, DisplayCompat, SableStart, Media, Calendar, Weather, Hub; design tests via rt.sh.
```

Results here: `SableSystemTokensTest` RAN=14 FAILED=0. DisplayCompat core
RAN=51 FAILED=0. Design tests (r8 contract and shared, via `rt.sh`) RAN=43
FAILED=0. `SableSettingsPolicyTest` 59 checks, 0 failed. `check-sable-design.py`
39/39 PASS. `tests/run.sh` CI=PASS. Type-check: no errors in sabledesign, the
reader design copy, DisplayCompat, SableStart, Media, Calendar, Weather or Hub.

## Integration

1. Merge `q25/corners`, then regenerate `patches/sable-src/` for the sable-src
   files listed above (two new `SableCornerStyle.kt`, new DisplayCompat
   `AppStyle.kt`, `AppearanceStyleStore.kt`, `AppStyleTest.kt`, and the edits).
2. No change to `product/q25/apps.tsv` or `stage-product.sh`. DisplayCompat
   stays a non-privileged product app. The 0103 writer rule depends on it
   being installed from the system image.
3. First operator build: `apply-framework-patches.sh check` should report
   `APPLIES` for Settings 0103 after 0101/0102. Build Settings and
   SableDisplayCompat and fix any compile error in the uncompiled code.
4. Run Q4-VISUAL, including the corner-style clause.

## Expected overlaps

* **Settings 0101 provider.** 0103 edits `SableAppearanceProvider.java` and
  `SableAppearancePolicy.java` from 0101. Any later rework of 0101 has to be
  rebased under 0103. The other Settings patches (0201-0203, 0300) don't touch
  these files, and the full stack applies.
* **sabledesign `SableTheme.kt`.** KF-A, KF-D and T3 may edit it. This package
  replaced the private `SableShapes` value with `sableShapes(style)` and added
  `cornerStyle` to `SableAppearance` (a defaulted parameter, so existing calls
  compile unchanged).
* **DisplayCompat `AppListScreen.kt`.** Any package that edits the list keys
  (Up/Down/Enter/Left/Right) will conflict with the new style row handling.
