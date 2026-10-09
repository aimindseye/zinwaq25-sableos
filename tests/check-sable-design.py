#!/usr/bin/env python3
"""Static checks for DESIGN-KF-B (SystemUI visual convergence) and Sable branding.

No Android tree, no network. Checks that:
  * the Sable token palette (sable-src sabledesign/SableSystemTokens.kt)
    satisfies the design's contrast rules (WCAG 2.x ratios);
  * the runtime resource overlays in product/common/overlay carry the same
    values as the token table (radii, default tiles, palette seed);
  * the Settings framework patch uses the same accent table, and so does
    AccentPreset in SableTheme.kt;
  * branding only changes Sable-owned strings: the SetupWizard overlay
    replaces the welcome title and logo, never os_name or LineageOS legal
    and attribution text;
  * the display density is one valid value in product/q25/sable-q25.mk.

Prints PASS/FAIL lines and exits non-zero on any failure.
"""
from __future__ import annotations

import hashlib
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
TOKENS = ROOT / "sable-src/src/android/shared/sabledesign/src/main/java/org/sableos/design/SableSystemTokens.kt"
THEME = ROOT / "sable-src/src/android/shared/sabledesign/src/main/java/org/sableos/design/SableTheme.kt"
OVERLAY = ROOT / "product/common/overlay"
SETTINGS_PATCHES = ROOT / "patches/framework/packages/apps/Settings"
SYSTEMUI_PATCHES = ROOT / "patches/framework/frameworks/base"
PRODUCT_MK = ROOT / "product/q25/sable-q25.mk"
SHIELD_SHA256 = "eed1b1277a77fa1f94b514585622c403c85c95191cfa3ba90edf526d02e5aea7"

failures = 0


def report(ok: bool, name: str, detail: str = "") -> None:
    global failures
    if ok:
        print(f"PASS  {name}")
    else:
        failures += 1
        print(f"FAIL  {name}{': ' + detail if detail else ''}")


# --- token table -------------------------------------------------------------

def parse_roles(text: str, name: str) -> dict[str, int]:
    m = re.search(rf"val {name} =\s*SableColorRoles\((.*?)\n\s*\)", text, re.S)
    if not m:
        raise SystemExit(f"cannot find SableColorRoles {name} in {TOKENS}")
    return {k: int(v, 16) for k, v in re.findall(r"(\w+) = 0x([0-9A-Fa-f]{8})", m.group(1))}


def parse_accents(text: str) -> dict[str, dict[str, int]]:
    out = {}
    for m in re.finditer(
        r'^\s*\w+\("(\w+)", 0x([0-9A-F]{8}), 0x([0-9A-F]{8}), 0x([0-9A-F]{8}), 0x([0-9A-F]{8}), 0x([0-9A-F]{8})\)',
        text,
        re.M,
    ):
        name, seed, dark, on_dark, light, on_light = m.groups()
        out[name] = {
            "seed": int(seed, 16),
            "dark": int(dark, 16),
            "onDark": int(on_dark, 16),
            "light": int(light, 16),
            "onLight": int(on_light, 16),
        }
    return out


def const_int(text: str, name: str) -> int:
    m = re.search(rf"const val {name} = (\w+)", text)
    if not m:
        raise SystemExit(f"cannot find {name}")
    value = m.group(1)
    return int(value) if value.isdigit() else const_int(text, value)


def luminance(argb: int) -> float:
    def channel(shift: int) -> float:
        c = ((argb >> shift) & 0xFF) / 255.0
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)


def contrast(a: int, b: int) -> float:
    la, lb = luminance(a), luminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)


text = TOKENS.read_text(encoding="utf-8")
roles = {"dark": parse_roles(text, "Dark"), "light": parse_roles(text, "Light")}
accents = parse_accents(text)
report(list(accents) == ["blue", "green", "purple", "orange", "slate"], "token accents parsed", str(list(accents)))
report(all(len(r) == 10 for r in roles.values()), "token roles parsed", str({k: len(v) for k, v in roles.items()}))

bad = []
for theme, r in roles.items():
    dark = theme == "dark"
    surfaces = [r["surfaceBase"], r["surfaceRaised"], r["surfaceOverlay"]]
    for i, s in enumerate(surfaces):
        for role, minimum in (
            ("textPrimary", 7.0),
            ("textSecondary", 4.5),
            ("textDisabled", 3.0),
            ("success", 3.0),
            ("warning", 3.0),
            ("danger", 3.0),
            ("privacy", 3.0),
        ):
            ratio = contrast(r[role], s)
            if ratio + 1e-9 < minimum:
                bad.append(f"{theme} surface{i} {role} {ratio:.2f}<{minimum}")
        for name, a in accents.items():
            tone = a["dark"] if dark else a["light"]
            ratio = contrast(tone, s)
            if ratio + 1e-9 < 3.0:
                bad.append(f"{theme} surface{i} {name} accent/focus {ratio:.2f}<3")
    for name, a in accents.items():
        tone, on = (a["dark"], a["onDark"]) if dark else (a["light"], a["onLight"])
        ratio = contrast(on, tone)
        if ratio + 1e-9 < 4.5:
            bad.append(f"{theme} {name} onAccent {ratio:.2f}<4.5")
    if r["privacy"] == r["danger"]:
        bad.append(f"{theme} privacy equals danger")
report(not bad, "palette contrast rules (text 7/4.5/3, non-text 3, on-accent 4.5)", "; ".join(bad))

card = const_int(text, "CARD_RADIUS_DP")
small = const_int(text, "SMALL_CONTROL_RADIUS_DP")
large = const_int(text, "SHAPE_LARGE_DP")
focus = const_int(text, "FOCUS_STROKE_DP")
report((card, small, large, focus) == (12, 8, 16, 2), "geometry tokens match design baseline", f"{card} {small} {large} {focus}")
m = re.search(r"val DEFAULT: List<String> =\s*listOf\(([^)]*)\)", text)
default_tiles = re.findall(r'"(\w+)"', m.group(1)) if m else []
report(bool(default_tiles), "default tile list parsed")

# AccentPreset in SableTheme.kt (what Sable apps use) must share the seeds.
theme_text = THEME.read_text(encoding="utf-8")
presets = {n: int(c, 16) for n, c in re.findall(r'\w+\("(\w+)", "[^"]+", Color\(0x([0-9A-F]{8})\)', theme_text)}
report(
    presets == {n: a["seed"] for n, a in accents.items()},
    "AccentPreset seeds equal SableAccentTokens seeds",
    f"{presets}",
)

# --- overlays ----------------------------------------------------------------

xml_files = sorted(OVERLAY.rglob("*.xml"))
broken = []
for f in xml_files:
    try:
        ET.parse(f)
    except ET.ParseError as e:
        broken.append(f"{f.relative_to(ROOT)}: {e}")
report(bool(xml_files) and not broken, f"overlay XML well-formed ({len(xml_files)} files)", "; ".join(broken))

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
targets = {}
for manifest in sorted(OVERLAY.glob("*/AndroidManifest.xml")):
    node = ET.parse(manifest).getroot().find("overlay")
    targets[manifest.parent.name] = (
        node.get(ANDROID_NS + "targetPackage") if node is not None else None,
        node.get(ANDROID_NS + "isStatic") if node is not None else None,
    )
report(
    targets
    == {
        "SableFrameworkOverlay": ("android", "true"),
        "SableSetupWizardOverlay": ("org.lineageos.setupwizard", "true"),
        "SableSystemUIOverlay": ("com.android.systemui", "true"),
    },
    "overlay manifests target framework, SystemUI and SetupWizard as static overlays",
    str(targets),
)
bp = (OVERLAY / "Android.bp").read_text(encoding="utf-8")
bp_names = re.findall(r'name: "(\w+)"', bp)
report(sorted(bp_names) == sorted(targets), "Android.bp builds every overlay", str(bp_names))
bp_code = "\n".join(line for line in bp.splitlines() if not line.lstrip().startswith("//"))
report("certificate" not in bp_code and "privileged" not in bp_code, "overlays are not platform-signed or privileged")


def resources(path: pathlib.Path) -> dict[tuple[str, str], ET.Element]:
    out = {}
    for el in ET.parse(path).getroot():
        out[(el.tag, el.get("name"))] = el
    return out


sysui = resources(OVERLAY / "SableSystemUIOverlay/res/values/dimens.xml")
want = {
    "notification_corner_radius": f"{card}dp",
    "notification_scrim_corner_radius": f"{large}dp",
    "qs_corner_radius": f"{large}dp",
    "qs_media_album_radius": f"{small}dp",
}
got = {name: (sysui[("dimen", name)].text or "").strip() for name in want if ("dimen", name) in sysui}
report(got == want, "SystemUI overlay radii equal the shape tokens", f"{got}")

cfg = resources(OVERLAY / "SableSystemUIOverlay/res/values/config.xml")
tiles = [t.strip() for t in (cfg[("string", "quick_settings_tiles_default")].text or "").split(",") if t.strip()]
report(tiles == default_tiles, "quick_settings_tiles_default equals SableQuickSettingsTiles.DEFAULT", f"{tiles}")
STOCK_TILES = {
    "internet", "wifi", "cell", "bt", "flashlight", "dnd", "modes_dnd", "alarm", "airplane", "nfc",
    "controls", "wallet", "rotation", "battery", "cast", "screenrecord", "mictoggle", "cameratoggle",
    "location", "hotspot", "inversion", "saver", "dark", "work", "night", "reverse",
    "reduce_brightness", "qr_code_scanner", "onehanded", "color_correction", "dream", "font_scaling",
}
report(set(tiles) <= STOCK_TILES, "default tiles are stock SystemUI tiles", str(set(tiles) - STOCK_TILES))
report(not ({"aod", "ambient_display"} & set(tiles)), "profile-gated tiles are not default")

notlong = resources(OVERLAY / "SableSystemUIOverlay/res/values-notlong/config.xml")
large_tiles = [i.text.strip() for i in notlong[("string-array", "quick_settings_default_large_tiles")]]
report(large_tiles == default_tiles, "square screens make every default tile large (two-column grid)", f"{large_tiles}")
patch0101 = next(SYSTEMUI_PATCHES.glob("0101-*.patch")).read_text(encoding="utf-8")
report(
    "R.array.quick_settings_default_large_tiles" in patch0101
    and 'name="quick_settings_default_large_tiles"' in patch0101,
    "SystemUI patch 0101 defines and reads the large-tile resource the overlay sets",
)
patch0102 = next(SYSTEMUI_PATCHES.glob("0102-*.patch")).read_text(encoding="utf-8")
report(
    f"+    val ActiveTileCornerRadius = {card}.dp" in patch0102
    and f"+    val InactiveCornerRadius = {card}.dp" in patch0102
    and f"+    val ActiveIconCornerRadius = {small}.dp" in patch0102,
    "SystemUI patch 0102 tile radii equal the shape tokens",
)
security = re.findall(r"^\+\+\+ b/(\S+)", patch0101 + patch0102, re.M)
report(
    not [p for p in security if re.search(r"keyguard|bouncer|biometric|lockscreen", p, re.I)],
    "SystemUI patches do not touch Keyguard or authentication code",
    str(security),
)

fw = resources(OVERLAY / "SableFrameworkOverlay/res/values/config.xml")
theming = [i.text.strip() for i in fw[("string-array", "theming_defaults")]]
report(
    theming == [f"*|TONAL_SPOT|#{accents['blue']['seed'] & 0xFFFFFF:06X}"],
    "first-boot system palette seeded from Sable Blue",
    str(theming),
)

# --- Settings patch shares the accent table ----------------------------------

settings_patch = next(SETTINGS_PATCHES.glob("0101-*.patch")).read_text(encoding="utf-8")


def java_array(name: str) -> list[str]:
    m = re.search(rf"{name} = \{{([^}}]*)\}}", settings_patch)
    return re.findall(r'"(\w+)"', m.group(1)) if m else []


report(java_array("ACCENTS") == list(accents), "Settings accent values equal the token accents", str(java_array("ACCENTS")))
report(
    java_array("ACCENT_SEEDS") == [f"{a['seed'] & 0xFFFFFF:06X}" for a in accents.values()],
    "Settings palette seeds equal the token seeds",
    str(java_array("ACCENT_SEEDS")),
)
report(
    'android:authorities="org.sableos.appearance"' in settings_patch
    and "MODE_FOLLOW_SYSTEM" in settings_patch,
    "Settings hosts org.sableos.appearance and reports follow-system",
)

# --- branding ----------------------------------------------------------------

sw = OVERLAY / "SableSetupWizardOverlay/res"
names, leaks = set(), []
for strings in sorted(sw.glob("values*/strings.xml")):
    for el in ET.parse(strings).getroot():
        names.add(el.get("name"))
        value = "".join(el.itertext())
        if "SableOS" not in value or re.search(r"lineage|%\d|\$s", value, re.I):
            leaks.append(f"{strings.parent.name}: {value}")
report(names == {"setup_welcome_message"}, "SetupWizard overlay changes only the welcome title", str(names))
report(not leaks, "every welcome title names SableOS with no placeholder left", "; ".join(leaks[:5]))
report(
    (sw / "values/strings.xml").is_file() and len(list(sw.glob("values*/strings.xml"))) > 40,
    "welcome title overlaid in the default and translated locales",
)
logo = ET.parse(sw / "drawable/logo.xml").getroot()
shield = sw / "drawable-nodpi/sableos_shield.png"
report(
    logo.get(ANDROID_NS + "drawable") == "@drawable/sableos_shield"
    and shield.is_file()
    and hashlib.sha256(shield.read_bytes()).hexdigest() == SHIELD_SHA256,
    "SetupWizard logo is the recorded Sable shield asset",
)
owned = []
for f in OVERLAY.rglob("*.xml"):
    for el in ET.parse(f).getroot():
        n = el.get("name") or ""
        if re.search(r"legal|license|lineage|os_name|notice", n, re.I):
            owned.append(f"{f.relative_to(OVERLAY)}:{n}")
report(not owned, "no overlay replaces LineageOS legal, licence or attribution resources", "; ".join(owned))
about = next(SETTINGS_PATCHES.glob("0102-*.patch")).read_text(encoding="utf-8")
report(
    "sable_os_version" in about
    and "-            android:key=\"lineage_os_version\"" not in about
    and "-            android:key=\"legal_container\"" not in about,
    "About phone adds SableOS version and keeps LineageOS version and legal rows",
)

# --- density -----------------------------------------------------------------

mk = PRODUCT_MK.read_text(encoding="utf-8")
values = re.findall(r"^SABLE_LCD_DENSITY\s*:=\s*(\S*)\s*$", mk, re.M)
ok = len(values) == 1 and (values[0] == "" or (values[0].isdigit() and 120 <= int(values[0]) <= 640))
detail = f"{values}"
if ok and values[0]:
    detail += f" -> smallest width {720 * 160 // int(values[0])} dp on 720x720"
report(ok and "ro.sf.lcd_density=$(SABLE_LCD_DENSITY)" in mk, "display density is one valid value", detail)

print()
print("SABLE_DESIGN_CHECK=" + ("FAIL" if failures else "PASS"))
sys.exit(1 if failures else 0)
