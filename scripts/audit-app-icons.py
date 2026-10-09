#!/usr/bin/env python3
"""Static icon/launch audit of the Sable apps in product/q25/apps.tsv (SableOS #83).

For every enabled row it reads the app's source manifest(s) and resources in
sable-src/ (nothing is built) and reports:

  launcher    launcher activities (MAIN + LAUNCHER in an <activity>/<activity-alias>),
              or "headless" when the module is listed in HEADLESS
  label       application/launcher label, resolved through values/strings.xml or a
              Gradle resValue
  icon        android:icon resolves to a resource in the module and is not an
              upstream/placeholder icon (ic_launcher, sym_def_app_icon)
  round       android:roundIcon resolves, or the icon itself is adaptive
  adaptive    the icon resolves to an <adaptive-icon>
  mono        that adaptive icon has a <monochrome> layer (Android 13+ themed icons)

Rows built as a Sable flavor over a pinned upstream (gradle_root flavor:NAME) have
no manifest in the tree; their apply_sable_flavor.py is scanned for the icon,
roundIcon and label it writes, and the row is marked "flavor".

Checks in --enforce (default: launcher,label,icon) make the exit status 1 when they
fail; the others are reported only. Launch timing needs a device and is not here.
"""
from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"
ALL_CHECKS = ("launcher", "label", "icon", "round", "adaptive", "mono")
PLACEHOLDER = re.compile(r"(^@mipmap/ic_launcher(_round)?$)|sym_def_app_icon|(^@android:)")
# Modules that intentionally have no launcher entry (none today). Any extra launcher entry beyond the first is
# reported, since a diagnostic in the app drawer is a product defect.
HEADLESS: set[str] = set()


def read_rows(apps_tsv: Path) -> list[dict[str, str]]:
    rows = []
    cols = None
    for line in apps_tsv.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        if parts[0] == "module":
            cols = parts
            continue
        if cols is None or len(parts) != len(cols):
            continue
        rows.append(dict(zip(cols, parts)))
    return rows


def module_dir(src: Path, row: dict[str, str]) -> Path | None:
    root = row["gradle_root"]
    if root.startswith("flavor:"):
        return None
    task = row["gradle_task"].split()[0]
    module = task.strip(":").split(":")[0]
    return src / root / module


def flavor_of(row: dict[str, str]) -> str | None:
    """Gradle product flavor named in the task, e.g. :games:assembleSudokuDebug -> sudoku."""
    m = re.match(r":[^:]+:assemble([A-Z][a-z0-9]*?)(Debug|Release)$", row["gradle_task"].split()[0])
    if not m:
        return None
    name = m.group(1)
    return name[0].lower() + name[1:]


def res_dirs(mod: Path, flavor: str | None) -> list[Path]:
    """Flavor resources first (they override main)."""
    dirs = []
    if flavor:
        dirs.append(mod / "src" / flavor / "res")
    dirs.append(mod / "src" / "main" / "res")
    return [d for d in dirs if d.is_dir()]


def resolve(ref: str | None, dirs: list[Path]) -> list[Path]:
    if not ref:
        return []
    m = re.match(r"^@(drawable|mipmap)/([A-Za-z0-9_]+)$", ref)
    if not m:
        return []
    kind, name = m.groups()
    found = []
    for d in dirs:
        for sub in sorted(d.glob(f"{kind}*")):
            for ext in (".xml", ".png", ".webp"):
                f = sub / (name + ext)
                if f.is_file():
                    found.append(f)
    return found


def flavor_block(gradle: str, flavor: str | None) -> str:
    """The body of create("<flavor>") { ... } in build.gradle.kts, or the whole file."""
    if not flavor:
        return gradle
    m = re.search(r'create\(\s*"%s"\s*\)\s*\{' % re.escape(flavor), gradle)
    if not m:
        return gradle
    depth, i = 1, m.end()
    while i < len(gradle) and depth:
        depth += {"{": 1, "}": -1}.get(gradle[i], 0)
        i += 1
    return gradle[m.end():i]


def string_value(ref: str | None, dirs: list[Path], gradle: str) -> str | None:
    if not ref:
        return None
    if not ref.startswith("@string/"):
        return ref
    name = ref.split("/", 1)[1]
    for d in dirs:
        f = d / "values" / "strings.xml"
        if f.is_file():
            for s in ET.parse(f).getroot().iter("string"):
                if s.get("name") == name:
                    return "".join(s.itertext())
    m = re.search(r'resValue\(\s*"string"\s*,\s*"%s"\s*,\s*"([^"]+)"' % re.escape(name), gradle)
    return m.group(1) if m else None


def launcher_activities(app: ET.Element) -> list[str]:
    out = []
    for act in list(app.iter("activity")) + list(app.iter("activity-alias")):
        for f in act.iter("intent-filter"):
            actions = {a.get(ANDROID + "name") for a in f.iter("action")}
            cats = {c.get(ANDROID + "name") for c in f.iter("category")}
            if "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in cats:
                if act.get(ANDROID + "enabled") != "false":
                    out.append(act.get(ANDROID + "name") or "?")
    return out


def adaptive_info(files: list[Path]) -> tuple[bool, bool]:
    adaptive = mono = False
    for f in files:
        if f.suffix != ".xml":
            continue
        root = ET.parse(f).getroot()
        if root.tag == "adaptive-icon":
            adaptive = True
            mono = mono or root.find("monochrome") is not None
    return adaptive, mono


def audit_module(src: Path, row: dict[str, str]) -> dict[str, object]:
    mod = module_dir(src, row)
    res: dict[str, object] = {"module": row["module"], "package": row["package"], "notes": []}
    manifest = mod / "src" / "main" / "AndroidManifest.xml" if mod else None
    if manifest is None or not manifest.is_file():
        res["notes"].append("no manifest at " + (str(manifest.relative_to(src)) if manifest else "?"))
        for c in ALL_CHECKS:
            res[c] = False
        return res
    flavor = flavor_of(row)
    dirs = res_dirs(mod, flavor)
    gradle_file = mod / "build.gradle.kts"
    gradle = gradle_file.read_text(encoding="utf-8") if gradle_file.is_file() else ""
    app = ET.parse(manifest).getroot().find("application")
    if app is None:
        res["notes"].append("no <application>")
        for c in ALL_CHECKS:
            res[c] = False
        return res

    launchers = launcher_activities(app)
    headless = row["module"] in HEADLESS
    res["launcher"] = bool(launchers) or headless
    res["launcher_detail"] = "headless" if headless and not launchers else str(len(launchers))
    if len(launchers) > 1:
        res["notes"].append("extra launcher entries: " + ", ".join(launchers[1:]))

    label = string_value(app.get(ANDROID + "label"), dirs, flavor_block(gradle, flavor))
    res["label"] = bool(label and label.strip())
    res["label_detail"] = label or "-"

    icon_ref = app.get(ANDROID + "icon")
    icon_files = resolve(icon_ref, dirs)
    placeholder = bool(icon_ref and PLACEHOLDER.search(icon_ref))
    res["icon"] = bool(icon_files) and not placeholder
    res["icon_detail"] = icon_ref or "-"
    res["icon_file"] = icon_files[0] if icon_files else None
    if flavor and icon_files and ("/src/%s/" % flavor) in str(icon_files[0]):
        res["notes"].append("icon from src/%s" % flavor)
    if placeholder:
        res["notes"].append("placeholder/upstream icon " + str(icon_ref))
    elif icon_ref and not icon_files:
        res["notes"].append("icon does not resolve: " + icon_ref)

    adaptive, mono = adaptive_info(icon_files)
    round_ref = app.get(ANDROID + "roundIcon")
    round_files = resolve(round_ref, dirs)
    r_adaptive, r_mono = adaptive_info(round_files)
    res["round"] = bool(round_files) or adaptive
    res["adaptive"] = adaptive
    res["mono"] = mono and (not round_files or r_mono or not r_adaptive)
    if round_files and round_ref == icon_ref:
        res["notes"].append("roundIcon = icon")
    if not adaptive:
        res["notes"].append("icon is a plain " + ("vector" if any(f.suffix == ".xml" for f in icon_files) else "bitmap"))
    return res


def audit_flavor(src: Path, row: dict[str, str]) -> dict[str, object]:
    name = row["gradle_root"].split(":", 1)[1]
    spec = src / "apps" / "r8" / name / "apply_sable_flavor.py"
    res: dict[str, object] = {"module": row["module"], "package": row["package"], "notes": ["flavor: upstream manifest not in tree"]}
    text = spec.read_text(encoding="utf-8") if spec.is_file() else ""
    icon = re.findall(r'android:icon="(@drawable/[A-Za-z0-9_]+)"', text)
    rnd = re.findall(r'android:roundIcon="(@drawable/[A-Za-z0-9_]+)"', text)
    icon_ref = icon[-1] if icon else None
    writes_icon = bool(icon_ref and (icon_ref.split("/")[1] + ".xml") in text)
    res["launcher"] = True
    res["launcher_detail"] = "upstream"
    res["label"] = row["package"] in text or "app_name" in text or "LABEL" in text
    res["label_detail"] = "flavor"
    res["icon"] = writes_icon
    res["icon_detail"] = icon_ref or "-"
    res["round"] = bool(rnd)
    res["adaptive"] = "<adaptive-icon" in text
    res["mono"] = "<monochrome" in text
    if not rnd:
        res["notes"].append("no Sable roundIcon written (launchers fall back to icon)")
    if not res["adaptive"]:
        res["notes"].append("icon is a plain vector")
    return res


def mark(v: object) -> str:
    return "yes" if v else "NO"


def main() -> int:
    repo = Path(__file__).resolve().parent.parent
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--src", type=Path, default=repo / "sable-src")
    p.add_argument("--apps", type=Path, default=repo / "product" / "q25" / "apps.tsv")
    p.add_argument("--enforce", default="launcher,label,icon",
                   help="comma-separated checks that fail the run (from: %s); 'none' for report only" % ",".join(ALL_CHECKS))
    p.add_argument("--all", action="store_true", help="include rows with enabled=no")
    args = p.parse_args()
    enforce = [] if args.enforce == "none" else [c for c in args.enforce.split(",") if c]
    unknown = [c for c in enforce if c not in ALL_CHECKS]
    if unknown:
        p.error("unknown checks: " + ",".join(unknown))

    rows = [r for r in read_rows(args.apps) if args.all or r.get("enabled") == "yes"]
    results = [audit_flavor(args.src, r) if r["gradle_root"].startswith("flavor:") else audit_module(args.src, r) for r in rows]

    by_content: dict[bytes, list[str]] = {}
    for r in results:
        f = r.get("icon_file")
        if isinstance(f, Path):
            by_content.setdefault(f.read_bytes(), []).append(str(r["module"]))
    for r in results:
        f = r.get("icon_file")
        if isinstance(f, Path):
            others = [m for m in by_content[f.read_bytes()] if m != r["module"]]
            if others:
                r["notes"].append("same icon as " + ", ".join(others))

    header = ["module", "package", "launcher", "label", "icon", "round", "adaptive", "mono", "notes"]
    print("\t".join(header))
    failures = []
    for r in results:
        cells = [
            str(r["module"]), str(r["package"]),
            "%s(%s)" % (mark(r["launcher"]), r.get("launcher_detail", "-")),
            "%s(%s)" % (mark(r["label"]), r.get("label_detail", "-")),
            "%s(%s)" % (mark(r["icon"]), r.get("icon_detail", "-")),
            mark(r["round"]), mark(r["adaptive"]), mark(r["mono"]),
            "; ".join(r["notes"]) or "-",
        ]
        print("\t".join(cells))
        failures += ["%s:%s" % (r["module"], c) for c in enforce if not r[c]]
    totals = {c: sum(1 for r in results if r[c]) for c in ALL_CHECKS}
    print()
    print("AUDIT_APPS=%d" % len(results))
    for c in ALL_CHECKS:
        print("AUDIT_%s=%d/%d%s" % (c.upper(), totals[c], len(results), "" if c in enforce else " (report only)"))
    if failures:
        print("AUDIT=FAIL " + " ".join(failures))
        return 1
    print("AUDIT=PASS (enforced: %s)" % (",".join(enforce) or "none"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
