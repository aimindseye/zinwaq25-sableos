#!/usr/bin/env python3
"""Check a composed SableOS home checkout (apply_sable_flavor.py) before Gradle builds it."""

from __future__ import annotations

import pathlib
import re
import sys

JAVA = "app/src/main/java/io/github/aimindseye/tenseven"


def main() -> None:
    root = pathlib.Path(sys.argv[1]).resolve()
    build = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
    manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
    activity = (root / JAVA / "SableStartActivity.kt").read_text(encoding="utf-8")
    state = (root / JAVA / "platform/StartStateRepository.kt").read_text(encoding="utf-8")
    hub_ui = (root / JAVA / "ui/HubConversationsUi.kt").read_text(encoding="utf-8")
    hub_panels = (root / JAVA / "ui/HubPanels.kt").read_text(encoding="utf-8")
    hub = (root / JAVA / "conversations/ConversationHub.kt").read_text(encoding="utf-8")
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
        "profile_setting": state.count('"sable_profile"') == 2,
        "hub_enter_opens": "onClick = { ui.onOpen(conversation) }" in hub_ui and "KEYCODE_MENU" in hub_ui,
        "hub_no_open_item": "openLabel(" not in hub_panels and "menuFromBottom" in hub_panels,
        "no_extras": "extras." not in manifest and not (root / JAVA / "extras").exists(),
        "no_screen_capture": "mediaProjection" not in manifest
        and "MEDIA_PROJECTION" not in manifest
        and not (root / JAVA / "ScreenCaptureActivity.kt").exists(),
        "no_first_run_setup": "loadOnboardingDone()" not in activity,
        # Hub reads Sable Mail's inboxes from its feed (patch_hub_mail).
        "hub_mail": 'android:authorities="org.sableos.mail.hub"' in manifest
        and "SableMailFeed.merge(items)" in hub
        and "SableMailFeed.markRead(context, n)" in hub,
        # Back at an app's root shows Active Frames, as Home does (patch_back_to_frames).
        # Hub over any app (patch_hub_peek).
        "hub_peek": ".SableHubPeekActivity" in manifest
        and (root / JAVA / "SableHubPeekActivity.kt").is_file(),
        # The cover API (patch_frame_covers).
        "frame_covers": 'android:authorities="org.sableos.launcher.covers"' in manifest
        and (root / JAVA / "frames/SableCoverProvider.kt").is_file(),
        # The Hub keeps items until deleted, with sections, Priority Hub and the ⋮ menu (patch_hub_keep).
        "hub_keep": "SableHubHistory.record(items)" in hub
        and (root / JAVA / "conversations/SableHubKeep.kt").is_file()
        and "SableHubMoreControl()" in (root / JAVA / "ui/HubCompactBar.kt").read_text(encoding="utf-8"),
        # No status dials or status line on either home (patch_no_status_dials).
        "no_status_dials": "HomeStatusMode.Hidden\n" in (root / JAVA / "platform/StartStateRepository.kt").read_text(encoding="utf-8")
        and "STATUS_RINGS" not in (root / JAVA / "model/HomeLayout.kt").read_text(encoding="utf-8")
        and "Settings2UnifiedHomeStatus" not in (root / JAVA / "LauncherSettingsActivity.kt").read_text(encoding="utf-8"),
        # Extra dim at the bottom of the brightness slider (patch_extra_dim).
        "extra_dim": "android.permission.WRITE_SECURE_SETTINGS" in manifest and "SableExtraDim.start(this)" in activity,
        # The home grants itself its permissions, and has no Active Frames settings page (patch_home_access).
        "home_access": "android.permission.MANAGE_NOTIFICATION_LISTENERS" in manifest
        and "SableHomeAccess.ensure(this)" in activity
        and "5. Active Frames Settings" not in (root / JAVA / "LauncherSettingsActivity.kt").read_text(encoding="utf-8")
        and "1. Permissions" not in (root / JAVA / "LauncherSettingsActivity.kt").read_text(encoding="utf-8")
        and '"frames",' not in (root / JAVA / "model/Settings2Flow.kt").read_text(encoding="utf-8"),
        # A slim name bar under each frame, and a log line that says why frames have no picture.
        "slim_frames": "heightIn(min = 22.dp)" in (root / JAVA / "ui/ActiveFramesPage.kt").read_text(encoding="utf-8")
        and "SableTaskSnapshots.explain(appContext)" in (root / JAVA / "platform/RecentTasksRepository.kt").read_text(encoding="utf-8"),
        # Live pictures as in Sable 1.0: always asked for, never switched off (patch_frames).
        "live_frames": "snapshots = true," in (root / JAVA / "platform/StartStateRepository.kt").read_text(encoding="utf-8")
        and "SableTaskSnapshots.note(" in (root / JAVA / "platform/ActiveFramesHost.kt").read_text(encoding="utf-8"),
        "back_to_frames": "SableBackToFrames.leftForAnotherApp(" in activity
        and (root / JAVA / "frames/SableBackToFrames.kt").is_file(),
        # Q25 sizes for Hub and Command, and a Command icon on the BB10 home bar (patch_q25_fit).
        "q25_fit": "displayMedium = SableTypography.displayMedium.copy(fontSize = 26.sp"
        in (root / "design/src/main/java/io/github/aimindseye/tenseven/design/SableTheme.kt").read_text(encoding="utf-8")
        and 'QuickSlot(QuickBar.COMMAND, "Command", null)\n        HomeShortcut(command,'
        in (root / JAVA / "ui/ReferenceHomeBar.kt").read_text(encoding="utf-8"),
        # No "Select a notification icon" on the lock layer without notifications (patch_quiet_lock).
        "quiet_lock": "if (content.entries.isNotEmpty()) {"
        in (root / JAVA / "ui/LockNotificationScreen.kt").read_text(encoding="utf-8"),
        # The tray's Recent panel after Favorites, saved with the usage counts (patch_recent_tab).
        "recent_tab": 'Recent("recent", fixedTitle = "Recent")' in (root / JAVA / "model/Bb7Home.kt").read_text(encoding="utf-8")
        and "SableRecents.keys.mapNotNull" in (root / JAVA / "model/Bb7Home.kt").read_text(encoding="utf-8")
        and (root / JAVA / "model/SableRecents.kt").is_file(),
        # The sound profile shortcut on both homes (patch_profiles_shortcut).
        "profiles_shortcut": "SoundProfileButton()" in (root / JAVA / "ui/Bb7HomeScreen.kt").read_text(encoding="utf-8")
        and "SoundProfileButton()" in (root / JAVA / "ui/Bb10HomeChrome.kt").read_text(encoding="utf-8"),
    }
    # The only name on screen is Sable Launcher: no string resource or Kotlin string literal says TenSeven.
    named = re.compile(r"Ten[Ss]even(?![A-Za-z0-9])")
    literal = re.compile(r'"(?:[^"\\\n]|\\.)*"')
    left = [
        str(p.relative_to(root))
        for p in (root / "app/src/main/res").glob("values*/*.xml")
        if any(named.search(m) for m in re.findall(r">([^<]*)<", p.read_text(encoding="utf-8")))
    ] + [
        str(p.relative_to(root))
        for p in (root / "app/src/main/java").rglob("*.kt")
        if '"""' not in p.read_text(encoding="utf-8")
        and any(named.search(m.group(0)) for m in literal.finditer(p.read_text(encoding="utf-8")))
    ]
    checks["visible_names"] = not left
    if left:
        print("SABLE_HOME_VISIBLE_NAMES_LEFT=" + ",".join(left[:10]), file=sys.stderr)
    bad = [name for name, ok in checks.items() if not ok]
    if bad:
        raise SystemExit("SABLE_HOME_VERIFY=FAIL " + ",".join(bad))
    print("SABLE_HOME_VERIFY=PASS")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: verify_sable_launcher.py <composed-checkout>")
    main()
