#!/usr/bin/env python3
"""Check a composed Sable Terminal checkout keeps the Sable changes and none of upstream's identity."""

from __future__ import annotations

import pathlib
import subprocess
import sys

EXPECTED_COMMIT = "21ce6f47cf16785bf2314b4ea931900164c179c1"


def fail(marker: str, detail: str = "") -> None:
    raise SystemExit(f"{marker}=FAIL {detail}".rstrip())


def text(path: pathlib.Path) -> str:
    if not path.is_file():
        fail("SABLE_TERMINAL_VERIFY_MISSING", f"path={path}")
    return path.read_text(encoding="utf-8")


def main() -> None:
    if len(sys.argv) != 2:
        fail("SABLE_TERMINAL_VERIFY_USAGE", "verify_sable_terminal.py <root>")
    root = pathlib.Path(sys.argv[1]).resolve()
    actual = subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip()
    if actual != EXPECTED_COMMIT:
        fail("SABLE_TERMINAL_VERIFY_UPSTREAM", f"actual={actual}")
    app = root / "app"
    build = text(app / "build.gradle.kts")
    manifest = text(app / "src/main/AndroidManifest.xml")
    console = text(app / "src/main/java/org/connectbot/ui/screens/console/ConsoleScreen.kt")
    checks = {
        "SABLE_TERMINAL_PACKAGE": 'applicationId = "org.sableos.terminal"' in build,
        "SABLE_TERMINAL_NO_GIT_VERSIONING": "appVersioning" not in build,
        "SABLE_TERMINAL_ARM64_ONLY": 'abiFilters.addAll(listOf("arm64-v8a"))' in build,
        "SABLE_TERMINAL_NO_MOSH": not (root / "mosh").exists() and "mosh" not in text(root / "settings.gradle.kts"),
        "SABLE_TERMINAL_NO_BACKUP": 'android:allowBackup="false"' in manifest,
        "SABLE_TERMINAL_ICON": 'android:icon="@mipmap/ic_sable_terminal"' in manifest
        and (app / "src/main/res/mipmap-anydpi-v26/ic_sable_terminal.xml").is_file(),
        "SABLE_TERMINAL_NAME": ">Sable Terminal<" in text(app / "src/main/res/values/notrans.xml"),
        "SABLE_TERMINAL_KEYS": "SableTerminalKeys.handle(keyEvent.nativeKeyEvent" in console
        and (app / "src/sable/kotlin/org/connectbot/sable/SableTerminalKeys.kt").is_file(),
        "SABLE_TERMINAL_KEYS_TEST": (app / "src/sableTest/kotlin/org/connectbot/sable/SableTerminalKeysTest.kt").is_file(),
    }
    for marker, ok in checks.items():
        if not ok:
            fail(marker)
        print(f"{marker}=PASS")
    print("SABLE_TERMINAL_POLICY=PASS")


if __name__ == "__main__":
    main()
