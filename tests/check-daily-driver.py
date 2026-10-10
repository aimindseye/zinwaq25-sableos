#!/usr/bin/env python3
"""Static checks for the R9 daily-driver presentation port (docs/implementation/daily-driver.md).

No Android tree, no network. Checks that:
  * the daily-driver overlays are well-formed, static, code-free RROs aimed at
    the LineageOS 23.2 packages, and overlay only resources that exist in the
    target at the recorded base commit (inventory below, taken from the
    target's res/values; the targets declare no <overlayable>);
  * the Gallery2 chrome colors equal the Sable dark surface tokens and the
    accent follows the system accent palette (seeded from the Sable accent);
  * the Glimpse framework patch changes resources only (no code, manifest,
    permission or build change) and draws focus as a 2dp outline, never a fill;
  * Sable Start maps exactly the overlaid LineageOS packages to the R9 roles.

Prints PASS/FAIL lines and exits non-zero on any failure.
"""
from __future__ import annotations

import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
OVERLAY = ROOT / "product/common/overlay"
TOKENS = ROOT / "sable-src/src/android/shared/sabledesign/src/main/java/org/sableos/design/SableSystemTokens.kt"
IDENTITY = ROOT / "sable-src/src/android/packages/apps/SableStart/src/com/sable/start/model/CoreAppIdentity.kt"
GLIMPSE_PATCHES = ROOT / "patches/framework/packages/apps/Glimpse"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

# overlay -> (target package, base commit, {(type, name)} that exist in the target's res/values)
TARGETS = {
    "SableGlimpseOverlay": (
        "org.lineageos.glimpse",
        "c7b5e8cfbb4e941473f3179322ec8513d83b4ca9",
        {("string", "app_name")},
    ),
    "SableGallery2Overlay": (
        "com.android.gallery3d",
        "73803da5c066e0d6f67319d28087c965234b7850",
        {("color", "primary"), ("color", "primaryDark"), ("color", "accent")},
    ),
}

failures = 0


def report(ok: bool, name: str, detail: str = "") -> None:
    global failures
    if ok:
        print(f"PASS  {name}")
    else:
        failures += 1
        print(f"FAIL  {name}{': ' + detail if detail else ''}")


def parse(path: pathlib.Path) -> ET.Element | None:
    try:
        return ET.parse(path).getroot()
    except (ET.ParseError, OSError) as error:
        report(False, f"XML {path.relative_to(ROOT)}", str(error))
        return None


bp = (OVERLAY / "Android.bp").read_text(encoding="utf-8")
for name, (package, base, inventory) in TARGETS.items():
    manifest = parse(OVERLAY / name / "AndroidManifest.xml")
    if manifest is None:
        continue
    overlay = manifest.find("overlay")
    app = manifest.find("application")
    report(
        overlay is not None
        and overlay.get(ANDROID_NS + "targetPackage") == package
        and overlay.get(ANDROID_NS + "isStatic") == "true"
        and app is not None
        and app.get(ANDROID_NS + "hasCode") == "false"
        and manifest.find("uses-permission") is None,
        f"{name} is a static code-free overlay of {package}",
    )
    report(f'name: "{name}"' in bp and f'"{name}/res"' in bp, f"{name} has an Android.bp module")
    entries = set()
    for values in sorted((OVERLAY / name / "res").glob("values*/*.xml")):
        root = parse(values)
        if root is None:
            continue
        for element in root:
            entries.add((element.tag, element.get("name")))
    missing = sorted(entries - inventory)
    report(bool(entries) and not missing, f"{name} overlays only resources present in {package} at {base[:12]}", str(missing))


def token(text: str, role: str, field: str) -> str:
    m = re.search(rf"val {role} =\s*SableColorRoles\((.*?)\n\s*\)", text, re.S)
    v = re.search(rf"{field} = 0x([0-9A-Fa-f]{{8}})", m.group(1)) if m else None
    return "#" + v.group(1).upper() if v else "?"


tokens = TOKENS.read_text(encoding="utf-8")
colors = {}
root = parse(OVERLAY / "SableGallery2Overlay/res/values/colors.xml")
if root is not None:
    colors = {e.get("name"): (e.text or "").strip() for e in root}
report(colors.get("primary", "").upper() == token(tokens, "Dark", "surfaceOverlay"), "Gallery2 primary = Sable dark surface.overlay", colors.get("primary", ""))
report(colors.get("primaryDark", "").upper() == token(tokens, "Dark", "surfaceBase"), "Gallery2 primaryDark = Sable dark surface.base", colors.get("primaryDark", ""))
report(colors.get("accent") == "@android:color/system_accent1_400", "Gallery2 accent follows the system (Sable-seeded) accent", colors.get("accent", ""))

patches = sorted(GLIMPSE_PATCHES.glob("08[0-9][0-9]-*.patch"))
report(len(patches) == 1, "one Glimpse daily-driver patch in range 0800-0899", str([p.name for p in patches]))
for patch in patches:
    text = patch.read_text(encoding="utf-8")
    files = re.findall(r"^diff --git a/(\S+) b/", text, re.M)
    report(bool(files) and all(f.startswith("app/src/main/res/") and f.endswith(".xml") for f in files),
           f"{patch.name} changes resources only", str(files))
    added = "\n".join(l[1:] for l in text.splitlines() if l.startswith("+") and not l.startswith("+++"))
    report("uses-permission" not in added and "android:onClick" not in added and "<solid" not in added
           and added.count('android:width="2dp"') == 2 and added.count('<item android:state_focused="true">') == 2,
           f"{patch.name} draws a 2dp focus outline, no fill, no behaviour")

identity = IDENTITY.read_text(encoding="utf-8")
mapped = set(re.findall(r'"(org\.lineageos\.\w+)" to ', identity))
report(mapped == {"org.lineageos.glimpse", "org.lineageos.aperture", "org.lineageos.jelly"},
       "Sable Start maps Glimpse, Aperture and Jelly to the R9 roles", str(sorted(mapped)))
report("import android." not in identity, "CoreAppIdentity is pure Kotlin")

sys.exit(1 if failures else 0)
