# Sable Start presentation source status

Status: **presentation-only after the R9L8 Launcher3 HOME ownership cutover**.

The canonical SableOS repository no longer contains a standalone SableStart
application, HOME activity, manifest, JNI bridge, Rust runtime, independent
LauncherApps inventory/launch implementation, or Launcher-owned appearance
provider.

`packages/apps/Launcher3` / `Launcher3QuickStep` is the sole HOME process.
The SableStart directory now contains only reusable presentation/state sources
compiled into `Launcher3QuickStepLib` through
`:sable_start_presentation_srcs`.

## Canonical presentation sources

```text
src/com/sable/start/live/LiveSurfaceModels.kt
src/com/sable/start/model/AppEntry.kt
src/com/sable/start/model/CoreAppIdentity.kt
src/com/sable/start/platform/LiveSurfaceRepository.kt
src/com/sable/start/platform/StartStateRepository.kt
src/com/sable/start/ui/SableGlyphIcon.kt
src/com/sable/start/ui/SableStartScreen.kt
```

Global Sable appearance is owned by Settings at
`content://org.sableos.appearance/appearance`. Launcher3-hosted Sable Start
consumes that shared authority; it does not declare an independent appearance
provider or persistence authority.

## Explicitly retired

```text
AndroidManifest.xml
res/values/strings.xml
rust/src/lib.rs
src/com/sable/start/SableStartActivity.kt
src/com/sable/start/bridge/SableStartNative.kt
src/com/sable/start/platform/LauncherAppsRepository.kt
src/com/sable/start/platform/SableAppearanceProvider.kt
```

`build/panther/sync-sablestart.sh` synchronizes only the presentation tree and
fails if any retired runtime/provider source reappears.
