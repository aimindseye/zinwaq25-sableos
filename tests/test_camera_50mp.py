#!/usr/bin/env python3
"""Offline tests for scripts/camera-50mp.py on a synthetic vendor tree (no real blobs, no kernel)."""
import importlib.util
import os
import shutil
import subprocess
import tempfile
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
spec = importlib.util.spec_from_file_location("camera50mp", os.path.join(ROOT, "scripts/camera-50mp.py"))
cam = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cam)

BP = """cc_prebuilt_library_shared {
    name: "libmtkcam_metastore",
    owner: "xelex",
    compile_multilib: "64",
    prefer: true,
    soc_specific: true,
}

cc_prebuilt_library_shared {
    name: "libremosaic_wrapper",
    owner: "xelex",
    strip: {
        none: true,
    },
    compile_multilib: "64",
    prefer: true,
    soc_specific: true,
}

cc_prebuilt_library_shared {
    name: "libremosaic_other",
    prefer: true,
}
"""


def stock_blob(size, edits):
    data = bytearray(size)
    for off, old, _ in edits:
        data[off:off + len(old)] = old
    return bytes(data)


class Camera50MpTest(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.mkdtemp()
        self.lib = os.path.join(self.root, cam.BLOBS, cam.LIB_DIR)
        os.makedirs(self.lib)
        for name, (size, edits) in cam.EDITS.items():
            with open(os.path.join(self.lib, name), "wb") as f:
                f.write(stock_blob(size, edits))
        with open(os.path.join(self.root, cam.BLOBS, "Android.bp"), "w") as f:
            f.write(BP)

    def tearDown(self):
        shutil.rmtree(self.root)

    def read(self, name):
        with open(os.path.join(self.lib, name), "rb") as f:
            return f.read()

    def test_edit_table_is_the_documented_62_bytes(self):
        self.assertEqual(62, sum(len(b) for _, edits in cam.EDITS.values() for _, _, b in edits))
        for _, edits in cam.EDITS.values():
            for _, old, new in edits:
                self.assertEqual(len(old), len(new))

    def test_blobs_apply_revert_round_trip(self):
        before = {n: self.read(n) for n in cam.EDITS}
        self.assertEqual("applied", cam.blobs_set(self.root, "applied"))
        for name, (_, edits) in cam.EDITS.items():
            data = self.read(name)
            self.assertEqual(len(before[name]), len(data))
            for off, _, new in edits:
                self.assertEqual(new, data[off:off + len(new)])
        self.assertEqual("applied", cam.blobs_set(self.root, "applied"))  # idempotent
        cam.blobs_set(self.root, "stock")
        self.assertEqual(before, {n: self.read(n) for n in cam.EDITS})

    def test_wrong_size_or_bytes_refuse_without_writing(self):
        path = os.path.join(self.lib, "libmtkcam_metastore.so")
        with open(path, "ab") as f:
            f.write(b"\0")
        with self.assertRaises(cam.Refuse):
            cam.blobs_set(self.root, "applied")
        self.setUp_one_bad_byte()
        before = {n: self.read(n) for n in cam.EDITS}
        with self.assertRaises(cam.Refuse):
            cam.blobs_states(self.root)
        self.assertEqual(before, {n: self.read(n) for n in cam.EDITS})

    def setUp_one_bad_byte(self):
        size, edits = cam.EDITS["libmtkcam_metastore.so"]
        data = bytearray(stock_blob(size, edits))
        data[edits[0][0]] ^= 0xFF
        with open(os.path.join(self.lib, "libmtkcam_metastore.so"), "wb") as f:
            f.write(data)

    def test_prefer_flips_only_the_wrapper_prebuilt(self):
        bp = os.path.join(self.root, cam.BLOBS, "Android.bp")
        self.assertEqual("applied", cam.prefer_set(self.root, "applied"))
        with open(bp) as f:
            text = f.read()
        self.assertEqual(BP.replace(
            'name: "libremosaic_wrapper",\n    owner: "xelex",\n    strip: {\n        none: true,\n    },\n'
            '    compile_multilib: "64",\n    prefer: true',
            'name: "libremosaic_wrapper",\n    owner: "xelex",\n    strip: {\n        none: true,\n    },\n'
            '    compile_multilib: "64",\n    prefer: false'), text)
        self.assertEqual("applied (already)", cam.prefer_set(self.root, "applied"))
        cam.prefer_set(self.root, "stock")
        with open(bp) as f:
            self.assertEqual(BP, f.read())

    def test_missing_kernel_tree(self):
        # revert is a no-op without a kernel tree (fake trees in tests/run.sh); apply refuses.
        self.assertEqual("absent", cam.kernel_set(self.root, "stock"))
        with self.assertRaises(cam.Refuse):
            cam.kernel_set(self.root, "applied")

    def test_kernel_tree_that_moved_refuses(self):
        tree = os.path.join(self.root, cam.KERNEL)
        os.makedirs(tree)
        subprocess.run(["git", "-C", tree, "init", "-q"], check=True)
        with self.assertRaises(cam.Refuse):
            cam.kernel_state(self.root)

    def test_apply_checks_everything_before_writing(self):
        # No kernel tree: apply must stop before touching the blobs.
        before = {n: self.read(n) for n in cam.EDITS}
        self.assertEqual(1, cam.main(["apply", "--android-root", self.root]))
        self.assertEqual(before, {n: self.read(n) for n in cam.EDITS})

    def test_revert_on_an_empty_tree_is_a_no_op(self):
        empty = tempfile.mkdtemp()
        try:
            self.assertEqual(0, cam.main(["revert", "--android-root", empty]))
        finally:
            shutil.rmtree(empty)

    def test_kernel_patch_keeps_crlf(self):
        (patch,) = cam.kernel_patches()
        with open(patch, "rb") as f:
            self.assertIn(b"\r\n", f.read())


SHIM = os.path.join(ROOT, "product/q25/camera-50mp/remosaic_shim_v4_rc1.c")
SHIM_DRIVER = r"""
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
int remosaic_init(int, int, int, int);
int remosaic_process(void *, unsigned long, void *, unsigned long);
int __android_log_print(int p, const char *t, const char *f, ...) { (void)p; (void)t; (void)f; return 0; }
int __system_property_get(const char *n, char *v)   /* defaults, but no calibration file on the host */
{ if (strcmp(n, "persist.vendor.remoshim.usecal") == 0) { strcpy(v, "0"); return 1; } v[0] = 0; return 0; }
int main(void)
{
    enum { W = 200, H = 136, S = 208 };
    static uint16_t in[S * H], out[S * H];
    unsigned r = 1;
    for (int y = 0; y < H; y++)
        for (int x = 0; x < S; x++) { r = r * 1103515245u + 12345u; in[y * S + x] = (uint16_t)(64 + (x * 3 + y * 2) % 600 + (r >> 16) % 9); }
    if (remosaic_init(W, H, 2, 64) != 0 || remosaic_process(in, sizeof in, out, sizeof out) != 0) return 1;
    if (remosaic_process(NULL, sizeof in, out, sizeof out) != -1 || remosaic_process(in, sizeof in, out, 2) != -1) return 2;
    for (int y = 0; y < H; y++)
        for (int x = 0; x < W; x++) if (out[y * S + x] > 1023) return 3;
    fwrite(out, sizeof out, 1, stdout);
    return 0;
}
"""


class RemosaicShimHostTest(unittest.TestCase):
    """The remosaic library builds warning-free with the Android.bp flags and runs a small frame on the host."""

    def test_builds_and_runs_on_the_host(self):
        cc = shutil.which("clang") or shutil.which("gcc")
        if not cc:
            self.skipTest("no host C compiler")
        tmp = tempfile.mkdtemp()
        try:
            for name, text in {
                "android/log.h": "#define ANDROID_LOG_INFO 4\n#define ANDROID_LOG_ERROR 6\n"
                                 "int __android_log_print(int, const char *, const char *, ...);\n",
                "sys/system_properties.h": "#define PROP_VALUE_MAX 92\nint __system_property_get(const char *, char *);\n",
                "driver.c": SHIM_DRIVER,
            }.items():
                os.makedirs(os.path.dirname(os.path.join(tmp, name)), exist_ok=True)
                with open(os.path.join(tmp, name), "w") as f:
                    f.write(text)
            flags = ["-O3", "-Wall", "-Werror", "-Wno-unused-function", "-pthread", "-I" + tmp]
            if "gcc" in os.path.basename(cc):
                flags.append("-Wno-format-truncation")   # gcc only: the shim truncates its log lines on purpose
            exe = os.path.join(tmp, "shim")
            subprocess.run([cc, *flags, SHIM, os.path.join(tmp, "driver.c"), "-lm", "-o", exe], check=True)
            first = subprocess.run([exe], capture_output=True, check=True).stdout
            self.assertEqual(first, subprocess.run([exe], capture_output=True, check=True).stdout)   # deterministic
            self.assertEqual(208 * 136 * 2, len(first))
        finally:
            shutil.rmtree(tmp)


if __name__ == "__main__":
    unittest.main()
