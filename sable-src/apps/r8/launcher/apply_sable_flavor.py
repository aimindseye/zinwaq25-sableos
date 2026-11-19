#!/usr/bin/env python3
"""Compose the SableOS home (Sable Launcher) from the pinned upstream launcher (Apache 2.0).

Owner decision 2026-11-12, 2026-11-18: Sable Launcher is SableOS 2.0's only home and Hub. It was cut from
Sable Start, keeps Sable Start's intent contract (SHOW_HUB, SHOW_ACTIVE_FRAMES, EDIT_HOME,
EDIT_QUICK_BAR, CHOOSE_HOME_STYLE) and reads the Sable apps' providers, and it follows the approved
BB10 design review. The upstream checkout is ephemeral build input. This flavor changes only what
the ROM needs:

- the package id org.sableos.launcher, which the framework patches, Setup, Settings and the
  permission files already name, so it replaces Sable Start (apps.tsv overrides SableLauncher);
- the two Sable Start entry points TenSeven lacks: Command from any app (ACTION_COMMAND, Alt+Space
  through frameworks/base 0319) and Setup's Look step (ACTION_SET_LOOK, SetupWizard 0417);
- the approved colours: blue #2E9BD6 instead of #4D9CFF, and black and grey surfaces instead of
  the navy ones in the utility and Settings themes.
- no leftover TenSeven code: its Phone, Calendar, bundled extras and screen capture, which Sable
  apps or the system task snapshot replace (patch_leftovers).
"""

from __future__ import annotations

import pathlib
import re
import shutil
import subprocess
import sys

EXPECTED_COMMIT = "fbdd2171d25eaf7f120e3ab829977ca328c83014"
PACKAGE = "org.sableos.launcher"
VERSION_NAME = "2.0-sable"
HERE = pathlib.Path(__file__).resolve().parent
JAVA = "app/src/main/java/io/github/aimindseye/tenseven"
TEST = "app/src/test/java/io/github/aimindseye/tenseven"
DESIGN = "design/src/main/java/io/github/aimindseye/tenseven/design"


def fail(message: str) -> None:
    raise SystemExit(message)


def replace_once(path: pathlib.Path, old: str, new: str, marker: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        fail(f"{marker}=FAIL path={path} count={count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def checked_upstream_commit(root: pathlib.Path) -> None:
    if not (root / ".git").exists():
        fail(f"SABLE_HOME_UPSTREAM=FAIL_NOT_GIT path={root}")
    actual = subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip()
    if actual != EXPECTED_COMMIT:
        fail(f"SABLE_HOME_UPSTREAM_COMMIT=FAIL expected={EXPECTED_COMMIT} actual={actual}")


def patch_build(root: pathlib.Path) -> None:
    build = root / "app/build.gradle.kts"
    replace_once(
        build,
        '        applicationId = "io.github.aimindseye.tenseven"\n',
        f'        applicationId = "{PACKAGE}"\n',
        "SABLE_HOME_APPLICATION_ID",
    )
    replace_once(build, '        versionName = "1.0.0"\n', f'        versionName = "{VERSION_NAME}"\n', "SABLE_HOME_VERSION")


def patch_manifest(root: pathlib.Path) -> None:
    manifest = root / "app/src/main/AndroidManifest.xml"
    # Command from any app: Alt+Space (frameworks/base 0319) starts Start with ACTION_COMMAND.
    replace_once(
        manifest,
        "                <action android:name=\"org.sableos.start.action.SHOW_HUB\" />\n"
        "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
        "            </intent-filter>\n",
        "                <action android:name=\"org.sableos.start.action.SHOW_HUB\" />\n"
        "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
        "            </intent-filter>\n"
        "\n"
        "            <!-- SableOS: Command, from Alt+Space in any app (frameworks/base 0319). -->\n"
        "            <intent-filter>\n"
        "                <action android:name=\"org.sableos.start.action.COMMAND\" />\n"
        "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
        "            </intent-filter>\n",
        "SABLE_HOME_COMMAND_FILTER",
    )
    # Setup's Look step (SetupWizard 0417); only setup and the system may send it.
    replace_once(
        manifest,
        "    </application>\n",
        "        <!-- SableOS: first-boot setup's Look step saves the home style. -->\n"
        "        <receiver\n"
        "            android:name=\".SableLookReceiver\"\n"
        "            android:exported=\"true\"\n"
        "            android:permission=\"android.permission.WRITE_SECURE_SETTINGS\">\n"
        "            <intent-filter>\n"
        "                <action android:name=\"org.sableos.start.action.SET_LOOK\" />\n"
        "            </intent-filter>\n"
        "        </receiver>\n"
        "    </application>\n",
        "SABLE_HOME_LOOK_RECEIVER",
    )
    shutil.copyfile(HERE / "src/SableLookReceiver.kt", root / JAVA / "SableLookReceiver.kt")
    # The SableOS home icon (sable-src/tools/gen_first_party_icons.py, row home).
    replace_once(
        manifest,
        'android:icon="@mipmap/ic_tenseven"\n',
        'android:icon="@mipmap/ic_sable_home"\n',
        "SABLE_HOME_ICON",
    )
    replace_once(
        manifest,
        'android:roundIcon="@mipmap/ic_tenseven_round"',
        'android:roundIcon="@mipmap/ic_sable_home_round"',
        "SABLE_HOME_ROUND_ICON",
    )
    icon_source = pathlib.Path(__file__).resolve().parent / "res"
    if not (icon_source / "mipmap-anydpi-v26/ic_sable_home.xml").is_file():
        fail("SABLE_HOME_ICON_SOURCE=FAIL")
    shutil.copytree(icon_source, root / "app/src/main/res", dirs_exist_ok=True)


def patch_command(root: pathlib.Path) -> None:
    activity = root / JAVA / "SableStartActivity.kt"
    replace_once(
        activity,
        "            ACTION_EDIT_QUICK_BAR, ACTION_EDIT_HOME -> {\n",
        "            ACTION_EDIT_QUICK_BAR, ACTION_EDIT_HOME, ACTION_COMMAND -> {\n",
        "SABLE_HOME_COMMAND_NEW_INTENT",
    )
    replace_once(
        activity,
        "        const val ACTION_SHOW_ACTIVE_FRAMES = \"org.sableos.start.action.SHOW_ACTIVE_FRAMES\"\n",
        "        const val ACTION_SHOW_ACTIVE_FRAMES = \"org.sableos.start.action.SHOW_ACTIVE_FRAMES\"\n"
        "\n"
        "        /** SableOS: Command from any app (Alt+Space, frameworks/base 0319). */\n"
        "        const val ACTION_COMMAND = \"org.sableos.start.action.COMMAND\"\n",
        "SABLE_HOME_COMMAND_ACTION",
    )
    replace_once(
        activity,
        "            setOf(ACTION_EDIT_HOME, ACTION_EDIT_QUICK_BAR, ACTION_SHOW_HUB)\n",
        "            setOf(ACTION_EDIT_HOME, ACTION_EDIT_QUICK_BAR, ACTION_SHOW_HUB, ACTION_COMMAND)\n",
        "SABLE_HOME_COMMAND_BB10_ONLY",
    )
    replace_once(
        activity,
        "                ACTION_EDIT_QUICK_BAR, ACTION_EDIT_HOME -> SCREEN_EDIT_HOME\n",
        "                ACTION_EDIT_QUICK_BAR, ACTION_EDIT_HOME -> SCREEN_EDIT_HOME\n"
        "                ACTION_COMMAND -> SCREEN_SEARCH\n",
        "SABLE_HOME_COMMAND_SCREEN",
    )


def patch_colours(root: pathlib.Path) -> None:
    """The approved BB10 tokens: blue #2E9BD6, black, raised #141414, dividers #262626, muted #A6A6A6."""
    replace_once(
        root / DESIGN / "SableTheme.kt",
        'Blue("blue", "Sable Blue", Color(0xFF4D9CFF), Color(0xFF06111F)),',
        'Blue("blue", "Sable Blue", Color(0xFF2E9BD6), Color(0xFF000000)),',
        "SABLE_HOME_BLUE_PRESET",
    )
    replace_once(
        root / DESIGN / "SableSystemTokens.kt",
        'Blue("blue", 0xFF4D9CFF, 0xFF4D9CFF, 0xFF06111F, 0xFF1F6FD1, 0xFFFFFFFF),',
        'Blue("blue", 0xFF2E9BD6, 0xFF2E9BD6, 0xFF000000, 0xFF1F6FD1, 0xFFFFFFFF),',
        "SABLE_HOME_BLUE_TOKENS",
    )
    replace_once(
        root / JAVA / "ui/StartPalette.kt",
        "internal val SableBlue = Color(0xFF4D9CFF)",
        "internal val SableBlue = Color(0xFF2E9BD6)",
        "SABLE_HOME_START_BLUE",
    )
    replace_once(
        root / JAVA / "model/AppGlyphs.kt",
        "private const val GLYPH_ACCENT_BLUE: Long = 0xFF4D9CFF",
        "private const val GLYPH_ACCENT_BLUE: Long = 0xFF2E9BD6",
        "SABLE_HOME_GLYPH_BLUE",
    )
    app_theme = root / JAVA / "TenSeven2AppTheme.kt"
    for old, new, marker in (
        ("TENSEVEN_THEME_OUTLINE_VARIANT_COLOR = Color(0xFF39414A)", "TENSEVEN_THEME_OUTLINE_VARIANT_COLOR = Color(0xFF262626)", "OUTLINE_VARIANT"),
        ("TENSEVEN_THEME_OUTLINE_COLOR = Color(0xFF59616A)", "TENSEVEN_THEME_OUTLINE_COLOR = Color(0xFF4D4D4D)", "OUTLINE"),
        ("TENSEVEN_THEME_ON_SURFACE_VARIANT_COLOR = Color(0xFFA9B2BC)", "TENSEVEN_THEME_ON_SURFACE_VARIANT_COLOR = Color(0xFFA6A6A6)", "ON_SURFACE_VARIANT"),
        ("TENSEVEN_THEME_SURFACE_VARIANT_COLOR = Color(0xFF0E1318)", "TENSEVEN_THEME_SURFACE_VARIANT_COLOR = Color(0xFF141414)", "SURFACE_VARIANT"),
        ("TENSEVEN_THEME_SURFACE_COLOR = Color(0xFF070A0D)", "TENSEVEN_THEME_SURFACE_COLOR = Color(0xFF000000)", "SURFACE"),
        ("TENSEVEN_THEME_PRIMARY_COLOR = Color(0xFF4D9CFF)", "TENSEVEN_THEME_PRIMARY_COLOR = Color(0xFF2E9BD6)", "PRIMARY"),
    ):
        replace_once(app_theme, old, new, f"SABLE_HOME_APP_THEME_{marker}")
    settings_theme = root / JAVA / "Settings2VisualTheme.kt"
    for old, new, marker in (
        ("TENSEVEN_CLASSIC_ACCENT_COLOR = Color(0xFF4D9CFF)", "TENSEVEN_CLASSIC_ACCENT_COLOR = Color(0xFF2E9BD6)", "ACCENT"),
        ("TENSEVEN_CLASSIC_SURFACE_COLOR = Color(0xFF0D1115)", "TENSEVEN_CLASSIC_SURFACE_COLOR = Color(0xFF141414)", "SURFACE"),
        ("TENSEVEN_CLASSIC_OUTLINE_COLOR = Color(0xFF444A52)", "TENSEVEN_CLASSIC_OUTLINE_COLOR = Color(0xFF4D4D4D)", "OUTLINE"),
    ):
        replace_once(settings_theme, old, new, f"SABLE_HOME_SETTINGS_THEME_{marker}")


SNAPSHOT_PERMISSIONS = (
    "REAL_GET_TASKS",
    "REORDER_TASKS",
    "REMOVE_TASKS",
    "START_TASKS_FROM_RECENTS",
    "READ_FRAME_BUFFER",
    "MANAGE_ACTIVITY_TASKS",
    "INTERACT_ACROSS_USERS",
    "OBSERVE_GRANT_REVOKE_PERMISSIONS",
    "MODIFY_QUIET_MODE",
)


def patch_frames(root: pathlib.Path) -> None:
    """Active Frames on SableOS: the system task snapshot, never screen capture (owner, 2026-11-12).

    The home is platform-signed, so these signature permissions are granted; TenSeven's probe then
    reports its snapshot bridge ready. Media frames already come from every app's media session
    through the notification listener, which the framework overlay grants on first boot.
    """
    manifest = root / "app/src/main/AndroidManifest.xml"
    permissions = "".join(
        f'    <uses-permission android:name="android.permission.{name}" tools:ignore="ProtectedPermissions" />\n'
        for name in SNAPSHOT_PERMISSIONS
    )
    replace_once(
        manifest,
        '    <uses-permission android:name="android.permission.READ_CALENDAR" />\n',
        '    <uses-permission android:name="android.permission.READ_CALENDAR" />\n'
        "    <!-- SableOS: the platform-signed home's permissions, as Sable Start held them: Active Frames'\n"
        "         live pictures from the system task snapshot, and Private Space and work-profile rows. -->\n"
        + permissions
        + '    <uses-permission android:name="org.sableos.permission.SPEED_DIAL" />\n',
        "SABLE_HOME_SNAPSHOT_PERMISSIONS",
    )
    shutil.copyfile(HERE / "src/SableTaskSnapshots.kt", root / JAVA / "platform/SableTaskSnapshots.kt")
    replace_once(
        root / JAVA / "platform/PlatformCapabilityProbe.kt",
        '                    taskMethod(context, "getTaskSnapshot"),\n',
        # The platform home always has the system's snapshot call (Sable 1.0 used it with no probe).
        "                    true,\n",
        "SABLE_HOME_SNAPSHOT_PROBE",
    )
    replace_once(
        root / JAVA / "platform/RecentTasksRepository.kt",
        "            if (PlatformCapabilityProbe.detect(appContext).thirdPartySnapshots != CapabilityStatus.Ready) return@runCatching null\n",
        # As Sable 1.0 did: always ask the system for the picture first, with no capability gate.
        "            SableTaskSnapshots.explain(appContext)\n"
        "            SableTaskSnapshots.snapshot(taskId, lowResolution)?.let { return@runCatching it }\n"
        "            if (PlatformCapabilityProbe.detect(appContext).thirdPartySnapshots != CapabilityStatus.Ready) return@runCatching null\n",
        "SABLE_HOME_SNAPSHOT_CALL",
    )
    # Frames follow the system's recent tasks (REAL_GET_TASKS) rather than only the apps opened from the
    # home, so a media app started from its notification or another app still gets its frame and media line.
    replace_once(
        root / JAVA / "frames/FrameModels.kt",
        "            input.openings?.map { opening ->\n",
        "            input.openings?.takeIf { native.isEmpty() }?.map { opening ->\n",
        "SABLE_HOME_FRAMES_FROM_SYSTEM_TASKS",
    )
    # Live pictures are always on for the platform home, as in Sable 1.0: no settings switch (the
    # Active Frames settings page is gone) and no probe can turn them into summary cards.
    replace_once(
        root / JAVA / "platform/StartStateRepository.kt",
        '                snapshots = preferences.getBoolean("frames_snapshots", true),\n',
        "                snapshots = true,\n",
        "SABLE_HOME_SNAPSHOTS_ALWAYS",
    )
    # The live picture fills the frame with no "Preview" tag over it, as in Sable 1.0.
    replace_once(
        root / JAVA / "ui/ActiveFramesPage.kt",
        "        Image(picture.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)\n"
        "        Text(\n"
        "            \"Preview\",\n"
        "            color = Bb10Text,\n"
        "            style = MaterialTheme.typography.labelSmall,\n"
        "            modifier = Modifier.align(Alignment.TopStart).background(Bb10Background).padding(4.dp),\n"
        "        )\n",
        "        Image(picture.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)\n",
        "SABLE_HOME_FRAME_PICTURE_ONLY",
    )
    # Say in the log why a frame has no picture, gate by gate.
    host = root / JAVA / "platform/ActiveFramesHost.kt"
    replace_once(
        host,
        "        if (entry == null || !available(entry)) return null\n"
        "        val bitmap = item.task?.let { native.snapshot(it.taskId) }\n",
        "        if (entry == null || !available(entry)) {\n"
        "            SableTaskSnapshots.note(\"no-app:${item.app.packageName}\", \"${item.app.packageName}: no unlocked launcher entry\")\n"
        "            return null\n"
        "        }\n"
        "        val task = item.task\n"
        "        if (task == null) SableTaskSnapshots.note(\"no-task:${item.app.packageName}\", \"${item.app.packageName}: frame has no running task\")\n"
        "        val bitmap = task?.let { native.snapshot(it.taskId) }\n",
        "SABLE_HOME_SNAPSHOT_WHY",
    )
    # A slim name bar under each frame, so more of the app shows (owner, 2026-11-12). X and Backspace
    # still close the focused frame; the × stays tappable.
    page = root / JAVA / "ui/ActiveFramesPage.kt"
    replace_once(page, "                .heightIn(min = 36.dp)\n", "                .heightIn(min = 22.dp)\n", "SABLE_HOME_SLIM_FRAME_BAR")
    replace_once(
        page,
        "                style = MaterialTheme.typography.labelLarge,\n                modifier = Modifier.weight(1f),\n",
        "                style = MaterialTheme.typography.labelMedium,\n                modifier = Modifier.weight(1f),\n",
        "SABLE_HOME_SLIM_FRAME_TITLE",
    )
    replace_once(page, "                        .size(48.dp)\n", "                        .size(width = 32.dp, height = 22.dp)\n", "SABLE_HOME_SLIM_FRAME_CLOSE")
    # No screen-capture offer where the system snapshot works.
    # Screen capture is never offered on SableOS; patch_leftovers removes it from the build.


def patch_system_menu(root: pathlib.Path) -> None:
    """The BlackBerry 7 style system menu on Alt+BlackBerry key (frameworks/base 0324; owner,
    2026-11-12): the app's menu, Close the app, Switch Application, Notifications, Quick Settings.
    Not exported: the system (PhoneWindowManager) starts it by its component."""
    manifest = root / "app/src/main/AndroidManifest.xml"
    replace_once(
        manifest,
        "        <!-- SableOS: first-boot setup's Look step saves the home style. -->\n",
        "        <!-- SableOS: the system menu, from Alt+BlackBerry key in any app (frameworks/base 0324). -->\n"
        "        <activity\n"
        "            android:name=\".SableSystemMenuActivity\"\n"
        "            android:exported=\"false\"\n"
        "            android:excludeFromRecents=\"true\"\n"
        "            android:noHistory=\"true\"\n"
        "            android:taskAffinity=\"\"\n"
        "            android:theme=\"@android:style/Theme.Translucent.NoTitleBar\" />\n"
        "        <!-- SableOS: first-boot setup's Look step saves the home style. -->\n",
        "SABLE_HOME_SYSTEM_MENU",
    )
    replace_once(
        manifest,
        '    <uses-permission android:name="org.sableos.permission.SPEED_DIAL" />\n',
        '    <uses-permission android:name="org.sableos.permission.SPEED_DIAL" />\n'
        "    <!-- SableOS system menu: the app's Menu key, and the shade. -->\n"
        '    <uses-permission android:name="android.permission.INJECT_EVENTS" tools:ignore="ProtectedPermissions" />\n'
        '    <uses-permission android:name="android.permission.EXPAND_STATUS_BAR" />\n',
        "SABLE_HOME_SYSTEM_MENU_PERMISSIONS",
    )
    shutil.copyfile(HERE / "src/SableSystemMenuActivity.kt", root / JAVA / "SableSystemMenuActivity.kt")


def patch_fit(root: pathlib.Path) -> None:
    """Fit the Q25's 411 dp square screen (about 383 dp under the status bar): the widget picker's
    list was a fixed 360 dp, so with its title and padding the dialog was taller than the window."""
    widgets = root / JAVA / "ui/WidgetsPage.kt"
    replace_once(
        widgets,
        "import androidx.compose.foundation.layout.height\n",
        "import androidx.compose.foundation.layout.height\nimport androidx.compose.foundation.layout.heightIn\n",
        "SABLE_HOME_FIT_IMPORT",
    )
    replace_once(
        widgets,
        "Modifier.weight(1f, fill = false).height(360.dp)",
        "Modifier.weight(1f, fill = false).heightIn(max = 360.dp)",
        "SABLE_HOME_FIT_WIDGETS",
    )


# TenSeven code SableOS does not ship (owner, 2026-11-12: "make sure no left over code is ported").
# Each of these duplicates a Sable app, or is a feature SableOS never uses:
# - TenSeven Phone, its in-call screen, call screening and dialer-role request: Sable Phone is the
#   Phone app (android.app.role.DIALER). With an InCallService and DIAL activities in the home APK,
#   Android listed the home as a Phone app and Settings offered to make it the default.
# - TenSeven Calendar: Sable Calendar. Hub still reads calendars (READ_CALENDAR stays).
# - The bundled extras: Calc Plus (Sable Calculator), Notes (Sable Notes), Sound Profiles (Sable
#   Profiles), the Tasks launcher entry (Sable Tasks) and ten games (Sable Games).
# - Screen capture for Active Frames: frames use the system task snapshot (patch_frames).
# - TenSeven's own first-run setup on first boot: SableOS Setup covers it. It stays in the menu.
LEFTOVER_COMPONENTS = (
    ".features.phone.TenSevenPhoneActivity",
    ".features.phone.TenSevenFloatingDialpadActivity",
    ".features.calendar.TenSevenCalendarActivity",
    ".features.phone.TenSevenInCallService",
    ".features.phone.TenSevenCallScreeningService",
    ".features.phone.TenSevenInCallActivity",
    ".D5PhoneRoleRequestActivity",
    ".tasks.TasksLauncherActivity",
    ".ScreenCaptureActivity",
    ".platform.ScreenCaptureService",
    "io.github.aimindseye.tenseven.extras.calculator.MainActivity",
    "io.github.aimindseye.tenseven.extras.profiles.MainActivity",
    "io.github.aimindseye.tenseven.extras.notes.MainActivity",
    *(
        f"io.github.aimindseye.tenseven.extras.games.{game}Activity"
        for game in (
            "Sudoku", "Minesweeper", "Game2048", "Bricks", "Snake", "Blocks", "Reversi", "Solitaire", "Mahjong", "Words",
        )
    ),
)
LEFTOVER_PERMISSIONS = (
    "android.permission.CALL_PHONE",
    "android.permission.USE_FULL_SCREEN_INTENT",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
)
LEFTOVER_COMMENTS = (
    "Optional D5 companions",
    "Default Phone role: system-owned Telecom calls",
    "Separate optional Android Call Screening role",
    "D5-R6: explicit user-controlled dialer-role request",
    "Extra apps ported from SableOS",
)
LEFTOVER_PATHS = (
    f"{JAVA}/features",
    f"{JAVA}/extras",
    f"{JAVA}/D5PhoneRoleRequestActivity.kt",
    f"{JAVA}/D5PhoneRoleSetting.kt",
    f"{JAVA}/ScreenCaptureActivity.kt",
    f"{JAVA}/platform/ScreenCaptureService.kt",
    f"{JAVA}/platform/ScreenCaptureState.kt",
    f"{TEST}/features",
    f"{TEST}/extras",
    f"{TEST}/model/ExtraAppsTest.kt",
    f"{TEST}/frames/FrameWindowTest.kt",
    f"{TEST}/onboarding/Setup2PlanTest.kt",
)
SABLE_PROFILES = "org.sableos.titan2.profiles"


def remove_component(text: str, name: str) -> str:
    """Removes the one <activity>, <activity-alias> or <service> whose android:name is [name]."""
    attribute = f'android:name="{name}"'
    if text.count(attribute) != 1:
        fail(f"SABLE_HOME_LEFTOVER_COMPONENT=FAIL name={name} count={text.count(attribute)}")
    at = text.index(attribute)
    opening = list(re.finditer(r"<(activity-alias|activity|service)\b", text[:at]))[-1]
    tag = opening.group(1)
    head_end = text.index(">", at)
    end = head_end + 1 if text[head_end - 1] == "/" else text.index(f"</{tag}>", at) + len(f"</{tag}>")
    start = text.rfind("\n", 0, opening.start()) + 1
    if text[start : opening.start()].strip():
        fail(f"SABLE_HOME_LEFTOVER_LAYOUT=FAIL name={name}")
    if text[end : end + 1] == "\n":
        end += 1
    return text[:start] + text[end:]


def remove_comment(text: str, marker: str) -> str:
    found = [m for m in re.finditer(r"[ \t]*<!--.*?-->\n", text, re.S) if marker in m.group(0)]
    if len(found) != 1:
        fail(f"SABLE_HOME_LEFTOVER_COMMENT=FAIL marker={marker} count={len(found)}")
    return text[: found[0].start()] + text[found[0].end() :]


def cut_once(path: pathlib.Path, start: str, end: str, replacement: str, marker: str) -> None:
    """Replaces the text from [start] up to (not including) [end] with [replacement]."""
    text = path.read_text(encoding="utf-8")
    if text.count(start) != 1:
        fail(f"{marker}=FAIL path={path} start_count={text.count(start)}")
    begin = text.index(start)
    stop = text.find(end, begin + len(start))
    if stop < 0:
        fail(f"{marker}=FAIL path={path} no_end")
    path.write_text(text[:begin] + replacement + text[stop:], encoding="utf-8")


def strip_manifest(root: pathlib.Path) -> None:
    manifest = root / "app/src/main/AndroidManifest.xml"
    text = manifest.read_text(encoding="utf-8")
    for name in LEFTOVER_COMPONENTS:
        text = remove_component(text, name)
    for marker in LEFTOVER_COMMENTS:
        text = remove_comment(text, marker)
    for permission in LEFTOVER_PERMISSIONS:
        line = f'    <uses-permission android:name="{permission}" />\n'
        if text.count(line) != 1:
            fail(f"SABLE_HOME_LEFTOVER_PERMISSION=FAIL name={permission}")
        text = text.replace(line, "", 1)
    # Only the Sound Profiles extra wrote system settings.
    text = remove_comment(text, "Sound Profiles (an extra)")
    line = '    <uses-permission android:name="android.permission.WRITE_SETTINGS" tools:ignore="ProtectedPermissions" />\n'
    if text.count(line) != 1:
        fail("SABLE_HOME_LEFTOVER_PERMISSION=FAIL name=WRITE_SETTINGS")
    manifest.write_text(text.replace(line, "", 1), encoding="utf-8")


def strip_extras(root: pathlib.Path) -> None:
    java = root / JAVA
    # No bundled extra apps: Setup's Apps & features stage, the Settings page and Command entry go.
    cut_once(
        java / "model/ExtraApps.kt",
        "    val ALL: List<ExtraApp> =\n",
        "\n    private val byClass",
        "    // SableOS: none. Each extra duplicated a Sable app (Calculator, Notes, Profiles, Tasks, Games,\n"
        "    // Phone, Calendar), so the SableOS home ships without them.\n"
        "    val ALL: List<ExtraApp> = emptyList()\n",
        "SABLE_HOME_NO_EXTRAS",
    )
    replace_once(
        java / "onboarding/Setup2Plan.kt",
        '    val essentials: Set<String> = setOf("calculator", "tasks", "notes")\n',
        "    val essentials: Set<String> = emptySet()\n",
        "SABLE_HOME_NO_ESSENTIALS",
    )
    replace_once(
        java / "onboarding/Setup2Plan.kt",
        "    val stages: List<Setup2Stage> = Setup2Stage.entries\n",
        "    val stages: List<Setup2Stage> = Setup2Stage.entries - Setup2Stage.APPS\n",
        "SABLE_HOME_NO_APPS_STAGE",
    )
    replace_once(
        java / "onboarding/OnboardingPlan.kt",
        "        SetupStep.entries.filterNot {\n            !state.notificationListenerAvailable",
        "        SetupStep.entries.filterNot {\n            it == SetupStep.EXTRAS ||\n"
        "                !state.notificationListenerAvailable",
        "SABLE_HOME_NO_EXTRAS_STEP",
    )
    replace_once(
        java / "LauncherSettingsActivity.kt",
        "    OptionalAppsSettings2()\n    Section(\"Other features\")\n",
        "    Section(\"Other features\")\n",
        "SABLE_HOME_NO_EXTRAS_SETTINGS",
    )
    cut_once(
        java / "LauncherSettingsActivity.kt",
        '/** A single persisted feature source, shared with the existing Setup extras picker. */\n',
        '@Suppress("FunctionNaming")\n@Composable\ninternal fun Settings2AppearanceChoices(',
        "",
        "SABLE_HOME_NO_EXTRAS_PICKER",
    )
    cut_once(
        java / "ui/CommandScreen.kt",
        "        SearchCommand(\n            context.getString(R.string.setup_extras_title),\n",
        "        SearchCommand(\n            context.getString(R.string.backup_menu),\n",
        "",
        "SABLE_HOME_NO_EXTRAS_COMMAND",
    )
    # Sound Profiles is Sable Profiles; the ringer key opens it, and a long press still cycles.
    cut_once(
        java / "platform/SoundProfileControl.kt",
        "    private val profilesApp get() =",
        "\n}\n",
        "    /** Whether Sable Profiles, the SableOS sound profiles app, is installed. */\n"
        "    fun profilesEnabled(context: Context): Boolean =\n"
        f'        context.packageManager.getLaunchIntentForPackage("{SABLE_PROFILES}") != null\n'
        "\n"
        "    /** Opens Sable Profiles; false (and nothing opened) when it is missing. */\n"
        "    fun openProfiles(context: Context): Boolean {\n"
        f'        val intent = context.packageManager.getLaunchIntentForPackage("{SABLE_PROFILES}")\n'
        "        return intent != null &&\n"
        "            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess\n"
        "    }",
        "SABLE_HOME_SABLE_PROFILES",
    )
    replace_once(
        java / "platform/SoundProfileControl.kt",
        "import io.github.aimindseye.tenseven.model.ExtraApps\n",
        "",
        "SABLE_HOME_SABLE_PROFILES_IMPORT",
    )
    # Active Frames: no summaries from the TenSeven Notes and Calculator extras (Tasks stays, for Hub reminders).
    summaries = java / "platform/AppFrameSummaryRepository.kt"
    replace_once(
        summaries,
        "import io.github.aimindseye.tenseven.extras.notes.core.NoteStore\n"
        "import io.github.aimindseye.tenseven.extras.notes.core.NoteText\n",
        "",
        "SABLE_HOME_NO_NOTES_IMPORT",
    )
    replace_once(summaries, "import java.io.File\n", "", "SABLE_HOME_NO_FILE_IMPORT")
    cut_once(
        summaries,
        "    private fun local(now: Long): List<AppSummary> =\n",
        "\n    private fun taskSummary",
        "    private fun local(now: Long): List<AppSummary> = listOfNotNull(taskSummary(now))\n",
        "SABLE_HOME_LOCAL_SUMMARIES",
    )
    replace_once(
        summaries,
        '                "local:notes" -> io.github.aimindseye.tenseven.extras.notes.MainActivity::class.java\n',
        "",
        "SABLE_HOME_NO_NOTES_FRAME",
    )
    replace_once(
        summaries,
        '                "local:calculator" -> io.github.aimindseye.tenseven.extras.calculator.MainActivity::class.java\n',
        "",
        "SABLE_HOME_NO_CALCULATOR_FRAME",
    )
    frames = java / "frames/FrameWindow.kt"
    for key in ("notes", "calculator"):
        replace_once(
            frames,
            f'            "local:{key}" to ROOT + "extras.{key}.MainActivity",\n',
            "",
            f"SABLE_HOME_NO_{key.upper()}_WINDOW",
        )
    replace_once(frames, '            "local:notes" -> "TenSeven Notes"\n', "", "SABLE_HOME_NO_NOTES_TITLE")
    replace_once(frames, '            "local:calculator" -> "TenSeven Calculator"\n', "", "SABLE_HOME_NO_CALCULATOR_TITLE")


    plan_test = root / TEST / "onboarding/OnboardingPlanTest.kt"
    replace_once(
        plan_test,
        "        assertEquals(SetupStep.entries, OnboardingPlan.steps(everything))\n",
        "        assertEquals(SetupStep.entries - SetupStep.EXTRAS, OnboardingPlan.steps(everything))\n",
        "SABLE_HOME_NO_EXTRAS_STEP_TEST",
    )
    replace_once(
        plan_test,
        "SetupStep.GRID in unavailable && SetupStep.EXTRAS in unavailable",
        "SetupStep.GRID in unavailable && SetupStep.EXTRAS !in unavailable",
        "SABLE_HOME_NO_EXTRAS_STEP_TEST_UNAVAILABLE",
    )


def strip_phone(root: pathlib.Path) -> None:
    java = root / JAVA
    replace_once(
        java / "LauncherSettingsActivity.kt",
        "    D5PhoneRoleSetting()\n",
        "",
        "SABLE_HOME_NO_PHONE_ROLE_SETTING",
    )
    cut_once(
        java / "OnboardingActivity.kt",
        "    private fun applyExtras(ids: Set<String>) {\n",
        "\n    private fun requestHomeRole() {",
        "    private fun applyExtras(ids: Set<String>) {\n"
        "        ExtraAppsControl.apply(this, ids)\n"
        "        refresh()\n"
        "    }\n",
        "SABLE_HOME_NO_PHONE_ROLE_OFFER",
    )


def strip_screen_capture(root: pathlib.Path) -> None:
    java = root / JAVA
    section = java / "ui/FrameSettingsSection.kt"
    replace_once(section, "import io.github.aimindseye.tenseven.ScreenCaptureActivity\n", "", "SABLE_HOME_NO_CAPTURE_IMPORT")
    replace_once(section, "    ScreenCaptureControls()\n", "", "SABLE_HOME_NO_CAPTURE_CONTROLS")
    cut_once(
        section,
        "@Composable\nprivate fun ScreenCaptureControls() {\n",
        "@Composable\nprivate fun FrameIntegrationControls() {\n",
        "",
        "SABLE_HOME_NO_CAPTURE_SETTINGS",
    )
    page = java / "ui/ActiveFramesPage.kt"
    replace_once(
        page,
        "    val capture by io.github.aimindseye.tenseven.platform.ScreenCaptureState.state\n"
        "        .collectAsState()\n",
        "",
        "SABLE_HOME_NO_CAPTURE_STATE",
    )
    cut_once(
        page,
        "        if (capture.running) {\n",
        "        ActiveFramesGrid(\n",
        "",
        "SABLE_HOME_NO_CAPTURE_PREVIEW",
    )
    start = java / "SableStartActivity.kt"
    for line, marker in (
        ("        io.github.aimindseye.tenseven.platform.ScreenCaptureState.homeVisible = true\n", "SABLE_HOME_NO_CAPTURE_START"),
        ("        io.github.aimindseye.tenseven.platform.ScreenCaptureState.homeVisible = false\n", "SABLE_HOME_NO_CAPTURE_STOP"),
        (
            "        if (style == HomeStyle.Bb7) stopService(Intent(this, "
            "io.github.aimindseye.tenseven.platform.ScreenCaptureService::class.java))\n",
            "SABLE_HOME_NO_CAPTURE_SERVICE",
        ),
    ):
        replace_once(start, line, "", marker)


def strip_first_run(root: pathlib.Path) -> None:
    """SableOS Setup already chose the home, its look and notification access; TenSeven's own setup
    stays in the menu and Command, but no longer opens over the home on first boot."""
    replace_once(
        root / JAVA / "SableStartActivity.kt",
        "            // Once, on first run: the home role and each optional permission, with the reason for it.\n"
        "            if (!startStateRepository.loadOnboardingDone()) {\n"
        "                runCatching { startActivity(Intent(this, OnboardingActivity::class.java)) }\n"
        "            }\n",
        "",
        "SABLE_HOME_NO_FIRST_RUN_SETUP",
    )


def patch_leftovers(root: pathlib.Path) -> None:
    strip_manifest(root)
    strip_extras(root)
    strip_phone(root)
    strip_screen_capture(root)
    strip_first_run(root)
    for relative in LEFTOVER_PATHS:
        path = root / relative
        if not path.exists():
            fail(f"SABLE_HOME_LEFTOVER_PATH=FAIL path={relative}")
        if path.is_dir():
            shutil.rmtree(path)
        else:
            path.unlink()

def patch_hub_mail(root: pathlib.Path) -> None:
    """Hub shows Sable Mail's inboxes from its feed (org.sableos.mail.hub), not its notifications."""
    shutil.copyfile(HERE / "src/SableMailFeed.kt", root / JAVA / "conversations/SableMailFeed.kt")
    (root / TEST / "conversations").mkdir(parents=True, exist_ok=True)
    shutil.copyfile(HERE / "src/SableMailFeedTest.kt", root / TEST / "conversations/SableMailFeedTest.kt")
    hub = root / JAVA / "conversations/ConversationHub.kt"
    replace_once(
        hub,
        "    fun ensureStateLoaded(context: Context) {\n        if (stateLoaded) return\n",
        "    fun ensureStateLoaded(context: Context) {\n"
        "        SableMailFeed.start(context) { listeners.forEach { it() } }\n"
        "        if (stateLoaded) return\n",
        "SABLE_HOME_HUB_MAIL_START",
    )
    replace_once(
        hub,
        "    fun notifications(): List<HubNotification> = synchronized(lock) { items }\n",
        "    fun notifications(): List<HubNotification> = synchronized(lock) { SableMailFeed.merge(items) }\n",
        "SABLE_HOME_HUB_MAIL_NOTIFICATIONS",
    )
    replace_once(
        hub,
        "synchronized(lock) { buildConversations(items, sentReplies) }",
        "synchronized(lock) { buildConversations(SableMailFeed.merge(items), sentReplies) }",
        "SABLE_HOME_HUB_MAIL_CONVERSATIONS",
    )
    replace_once(
        hub,
        "            markHubSourceRead(context, n)\n",
        "            markHubSourceRead(context, n)\n            SableMailFeed.markRead(context, n)\n",
        "SABLE_HOME_HUB_MAIL_MARK_READ",
    )
    replace_once(
        root / "app/src/main/AndroidManifest.xml",
        '        <provider android:authorities="org.sableos.mail.snapshot" />\n',
        '        <provider android:authorities="org.sableos.mail.snapshot" />\n'
        '        <provider android:authorities="org.sableos.mail.hub" />\n',
        "SABLE_HOME_HUB_MAIL_QUERIES",
    )


def patch_back_to_frames(root: pathlib.Path) -> None:
    """Back at an app's root returns to Active Frames, as Home does (2.0 plan, BB10)."""
    shutil.copyfile(HERE / "src/SableBackToFrames.kt", root / JAVA / "frames/SableBackToFrames.kt")
    (root / TEST / "frames").mkdir(parents=True, exist_ok=True)
    shutil.copyfile(HERE / "src/SableBackToFramesTest.kt", root / TEST / "frames/SableBackToFramesTest.kt")
    activity = root / JAVA / "SableStartActivity.kt"
    replace_once(
        activity,
        "    override fun onResume() {\n        super.onResume()\n",
        "    // SableOS: set as the home stops for another app; Back at that app's root then shows Active Frames.\n"
        "    private var leftForAnotherApp = false\n"
        "\n"
        "    override fun onResume() {\n"
        "        super.onResume()\n"
        "        if (leftForAnotherApp) {\n"
        "            leftForAnotherApp = false\n"
        "            screen.homeGeneration.intValue += 1\n"
        "            endBb10Detour()\n"
        "        }\n",
        "SABLE_HOME_BACK_TO_FRAMES_RESUME",
    )
    replace_once(
        activity,
        "        super.onNewIntent(intent)\n        setIntent(intent)\n",
        "        super.onNewIntent(intent)\n        setIntent(intent)\n        leftForAnotherApp = false\n",
        "SABLE_HOME_BACK_TO_FRAMES_NEW_INTENT",
    )
    replace_once(
        activity,
        "    override fun onStop() {\n",
        "    override fun onStop() {\n"
        "        leftForAnotherApp =\n"
        "            io.github.aimindseye.tenseven.frames.SableBackToFrames.leftForAnotherApp(\n"
        "                packageName,\n"
        "                io.github.aimindseye.tenseven.frames.SableBackToFrames.topPackage(this),\n"
        "                isChangingConfigurations,\n"
        "            )\n",
        "SABLE_HOME_BACK_TO_FRAMES_STOP",
    )


def patch_hub_peek(root: pathlib.Path) -> None:
    """Hub over any app (2.0 plan, BB10): a panel over the app in front, from holding the Recents key
    (frameworks/base 0326) or Hub in the Alt+BlackBerry key menu. Not exported: the system and the home
    start it by its component."""
    manifest = root / "app/src/main/AndroidManifest.xml"
    replace_once(
        manifest,
        "        <!-- SableOS: first-boot setup's Look step saves the home style. -->\n",
        "        <!-- SableOS: Hub over any app (hold the Recents key; frameworks/base 0326). -->\n"
        "        <activity\n"
        "            android:name=\".SableHubPeekActivity\"\n"
        "            android:exported=\"false\"\n"
        "            android:excludeFromRecents=\"true\"\n"
        "            android:noHistory=\"true\"\n"
        "            android:taskAffinity=\"\"\n"
        "            android:theme=\"@android:style/Theme.Translucent.NoTitleBar\" />\n"
        "        <!-- SableOS: first-boot setup's Look step saves the home style. -->\n",
        "SABLE_HOME_HUB_PEEK",
    )
    shutil.copyfile(HERE / "src/SableHubPeekActivity.kt", root / JAVA / "SableHubPeekActivity.kt")
    shutil.copyfile(HERE / "src/SableHubPeek.kt", root / JAVA / "conversations/SableHubPeek.kt")
    (root / TEST / "conversations").mkdir(parents=True, exist_ok=True)
    shutil.copyfile(HERE / "src/SableHubPeekTest.kt", root / TEST / "conversations/SableHubPeekTest.kt")


def patch_frame_covers(root: pathlib.Path) -> None:
    """The cover API (2.0 plan, BB10 Active Frame covers): apps say what their own frame shows through
    content://org.sableos.launcher.covers (SableCoverProvider); covers are in memory and the caller's own."""
    frames = root / JAVA / "frames"
    shutil.copyfile(HERE / "src/SableFrameCovers.kt", frames / "SableFrameCovers.kt")
    shutil.copyfile(HERE / "src/SableCoverProvider.kt", frames / "SableCoverProvider.kt")
    (root / TEST / "frames").mkdir(parents=True, exist_ok=True)
    shutil.copyfile(HERE / "src/SableFrameCoversTest.kt", root / TEST / "frames/SableFrameCoversTest.kt")
    replace_once(
        frames / "AppSummary.kt",
        '    Local("Local summary"),\n',
        '    Local("Local summary"),\n    Cover("App cover"),\n',
        "SABLE_HOME_COVER_SOURCE",
    )
    # A cover is the app's own words, so like Local data it needs no notification access.
    replace_once(
        root / TEST / "frames/AppSummaryTest.kt",
        "source in setOf(SummarySource.Local)",
        "source in setOf(SummarySource.Local, SummarySource.Cover)",
        "SABLE_HOME_COVER_SUMMARY_TEST",
    )
    replace_once(
        root / JAVA / "platform/AppFrameSummaryRepository.kt",
        "            buildList {\n                if (allowed) {\n",
        "            buildList {\n"
        "                // SableOS cover API: an app's own words for its frame come first.\n"
        "                addAll(SableFrameCovers.snapshot(now))\n"
        "                if (allowed) {\n",
        "SABLE_HOME_COVER_SNAPSHOT",
    )
    replace_once(
        root / "app/src/main/AndroidManifest.xml",
        "        <!-- SableOS: first-boot setup's Look step saves the home style. -->\n",
        "        <!-- SableOS cover API: apps say what their own Active Frame shows (in memory only). -->\n"
        "        <provider\n"
        "            android:name=\".frames.SableCoverProvider\"\n"
        "            android:authorities=\"org.sableos.launcher.covers\"\n"
        "            android:exported=\"true\" />\n"
        "        <!-- SableOS: first-boot setup's Look step saves the home style. -->\n",
        "SABLE_HOME_COVER_PROVIDER",
    )


def patch_no_status_dials(root: pathlib.Path) -> None:
    """No status dials and no status line on either home (owner, 2026-11-18). The BB10 home keeps only the
    Settings gear; the BB7 banner keeps its date, weather and unread count. Home status is always Hidden,
    so a saved Dials or Text choice from an earlier build is ignored; the Edit Home "rings" module and the
    Launcher settings Home status choices are gone. The Device Monitor frame keeps its dials. The status
    composables and strings stay in the source, unreachable (a later clean-up removes them)."""
    state = root / JAVA / "platform/StartStateRepository.kt"
    replace_once(
        state,
        "    /** One Home status mode, shared across BB7/BB10. The old selected-style choice is retained. */\n"
        "    fun loadHomeStatusMode(): io.github.aimindseye.tenseven.model.HomeStatusMode =\n"
        "        io.github.aimindseye.tenseven.model.HomeStatusMode.decode(\n"
        '            preferences.getString("home_status_mode_shared", null)\n'
        "                ?: preferences.getString(\n"
        "                    io.github.aimindseye.tenseven.model.HomeStatusMode\n"
        "                        .preference(loadHomeStyle()),\n"
        "                    null,\n"
        "                ),\n"
        "            loadHomeDials().size,\n"
        "        )\n",
        "    /** SableOS: the status dials and the status line are removed, so Home status is always Hidden. */\n"
        "    fun loadHomeStatusMode(): io.github.aimindseye.tenseven.model.HomeStatusMode =\n"
        "        io.github.aimindseye.tenseven.model.HomeStatusMode.Hidden\n",
        "SABLE_NO_STATUS_DIALS_MODE",
    )
    replace_once(
        root / JAVA / "ui/ActiveFramesPage.kt",
        "        if (shown(HomeLayout.STATUS_RINGS) && dials.isNotEmpty()) StatusDialsRow(onOpenSettings, dials)\n",
        "        StatusDialsRow(onOpenSettings, dials)\n",
        "SABLE_NO_STATUS_DIALS_GEAR",
    )
    layout = root / JAVA / "model/HomeLayout.kt"
    replace_once(layout, '    const val STATUS_RINGS = "rings"\n', "", "SABLE_NO_STATUS_DIALS_CONST")
    replace_once(
        layout,
        "listOf(STATUS_RINGS, WEATHER, SEARCH_ROW)",
        "listOf(WEATHER, SEARCH_ROW)",
        "SABLE_NO_STATUS_DIALS_SECTIONS",
    )
    replace_once(layout, '            STATUS_RINGS -> t("status_dials")\n', "", "SABLE_NO_STATUS_DIALS_LABEL")
    replace_once(layout, '            STATUS_RINGS -> t("wi_fi_mobile_cpu_ram")\n', "", "SABLE_NO_STATUS_DIALS_DESCRIPTION")
    replace_once(
        layout,
        " * Top to bottom: the status dials (Wi-Fi, Mobile, CPU, RAM, battery time\n"
        " * left, Cell, and the Settings gear; on by default, owner request), the\n"
        " * location's weather",
        " * Top to bottom: the Settings gear (the status dials are removed), the\n"
        " * location's weather",
        "SABLE_NO_STATUS_DIALS_DOC",
    )
    replace_once(
        layout,
        "    /** Never customised: status dials and weather shown; the search row hidden. */\n",
        "    /** Never customised: weather shown; the search row hidden. */\n",
        "SABLE_NO_STATUS_DIALS_DEFAULT_DOC",
    )
    layout_test = root / TEST / "model/HomeLayoutTest.kt"
    replace_once(
        layout_test,
        "    fun neverCustomisedShowsStatusDialsAndWeather() {\n"
        "        val hidden = HomeLayout.normalizeHidden(null)\n"
        "        assertTrue(HomeLayout.isShown(hidden, HomeLayout.STATUS_RINGS))\n",
        "    fun neverCustomisedShowsWeather() {\n"
        "        val hidden = HomeLayout.normalizeHidden(null)\n",
        "SABLE_NO_STATUS_DIALS_TEST_DEFAULT",
    )
    replace_once(
        layout_test,
        '        // Dials first, then weather and date: no clock, no greeting.\n'
        '        assertEquals(listOf("rings", "weather", "search"), HomeLayout.SECTIONS)\n',
        '        // Weather and date: no clock, no greeting, no status dials.\n'
        '        assertEquals(listOf("weather", "search"), HomeLayout.SECTIONS)\n',
        "SABLE_NO_STATUS_DIALS_TEST_SECTIONS",
    )
    replace_once(
        layout_test,
        '        assertEquals(setOf("rings"), HomeLayout.normalizeHidden(setOf("rings", "bogus")))\n',
        '        assertEquals(setOf("search"), HomeLayout.normalizeHidden(setOf("rings", "search", "bogus")))\n',
        "SABLE_NO_STATUS_DIALS_TEST_HIDDEN",
    )
    replace_once(
        root / "app/src/androidTest/java/io/github/aimindseye/tenseven/ui/ActiveFramesUiTest.kt",
        "                        io.github.aimindseye.tenseven.model.HomeLayout.STATUS_RINGS,\n",
        "",
        "SABLE_NO_STATUS_DIALS_UI_TEST",
    )
    settings = root / JAVA / "LauncherSettingsActivity.kt"
    replace_once(
        settings,
        '    Section("Home status")\n    Settings2UnifiedHomeStatus(state)\n',
        "",
        "SABLE_NO_STATUS_DIALS_SETTINGS_PAGE",
    )
    replace_once(
        settings,
        "    Card(\n"
        '        "Home status",\n'
        "        state.loadHomeStatusMode().name.let {\n"
        "            if (it ==\n"
        '                "Text"\n'
        "            ) {\n"
        '                "Icons"\n'
        "            } else {\n"
        "                it\n"
        "            }\n"
        "        },\n"
        "        false,\n"
        '    ) { select("appearance_home") }\n',
        "",
        "SABLE_NO_STATUS_DIALS_SETTINGS_CARD",
    )
    text = settings.read_text(encoding="utf-8")
    head = '@Suppress("FunctionNaming")\n@Composable\nprivate fun Settings2UnifiedHomeStatus('
    tail = "/** Weather is off until a city is provided."
    if text.count(head) != 1 or text.count(tail) != 1:
        fail(f"SABLE_NO_STATUS_DIALS_SETTINGS_FUNCTION=FAIL path={settings}")
    start, end = text.index(head), text.index(tail)
    if start > end:
        fail(f"SABLE_NO_STATUS_DIALS_SETTINGS_FUNCTION=FAIL_ORDER path={settings}")
    settings.write_text(text[:start] + text[end:], encoding="utf-8")


def patch_extra_dim(root: pathlib.Path) -> None:
    """Dimmer than the panel's lowest step (owner, 2026-11-12): with the brightness slider at the bottom the
    home turns on Android's Extra dim, and turns it off when the slider moves up (SableExtraDim)."""
    shutil.copyfile(HERE / "src/SableExtraDim.kt", root / JAVA / "platform/SableExtraDim.kt")
    (root / TEST / "platform").mkdir(parents=True, exist_ok=True)
    shutil.copyfile(HERE / "src/SableExtraDimTest.kt", root / TEST / "platform/SableExtraDimTest.kt")
    replace_once(
        root / "app/src/main/AndroidManifest.xml",
        '    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />\n',
        '    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />\n'
        "    <!-- SableOS: Extra dim at the bottom of the brightness slider (platform certificate). -->\n"
        '    <uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" tools:ignore="ProtectedPermissions" />\n',
        "SABLE_HOME_EXTRA_DIM_PERMISSION",
    )
    replace_once(
        root / JAVA / "SableStartActivity.kt",
        "        StartText.t.bindTo(this)\n",
        "        StartText.t.bindTo(this)\n"
        "        io.github.aimindseye.tenseven.platform.SableExtraDim.start(this)\n",
        "SABLE_HOME_EXTRA_DIM_START",
    )
    replace_once(
        root / JAVA / "LauncherSettingsActivity.kt",
        "    Settings2ReadabilityOptions(state)\n",
        "    Settings2ReadabilityOptions(state)\n"
        "    run {\n"
        "        val dimContext = androidx.compose.ui.platform.LocalContext.current\n"
        "        var extraDim by remember { mutableStateOf(io.github.aimindseye.tenseven.platform.SableExtraDim.isEnabled(dimContext)) }\n"
        "        SwitchRow(\n"
        '            "Extra dim at lowest brightness",\n'
        '            "With the brightness slider at the bottom, Android\'s Extra dim makes the screen dimmer than the panel goes",\n'
        "            extraDim,\n"
        "        ) {\n"
        "            extraDim = it\n"
        "            io.github.aimindseye.tenseven.platform.SableExtraDim.setEnabled(dimContext, it)\n"
        "        }\n"
        "    }\n",
        "SABLE_HOME_EXTRA_DIM_SETTING",
    )


def patch_home_access(root: pathlib.Path) -> None:
    """The home is part of SableOS, so it needs no permission setup (owner, 2026-11-12): SableHomeAccess
    grants it Contacts, Calendar, Photos, Call log, Notifications, notification access, usage access and
    display over other apps once, and Launcher settings drop the Active Frames page."""
    shutil.copyfile(HERE / "src/SableHomeAccess.kt", root / JAVA / "platform/SableHomeAccess.kt")
    shutil.copyfile(HERE / "src/SableHomeAccessTest.kt", root / TEST / "platform/SableHomeAccessTest.kt")
    replace_once(
        root / "app/src/main/AndroidManifest.xml",
        "    <!-- SableOS: Extra dim at the bottom of the brightness slider (platform certificate). -->\n",
        "    <!-- SableOS: the home grants itself its permissions once (SableHomeAccess, platform certificate). -->\n"
        '    <uses-permission android:name="android.permission.GRANT_RUNTIME_PERMISSIONS" tools:ignore="ProtectedPermissions" />\n'
        '    <uses-permission android:name="android.permission.MANAGE_APP_OPS_MODES" tools:ignore="ProtectedPermissions" />\n'
        '    <uses-permission android:name="android.permission.MANAGE_NOTIFICATION_LISTENERS" tools:ignore="ProtectedPermissions" />\n'
        "    <!-- SableOS: Extra dim at the bottom of the brightness slider (platform certificate). -->\n",
        "SABLE_HOME_ACCESS_PERMISSIONS",
    )
    replace_once(
        root / JAVA / "SableStartActivity.kt",
        "        io.github.aimindseye.tenseven.platform.SableExtraDim.start(this)\n",
        "        io.github.aimindseye.tenseven.platform.SableExtraDim.start(this)\n"
        "        io.github.aimindseye.tenseven.platform.SableHomeAccess.ensure(this)\n",
        "SABLE_HOME_ACCESS_START",
    )
    settings = root / JAVA / "LauncherSettingsActivity.kt"
    replace_once(
        settings,
        '    Settings2Category(\n        "frames",\n        "5. Active Frames Settings",\n'
        '        "Media, app summaries and controls",\n    ) { select(it) }\n',
        "",
        "SABLE_HOME_ACCESS_NO_FRAMES_PAGE",
    )
    replace_once(settings, '"6. Apps & Features"', '"5. Apps & Features"', "SABLE_HOME_ACCESS_RENUMBER_APPS")
    replace_once(settings, '"7. Review & Finish"', '"6. Review & Finish"', "SABLE_HOME_ACCESS_RENUMBER_REVIEW")
    replace_once(
        settings,
        '    Card("Active Frames", "Saved media and recent-app settings", false) {\n        select("frames")\n    }\n',
        "",
        "SABLE_HOME_ACCESS_NO_FRAMES_CARD",
    )
    # No Permissions page either (owner, 2026-11-12): SableHomeAccess grants them, so setup and Settings
    # open on Appearance & Home, the steps renumber from 1, and Next/Back skip both removed pages.
    replace_once(
        settings,
        '    Settings2Category(\n        "permissions",\n        "1. Permissions",\n'
        '        "Contacts, photos, recent apps and notifications",\n    ) { select(it) }\n',
        "",
        "SABLE_HOME_NO_PERMISSIONS_PAGE",
    )
    for old_title, new_title in (
        ('"2. Appearance & Home"', '"1. Appearance & Home"'),
        ('"3. Hub Settings"', '"2. Hub Settings"'),
        ('"4. Lockscreen Settings"', '"3. Lockscreen Settings"'),
        ('"5. Apps & Features"', '"4. Apps & Features"'),
        ('"6. Review & Finish"', '"5. Review & Finish"'),
    ):
        replace_once(settings, old_title, new_title, "SABLE_HOME_NO_PERMISSIONS_RENUMBER")
    replace_once(
        settings,
        'onSetup = { settingsPage.value = "permissions" },',
        'onSetup = { settingsPage.value = "appearance_home" },',
        "SABLE_HOME_NO_PERMISSIONS_SETUP",
    )
    text = settings.read_text(encoding="utf-8")
    start = text.index("    val overlayAllowedForReview = android.provider.Settings.canDrawOverlays(context)\n")
    end_marker = '    ) { select("permissions") }\n'
    end = text.index(end_marker, start) + len(end_marker)
    if text.count(end_marker) != 1:
        fail("SABLE_HOME_NO_PERMISSIONS_CARD=FAIL")
    settings.write_text(text[:start] + text[end:], encoding="utf-8")
    replace_once(
        settings,
        '"Optional. Grant Weather location under Permissions; otherwise the manual city is used."',
        '"Optional. Allow location for the home in Android Settings > Apps; otherwise the manual city is used."',
        "SABLE_HOME_NO_PERMISSIONS_WEATHER_HINT",
    )
    replace_once(
        root / JAVA / "OnboardingActivity.kt",
        '.putExtra("tenseven.settings.category", "permissions"),',
        '.putExtra("tenseven.settings.category", "appearance_home"),',
        "SABLE_HOME_NO_PERMISSIONS_ONBOARDING",
    )
    flow = root / JAVA / "model/Settings2Flow.kt"
    replace_once(flow, '            "permissions",\n', "", "SABLE_HOME_NO_PERMISSIONS_FLOW")
    replace_once(flow, '            "frames",\n', "", "SABLE_HOME_NO_FRAMES_FLOW")
    (root / TEST / "model/Settings2FlowTest.kt").write_text(
        "package io.github.aimindseye.tenseven.model\n\n"
        "import org.junit.Assert.assertEquals\n"
        "import org.junit.Assert.assertNull\n"
        "import org.junit.Test\n\n"
        "class Settings2FlowTest {\n"
        "    @Test fun fivePagesWithoutPermissionsOrFrames() {\n"
        "        assertEquals(listOf(\"appearance_home\", \"hub\", \"lock\", \"apps\", \"review\"), Settings2Flow.ordered)\n"
        "    }\n\n"
        "    @Test fun footerCanTraverseEachPage() {\n"
        "        assertEquals(\"appearance_home\", Settings2Flow.next(\"root\"))\n"
        "        assertEquals(\"hub\", Settings2Flow.next(\"appearance_home\"))\n"
        "        assertEquals(\"lock\", Settings2Flow.next(\"hub\"))\n"
        "        assertEquals(\"apps\", Settings2Flow.next(\"lock\"))\n"
        "        assertEquals(\"review\", Settings2Flow.next(\"apps\"))\n"
        "        assertNull(Settings2Flow.next(\"review\"))\n"
        "        assertEquals(\"root\", Settings2Flow.previous(\"appearance_home\"))\n"
        "        assertEquals(1, Settings2Flow.position(\"appearance_home\"))\n"
        "        assertNull(Settings2Flow.previous(\"root\"))\n"
        "    }\n\n"
        "    @Test fun themesReturnToAppearanceHome() {\n"
        "        assertEquals(\"appearance_home\", Settings2Flow.previous(\"themes\"))\n"
        "        assertEquals(\"appearance_home\", Settings2Flow.next(\"themes\"))\n"
        "    }\n"
        "}\n",
        encoding="utf-8",
    )


def patch_hub_keep(root: pathlib.Path) -> None:
    """Hub after the JL10 review (owner, 2026-11-12): items kept until deleted, Unread and date sections,
    each app with its count and Priority Hub in Views, a ⋮ menu, Mark Unread, Delete, and Hub settings for
    every message on its own, system notifications and the account apps."""
    conv = root / JAVA / "conversations"
    ui = root / JAVA / "ui"
    shutil.copyfile(HERE / "src/SableHubKeep.kt", conv / "SableHubKeep.kt")
    shutil.copyfile(HERE / "src/SableHubUi.kt", ui / "SableHubUi.kt")
    (root / TEST / "conversations").mkdir(parents=True, exist_ok=True)
    shutil.copyfile(HERE / "src/SableHubKeepTest.kt", root / TEST / "conversations/SableHubKeepTest.kt")

    hub = conv / "ConversationHub.kt"
    replace_once(
        hub,
        "        SableMailFeed.start(context) { listeners.forEach { it() } }\n",
        "        SableMailFeed.start(context) { listeners.forEach { it() } }\n        SableHubHistory.init(context)\n",
        "SABLE_HOME_HUB_KEEP_INIT",
    )
    replace_once(
        hub,
        "synchronized(lock) { SableMailFeed.merge(items) }",
        "synchronized(lock) { SableHubHistory.view(SableMailFeed.merge(items)) }",
        "SABLE_HOME_HUB_KEEP_NOTIFICATIONS",
    )
    replace_once(
        hub,
        "synchronized(lock) { buildConversations(SableMailFeed.merge(items), sentReplies) }",
        "synchronized(lock) {\n"
        "            SableHubHistory.rows(buildConversations(SableHubHistory.view(SableMailFeed.merge(items)), sentReplies))\n"
        "        }",
        "SABLE_HOME_HUB_KEEP_CONVERSATIONS",
    )
    replace_once(
        hub,
        "            items = change(items)\n",
        "            items = change(items)\n            SableHubHistory.record(items)\n",
        "SABLE_HOME_HUB_KEEP_RECORD",
    )
    replace_once(
        hub,
        "    fun clearNow() = clear()\n",
        "    fun clearNow() {\n        SableHubHistory.clear()\n        clear()\n    }\n",
        "SABLE_HOME_HUB_KEEP_CLEAR",
    )
    replace_once(
        hub,
        "    internal fun dropPackages(packages: Set<String>) = update { old -> old.filterNot { it.packageName in packages } }\n",
        "    internal fun dropPackages(packages: Set<String>) {\n"
        "        SableHubHistory.forget(packages)\n"
        "        update { old -> old.filterNot { it.packageName in packages } }\n"
        "    }\n",
        "SABLE_HOME_HUB_KEEP_DROP",
    )
    replace_once(
        hub,
        "    const val DEFAULT_MAX_APPS = 32\n",
        "    /** SableOS: the conversation counts as unread again, from its newest incoming message. */\n"
        "    fun markUnread(\n"
        "        context: Context,\n"
        "        conversation: HubConversation,\n"
        "    ) {\n"
        "        val at = SableHubKeep.unreadMark(conversation) ?: return\n"
        "        changeState(context) { it.markRead(conversation.id, at) }\n"
        "    }\n"
        "\n"
        "    /** SableOS: Delete takes the row out of the Hub and its notifications out of the shade. */\n"
        "    fun delete(\n"
        "        context: Context,\n"
        "        conversation: HubConversation,\n"
        "    ) {\n"
        "        ensureStateLoaded(context)\n"
        "        SableHubHistory.delete(conversation.notifications)\n"
        "        ConversationListenerService.cancelKeys(conversation.notifications.map(HubNotification::key))\n"
        "        notifyChanged()\n"
        "    }\n"
        "\n"
        "    fun notifyChanged() = listeners.forEach { it() }\n"
        "\n"
        "    const val DEFAULT_MAX_APPS = 32\n",
        "SABLE_HOME_HUB_KEEP_ACTIONS",
    )
    replace_once(
        conv / "ConversationListenerService.kt",
        "        fun refreshConnected(): Boolean {\n",
        "        /** SableOS Hub Delete: also clears those notifications from the shade (when the app allows). */\n"
        "        fun cancelKeys(keys: List<String>) {\n"
        "            val service = live.get() ?: return\n"
        "            keys.forEach { key -> runCatching { service.cancelNotification(key) } }\n"
        "        }\n"
        "\n"
        "        fun refreshConnected(): Boolean {\n",
        "SABLE_HOME_HUB_KEEP_CANCEL",
    )
    state = conv / "ConversationState.kt"
    replace_once(
        state,
        "    val snoozed: Boolean = false,\n",
        "    val snoozed: Boolean = false,\n    /** SableOS Priority Hub. */\n    val priority: Boolean = false,\n",
        "SABLE_HOME_HUB_PRIORITY_FILTER",
    )
    replace_once(
        state,
        "|| kind != ConversationKind.All || snoozed\n",
        "|| kind != ConversationKind.All || snoozed || priority\n",
        "SABLE_HOME_HUB_PRIORITY_ACTIVE",
    )
    replace_once(
        state,
        "            state.isSnoozed(c.id, now) == filter.snoozed &&\n",
        "            state.isSnoozed(c.id, now) == filter.snoozed &&\n"
        "                (!filter.priority || SableHubOptions.isPriority(c)) &&\n",
        "SABLE_HOME_HUB_PRIORITY_VIEW",
    )
    rows = ui / "HubConversationsUi.kt"
    replace_once(rows, "private const val MAX_CONVERSATIONS = 30\n", "private const val MAX_CONVERSATIONS = 300\n",
                 "SABLE_HOME_HUB_KEEP_MAX")
    replace_once(
        rows,
        '    conversationItems(visible.take(MAX_CONVERSATIONS), key = { "conversation:" + it.id }) { conversation ->\n'
        "        ConversationRow(conversation, ui)\n"
        "    }\n",
        "    // SableOS: Unread first, then a section per day, as BB10's Hub.\n"
        "    io.github.aimindseye.tenseven.conversations.SableHubKeep\n"
        "        .sections(visible.take(MAX_CONVERSATIONS), { ui.state.unreadCount(it) }, ui.now)\n"
        "        .forEach { section ->\n"
        '            item(key = "section:" + section.key) { SableHubSectionHeader(section.label) }\n'
        '            conversationItems(section.rows, key = { "conversation:" + it.id }) { conversation ->\n'
        "                ConversationRow(conversation, ui)\n"
        "            }\n"
        "        }\n",
        "SABLE_HOME_HUB_SECTIONS",
    )
    replace_once(
        ui / "HubPage.kt",
        "            modifier = Modifier.semantics { heading() },\n        )\n    }\n    Spacer(Modifier.height(6.dp))\n",
        "            modifier = Modifier.semantics { heading() },\n        )\n    }\n"
        "    SableHubSubtitle(session.data.conversations)\n"
        "    Spacer(Modifier.height(6.dp))\n",
        "SABLE_HOME_HUB_SUBTITLE",
    )
    bar = ui / "HubCompactBar.kt"
    replace_once(
        bar,
        '        HubCompactControl("Search", LineIconPaths.SEARCH, Modifier.weight(1f), onSearch)\n',
        '        HubCompactControl("Search", LineIconPaths.SEARCH, Modifier.weight(1f), onSearch)\n'
        "        SableHubMoreControl()\n",
        "SABLE_HOME_HUB_MORE",
    )
    replace_once(bar, "            .heightIn(max = 176.dp)\n", "            .heightIn(max = 300.dp)\n",
                 "SABLE_HOME_HUB_VIEWS_HEIGHT")
    replace_once(
        bar,
        "            }\n        }\n    }\n}\n\n/** Route a compact Hub action",
        "            }\n        }\n        SableHubViewsExtras(ui, onTabChange, onDismiss)\n    }\n}\n\n/** Route a compact Hub action",
        "SABLE_HOME_HUB_VIEWS_SOURCES",
    )
    panels = ui / "HubPanels.kt"
    replace_once(panels, "private fun PanelAction(\n", "internal fun PanelAction(\n", "SABLE_HOME_HUB_PANEL_ACTION")
    replace_once(
        panels,
        "    val remindLocation = remember { BringIntoViewRequester() }\n",
        "    SableHubRowActions(conversation, ui, onClose)\n"
        "    val remindLocation = remember { BringIntoViewRequester() }\n",
        "SABLE_HOME_HUB_ROW_ACTIONS",
    )
    replace_once(
        root / JAVA / "HubSettingsActivity.kt",
        "                hubContentSection(content)\n",
        "                hubContentSection(content)\n"
        "                io.github.aimindseye.tenseven.ui.SableHubSettingsSection()\n",
        "SABLE_HOME_HUB_SETTINGS",
    )


SHORT = "io.github.aimindseye.tenseven.design.isSableShortWindow()"


def patch_q25_fit(root: pathlib.Path) -> None:
    """Fits the Hub, Command and the home bar to the Q25's 411 dp square screen (owner, 2026-11-12).

    Sizes follow the Zinwa Q25 column of the approved TenSeven BB10 design review: a 22 sp Hub
    title, a flat 13 sp calendar row, 20-24 dp message icons, a one-line selected message and a
    26 sp screen title instead of Material's 45 sp display style. Each change applies only in a
    short window (isSableShortWindow), so the Titan 2 layout is unchanged. The BB10 home bar keeps
    a Command (search) icon on the Q25, where the compact policy used to drop it.
    """
    theme = root / DESIGN / "SableTheme.kt"
    replace_once(
        theme,
        "        labelLarge = SableTypography.labelLarge.copy(fontSize = 13.sp, lineHeight = 17.sp),\n    )\n",
        "        labelLarge = SableTypography.labelLarge.copy(fontSize = 13.sp, lineHeight = 17.sp),\n"
        "        // SableOS Q25: the Material roles the short scale left at phone sizes (display 45 sp).\n"
        "        displayMedium = SableTypography.displayMedium.copy(fontSize = 26.sp, lineHeight = 30.sp),\n"
        "        displaySmall = SableTypography.displaySmall.copy(fontSize = 24.sp, lineHeight = 28.sp),\n"
        "        headlineSmall = SableTypography.headlineSmall.copy(fontSize = 22.sp, lineHeight = 26.sp),\n"
        "        titleSmall = SableTypography.titleSmall.copy(fontSize = 15.sp, lineHeight = 19.sp),\n"
        "    )\n",
        "SABLE_HOME_Q25_TYPE",
    )
    peek = root / JAVA / "ui/HubCalendarPeek.kt"
    replace_once(
        peek,
        "                    .fillMaxWidth()\n"
        "                    .clip(RoundedCornerShape(7.dp))\n"
        "                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))\n"
        "                    .padding(horizontal = 9.dp, vertical = 6.dp),\n"
        "            verticalArrangement = Arrangement.spacedBy(8.dp),\n",
        "                    .fillMaxWidth()\n"
        "                    .then(\n"
        f"                        if ({SHORT}) {{\n"
        "                            Modifier.padding(horizontal = 4.dp)\n"
        "                        } else {\n"
        "                            Modifier\n"
        "                                .clip(RoundedCornerShape(7.dp))\n"
        "                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))\n"
        "                                .padding(horizontal = 9.dp, vertical = 6.dp)\n"
        "                        },\n"
        "                    ),\n"
        f"            verticalArrangement = Arrangement.spacedBy(if ({SHORT}) 2.dp else 8.dp),\n",
        "SABLE_HOME_Q25_CALENDAR_ROW",
    )
    replace_once(
        peek,
        "                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 6.dp),\n"
        "                color = MaterialTheme.colorScheme.primary,\n"
        "                style = MaterialTheme.typography.titleSmall,\n",
        "                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 6.dp),\n"
        "                color = MaterialTheme.colorScheme.primary,\n"
        f"                style = if ({SHORT}) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,\n",
        "SABLE_HOME_Q25_CALENDAR_TEXT",
    )
    rows = root / JAVA / "ui/HubConversationsUi.kt"
    replace_once(
        rows,
        "                        .padding(horizontal = 4.dp, vertical = 10.dp),\n",
        f"                        .padding(horizontal = 4.dp, vertical = if ({SHORT}) 7.dp else 10.dp),\n",
        "SABLE_HOME_Q25_ROW_HEIGHT",
    )
    replace_once(
        rows,
        "SableGlyphIcon(glyph = sourceGlyph, modifier = Modifier.size(30.dp))",
        f"SableGlyphIcon(glyph = sourceGlyph, modifier = Modifier.size(if ({SHORT}) 24.dp else 30.dp))",
        "SABLE_HOME_Q25_ROW_GLYPH",
    )
    replace_once(
        rows,
        "modifier = Modifier.size(36.dp).clip(RoundedCornerShape(7.dp)),",
        f"modifier = Modifier.size(if ({SHORT}) 28.dp else 36.dp).clip(RoundedCornerShape(7.dp)),",
        "SABLE_HOME_Q25_ROW_ICON",
    )
    panels = root / JAVA / "ui/HubPanels.kt"
    replace_once(
        panels,
        "            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {\n",
        f"            val short = {SHORT}\n"
        "            Column(Modifier.padding(if (short) 7.dp else 10.dp), verticalArrangement = Arrangement.spacedBy(if (short) 2.dp else 5.dp)) {\n",
        "SABLE_HOME_Q25_SELECTED_PADDING",
    )
    replace_once(
        panels,
        "Text(preview, color = Bb10Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 3)",
        "Text(\n"
        "                        preview,\n"
        "                        color = Bb10Muted,\n"
        "                        style = MaterialTheme.typography.bodyMedium,\n"
        "                        maxLines = if (short) 1 else 3,\n"
        "                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,\n"
        "                    )",
        "SABLE_HOME_Q25_SELECTED_ONE_LINE",
    )
    page = root / JAVA / "ui/HubPage.kt"
    replace_once(
        page,
        "    SableHubSubtitle(session.data.conversations)\n    Spacer(Modifier.height(6.dp))\n",
        f"    SableHubSubtitle(session.data.conversations)\n    Spacer(Modifier.height(if ({SHORT}) 2.dp else 6.dp))\n",
        "SABLE_HOME_Q25_HUB_HEADER",
    )
    command = root / JAVA / "ui/CommandScreen.kt"
    replace_once(
        command,
        "    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {\n"
        "        MetroHeader(title = \"search\", onBack = onBack)\n",
        f"    val short = {SHORT}\n"
        "    Column(\n"
        "        modifier =\n"
        "            Modifier.fillMaxSize().padding(\n"
        "                horizontal = if (short) 12.dp else 20.dp,\n"
        "                vertical = if (short) 6.dp else 18.dp,\n"
        "            ),\n"
        "    ) {\n"
        "        MetroHeader(title = \"search\", onBack = onBack)\n",
        "SABLE_HOME_Q25_COMMAND_PADDING",
    )
    replace_once(
        command,
        "                .heightIn(min = 62.dp),\n",
        f"                .heightIn(min = if ({SHORT}) 48.dp else 62.dp),\n",
        "SABLE_HOME_Q25_COMMAND_FIELD",
    )
    bar = root / JAVA / "ui/ReferenceHomeBar.kt"
    replace_once(
        bar,
        "        slots.filter { it.token in shown && it.token == QuickBar.COMMAND }.forEach { slot ->\n"
        "            HomeShortcut(slot, actions.openSlot, actions.options, actions.phoneFocus)\n"
        "        }\n",
        "        // SableOS: Command always has its icon before Camera, also on the Q25 (owner, 2026-11-12).\n"
        "        val command =\n"
        "            slots.firstOrNull { it.token in shown && it.token == QuickBar.COMMAND }\n"
        "                ?: QuickSlot(QuickBar.COMMAND, \"Command\", null)\n"
        "        HomeShortcut(command, actions.openSlot, actions.options, actions.phoneFocus)\n",
        "SABLE_HOME_Q25_COMMAND_ICON",
    )


def patch_quiet_lock(root: pathlib.Path) -> None:
    """With no notifications the home's lock layer is just the clock and date (owner, 2026-11-13).

    "Select a notification icon" only makes sense beside a column of icons; with none it read as an
    instruction with nothing to select. It shows again as soon as a notification arrives.
    """
    lock = root / JAVA / "ui/LockNotificationScreen.kt"
    replace_once(
        lock,
        "        content.source == null -> {\n"
        "            Text(\"Select a notification icon\", color = Bb10Muted, style = MaterialTheme.typography.bodyMedium)\n"
        "        }\n",
        "        content.source == null -> {\n"
        "            // SableOS: no hint without icons to pick from (owner, 2026-11-13).\n"
        "            if (content.entries.isNotEmpty()) {\n"
        "                Text(\"Select a notification icon\", color = Bb10Muted, style = MaterialTheme.typography.bodyMedium)\n"
        "            }\n"
        "        }\n",
        "SABLE_HOME_QUIET_LOCK",
    )


def patch_recent_tab(root: pathlib.Path) -> None:
    """The BlackBerry 7 tray gets a Recent panel after Favorites (owner, 2026-11-16): the apps opened last,
    newest first, saved beside the usage counts the Frequent panel already keeps. It is on by default;
    Menu can hide it like any panel."""
    shutil.copyfile(HERE / "src/SableRecents.kt", root / JAVA / "model/SableRecents.kt")
    shutil.copyfile(HERE / "src/SableRecentsTest.kt", root / TEST / "model/SableRecentsTest.kt")
    home = root / JAVA / "model/Bb7Home.kt"
    replace_once(
        home,
        '    Favorites("favorites"),\n    All("all"),\n',
        '    Favorites("favorites"),\n\n    /** SableOS: the apps opened last, newest first (SableRecents). */\n'
        '    Recent("recent", fixedTitle = "Recent"),\n    All("all"),\n',
        "SABLE_RECENT_ENUM",
    )
    replace_once(
        home,
        "listOf(Bb7Panel.Favorites, Bb7Panel.All, Bb7Panel.Downloads)",
        "listOf(Bb7Panel.Favorites, Bb7Panel.Recent, Bb7Panel.All, Bb7Panel.Downloads)",
        "SABLE_RECENT_DEFAULT",
    )
    replace_once(
        home,
        "        return when (panel) {\n",
        "        return when (panel) {\n"
        "            Bb7Panel.Recent -> {\n"
        "                val byKey = apps.associateBy { it.stableKey() }\n"
        "                SableRecents.keys.mapNotNull { byKey[it] }\n"
        "            }\n\n",
        "SABLE_RECENT_APPS",
    )
    usage = root / JAVA / "platform/AppUsageRepository.kt"
    replace_once(
        usage,
        "    fun load(): AppUsage = AppUsage.decode(preferences.getString(KEY_COUNTS, null))\n",
        "    fun load(): AppUsage {\n"
        "        // SableOS: the Recent panel reads what was saved, so it survives the home restarting.\n"
        "        io.github.aimindseye.tenseven.model.SableRecents.keys =\n"
        "            io.github.aimindseye.tenseven.model.SableRecents.decode(preferences.getString(KEY_RECENT, null))\n"
        "        return AppUsage.decode(preferences.getString(KEY_COUNTS, null))\n"
        "    }\n",
        "SABLE_RECENT_LOAD",
    )
    replace_once(
        usage,
        "        preferences.edit().putString(KEY_COUNTS, load().record(key).encode()).apply()\n",
        "        val recent =\n"
        "            io.github.aimindseye.tenseven.model.SableRecents.record(\n"
        "                io.github.aimindseye.tenseven.model.SableRecents.decode(preferences.getString(KEY_RECENT, null)),\n"
        "                key,\n"
        "            )\n"
        "        val counts = load().record(key).encode()\n"
        "        io.github.aimindseye.tenseven.model.SableRecents.keys = recent\n"
        "        preferences\n"
        "            .edit()\n"
        "            .putString(KEY_COUNTS, counts)\n"
        "            .putString(KEY_RECENT, io.github.aimindseye.tenseven.model.SableRecents.encode(recent))\n"
        "            .apply()\n",
        "SABLE_RECENT_RECORD",
    )
    replace_once(
        usage,
        "        preferences.edit().remove(KEY_COUNTS).apply()\n",
        "        io.github.aimindseye.tenseven.model.SableRecents.keys = emptyList()\n"
        "        preferences.edit().remove(KEY_COUNTS).remove(KEY_RECENT).apply()\n",
        "SABLE_RECENT_CLEAR",
    )
    replace_once(
        usage,
        '        const val KEY_COUNTS = "counts"\n',
        '        const val KEY_COUNTS = "counts"\n        const val KEY_RECENT = "recent"\n',
        "SABLE_RECENT_KEY",
    )
    tests = root / TEST / "model/Bb7HomeTest.kt"
    replace_once(
        tests,
        "        assertEquals(Bb7Panel.All, Bb7Home.step(Bb7Panel.Favorites, 1))\n",
        "        assertEquals(Bb7Panel.Recent, Bb7Home.step(Bb7Panel.Favorites, 1))\n",
        "SABLE_RECENT_TEST_STEP",
    )
    replace_once(
        tests,
        "listOf(Bb7Panel.Favorites, Bb7Panel.All, Bb7Panel.Downloads, Bb7Panel.Frequent), withFrequent)",
        "listOf(Bb7Panel.Favorites, Bb7Panel.Recent, Bb7Panel.All, Bb7Panel.Downloads, Bb7Panel.Frequent), withFrequent)",
        "SABLE_RECENT_TEST_FREQUENT",
    )


def patch_profiles_shortcut(root: pathlib.Path) -> None:
    """The sound profile shortcut is on both homes (owner, 2026-11-16): the speaker icon in the BB7 banner
    and in the BB10 date header. A tap opens Sable Profiles (patch_leftovers), a long press cycles the
    ringer. The pinned TenSeven defines SoundProfileButton but places it on neither home (later TenSeven
    does), so a phone stuck on silent had no way back from the home screen."""
    replace_once(
        root / JAVA / "ui/Bb7HomeScreen.kt",
        "                if (!compact) HomeWeatherLine(header, modifier = Modifier.fillMaxWidth())\n"
        "            }\n"
        "            Spacer(Modifier.width(6.dp))\n",
        "                if (!compact) HomeWeatherLine(header, modifier = Modifier.fillMaxWidth())\n"
        "            }\n"
        "            SoundProfileButton()\n"
        "            Spacer(Modifier.width(6.dp))\n",
        "SABLE_PROFILES_SHORTCUT_BB7",
    )
    replace_once(
        root / JAVA / "ui/Bb10HomeChrome.kt",
        "                textAlign = TextAlign.Center,\n"
        "            )\n"
        "        }\n"
        "    }\n"
        "}\n"
        "\n"
        "/** [Bb10Theme] with the Hub peek layered over the Start strip. */\n",
        "                textAlign = TextAlign.Center,\n"
        "            )\n"
        "        }\n"
        "        SoundProfileButton()\n"
        "    }\n"
        "}\n"
        "\n"
        "/** [Bb10Theme] with the Hub peek layered over the Start strip. */\n",
        "SABLE_PROFILES_SHORTCUT_BB10",
    )


def patch_profile_setting(root: pathlib.Path) -> None:
    """The profile (BlackBerry 10 or 7) is a whole-OS setting, not the launcher's own preference: Sable
    Setup's Profile step and the launcher's style choice write Settings.Secure "sable_profile" (the key
    org.sableos.design.SableProfile reads), and the launcher reads it first so Hub, Settings and the
    Sable apps all follow one value. The launcher's own preference is the fallback until it is set."""
    repo = root / JAVA / "platform/StartStateRepository.kt"
    replace_once(
        repo,
        "    private val hubSources = HubSourceRepository(context)\n",
        "    private val hubSources = HubSourceRepository(context)\n"
        "    private val profileResolver = context.applicationContext.contentResolver\n",
        "SABLE_PROFILE_RESOLVER",
    )
    replace_once(
        repo,
        "    fun loadHomeStyle(): HomeStyle = HomeStyle.fromStableValue(preferences.getString(KEY_HOME_STYLE, null))\n",
        "    fun loadHomeStyle(): HomeStyle =\n"
        "        HomeStyle.fromStableValue(\n"
        "            runCatching { android.provider.Settings.Secure.getString(profileResolver, \"sable_profile\") }.getOrNull()\n"
        "                ?: preferences.getString(KEY_HOME_STYLE, null),\n"
        "        )\n",
        "SABLE_PROFILE_LOAD",
    )
    replace_once(
        repo,
        "        preferences.edit().putString(KEY_HOME_STYLE, style.stableValue).apply()\n",
        "        preferences.edit().putString(KEY_HOME_STYLE, style.stableValue).apply()\n"
        "        runCatching { android.provider.Settings.Secure.putString(profileResolver, \"sable_profile\", style.stableValue) }\n",
        "SABLE_PROFILE_SAVE",
    )


VISIBLE_UPSTREAM_NAME = re.compile(r"Ten[Ss]even(?: Launcher)?(?![A-Za-z0-9])")
KOTLIN_STRING = re.compile(r'"(?:[^"\\\n]|\\.)*"')


def patch_hub_default_action(root: pathlib.Path) -> None:
    """Hub rows follow the BlackBerry rule that Enter runs the default action and Menu shows the rest
    (conformance matrix, owner decisions 2026-11-18). Enter or a tap on a row opens the message in its
    source app; the Menu key or a long press opens the row's menu, which no longer starts with an Open
    item. The menu is the right-edge panel on BlackBerry 10 and a Menu-key list from the bottom edge on
    BlackBerry 7 (the profile is Settings.Secure sable_profile, read through the launcher's style)."""
    rows = root / JAVA / "ui/HubConversationsUi.kt"
    replace_once(
        rows,
        "import androidx.compose.foundation.clickable\n",
        "import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.combinedClickable\n",
        "SABLE_HUB_ENTER_IMPORT",
    )
    replace_once(
        rows,
        "                        .clickable { selectRow(conversation.id, rowFocus) }\n",
        "                        .combinedClickable(\n"
        "                            onClick = { ui.onOpen(conversation) },\n"
        "                            onLongClick = { selectRow(conversation.id, rowFocus) },\n"
        "                        )\n",
        "SABLE_HUB_ENTER_OPENS",
    )
    replace_once(
        rows,
        "                                event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_S -> {\n",
        "                                event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_MENU -> {\n"
        "                                    selectRow(conversation.id, rowFocus)\n"
        "                                    true\n"
        "                                }\n"
        "\n"
        "                                event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_S -> {\n",
        "SABLE_HUB_MENU_KEY",
    )
    panels = root / JAVA / "ui/HubPanels.kt"
    replace_once(
        panels,
        "    HubSelectedMessageContext(conversation, ui)\n"
        "    PanelAction(\n"
        "        io.github.aimindseye.tenseven.model.HubActionPolicy\n"
        "            .openLabel(conversation.latestNotification.kind),\n"
        "        iconName = \"mail\",\n"
        "    ) {\n"
        "        ui.onOpen(conversation)\n"
        "        onClose()\n"
        "    }\n",
        "    HubSelectedMessageContext(conversation, ui)\n",
        "SABLE_HUB_NO_OPEN_ITEM",
    )
    replace_once(
        panels,
        "    if (settings || conversation != null) {\n"
        "        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable { onClose() })\n"
        "        Column(\n"
        "            Modifier\n"
        "                .align(if (settings) Alignment.CenterStart else Alignment.CenterEnd)\n"
        "                .widthIn(max = 340.dp)\n"
        "                .fillMaxWidth(HUB_PANEL_FRACTION)\n"
        "                .fillMaxHeight()\n",
        "    val panelContext = LocalContext.current\n"
        "    val bb7Profile =\n"
        "        remember(panelContext, conversation) {\n"
        "            io.github.aimindseye.tenseven.platform\n"
        "                .StartStateRepository(panelContext)\n"
        "                .loadHomeStyle() == io.github.aimindseye.tenseven.model.HomeStyle.Bb7\n"
        "        }\n"
        "    val menuFromBottom = !settings && bb7Profile\n"
        "    if (settings || conversation != null) {\n"
        "        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable { onClose() })\n"
        "        Column(\n"
        "            Modifier\n"
        "                .align(\n"
        "                    if (menuFromBottom) {\n"
        "                        Alignment.BottomCenter\n"
        "                    } else if (settings) {\n"
        "                        Alignment.CenterStart\n"
        "                    } else {\n"
        "                        Alignment.CenterEnd\n"
        "                    },\n"
        "                ).then(\n"
        "                    if (menuFromBottom) {\n"
        "                        Modifier.fillMaxWidth().heightIn(max = 420.dp)\n"
        "                    } else {\n"
        "                        Modifier.widthIn(max = 340.dp).fillMaxWidth(HUB_PANEL_FRACTION).fillMaxHeight()\n"
        "                    },\n"
        "                )\n",
        "SABLE_HUB_MENU_PER_PROFILE",
    )


def patch_hub_reply_selector(root: pathlib.Path) -> None:
    """A merged Hub conversation (one person across SMS, chat and email, which the pinned upstream already
    groups by contact) replies through the source app the user picks: the message menu lists one Reply row
    per source app that can reply, and the newest one is used until a choice is made (conformance matrix,
    owner decision 2026-11-18: merged conversation with a reply selector)."""
    shutil.copyfile(HERE / "src/SableReplyChoice.kt", root / JAVA / "conversations/SableReplyChoice.kt")
    (root / TEST / "conversations").mkdir(parents=True, exist_ok=True)
    shutil.copyfile(HERE / "src/SableReplyChoiceTest.kt", root / TEST / "conversations/SableReplyChoiceTest.kt")
    replace_once(
        root / JAVA / "conversations/ConversationModels.kt",
        "    val replyTarget: HubNotification?\n"
        "        get() = notifications.filter(HubNotification::canReply).maxByOrNull(HubNotification::postedAt)\n",
        "    /** The newest replyable notification of each source app, newest first: the reply selector's choices. */\n"
        "    val replyTargets: List<HubNotification>\n"
        "        get() =\n"
        "            notifications\n"
        "                .filter(HubNotification::canReply)\n"
        "                .groupBy(HubNotification::packageName)\n"
        "                .values\n"
        "                .map { it.maxBy(HubNotification::postedAt) }\n"
        "                .sortedByDescending(HubNotification::postedAt)\n"
        "    val replyTarget: HubNotification?\n"
        "        get() = SableReplyChoice.pick(replyTargets, SableReplyChoice.packageFor(id), HubNotification::packageName)\n",
        "SABLE_REPLY_TARGETS",
    )
    panels = root / JAVA / "ui/HubPanels.kt"
    replace_once(
        panels,
        "    var failed by remember(source) { mutableStateOf(false) }\n"
        "    if (ui.deviceLocked) return\n"
        "    val primaryLabel =\n"
        "        source.replyAction\n",
        "    var failed by remember(source) { mutableStateOf(false) }\n"
        "    var choiceTick by remember(conversation.id) { mutableStateOf(0) }\n"
        "    if (ui.deviceLocked) return\n"
        "    val replySource = remember(conversation, choiceTick) { conversation.replyTarget ?: source }\n"
        "    val primaryLabel =\n"
        "        replySource.replyAction\n",
        "SABLE_REPLY_SOURCE",
    )
    replace_once(
        panels,
        "    if (source.canReply) {\n"
        "        PanelAction(primaryLabel, iconName = \"message-dots\") {\n"
        "            primary = !primary\n"
        "            selected = null\n"
        "        }\n"
        "    }\n",
        "    val replyChoices = conversation.replyTargets\n"
        "    if (replyChoices.size > 1) {\n"
        "        replyChoices.forEach { choice ->\n"
        "            val label =\n"
        "                choice.replyAction\n"
        "                    ?.title\n"
        "                    ?.toString()\n"
        "                    ?.takeIf(String::isNotBlank) ?: \"Reply\"\n"
        "            PanelAction(\n"
        "                (if (choice === replySource) \"\\u2713 \" else \"\") + label + \" \\u00b7 \" + choice.appName,\n"
        "                iconName = \"message-dots\",\n"
        "            ) {\n"
        "                io.github.aimindseye.tenseven.conversations.SableReplyChoice\n"
        "                    .choose(conversation.id, choice.packageName)\n"
        "                choiceTick++\n"
        "                primary = true\n"
        "                selected = null\n"
        "            }\n"
        "        }\n"
        "    } else if (replySource.canReply) {\n"
        "        PanelAction(primaryLabel, iconName = \"message-dots\") {\n"
        "            primary = !primary\n"
        "            selected = null\n"
        "        }\n"
        "    }\n",
        "SABLE_REPLY_CHOICES",
    )
    replace_once(
        panels,
        "        androidx.compose.runtime.key(source.key, primaryLabel) {\n",
        "        androidx.compose.runtime.key(replySource.key, primaryLabel) {\n",
        "SABLE_REPLY_KEY",
    )


def patch_bb7_highlight(root: pathlib.Path) -> None:
    """BlackBerry 7 marks the focused item with a filled highlight, BlackBerry 10 with an outline
    (conformance matrix, owner decision 2026-11-18). The shared focus modifier reads the profile
    (Settings.Secure sable_profile) and fills the focused item with the accent in the BlackBerry 7
    profile; every other profile keeps the 2 dp outline."""
    replace_once(
        root / DESIGN / "SableResponsiveComponents.kt",
        "        var focused by remember { mutableStateOf(false) }\n"
        "        val color = MaterialTheme.colorScheme.primary\n"
        "        this\n"
        "            .onFocusChanged { focused = it.hasFocus }\n"
        "            .then(\n"
        "                if (focused) {\n"
        "                    Modifier.border(BorderStroke(2.dp, color), MaterialTheme.shapes.small)\n"
        "                } else {\n",
        "        var focused by remember { mutableStateOf(false) }\n"
        "        val color = MaterialTheme.colorScheme.primary\n"
        "        val profileContext = androidx.compose.ui.platform.LocalContext.current\n"
        "        val filled =\n"
        "            remember(profileContext) {\n"
        "                runCatching {\n"
        "                    android.provider.Settings.Secure.getString(profileContext.contentResolver, \"sable_profile\")\n"
        "                }.getOrNull() == \"bb7\"\n"
        "            }\n"
        "        this\n"
        "            .onFocusChanged { focused = it.hasFocus }\n"
        "            .then(\n"
        "                if (focused && filled) {\n"
        "                    Modifier.background(color.copy(alpha = 0.4f), MaterialTheme.shapes.small)\n"
        "                } else if (focused) {\n"
        "                    Modifier.border(BorderStroke(2.dp, color), MaterialTheme.shapes.small)\n"
        "                } else {\n",
        "SABLE_BB7_FILLED_HIGHLIGHT",
    )


def patch_hub_inline_reply(root: pathlib.Path) -> None:
    """The Hub's reply field is inline: opening a message's menu shows the reply field at once, with
    the keyboard focus in it, and the keyboard's Send key sends (conformance matrix, owner decision
    2026-11-18). A conversation with several reply apps still asks which one first."""
    panels = root / JAVA / "ui/HubPanels.kt"
    replace_once(
        panels,
        "    var primary by remember(source) { mutableStateOf(false) }\n",
        "    var primary by remember(source) {\n"
        "        mutableStateOf(conversation.replyTargets.size <= 1 && (conversation.replyTarget ?: source).canReply)\n"
        "    }\n",
        "SABLE_INLINE_REPLY_OPEN",
    )
    replace_once(
        panels,
        "    var draft by remember { mutableStateOf(\"\") }\n"
        "    var failed by remember { mutableStateOf(false) }\n"
        "    OutlinedTextField(\n",
        "    var draft by remember { mutableStateOf(\"\") }\n"
        "    var failed by remember { mutableStateOf(false) }\n"
        "    val focus = remember { androidx.compose.ui.focus.FocusRequester() }\n"
        "    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }\n"
        "    val send = {\n"
        "        if (draft.isNotBlank()) {\n"
        "            failed = !onSend(draft)\n"
        "            if (!failed) draft = \"\"\n"
        "        }\n"
        "    }\n"
        "    OutlinedTextField(\n",
        "SABLE_INLINE_REPLY_FOCUS",
    )
    replace_once(
        panels,
        "        modifier = Modifier.fillMaxWidth(),\n"
        "        isError = failed,\n"
        "    )\n"
        "    PanelAction(stringResource(R.string.hub_send)) {\n"
        "        if (draft.isNotBlank()) {\n"
        "            failed = !onSend(draft)\n"
        "            if (!failed) draft = \"\"\n"
        "        }\n"
        "    }\n",
        "        modifier = Modifier.fillMaxWidth().focusRequester(focus),\n"
        "        isError = failed,\n"
        "        singleLine = true,\n"
        "        keyboardOptions =\n"
        "            androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),\n"
        "        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { send() }),\n"
        "    )\n"
        "    PanelAction(stringResource(R.string.hub_send)) { send() }\n",
        "SABLE_INLINE_REPLY_SEND",
    )


def patch_visible_names(root: pathlib.Path) -> None:
    """Sable Launcher is the only name the user sees. The upstream project name stays in package ids,
    class names, file formats and comments (identifiers, not text on screen), so only the text of string
    resources and Kotlin string literals changes."""
    app = root / "app/src/main"
    for path in sorted((app / "res").glob("values*/*.xml")):
        text = path.read_text(encoding="utf-8")
        new = re.sub(
            r">([^<]*)<",
            lambda m: ">" + VISIBLE_UPSTREAM_NAME.sub("Sable Launcher", m.group(1)) + "<",
            text,
        )
        if new != text:
            path.write_text(new, encoding="utf-8")
    changed = 0
    for path in sorted((app / "java").rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        if '"""' in text:
            continue
        new = KOTLIN_STRING.sub(lambda m: VISIBLE_UPSTREAM_NAME.sub("Sable Launcher", m.group(0)), text)
        if new != text:
            path.write_text(new, encoding="utf-8")
            changed += 1
    if not changed:
        fail("SABLE_HOME_VISIBLE_NAMES=FAIL nothing changed")


def main() -> None:
    if len(sys.argv) != 2:
        fail("usage: apply_sable_flavor.py <tenseven-checkout>")
    root = pathlib.Path(sys.argv[1]).resolve()
    checked_upstream_commit(root)
    patch_build(root)
    patch_manifest(root)
    patch_command(root)
    patch_colours(root)
    patch_frames(root)
    patch_fit(root)
    patch_system_menu(root)
    patch_leftovers(root)
    patch_hub_mail(root)
    patch_back_to_frames(root)
    patch_hub_peek(root)
    patch_frame_covers(root)
    patch_hub_keep(root)
    patch_no_status_dials(root)
    patch_extra_dim(root)
    patch_home_access(root)
    patch_q25_fit(root)
    patch_quiet_lock(root)
    patch_recent_tab(root)
    patch_profiles_shortcut(root)
    patch_profile_setting(root)
    patch_hub_default_action(root)
    patch_hub_reply_selector(root)
    patch_bb7_highlight(root)
    patch_hub_inline_reply(root)
    patch_visible_names(root)
    print("SABLE_HOME_FLAVOR=PASS")


if __name__ == "__main__":
    main()
