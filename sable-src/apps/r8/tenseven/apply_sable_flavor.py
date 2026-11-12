#!/usr/bin/env python3
"""Compose the SableOS home (Sable Start 2.0) from the pinned TenSeven Launcher (Apache 2.0).

Owner decision 2026-11-12: TenSeven Launcher is SableOS 2.0's home and Hub. It was cut from
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
        '                    (taskMethod(context, "getTaskSnapshot") || SableTaskSnapshots.available()),\n',
        "SABLE_HOME_SNAPSHOT_PROBE",
    )
    replace_once(
        root / JAVA / "platform/RecentTasksRepository.kt",
        "            if (PlatformCapabilityProbe.detect(appContext).thirdPartySnapshots != CapabilityStatus.Ready) return@runCatching null\n",
        "            if (PlatformCapabilityProbe.detect(appContext).thirdPartySnapshots != CapabilityStatus.Ready) return@runCatching null\n"
        "            SableTaskSnapshots.snapshot(taskId, lowResolution)?.let { return@runCatching it }\n",
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
    print("SABLE_HOME_FLAVOR=PASS")


if __name__ == "__main__":
    main()
