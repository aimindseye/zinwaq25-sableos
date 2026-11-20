#!/usr/bin/env python3
"""Compose Sable Speech from the pinned eSpeak NG 1.52.0 Android engine.

The upstream checkout is ephemeral build input. Sable keeps the engine as it is
(an offline text-to-speech service with its bundled voice data) and changes only
what the product needs:

* application id org.sableos.speech, so a user-installed eSpeak NG never clashes
  with the system copy;
* arm64-v8a only (the Q25 has no other ABI), which keeps the system image small;
* no launcher entry: the engine is reached from Settings > Accessibility >
  Text-to-speech output, and its settings from the gear there.
"""

from __future__ import annotations

import pathlib
import subprocess
import sys

EXPECTED_COMMIT = "4870adfa25b1a32b4361592f1be8a40337c58d6c"


def fail(message: str) -> None:
    raise SystemExit(message)


def replace_once(path: pathlib.Path, old: str, new: str, marker: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        fail(f"{marker}=FAIL path={path} count={count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def main() -> None:
    if len(sys.argv) != 2:
        fail("usage: apply_sable_flavor.py <espeak-ng-checkout>")
    root = pathlib.Path(sys.argv[1]).resolve()
    actual = subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip()
    if actual != EXPECTED_COMMIT:
        fail(f"SABLE_SPEECH_UPSTREAM_COMMIT=FAIL expected={EXPECTED_COMMIT} actual={actual}")

    android = root / "android"
    replace_once(
        android / "build.gradle",
        '        minSdk 21\n',
        '        applicationId "org.sableos.speech"\n'
        '        minSdk 21\n'
        "        ndk {\n"
        "            abiFilters 'arm64-v8a'\n"
        "        }\n",
        "SABLE_SPEECH_APPLICATION_ID",
    )
    replace_once(
        android / "AndroidManifest.xml",
        "                <action android:name=\"android.intent.action.MAIN\" />\n\n"
        "                <category android:name=\"android.intent.category.LAUNCHER\" />\n",
        "                <action android:name=\"android.intent.action.MAIN\" />\n",
        "SABLE_SPEECH_NO_LAUNCHER",
    )
    print("SABLE_SPEECH_FLAVOR=PASS")


if __name__ == "__main__":
    main()
