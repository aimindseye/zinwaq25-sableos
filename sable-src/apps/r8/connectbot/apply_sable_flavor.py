#!/usr/bin/env python3
"""Compose Sable Terminal from the pinned ConnectBot release (v1.10.9, Apache 2.0).

The upstream checkout is ephemeral build input. Sable keeps ConnectBot's SSH,
Telnet and local shell, its on-screen Ctrl/Esc/Tab/arrow row and its translations,
and changes only what the Zinwa Q25 needs: the Sable name, package and icon, a
fixed version (the cache has no git tags), arm64 only, no backup, no translation
tooling, and keys for a keyboard with no Ctrl or Esc (Menu is a sticky Ctrl,
Back with Ctrl on sends Esc). v1.10.9 is used because v1.11 downloads mosh
binaries at run time.
"""

from __future__ import annotations

import pathlib
import shutil
import subprocess
import sys

EXPECTED_COMMIT = "21ce6f47cf16785bf2314b4ea931900164c179c1"
VERSION_CODE = 11009000  # upstream's own formula for v1.10.9
VERSION_NAME = "1.10.9-sable"
HERE = pathlib.Path(__file__).resolve().parent


def fail(message: str) -> None:
    raise SystemExit(message)


def replace_once(path: pathlib.Path, old: str, new: str, marker: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        fail(f"{marker}=FAIL path={path} count={count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def checked_upstream_commit(root: pathlib.Path) -> str:
    if not (root / ".git").exists():
        fail(f"SABLE_TERMINAL_UPSTREAM=FAIL_NOT_GIT path={root}")
    actual = subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip()
    if actual != EXPECTED_COMMIT:
        fail(f"SABLE_TERMINAL_UPSTREAM_COMMIT=FAIL expected={EXPECTED_COMMIT} actual={actual}")
    return actual


def patch_settings(root: pathlib.Path) -> None:
    # The :translations module drives a Python/uv translation tool; the shipped strings are already in res/.
    replace_once(root / "settings.gradle.kts", 'include(":translations")\n', "", "SABLE_TERMINAL_NO_TRANSLATIONS_TOOL")


def patch_build(build: pathlib.Path) -> None:
    replace_once(build, "import io.github.reactivecircus.appversioning.toSemVer\n", "", "SABLE_TERMINAL_VERSIONING_IMPORT")
    replace_once(build, "    alias(libs.plugins.app.versioning)\n", "", "SABLE_TERMINAL_VERSIONING_PLUGIN")
    text = build.read_text(encoding="utf-8")
    start = text.find("appVersioning {")
    end = text.find("\nandroid {")
    if start < 0 or end < start:
        fail("SABLE_TERMINAL_VERSIONING_BLOCK=FAIL")
    build.write_text(text[:start] + text[end + 1:], encoding="utf-8")
    replace_once(
        build,
        '        applicationId = "org.connectbot"\n',
        '        applicationId = "org.sableos.terminal"\n'
        f"        versionCode = {VERSION_CODE}\n"
        f'        versionName = "{VERSION_NAME}"\n',
        "SABLE_TERMINAL_APPLICATION_ID",
    )
    replace_once(
        build,
        'abiFilters.addAll(listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a"))',
        'abiFilters.addAll(listOf("arm64-v8a"))',
        "SABLE_TERMINAL_ABI",
    )
    # Sable's key handler and its test, kept beside upstream's sources.
    replace_once(
        build,
        '        getByName("main") {\n',
        '        getByName("main") {\n            kotlin.directories.add("src/sable/kotlin")\n',
        "SABLE_TERMINAL_SOURCES",
    )
    replace_once(
        build,
        '        getByName("test") {\n',
        '        getByName("test") {\n            kotlin.directories.add("src/sableTest/kotlin")\n',
        "SABLE_TERMINAL_TEST_SOURCES",
    )


def patch_manifest(manifest: pathlib.Path) -> None:
    replace_once(manifest, 'android:allowBackup="true"', 'android:allowBackup="false"', "SABLE_TERMINAL_BACKUP")
    replace_once(manifest, 'android:icon="@mipmap/icon"', 'android:icon="@mipmap/ic_sable_terminal"', "SABLE_TERMINAL_ICON")
    replace_once(
        manifest,
        'android:roundIcon="@mipmap/icon_round"',
        'android:roundIcon="@mipmap/ic_sable_terminal_round"',
        "SABLE_TERMINAL_ROUND_ICON",
    )


def patch_console(console: pathlib.Path) -> None:
    replace_once(
        console,
        "        val handleShortcut: (KeyEvent) -> Boolean = { keyEvent ->\n            handleConsoleShortcut(\n",
        "        val handleShortcut: (KeyEvent) -> Boolean = { keyEvent ->\n"
        "            org.connectbot.sable.SableTerminalKeys.handle(keyEvent.nativeKeyEvent, currentBridge?.keyHandler) ||\n"
        "                handleConsoleShortcut(\n",
        "SABLE_TERMINAL_KEYS_HOOK",
    )


def copy_tree(source: pathlib.Path, destination: pathlib.Path) -> int:
    files = sorted(p for p in source.rglob("*") if p.is_file())
    for path in files:
        target = destination / path.relative_to(source)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, target)
    return len(files)


def main() -> None:
    if len(sys.argv) != 2:
        fail("usage: apply_sable_flavor.py <connectbot-checkout>")
    root = pathlib.Path(sys.argv[1]).resolve()
    actual = checked_upstream_commit(root)
    app = root / "app"
    strings = app / "src/main/res/values/notrans.xml"
    console = app / "src/main/java/org/connectbot/ui/screens/console/ConsoleScreen.kt"
    for path in (app / "build.gradle.kts", app / "src/main/AndroidManifest.xml", strings, console):
        if not path.is_file():
            fail(f"SABLE_TERMINAL_INPUT=FAIL_MISSING path={path}")

    patch_settings(root)
    patch_build(app / "build.gradle.kts")
    patch_manifest(app / "src/main/AndroidManifest.xml")
    replace_once(
        strings,
        '<string name="app_name" translatable="false">ConnectBot</string>',
        '<string name="app_name" translatable="false">Sable Terminal</string>',
        "SABLE_TERMINAL_NAME",
    )
    patch_console(console)
    # Launcher icon generated by sable-src/tools/gen_first_party_icons.py (row terminal).
    icon_source = pathlib.Path(__file__).resolve().parent / "res"
    if not (icon_source / "mipmap-anydpi-v26/ic_sable_terminal.xml").is_file():
        fail("SABLE_TERMINAL_ICON=FAIL_MISSING_ADAPTIVE")
    icons = copy_tree(icon_source, app / "src/main/res")
    sources = copy_tree(HERE / "src/main/kotlin", app / "src/sable/kotlin")
    tests = copy_tree(HERE / "src/test/kotlin", app / "src/sableTest/kotlin")
    print(f"SABLE_TERMINAL_FLAVOR=PASS upstream={actual} icons={icons} sources={sources} tests={tests}")


if __name__ == "__main__":
    main()
