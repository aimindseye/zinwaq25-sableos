"""Shared by the Sable flavors of upstream apps (sable-src/apps/r8/*/apply_sable_flavor.py)."""

from __future__ import annotations

import pathlib
import shutil
import subprocess

# SableOS 2.0 colours (black, white and the one blue #2E9BD6; raised #141414, dividers #262626),
# as Material 3 roles, for the upstream flavors that draw with Material 3 (DAVx5, ICSx5, Terminal).
SABLE_DARK = {
    "primary": "2E9BD6",
    "onPrimary": "000000",
    "primaryContainer": "262626",
    "onPrimaryContainer": "FFFFFF",
    "secondary": "2E9BD6",
    "onSecondary": "000000",
    "secondaryContainer": "262626",
    "onSecondaryContainer": "FFFFFF",
    "tertiary": "2E9BD6",
    "onTertiary": "000000",
    "tertiaryContainer": "262626",
    "onTertiaryContainer": "FFFFFF",
    "error": "E0463C",
    "onError": "000000",
    "errorContainer": "3A1210",
    "onErrorContainer": "FFFFFF",
    "background": "000000",
    "onBackground": "FFFFFF",
    "surface": "000000",
    "onSurface": "FFFFFF",
    "surfaceVariant": "262626",
    "onSurfaceVariant": "A6A6A6",
    "outline": "595959",
    "outlineVariant": "262626",
    "scrim": "000000",
    "inverseSurface": "FFFFFF",
    "inverseOnSurface": "000000",
    "inversePrimary": "1A6FA0",
    "surfaceDim": "000000",
    "surfaceBright": "262626",
    "surfaceContainerLowest": "000000",
    "surfaceContainerLow": "0A0A0A",
    "surfaceContainer": "141414",
    "surfaceContainerHigh": "1A1A1A",
    "surfaceContainerHighest": "262626",
}
SABLE_LIGHT = {
    "primary": "1A6FA0",
    "onPrimary": "FFFFFF",
    "primaryContainer": "E9EDF2",
    "onPrimaryContainer": "12171C",
    "secondary": "1A6FA0",
    "onSecondary": "FFFFFF",
    "secondaryContainer": "E9EDF2",
    "onSecondaryContainer": "12171C",
    "tertiary": "1A6FA0",
    "onTertiary": "FFFFFF",
    "tertiaryContainer": "E9EDF2",
    "onTertiaryContainer": "12171C",
    "error": "BA1A1A",
    "onError": "FFFFFF",
    "errorContainer": "FFDAD6",
    "onErrorContainer": "410002",
    "background": "F5F7F9",
    "onBackground": "12171C",
    "surface": "FFFFFF",
    "onSurface": "12171C",
    "surfaceVariant": "E9EDF2",
    "onSurfaceVariant": "515B66",
    "outline": "8A939C",
    "outlineVariant": "D5DBE1",
    "scrim": "000000",
    "inverseSurface": "12171C",
    "inverseOnSurface": "FFFFFF",
    "inversePrimary": "2E9BD6",
    "surfaceDim": "E9EDF2",
    "surfaceBright": "FFFFFF",
    "surfaceContainerLowest": "FFFFFF",
    "surfaceContainerLow": "F5F7F9",
    "surfaceContainer": "F0F3F6",
    "surfaceContainerHigh": "E9EDF2",
    "surfaceContainerHighest": "E2E7EC",
}


def sable_scheme(function: str, roles: dict[str, str], indent: str) -> str:
    lines = [f"{indent}    {role} = Color(0xFF{value})," for role, value in roles.items()]
    return f"{function}(\n" + "\n".join(lines) + f"\n{indent})"


def copy_tree(source: pathlib.Path, target: pathlib.Path) -> int:
    """Copies every file under [source] into [target], keeping relative paths; returns the count."""
    files = sorted(path for path in source.rglob("*") if path.is_file())
    for path in files:
        destination = target / path.relative_to(source)
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, destination)
    return len(files)


def check_commit(root: pathlib.Path, expected: str, marker: str) -> None:
    """Stops unless the checkout is at the pinned commit."""
    actual = subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip()
    if actual != expected:
        raise SystemExit(f"{marker}_UPSTREAM_COMMIT=FAIL expected={expected} actual={actual}")


def set_sable_icon(source: pathlib.Path, module: pathlib.Path, drawable: str, marker: str) -> int:
    """Copies the generated Sable icon (sable-src/tools/gen_first_party_icons.py) into [module] and makes it
    the application's icon and round icon; the app's name stays upstream's. Returns the files copied."""
    files = copy_tree(source, module / "src/main/res")
    manifest = module / "src/main/AndroidManifest.xml"
    text = manifest.read_text(encoding="utf-8")
    old = 'android:icon="@mipmap/ic_launcher"'
    if text.count(old) != 1 or "android:roundIcon" in text:
        raise SystemExit(f"{marker}_ICON=FAIL")
    new = f'android:icon="@mipmap/{drawable}"\n        android:roundIcon="@mipmap/{drawable}_round"'
    manifest.write_text(text.replace(old, new, 1), encoding="utf-8")
    return files
