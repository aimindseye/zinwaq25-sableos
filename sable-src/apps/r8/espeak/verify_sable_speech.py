#!/usr/bin/env python3
"""Check the composed Sable Speech tree before it is built."""

from __future__ import annotations

import pathlib
import subprocess
import sys

EXPECTED_COMMIT = "4870adfa25b1a32b4361592f1be8a40337c58d6c"


def fail(marker: str, detail: str = "") -> None:
    raise SystemExit(f"{marker}=FAIL{' ' + detail if detail else ''}")


def main() -> None:
    if len(sys.argv) != 2:
        fail("SABLE_SPEECH_VERIFY_USAGE", "verify_sable_speech.py <root>")
    root = pathlib.Path(sys.argv[1]).resolve()
    actual = subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip()
    if actual != EXPECTED_COMMIT:
        fail("SABLE_SPEECH_UPSTREAM_COMMIT", f"actual={actual}")

    build = (root / "android/build.gradle").read_text(encoding="utf-8")
    manifest = (root / "android/AndroidManifest.xml").read_text(encoding="utf-8")
    if 'applicationId "org.sableos.speech"' not in build:
        fail("SABLE_SPEECH_APPLICATION_ID")
    if 'namespace "com.reecedunn.espeak"' not in build:
        fail("SABLE_SPEECH_NAMESPACE")
    if "abiFilters 'arm64-v8a'" not in build:
        fail("SABLE_SPEECH_ABI")
    if "uses-permission" in manifest:
        fail("SABLE_SPEECH_PERMISSIONS", "the engine must request no permission")
    if "android.intent.category.LAUNCHER" in manifest:
        fail("SABLE_SPEECH_LAUNCHER")
    if 'android.intent.action.TTS_SERVICE' not in manifest:
        fail("SABLE_SPEECH_TTS_SERVICE")
    print("SABLE_SPEECH_UPSTREAM=PASS_1.52.0")
    print("SABLE_SPEECH_PERMISSIONS=PASS_NONE")
    print("SABLE_SPEECH_TTS_SERVICE=PASS")


if __name__ == "__main__":
    main()
