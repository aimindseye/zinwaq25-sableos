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
"""

from __future__ import annotations

import pathlib
import shutil
import subprocess
import sys

EXPECTED_COMMIT = "fbdd2171d25eaf7f120e3ab829977ca328c83014"
PACKAGE = "org.sableos.launcher"
VERSION_NAME = "2.0-sable"
HERE = pathlib.Path(__file__).resolve().parent
JAVA = "app/src/main/java/io/github/aimindseye/tenseven"
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
    replace_once(
        root / JAVA / "ui/FrameSettingsSection.kt",
        "    ScreenCaptureControls()\n",
        "    if (capabilities.thirdPartySnapshots != CapabilityStatus.Ready) ScreenCaptureControls()\n",
        "SABLE_HOME_NO_SCREEN_CAPTURE",
    )


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
    print("SABLE_HOME_FLAVOR=PASS")


if __name__ == "__main__":
    main()
