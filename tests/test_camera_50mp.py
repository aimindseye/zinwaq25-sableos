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


if __name__ == "__main__":
    unittest.main()
