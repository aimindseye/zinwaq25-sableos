#!/usr/bin/env python3
"""Generates the product-lane first-party icon drawables from the pinned Phosphor glyphs.

Usage: gen_first_party_icons.py [--check]
Reads third_party/phosphor-icons/ICON_MANIFEST.tsv and glyphs/*.svg (verbatim Phosphor regular SVGs at the commit
pinned in third_party/phosphor-icons/PROVENANCE.md). Writes apps/titan2/platform/<module>/src/main/res/drawable/<drawable>.xml
in the SableOS R9 language: 256x256 viewport, #161C24 tonal rounded background, #F7F9FC glyph at 0.70 scale,
per-app accent bottom rail. With --check, writes nothing and exits 1 if any file differs.
"""
import hashlib
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PH = os.path.join(ROOT, "third_party", "phosphor-icons")
BG_PATH = "M32,16h192c8.8,0 16,7.2 16,16v192c0,8.8 -7.2,16 -16,16H32c-8.8,0 -16,-7.2 -16,-16V32c0,-8.8 7.2,-16 16,-16z"
RAIL_PATH = "M28,226h200v6H28z"

TEMPLATE = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="256"
    android:viewportHeight="256">
    <path
        android:fillColor="#161C24"
        android:pathData="%(bg)s" />
    <group
        android:scaleX="0.70"
        android:scaleY="0.70"
        android:translateX="38.4"
        android:translateY="34">
        <path
            android:fillColor="#F7F9FC"
            android:pathData="%(glyph)s" />
    </group>
    <path
        android:fillColor="%(accent)s"
        android:pathData="%(rail)s" />
</vector>
"""


def rows():
    lines = open(os.path.join(PH, "ICON_MANIFEST.tsv"), encoding="utf8").read().strip().split("\n")
    head = lines[0].split("\t")
    return [dict(zip(head, ln.split("\t"))) for ln in lines[1:]]


def glyph_path(name, want_sha):
    data = open(os.path.join(PH, "glyphs", name + ".svg"), "rb").read()
    if hashlib.sha256(data).hexdigest() != want_sha:
        sys.exit("ICON_GENERATOR_FAIL: glyph %s sha256 differs from ICON_MANIFEST.tsv" % name)
    d = re.findall(r'<path d="([^"]+)"', data.decode("utf8"))
    if len(d) != 1 or 'viewBox="0 0 256 256"' not in data.decode("utf8"):
        sys.exit("ICON_GENERATOR_FAIL: glyph %s must be a single path on a 256x256 viewBox" % name)
    return d[0]


def render(r):
    return TEMPLATE % {"bg": BG_PATH, "glyph": glyph_path(r["glyph"], r["glyph_svg_sha256"]), "accent": r["accent"], "rail": RAIL_PATH}


def target(r):
    return os.path.join(ROOT, "apps/titan2/platform", r["module"], "src/main/res/drawable", r["drawable"] + ".xml")


def main(check):
    bad = 0
    for r in rows():
        out = render(r)
        p = target(r)
        if check:
            if not os.path.isfile(p) or open(p, encoding="utf8").read() != out:
                print("ICON_DRIFT %s" % p)
                bad += 1
        else:
            os.makedirs(os.path.dirname(p), exist_ok=True)
            open(p, "w", encoding="utf8", newline="\n").write(out)
    if check:
        sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main("--check" in sys.argv)
