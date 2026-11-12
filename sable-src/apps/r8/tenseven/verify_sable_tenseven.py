#!/usr/bin/env python3
"""Check a composed SableOS home checkout (apply_sable_flavor.py) before Gradle builds it."""

from __future__ import annotations

import pathlib
import sys

JAVA = "app/src/main/java/io/github/aimindseye/tenseven"


def main() -> None:
    root = pathlib.Path(sys.argv[1]).resolve()
    build = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
    manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
    activity = (root / JAVA / "SableStartActivity.kt").read_text(encoding="utf-8")
    checks = {
        "package": 'applicationId = "org.sableos.launcher"' in build,
        "home": "android.intent.category.HOME" in manifest,
        "show_hub": "org.sableos.start.action.SHOW_HUB" in manifest,
        "active_frames": "org.sableos.start.action.SHOW_ACTIVE_FRAMES" in manifest,
        "home_style": "org.sableos.start.action.CHOOSE_HOME_STYLE" in manifest,
        "command": "org.sableos.start.action.COMMAND" in manifest and "ACTION_COMMAND -> SCREEN_SEARCH" in activity,
        "look": ".SableLookReceiver" in manifest and (root / JAVA / "SableLookReceiver.kt").is_file(),
        "look_guarded": 'android:permission="android.permission.WRITE_SECURE_SETTINGS"' in manifest,
        "snapshots": "uses-permission android:name=\"android.permission.READ_FRAME_BUFFER\"" in manifest
        and (root / JAVA / "platform/SableTaskSnapshots.kt").is_file(),
        "system_menu": ".SableSystemMenuActivity" in manifest
        and "android.permission.INJECT_EVENTS" in manifest
        and (root / JAVA / "SableSystemMenuActivity.kt").is_file(),
        "fit": "heightIn(max = 360.dp)" in (root / JAVA / "ui/WidgetsPage.kt").read_text(encoding="utf-8"),
        "blue": "0xFF4D9CFF" not in (root / JAVA / "ui/StartPalette.kt").read_text(encoding="utf-8"),
        # No leftover TenSeven code (apply_sable_flavor.py patch_leftovers): Sable Phone stays the only
        # Phone app, and the Sable apps replace the bundled extras.
        "no_phone": "android.telecom.InCallService" not in manifest
        and "android.intent.action.DIAL" not in manifest
        and "CallScreeningService" not in manifest
        and not (root / JAVA / "features").exists(),
        "no_extras": "extras." not in manifest and not (root / JAVA / "extras").exists(),
        "no_screen_capture": "mediaProjection" not in manifest
        and "MEDIA_PROJECTION" not in manifest
        and not (root / JAVA / "ScreenCaptureActivity.kt").exists(),
        "no_first_run_setup": "loadOnboardingDone()" not in activity,
    }
    bad = [name for name, ok in checks.items() if not ok]
    if bad:
        raise SystemExit("SABLE_HOME_VERIFY=FAIL " + ",".join(bad))
    print("SABLE_HOME_VERIFY=PASS")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: verify_sable_tenseven.py <composed-checkout>")
    main()
