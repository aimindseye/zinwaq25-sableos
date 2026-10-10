# Daily driver: R9 core-app presentation on LineageOS 23.2

Package `daily-driver` ports SableOS R9 *daily driver core presentation*
(`sableos/patches/android-17-grapheneos-2026081300/apply_r9_daily_driver_core_presentation.py`)
to the LineageOS 23.2 base of the Q25. The port follows
`FILES_DOCUMENTS_UX.md`, `MEDIA_GALLERY_UX.md`, `BROWSER_WEBVIEW_UX.md`,
`CAMERA_CAPTURE_UX.md` and `docs/R9_DAILY_DRIVER_VISUAL_REVIEW.md` where they
apply to presentation.

Status: **written and statically checked, not built, not run on a device.**
The Kotlin in Sable Start was type-checked against the Android 16 framework
jar, and the resource changes were checked against the LineageOS sources at
their base commits (see [What was checked](#what-was-checked)). No Android
build ran here. Nothing is claimed tested on a device.

## What R9 changed, and what carries over

R9 changed resources only, on GrapheneOS 2026081300. It made AOSP Clock and
Gallery2 follow day/night, replaced their hard-coded colors (Holo blue, red,
grey) with the Sable accent and Sable surfaces, recolored DocumentsUI's
legacy palette, and renamed Gallery to "Photos". It did not touch GrapheneOS
Camera (a preprocessed APK) or Vanadium (a separate security build). The
camera capture screen stayed dark, as an accepted exception.

LineageOS 23.2 ships different apps for some of these roles. The list was
confirmed from `LineageOS/android` lineage-23.2 (`snippets/lineage.xml`),
`vendor/lineage` (`config/common.mk`, `common_mobile.mk`,
`common_mobile_full.mk`) and the Q25 product (`lineage_Q25.mk` inherits
`common_full_phone.mk`):

| Role | GrapheneOS (R9) | LineageOS 23.2 (Q25) | Base commit | Decision |
|---|---|---|---|---|
| Clock | AOSP DeskClock (AppCompat, hard-coded colors) | DeskClock, Material 3 Expressive, `DynamicColors.Dark` | `616807bba7008e460a714bc4e122b842c12dd0e1` | **No change.** The accent already comes from the system palette, which KF-B Settings patch 0101 seeds from the Sable accent. LineageOS made the app deliberately dark. A day/night switch would need about 20 white vector assets (analog dial and hands, tab and action icons), the alarm and screensaver surfaces, and hard-coded `Color.WHITE` in Java to be redone. That is a redesign that can't be checked without rendering it, not R9's resource swap. |
| Files | DocumentsUI, legacy palette | DocumentsUI with `use_material3` ENABLED (`build_release` bp4a, `d76088e5`) and `force_material3=true` (LineageOS `DocumentsUIOverlay`) | `860ac584e6b4c8b99a9ba9777d4784e076bc9e5b` | **No change.** The R9 anchors still exist in `res/values*/colors.xml`, but under Material 3 `ThemeUtils.getRes` maps them to the `*_m3` colors (`@android:color/system_accent1_600`, dynamic neutrals). Recoloring them would have no visible effect. Day/night and the Sable accent already come from the system. The app's `<overlayable>` also blocks the theme overrides that would matter. |
| Photos | Gallery2 (launcher gallery) | **Glimpse** (`org.lineageos.glimpse`) is the gallery. Gallery2 stays installed with its gallery activities disabled; its editor (`FilterShowActivity`), crop and photo-frame widget picker are still reachable | Glimpse `c7b5e8cfbb4e941473f3179322ec8513d83b4ca9`, Gallery2 `73803da5c066e0d6f67319d28087c965234b7850` | **Ported.** Glimpse is labelled "Photos" (overlay). Its thumbnails get the Sable focus ring (patch 0801). Gallery2's Holo-blue accent (`#33b5e5`) and grey chrome become the Sable accent and Sable dark surfaces (overlay). Glimpse is already Material 3 DayNight with dynamic color. |
| Camera | GrapheneOS Camera (untouched) | Aperture (`org.lineageos.aperture`), Material 3 dynamic color; capture screen `Theme.Material3.Dark` | `f7c0a93f787cc9e0db4193b6094bd51575567898` | **No change**, as in R9. Sable Camera (`org.sableos.titan2.camera`) does **not** replace Aperture (its `overrides` column in `product/q25/apps.tsv` is `-`), so both are installed. Camera is skipped because R9 has nothing to port for it, not because of an override. Aperture already follows the accent, and its dark capture screen is the same exception R9 accepted. |
| Browser | Vanadium (untouched) | Jelly (`org.lineageos.jelly`), label "Browser", Material 3 dynamic color | `1bb5edd564f2adb475fb565373426166f3ca252a` | **No change**, as in R9. The label is accurate (`BROWSER_LABEL_ACCURATE`), and Jelly is never called Vanadium or Chrome. |

The Sable Start side carries over as well. R9's Start recognised the core
apps by GrapheneOS package names (`com.android.gallery3d`,
`app.grapheneos.camera`, `app.vanadium.browser`). On LineageOS, that left
Glimpse, Aperture and Jelly without their Sable glyphs or identity accents.
The Live "Photos" row also found no app to open. This is fixed in Sable Start
(below).

## What was implemented

### 1. Sable Start core-app roles (sable-src)

`sable-src/src/android/packages/apps/SableStart/src/com/sable/start/model/CoreAppIdentity.kt`
is pure Kotlin. It maps a base's package for a core role to the canonical R9
key: `org.lineageos.glimpse` to Photos, `org.lineageos.aperture` to Camera
and `org.lineageos.jelly` to Browser. Files and Clock use the same packages
on both bases. `SableGlyphIcon.sableGlyphForPackage`, and in
`SableStartScreen.kt` `appColor`, `liveDatumForApp`, `approvedCompactDetail`,
`findLiveApp` and the default Start order (`PREFERRED_START_APP_GROUPS`), now
compare canonical packages. Labels, components, launch and permissions are the
app's own. Sable Camera is not remapped: it keeps its own label and identity.
The mapping is base-specific data, not device branching, and the GrapheneOS
names still match themselves.

The file is added to the `sable_start_presentation_srcs` filegroup
(`Android.bp`), to the `sablestart-presentation-check` source sync and to
`PORTABILITY_STATUS.md`. Its test is
`apps/r8/android/sablestart-presentation-check/src/test/java/org/sableos/start/model/CoreAppIdentityTest.kt`.

### 2. Resource overlays (`product/common/overlay`, staged at Q4)

These are two static, product-partition, code-free RROs, built as
`runtime_resource_overlay` modules in the existing `Android.bp`. Neither
target declares `<overlayable>`, so a preinstalled overlay may set these
values.

* `SableGlimpseOverlay` targets `org.lineageos.glimpse` and sets `app_name`
  to "Photos". As in R9, only the default locale changes. Locales that
  LineageOS translated keep their own word for "Gallery" until Sable
  translations exist.
* `SableGallery2Overlay` targets `com.android.gallery3d`:
  * `primary` becomes `#FF161C24` (Sable dark `surface.overlay`, was `#333333`).
  * `primaryDark` becomes `#FF05070A` (Sable dark `surface.base`, was `#000000`).
  * `accent` becomes `@android:color/system_accent1_400` (was Holo blue `#33b5e5`).

  Every Gallery2 accent use (`popup_title_color`, `mode_selection_border`,
  `holo_blue_light`, ingest and review colors, theme `colorAccent`) refers
  to `@color/accent`, so one value covers what R9 changed one color at a
  time. The accent follows the system palette, so a change in Settings >
  Display > Accent reaches it. Gallery2's label stays "Gallery", so two
  apps aren't both called "Photos".

`scripts/stage-product.sh` adds both overlays to `PRODUCT_PACKAGES` for Q4
and later, next to the KF-B overlays (the directory was already copied).
`tests/run.sh` checks that staging.

### 3. Framework patch (range 0800-0899)

| Patch | Base (lineage-23.2) | What it does |
|---|---|---|
| `framework/packages/apps/Glimpse/0801-SableOS-Sable-keyboard-focus-ring-on-gallery-thumbna.patch` | `c7b5e8cfbb4e941473f3179322ec8513d83b4ca9` | Photo thumbnails (`thumbnail_view.xml`) and album tiles (`album_thumbnail_view.xml`) get `android:foreground` set to a `state_focused`-only selector: a 2dp `?attr/colorPrimary` outline with a 2dp gap to the thumbnail (8dp corners around the 4dp thumbnail corners; the album ring is inset 8dp inside the 12dp padding). They also get `android:defaultFocusHighlightEnabled="false"` and an explicit `android:focusable="true"`. They were already focusable through their click listener. On a hardware keyboard, focus was shown only by the platform's grey translucent fill, which design forbids (focus is an outline, never a fill, so it stays distinct from the selection check and blur). Resources only. Touch, selection, navigation and media access are unchanged. |

It was produced with `git format-patch -1` from a commit in
`/home/claude/lineage-daily-driver/Glimpse`, and checked with
`git apply --check` on a fresh checkout of the base.

## What was checked

| Item | How | Result |
|---|---|---|
| Sable Start presentation sources incl. `CoreAppIdentity.kt` (the `sable_start_presentation_srcs` set) | kotlinc 2.x with the Compose plugin against Robolectric `android-all` API 36 (Android 16) plus the androidx/Compose jars used for the other Sable modules (`scratchpad/cc/kc.sh`) | 0 errors |
| Whole SableStart `src/` tree | same | 0 errors |
| `sablestart-presentation-check` tests (KF-D privacy + `CoreAppIdentityTest`) | kotlinc + JUnit shim | `RAN=35 FAILED=0` (`CoreAppIdentityTest`: `RAN=4 FAILED=0`) |
| Overlay XML | parsed; manifests static, `hasCode=false`, no permissions | pass |
| Overlaid names exist in the target | every `(type, name)` in each overlay looked up in the target's `res/values` at the base commit (Glimpse `string/app_name` in `values/strings.xml`; Gallery2 `color/primary`, `primaryDark`, `accent` in `values/cm_colors.xml`, defined nowhere else); targets checked for `<overlayable>` (none); package names checked against the targets' manifests | all present; recorded in `tests/check-daily-driver.py` |
| Framework references | `android:defaultFocusHighlightEnabled`, `foreground`, `focusable`, `inset`, `radius` in `android.R$attr` and `system_accent1_400` in `android.R$color` of the API 36 jar; Glimpse `minSdk` is 30 (attributes are API 23/26); `?attr/colorPrimary` is already used by Glimpse (`color/settingslib_switch_thumb_icon.xml`) | present |
| Patch | `git apply --check` on Glimpse `c7b5e8cf`; XML of the four changed files parses | applies |

aapt2 did not run (no SDK build tools here), so the overlays and the Glimpse
resources have not been compiled.

## Design acceptance keys

| Key | Covered by | Needs device |
|---|---|---|
| R9_PHOTOS_PRESENTATION (Sable accent and surfaces, "Photos") | Glimpse overlay, Gallery2 overlay, dynamic color + KF-B accent | rendering, `Q4-DAILY-DRIVER` |
| R9_PHOTOS_DAY_NIGHT_PRESENTATION | already met upstream (Glimpse `Theme.Material3.DayNight`) | yes |
| R9_CLOCK_PRESENTATION (accent) | already met upstream (dynamic color + KF-B) | yes |
| R9_CLOCK_DAY_NIGHT_PRESENTATION | **not ported** (see table; LineageOS Clock is dark by design) | n/a |
| R9_FILES_PRESENTATION | already met upstream (Material 3 + system accent) | yes |
| R9_CAMERA / R9_VANADIUM boundary (no change) | unchanged; Aperture/Jelly not patched | n/a |
| R9_CORE_APP_BEHAVIORAL_MUTATION=ABSENT | resources-only patch and overlays, checked by `tests/check-daily-driver.py`; Sable Start change is presentation lookup only | `Q4-DAILY-DRIVER-BEHAVIOUR` |
| MEDIA_GALLERY: FOCUSED_ITEM visible, KEYBOARD_FIRST_MEDIA (focus part) | patch 0801 | keyboard pass on the phone |
| BROWSER_LABEL_ACCURATE, CHROME_LABEL_FOR_VANADIUM=NO | unchanged Jelly label; `CoreAppIdentity` shares only the glyph | All Apps check |
| Start live/identity tiles for Photos, Camera, Files, Clock | `CoreAppIdentity` + test | Start capture on the phone |

The other keys in these designs are larger product work outside a
presentation port: keyboard shortcuts, contact sheet with preview, page mode,
redaction and handoffs. Implementing them would change app behaviour, which
this package must not do.

## Left for the device

* Rendering: the Glimpse focus ring at 193 dpi, the Gallery2 editor and crop
  chrome in light and dark, and the Start tiles. Proposed gates:
  `Q4-DAILY-DRIVER` and `Q4-DAILY-DRIVER-BEHAVIOUR` in
  [QUALIFICATION](../QUALIFICATION.md).
* Density and clipping: no clipping defect could be identified from source.
  At 193 dpi the screen is about 597dp square, below every `w600dp`, `h600dp`
  and `sw600dp` qualifier these apps use, so the phone layouts apply.
  Glimpse's grid computes its span count from the display. A clipping fix
  needs a capture at font scale 1.0 and 1.3.
* Keyboard focus in Clock, Files, Camera and Browser: these use Material
  components with their own focus states. Any defect needs a keyboard pass on
  the phone.

## Test commands

```bash
bash tests/run.sh                       # includes check-daily-driver.py and the Q4 staging of both overlays
python3 tests/check-daily-driver.py     # overlays, patch scope, Sable Start roles
# Sable Start type-check and tests (host; see scratchpad/cc/kc.sh and rt.sh):
#   kc.sh <out> <design+androidx cp> SableStart/src/.../{live,model,platform,privacy,ui}/*.kt
#   rt.sh <out> <presentation cp> apps/r8/android/sablestart-presentation-check/src/test/java
```

## Integration

* `product/common/overlay`: two new overlays and `Android.bp` modules.
  `scripts/stage-product.sh` adds `SableGlimpseOverlay` and
  `SableGallery2Overlay` to the Q4 `PRODUCT_PACKAGES`. `tests/run.sh`
  checks for them, and `tests/check-sable-design.py`'s overlay target list
  now includes them.
* `patches/framework/packages/apps/Glimpse/0801-*.patch` is applied by
  `scripts/apply-framework-patches.sh` like the others (new project path
  `packages/apps/Glimpse`).
* sable-src (edited directly, so the integrator regenerates
  `patches/sable-src`): `SableStart/.../model/CoreAppIdentity.kt` (new),
  `ui/SableStartScreen.kt`, `ui/SableGlyphIcon.kt`, `SableStart/Android.bp`,
  `SableStart/PORTABILITY_STATUS.md`,
  `apps/r8/android/sablestart-presentation-check/build.gradle.kts` and the
  new test.
* `scratchpad/cc/all.sh` (the integrator's type-check script) lists the
  presentation sources explicitly, so add `$ST/model/CoreAppIdentity.kt` to
  its "presentation" line.
