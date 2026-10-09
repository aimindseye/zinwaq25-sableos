#!/usr/bin/env python3
from __future__ import annotations

import os
import pathlib
import subprocess
import sys
import xml.etree.ElementTree as ET

EXPECTED_COMMIT = "50fca365baae9869264716569830690fb62029a7"
EXPECTED_PACKAGE = "org.sableos.textreader"


def fail(marker: str, detail: str = "") -> None:
    suffix = f" {detail}" if detail else ""
    raise SystemExit(f"{marker}=FAIL{suffix}")


def require(path: pathlib.Path, needle: str, marker: str) -> None:
    text = path.read_text(encoding="utf-8")
    if needle not in text:
        fail(marker, f"path={path} missing={needle!r}")


def forbid(path: pathlib.Path, needle: str, marker: str) -> None:
    text = path.read_text(encoding="utf-8")
    if needle in text:
        fail(marker, f"path={path} forbidden={needle!r}")


def main() -> None:
    if len(sys.argv) not in (2, 3):
        fail(
            "SABLE_TEXT_READER_VERIFY_USAGE",
            "verify_sable_textreader.py <root> [apk]",
        )

    root = pathlib.Path(sys.argv[1]).resolve()
    actual = subprocess.check_output(
        ["git", "-C", str(root), "rev-parse", "HEAD"],
        text=True,
    ).strip()
    if actual != EXPECTED_COMMIT:
        fail(
            "SABLE_TEXT_READER_UPSTREAM_COMMIT",
            f"expected={EXPECTED_COMMIT} actual={actual}",
        )
    print(f"SABLE_TEXT_READER_UPSTREAM_COMMIT=PASS value={actual}")

    build = root / "app/build.gradle.kts"
    manifest_path = root / "app/src/main/AndroidManifest.xml"
    strings = root / "app/src/main/res/values/strings.xml"
    main_source = root / "app/src/main/java/org/vaachak/textreader/MainActivity.kt"
    shared = root / "app/src/main/java/org/vaachak/textreader/SharedComponents.kt"
    text_util = root / "app/src/main/java/org/vaachak/textreader/TextReaderUtil.kt"
    tts_settings = root / "app/src/main/java/org/vaachak/textreader/TtsSettingsView.kt"
    design = root / "app/src/main/java/org/sableos/design/SableGlobalAppearance.kt"

    require(build, 'applicationId = "org.sableos.textreader"', "SABLE_TEXT_READER_APPLICATION_ID")
    require(build, "minSdk = 30", "SABLE_TEXT_READER_MIN_SDK")
    require(build, 'SableTextReader-${variantName}.apk', "SABLE_TEXT_READER_APK_NAME")
    forbid(build, "com.google.mlkit:translate", "SABLE_TEXT_READER_TRANSLATION_DEPENDENCY")
    require(strings, "Sable Text Reader", "SABLE_TEXT_READER_BRANDING")
    require(main_source, "SableGlobalTheme(window = window)", "SABLE_TEXT_READER_GLOBAL_APPEARANCE")
    require(main_source, "ActivityResultContracts.OpenDocument", "SABLE_TEXT_READER_OPEN_DOCUMENT")
    require(main_source, "TextToSpeech", "SABLE_TEXT_READER_TTS")
    require(main_source, 'CreateDocument("audio/wav")', "SABLE_TEXT_READER_AUDIO_EXPORT")
    require(main_source, "TextRecognition.getClient", "SABLE_TEXT_READER_GALLERY_OCR")
    require(main_source, "Intent.ACTION_VIEW", "SABLE_TEXT_READER_VIEW_HANDLER")
    require(
        main_source,
        "TextReaderUtil.readTextFromStream",
        "SABLE_TEXT_READER_BOUNDED_VIEW_READER",
    )
    require(
        main_source,
        "selectLocalVoice(tts, locale)",
        "SABLE_TEXT_READER_LOCAL_TTS_PLAYBACK",
    )
    require(
        main_source,
        "selectLocalVoice(ttsEngine, currentTtsLocale)",
        "SABLE_TEXT_READER_LOCAL_TTS_EXPORT",
    )
    forbid(main_source, "ProfessionalTranslationRow(", "SABLE_TEXT_READER_TRANSLATION_UI")
    forbid(shared, "downloadModelIfNeeded", "SABLE_TEXT_READER_MODEL_FETCH")
    forbid(shared, "TranslateLanguage", "SABLE_TEXT_READER_TRANSLATION_SOURCE")
    require(text_util, "MAX_TEXT_BYTES = 2 * 1024 * 1024L", "SABLE_TEXT_READER_TEXT_BOUND")
    require(text_util, "BoundedInputStream", "SABLE_TEXT_READER_BOUNDED_STREAM")
    require(
        tts_settings,
        "filterNot { it.isNetworkConnectionRequired }",
        "SABLE_TEXT_READER_LOCAL_TTS_FILTER",
    )
    require(tts_settings, "fun selectLocalVoice(", "SABLE_TEXT_READER_LOCAL_TTS_SELECTOR")
    require(design, 'AUTHORITY = "org.sableos.appearance"', "SABLE_TEXT_READER_APPEARANCE_AUTHORITY")

    manifest_text = manifest_path.read_text(encoding="utf-8")
    for token in (
        "android.permission.CAMERA",
        'android.hardware.camera" android:required="false"',
        "android.intent.action.MAIN",
        "android.intent.category.LAUNCHER",
        "android.intent.action.VIEW",
        "android.intent.action.SEND",
        "android.intent.action.PROCESS_TEXT",
        'android:mimeType="text/plain"',
        'android:authorities="org.sableos.appearance"',
    ):
        if token not in manifest_text:
            fail("SABLE_TEXT_READER_MANIFEST", f"missing={token!r}")

    root_xml = ET.fromstring(manifest_text)
    android = "{http://schemas.android.com/apk/res/android}"
    tools = "{http://schemas.android.com/tools}"

    internet_rules = [
        node
        for node in root_xml.findall("uses-permission")
        if node.attrib.get(android + "name") == "android.permission.INTERNET"
    ]
    if len(internet_rules) != 1:
        fail(
            "SABLE_TEXT_READER_INTERNET_MERGE_RULE",
            f"count={len(internet_rules)}",
        )
    if internet_rules[0].attrib.get(tools + "node") != "remove":
        fail("SABLE_TEXT_READER_INTERNET_MERGE_RULE", "tools:node!=remove")

    app = root_xml.find("application")
    if app is None:
        fail("SABLE_TEXT_READER_MANIFEST_APPLICATION")
    if app.attrib.get(android + "allowBackup") != "false":
        fail("SABLE_TEXT_READER_BACKUP_POLICY")

    launcher_count = 0
    for tag in ("activity", "activity-alias"):
        for component in app.findall(tag):
            for intent_filter in component.findall("intent-filter"):
                actions = {
                    action.attrib.get(android + "name")
                    for action in intent_filter.findall("action")
                }
                categories = {
                    category.attrib.get(android + "name")
                    for category in intent_filter.findall("category")
                }
                if (
                    "android.intent.action.MAIN" in actions
                    and "android.intent.category.LAUNCHER" in categories
                ):
                    launcher_count += 1
    if launcher_count != 1:
        fail("SABLE_TEXT_READER_LAUNCHER_COUNT", f"count={launcher_count}")

    print("SABLE_TEXT_READER_MIN_SDK=PASS_30")
    print("SABLE_TEXT_READER_INTERNET_MERGE_RULE=PASS_REMOVE")
    print("SABLE_TEXT_READER_INTERNET_PERMISSION=PASS_FINAL_APK_ABSENT_REQUIRED")
    print("SABLE_TEXT_READER_TRANSLATION=PASS_RETIRED")
    print("SABLE_TEXT_READER_CAMERA_PERMISSION=PASS_OPTIONAL_RUNTIME_ONLY")
    print("SABLE_TEXT_READER_VIEW=PASS")
    print("SABLE_TEXT_READER_SEND=PASS")
    print("SABLE_TEXT_READER_PROCESS_TEXT=PASS")
    print("SABLE_TEXT_READER_SINGLE_LAUNCHER=PASS")
    print("SABLE_TEXT_READER_GLOBAL_APPEARANCE=PASS")
    print("SABLE_TEXT_READER_TEXT_INPUT=PASS_BOUNDED_2_MIB")
    print("SABLE_TEXT_READER_TTS_NETWORK_VOICES=PASS_FILTERED")

    if len(sys.argv) == 3:
        apk = pathlib.Path(sys.argv[2]).resolve()
        if not apk.is_file():
            fail("SABLE_TEXT_READER_APK", f"missing={apk}")
        apkanalyzer = os.environ.get("APKANALYZER")
        if not apkanalyzer:
            fail("SABLE_TEXT_READER_APKANALYZER", "APKANALYZER not set")
        package = subprocess.check_output(
            [apkanalyzer, "manifest", "application-id", str(apk)],
            text=True,
        ).strip()
        if package != EXPECTED_PACKAGE:
            fail(
                "SABLE_TEXT_READER_APK_PACKAGE",
                f"expected={EXPECTED_PACKAGE} actual={package}",
            )
        print(f"SABLE_TEXT_READER_APK_PACKAGE=PASS value={package}")

        apk_manifest = subprocess.check_output(
            [apkanalyzer, "manifest", "print", str(apk)],
            text=True,
        )
        if "android.permission.INTERNET" in apk_manifest:
            merge_report = (
                root
                / "app/build/outputs/logs/manifest-merger-debug-report.txt"
            )
            if merge_report.is_file():
                print("SABLE_TEXT_READER_MANIFEST_MERGER_INTERNET_EVIDENCE=BEGIN")
                for line in merge_report.read_text(
                    encoding="utf-8",
                    errors="replace",
                ).splitlines():
                    if "INTERNET" in line or "uses-permission" in line:
                        print(line)
                print("SABLE_TEXT_READER_MANIFEST_MERGER_INTERNET_EVIDENCE=END")
            fail("SABLE_TEXT_READER_APK_INTERNET_PERMISSION")
        if "android.permission.CAMERA" not in apk_manifest:
            fail("SABLE_TEXT_READER_APK_CAMERA_PERMISSION")
        if "android.intent.action.PROCESS_TEXT" not in apk_manifest:
            fail("SABLE_TEXT_READER_APK_PROCESS_TEXT")
        if "android.intent.action.SEND" not in apk_manifest:
            fail("SABLE_TEXT_READER_APK_SEND")
        print("SABLE_TEXT_READER_APK_INTERNET_PERMISSION=PASS_ABSENT")
        print("SABLE_TEXT_READER_APK_CAMERA_PERMISSION=PASS_PRESENT")
        print("SABLE_TEXT_READER_APK_SEND_PROCESS_TEXT=PASS")
        print("SABLE_TEXT_READER_APK_OFFLINE_BOUNDARY=PASS")
        print(
            "SABLE_TEXT_READER_APK_SHA256="
            + subprocess.check_output(
                ["sha256sum", str(apk)],
                text=True,
            ).split()[0]
        )

    print("SABLE_TEXT_READER_POLICY=PASS")


if __name__ == "__main__":
    main()
