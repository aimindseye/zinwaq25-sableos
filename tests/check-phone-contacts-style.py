#!/usr/bin/env python3
"""Static checks for the Sable Phone/Contacts restyle (Dialer 0707-0708, Contacts 0755).

No Android tree, no network. Reads the patches and checks that:
  * every XML file the patches add is well-formed;
  * the Sable dark colours they add equal the token table
    (SableSystemTokens.Dark and SableTheme's dark outline tones), the
    same in both apps;
  * the accent is never hard-coded: sable_accent and its container come
    from the system palette (system_accent1_*), which the Sable accent seeds;
  * the focus outline uses the token geometry (2dp stroke, 12dp rows);
  * both apps are dark only: dark parents, never force-darkened, no
    -night colour files left to override the palette;
  * the theme's selectableItemBackground (and Contacts' list selector) is
    the Sable focus row;
  * the dial pad key stays a RippleDrawable (DialpadView casts it), End is
    the danger pill and the in-call controls use the Sable panel style.

Prints PASS/FAIL lines and exits non-zero on any failure.
"""
from __future__ import annotations

import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
PATCHES = ROOT / "patches/framework/packages/apps"
DESIGN = ROOT / "sable-src/src/android/shared/sabledesign/src/main/java/org/sableos/design"

failures = 0


def report(ok: bool, name: str, detail: str = "") -> None:
    global failures
    if ok:
        print(f"PASS  {name}")
    else:
        failures += 1
        print(f"FAIL  {name}{': ' + detail if detail else ''}")


def one(pattern: str) -> pathlib.Path | None:
    found = sorted(PATCHES.glob(pattern))
    return found[0] if len(found) == 1 else None


def parse_patch(path: pathlib.Path) -> dict[str, dict]:
    """Per file: added lines, removed lines, whether it is new or deleted."""
    files: dict[str, dict] = {}
    cur = None
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("diff --git "):
            name = line.split(" b/", 1)[1]
            cur = files.setdefault(name, {"added": [], "removed": [], "new": False, "deleted": False})
        elif cur is None:
            continue
        elif line.startswith("new file mode"):
            cur["new"] = True
        elif line.startswith("deleted file mode"):
            cur["deleted"] = True
        elif line.startswith("+++ ") or line.startswith("--- "):
            continue
        elif line.startswith("+"):
            cur["added"].append(line[1:])
        elif line.startswith("-"):
            cur["removed"].append(line[1:])
    return files


dialer = [one("Dialer/0707-*.patch"), one("Dialer/0708-*.patch")]
contacts = [one("Contacts/0755-*.patch")]
report(all(dialer) and all(contacts), "restyle patches present (Dialer 0707, 0708; Contacts 0755)")
if not (all(dialer) and all(contacts)):
    sys.exit(1)

d_files: dict[str, dict] = {}
for p in dialer:
    for k, v in parse_patch(p).items():
        prev = d_files.get(k)
        if prev:
            prev["added"] += v["added"]
            prev["removed"] += v["removed"]
            prev["new"] |= v["new"]
            prev["deleted"] |= v["deleted"]
        else:
            d_files[k] = v
c_files = parse_patch(contacts[0])


def added_text(files: dict, suffix: str) -> str:
    hits = [v for k, v in files.items() if k.endswith(suffix)]
    return "\n".join(l for v in hits for l in v["added"])


# 1. New XML files are well-formed.
bad = []
for app, files in (("Dialer", d_files), ("Contacts", c_files)):
    for name, v in files.items():
        if v["new"] and name.endswith(".xml"):
            try:
                ET.fromstring("\n".join(v["added"]).encode())
            except ET.ParseError as e:
                bad.append(f"{app}:{name}: {e}")
report(not bad, "new Phone/Contacts XML files are well-formed", "; ".join(bad))


def colors(text: str) -> dict[str, str]:
    return dict(re.findall(r'<color name="(sable_[a-z_]+)">([^<]+)</color>', text))


d_colors = colors(added_text(d_files, "values/sable_colors.xml"))
c_colors = colors(added_text(c_files, "values/sable_colors.xml"))

# 2. Colours equal the token table, and both apps agree.
tokens = (DESIGN / "SableSystemTokens.kt").read_text(encoding="utf-8")
dark = re.search(r"val Dark =\s*SableColorRoles\((.*?)\)", tokens, re.S)
roles = dict(re.findall(r"(\w+) = 0x([0-9A-Fa-f]{8})", dark.group(1))) if dark else {}
theme = (DESIGN / "SableTheme.kt").read_text(encoding="utf-8")
outlines = re.search(r"darkColorScheme\((.*?)\n\s*\)", theme, re.S)
outline = dict(re.findall(r"(outline(?:Variant)?) = Color\(0x([0-9A-Fa-f]{8})\)", outlines.group(1))) if outlines else {}
want = {
    "sable_background": roles.get("surfaceBase"),
    "sable_surface": roles.get("surfaceRaised"),
    "sable_surface_raised": roles.get("surfaceOverlay"),
    "sable_text": roles.get("textPrimary"),
    "sable_text_muted": roles.get("textSecondary"),
    "sable_text_disabled": roles.get("textDisabled"),
    "sable_call": roles.get("success"),
    "sable_danger": roles.get("danger"),
    "sable_outline": outline.get("outline"),
    "sable_outline_variant": outline.get("outlineVariant"),
}
mism = [
    f"{k}={d_colors.get(k)} (token {v})"
    for k, v in want.items()
    if not v or d_colors.get(k, "").upper() != "#" + v.upper()
]
report(not mism, "Sable dark colours equal SableSystemTokens.Dark and SableTheme outlines", "; ".join(mism))
report(bool(d_colors) and d_colors == c_colors, "Phone and Contacts carry the same Sable palette")

# 3. Accent from the system palette, never a hex value.
acc = {k: v for k, v in d_colors.items() if "accent" in k}
report(
    len(acc) >= 3 and all(re.fullmatch(r"@android:color/system_accent1_\d+", v) for v in acc.values()),
    "accent tokens come from system_accent1_* (no hard-coded hue)",
    str(acc),
)
hexes = []
for app, files in (("Dialer", d_files), ("Contacts", c_files)):
    for name, v in files.items():
        for l in v["added"]:
            if re.search(r'name="(?:\w*accent\w*|primary_color|dialer_theme_color\w*|colorAccent)"[^>]*>\s*#', l):
                hexes.append(f"{app}:{name}")
report(not hexes, "no accent or theme colour is set to a hex value", ", ".join(hexes))

# 4. Focus outline geometry.
geo = (DESIGN / "SableSystemTokens.kt").read_text(encoding="utf-8")
stroke = re.search(r"FOCUS_STROKE_DP = (\d+)", geo)
card = re.search(r"SHAPE_MEDIUM_DP = (\d+)", geo)
for app, files in (("Dialer", d_files), ("Contacts", c_files)):
    dims = dict(re.findall(r'<dimen name="(sable_[a-z_]+)">([^<]+)</dimen>', added_text(files, "sable_dimens.xml")))
    focus = added_text(files, "drawable/sable_focus_outline.xml")
    ok = (
        stroke and card
        and dims.get("sable_focus_stroke") == f"{stroke.group(1)}dp"
        and dims.get("sable_row_radius") == f"{card.group(1)}dp"
        and 'android:state_focused="true"' in focus
        and "@color/sable_accent" in focus
        and "@dimen/sable_focus_stroke" in focus
    )
    report(bool(ok), f"{app}: focused rows get a {stroke.group(1) if stroke else '?'}dp accent outline with token radius", str(dims))

# 5. Dark only.
d_theme = added_text(d_files, "theme/hidden/res/values/styles.xml")
report('<style name="Dialer" parent="Theme.AppCompat">' in d_theme, "Phone base theme is dark AppCompat")
c_v27 = added_text(c_files, "res/values-v27/styles.xml")
report(
    '<style name="PeopleTheme" parent="@android:style/Theme.Material">' in c_v27
    and '<style name="PeopleThemeAppCompat" parent="Theme.AppCompat">' in c_v27,
    "Contacts themes are dark Material/AppCompat",
)
forced = [
    f"{app}:{n}"
    for app, files in (("Dialer", d_files), ("Contacts", c_files))
    for n, v in files.items()
    if any('forceDarkAllowed">true' in l for l in v["added"])
]
report(not forced, "no restyled theme asks to be force-darkened", ", ".join(forced))
night_left = [
    n for n, v in list(d_files.items()) + list(c_files.items())
    if "-night/" in n and not v["deleted"]
]
night_gone = [
    n for n, v in list(d_files.items()) + list(c_files.items()) if "-night/" in n and v["deleted"]
]
report(not night_left and len(night_gone) >= 8, "-night colour and theme files removed (one palette)", ", ".join(night_left))

# 6. Focus background in the themes.
d_base = added_text(d_files, "theme/base/res/values/theme_dialer_light.xml")
report(
    '<item name="android:selectableItemBackground">@drawable/sable_selectable_item</item>' in d_base
    and '<item name="selectableItemBackground">@drawable/sable_selectable_item</item>' in d_base,
    "Phone theme selectableItemBackground is the Sable focus row",
)
c_styles = added_text(c_files, "res/values/styles.xml")
report(
    '<item name="android:listSelector">@drawable/sable_selectable_item</item>' in c_styles
    and "@drawable/sable_selectable_item" in c_v27,
    "Contacts list selector and selectableItemBackground are the Sable focus row",
)
call_log = added_text(d_files, "app/res/layout/call_log_list_item.xml")
report('android:background="?android:attr/selectableItemBackground"' in call_log, "Recents caller row shows the focus outline")

# 7. Controls.
key = d_files.get("java/com/android/dialer/dialpadview/res/drawable/btn_dialpad_key.xml", {"added": []})
key_text = "\n".join(key["added"])
report(not any("<ripple" in l for l in key.get("removed", [])) and "@android:id/mask" in key_text
       and "state_focused" in key_text,
       "dial pad key stays a RippleDrawable with a focus border")
common = added_text(d_files, "incallui/commontheme/res/values/styles.xml")
grid = added_text(d_files, "incallui/incall/impl/res/layout/incall_button_grid.xml")
report(
    "@drawable/sable_end_call_pill" in common
    and "@dimen/sable_end_call_width" in common
    and grid.count('style="@style/SableInCallControl"') == 6,
    "in-call End is the danger pill and the six controls are Sable panels",
)
toolbar = added_text(d_files, "main/impl/toolbar/res/layout/toolbar_layout.xml")
report('android:text="@string/applicationLabel"' in toolbar and "SableScreenTitle" in toolbar,
       "Phone header title in the ScreenTitle style")

print(f"{'FAIL' if failures else 'PASS'}  phone-contacts style: {failures} failure(s)")
sys.exit(1 if failures else 0)
