#!/usr/bin/env python3
"""Apply, revert or check the optional Q25 50 MP camera change in a LineageOS tree.

    python3 scripts/camera-50mp.py apply|revert|check [--android-root DIR]

The change comes from bellhopsw/q25-50mp (see product/q25/camera-50mp/README.md):

  kernel/xelex/mt6789   the s5kjn1 sensor driver gets the full 8160x6144 capture mode
                        (product/q25/camera-50mp/kernel/*.patch, GPL-2.0, applied with git apply)
  vendor/xelex/Q25      62 documented byte edits in three MediaTek camera libraries, and the
                        prebuilt libremosaic_wrapper stops being preferred so the MIT remosaic
                        library built from source (product/q25/camera-50mp, staged to
                        vendor/sable/q25/camera-50mp) replaces it

Every step checks exact sizes and bytes first and refuses on any mismatch, so a moved kernel or a
new blob drop fails here instead of producing a camera that does not work. Each step recognises
its own applied state, so apply and revert can be run again safely. No modified vendor file is
ever written outside the Android tree.

scripts/stage-product.sh runs `revert` before staging and `apply` when SABLE_Q25_CAMERA_50MP=YES.
Run `revert` before `repo sync` of kernel/xelex/mt6789 or vendor/xelex/Q25.
"""
import argparse
import glob
import os
import re
import subprocess
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KERNEL = "kernel/xelex/mt6789"
BLOBS = "vendor/xelex/Q25"
LIB_DIR = "proprietary/vendor/lib64"
WRAPPER = "libremosaic_wrapper"

H = bytes.fromhex
# The code cave in libmtkcam_featurepolicy.so: requests up to 4096 px wide decline the remosaic
# path, wider ones take it (disassembly in q25-50mp src/hal/PATCHES.md).
CAVE = H("f50300aa" "7f1200f1" "e184e554" "f00e40f9" "b084e5b4"
         "119641b9" "3f064071" "4c84e554" "f4031f2a" "4231ff17")

# library: (exact size, [(file offset, stock bytes, patched bytes), ...]); from q25-50mp
# tools/apply_lineage.py (MIT), checked against TheMuppets proprietary_vendor_xelex_Q25 b8c2c8f.
EDITS = {
    "libmtkcam_metastore.so": (655096, [
        (0x44741, H("4082"), H("fc83")),          # largest picture width 4608 -> 8160
        (0x44755, H("b081"), H("0083")),          # largest picture height 3456 -> 6144
    ]),
    "libmtkcam_3rdparty.mtk.so": (416632, [
        (0x3f048, H("e8140034"), H("1f2003d5")),  # vendor-tag check -> nop
        (0x4155d, H("1980"), H("9081")),          # ISO limit 200 -> 3200 (three places)
        (0x41695, H("1980"), H("9081")),
        (0x416d9, H("1980"), H("9081")),
    ]),
    "libmtkcam_featurepolicy.so": (441352, [
        (0x33c20, H("f50300aa"), H("d8d30014")),  # jump into the code cave
        (0x68b80, bytes(40), CAVE),
        (0xd0, H("807b"), H("007c")),             # executable LOAD segment covers the cave
        (0xd8, H("807b"), H("007c")),
    ]),
}


class Refuse(Exception):
    pass


def kernel_patches():
    found = sorted(glob.glob(os.path.join(REPO, "product/q25/camera-50mp/kernel/*.patch")))
    if not found:
        raise Refuse("no kernel patch in product/q25/camera-50mp/kernel")
    return found


def git_apply_ok(tree, patch, reverse=False):
    cmd = ["git", "-C", tree, "apply", "--check"] + (["--reverse"] if reverse else []) + [patch]
    return subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0


def kernel_state(root):
    """'stock', 'applied' or None when the kernel tree is missing."""
    tree = os.path.join(root, KERNEL)
    if not os.path.isdir(tree):
        return None
    states = set()
    for p in kernel_patches():
        if git_apply_ok(tree, p):
            states.add("stock")
        elif git_apply_ok(tree, p, reverse=True):
            states.add("applied")
        else:
            raise Refuse("%s neither applies to nor is applied in %s (kernel moved?)"
                         % (os.path.basename(p), KERNEL))
    if len(states) != 1:
        raise Refuse("kernel patches are partly applied in %s" % KERNEL)
    return states.pop()


def kernel_set(root, want):
    state = kernel_state(root)
    if state is None:
        if want == "applied":
            raise Refuse("no kernel tree at %s" % os.path.join(root, KERNEL))
        return "absent"
    if state == want:
        return want + " (already)"
    tree = os.path.join(root, KERNEL)
    patches = kernel_patches()
    for p in (patches if want == "applied" else reversed(patches)):
        cmd = ["git", "-C", tree, "apply"] + (["--reverse"] if want == "stock" else []) + [p]
        subprocess.run(cmd, check=True)
    return want


def blob_state(data, edits, size):
    if len(data) != size:
        raise Refuse("size %d, expected %d (another blob drop, or a Git LFS pointer?)"
                     % (len(data), size))
    old = all(data[o:o + len(a)] == a for o, a, _ in edits)
    new = all(data[o:o + len(b)] == b for o, _, b in edits)
    if old:
        return "stock"
    if new:
        return "applied"
    raise Refuse("bytes match neither the stock nor the patched library")


def blobs_states(root):
    out = {}
    for name, (size, edits) in EDITS.items():
        path = os.path.join(root, BLOBS, LIB_DIR, name)
        if not os.path.isfile(path):
            out[name] = None
            continue
        with open(path, "rb") as f:
            data = f.read()
        try:
            out[name] = blob_state(data, edits, size)
        except Refuse as e:
            raise Refuse("%s: %s" % (name, e))
    return out


def blobs_set(root, want):
    states = blobs_states(root)
    if all(s is None for s in states.values()) and want == "stock":
        return "absent"
    missing = [n for n, s in states.items() if s is None]
    if missing:
        raise Refuse("missing in %s/%s: %s" % (BLOBS, LIB_DIR, ", ".join(missing)))
    for name, state in states.items():
        if state == want:
            continue
        size, edits = EDITS[name]
        path = os.path.join(root, BLOBS, LIB_DIR, name)
        with open(path, "rb") as f:
            data = bytearray(f.read())
        for off, a, b in edits:
            data[off:off + len(a)] = b if want == "applied" else a
        # Overwrite in place: keeps the file's mode and its place in the vendor checkout.
        with open(path, "r+b") as f:
            f.write(data)
        if blob_state(bytes(data), edits, size) != want:
            raise Refuse("%s did not reach the %s state" % (name, want))
    return want


WRAPPER_BLOCK = re.compile(
    r'(cc_prebuilt_library_shared \{\n    name: "%s",\n.*?\n    prefer: )(true|false)(,\n)'
    % re.escape(WRAPPER), re.S)


def prefer_set(root, want):
    """Prebuilt libremosaic_wrapper preferred (stock) or not (applied: the source module wins)."""
    path = os.path.join(root, BLOBS, "Android.bp")
    if not os.path.isfile(path):
        if want == "applied":
            raise Refuse("no %s/Android.bp" % BLOBS)
        return "absent"
    with open(path) as f:
        text = f.read()
    blocks = [m for m in WRAPPER_BLOCK.finditer(text) if "\n}\n" not in m.group(1)]
    if len(blocks) != 1:
        raise Refuse("%s/Android.bp: expected one %s prebuilt with a prefer line, found %d"
                     % (BLOBS, WRAPPER, len(blocks)))
    m = blocks[0]
    value = "false" if want == "applied" else "true"
    if m.group(2) == value:
        return want + " (already)"
    with open(path, "w") as f:
        f.write(text[:m.start(2)] + value + text[m.end(2):])
    return want


def check(root):
    k = kernel_state(root)
    b = blobs_states(root)
    print("CAMERA_50MP_KERNEL=%s" % (k or "ABSENT").upper())
    for name, s in b.items():
        print("CAMERA_50MP_BLOB %s=%s" % (name, (s or "ABSENT").upper()))


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("action", choices=["apply", "revert", "check"])
    ap.add_argument("--android-root", default=os.environ.get("SABLE_ANDROID_ROOT"))
    a = ap.parse_args(argv)
    if not a.android_root or not os.path.isdir(a.android_root):
        print("camera-50mp: no Android tree (set SABLE_ANDROID_ROOT)", file=sys.stderr)
        return 2
    root = a.android_root
    try:
        if a.action == "check":
            check(root)
        elif a.action == "apply":
            # Check everything before writing anything.
            kernel_state(root)
            blobs_states(root)
            print("CAMERA_50MP_KERNEL=%s" % kernel_set(root, "applied"))
            print("CAMERA_50MP_BLOBS=%s" % blobs_set(root, "applied"))
            print("CAMERA_50MP_WRAPPER_PREBUILT=%s" % prefer_set(root, "applied"))
            print("CAMERA_50MP=APPLIED")
        else:
            print("CAMERA_50MP_WRAPPER_PREBUILT=%s" % prefer_set(root, "stock"))
            print("CAMERA_50MP_BLOBS=%s" % blobs_set(root, "stock"))
            print("CAMERA_50MP_KERNEL=%s" % kernel_set(root, "stock"))
            print("CAMERA_50MP=REVERTED")
    except Refuse as e:
        print("camera-50mp: STOP: %s" % e, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
