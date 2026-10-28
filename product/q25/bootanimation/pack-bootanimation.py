#!/usr/bin/env python3
"""Packs frames/ into bootanimation.zip: desc.txt first, then the parts in order.

bootanimation reads the zip in place, so every entry is stored, not compressed.
Fixed timestamps keep the zip identical from build to build.
Usage: pack-bootanimation.py FRAMES_DIR OUT_ZIP
"""

import pathlib
import sys
import zipfile

STAMP = (2026, 1, 1, 0, 0, 0)


def main(frames, out):
    entries = [frames / "desc.txt"] + sorted(p for p in frames.rglob("*.png"))
    with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_STORED) as z:
        for path in entries:
            z.writestr(zipfile.ZipInfo(path.relative_to(frames).as_posix(), STAMP), path.read_bytes())


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit("usage: pack-bootanimation.py FRAMES_DIR OUT_ZIP")
    main(pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]))
