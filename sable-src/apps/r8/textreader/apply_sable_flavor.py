#!/usr/bin/env python3
"""Compose the standalone Sable Text Reader from the pinned Vaachak Text Reader.

The upstream checkout is ephemeral build input. Sable keeps local text reading,
Android VIEW/SEND/PROCESS_TEXT handoff, TTS/audio export and user-directed
camera/gallery OCR. Network-backed translation is deliberately removed.
"""

from __future__ import annotations

import pathlib
import shutil
import subprocess
import sys

EXPECTED_COMMIT = "50fca365baae9869264716569830690fb62029a7"


def fail(message: str) -> None:
    raise SystemExit(message)


def replace_once(path: pathlib.Path, old: str, new: str, marker: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        fail(f"{marker}=FAIL path={path} count={count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def repo_root() -> pathlib.Path:
    return pathlib.Path(__file__).resolve().parents[3]


def main() -> None:
    if len(sys.argv) != 2:
        fail("usage: apply_sable_flavor.py <vaachak-textreader-checkout>")

    root = pathlib.Path(sys.argv[1]).resolve()
    if not (root / ".git").exists():
        fail(f"SABLE_TEXT_READER_UPSTREAM=FAIL_NOT_GIT path={root}")

    actual = subprocess.check_output(
        ["git", "-C", str(root), "rev-parse", "HEAD"],
        text=True,
    ).strip()
    if actual != EXPECTED_COMMIT:
        fail(
            "SABLE_TEXT_READER_UPSTREAM_COMMIT=FAIL "
            f"expected={EXPECTED_COMMIT} actual={actual}"
        )

    build = root / "app/build.gradle.kts"
    manifest = root / "app/src/main/AndroidManifest.xml"
    strings = root / "app/src/main/res/values/strings.xml"
    main = root / "app/src/main/java/org/vaachak/textreader/MainActivity.kt"
    shared = root / "app/src/main/java/org/vaachak/textreader/SharedComponents.kt"

    for path in (build, manifest, strings, main, shared):
        if not path.is_file():
            fail(f"SABLE_TEXT_READER_INPUT=FAIL_MISSING path={path}")

    replace_once(
        build,
        'applicationId = "org.vaachak.textreader"',
        'applicationId = "org.sableos.textreader"',
        "SABLE_TEXT_READER_APPLICATION_ID",
    )
    replace_once(
        build,
        "        minSdk = 24",
        "        minSdk = 30",
        "SABLE_TEXT_READER_MIN_SDK",
    )
    replace_once(
        build,
        'outputImpl?.outputFileName = "Vaachak-TextReader-${variantName}.apk"',
        'outputImpl?.outputFileName = "SableTextReader-${variantName}.apk"',
        "SABLE_TEXT_READER_APK_NAME",
    )
    replace_once(
        build,
        '    implementation("com.google.mlkit:translate:17.0.3")\n',
        "",
        "SABLE_TEXT_READER_TRANSLATION_DEPENDENCY",
    )

    manifest_text = manifest.read_text(encoding="utf-8")
    internet = '    <uses-permission android:name="android.permission.INTERNET" />\n'
    if manifest_text.count(internet) != 1:
        fail("SABLE_TEXT_READER_INTERNET_ANCHOR=FAIL")
    manifest_text = manifest_text.replace(
        internet,
        '    <uses-permission\n'
        '        android:name="android.permission.INTERNET"\n'
        '        tools:node="remove" />\n',
        1,
    )
    manifest_text = manifest_text.replace(
        'android:allowBackup="true"',
        'android:allowBackup="false"',
        1,
    )
    manifest_text = manifest_text.replace(
        'android:icon="@mipmap/ic_launcher"',
        'android:icon="@mipmap/ic_sable_text_reader"',
        1,
    )
    manifest_text = manifest_text.replace(
        'android:roundIcon="@mipmap/ic_launcher_round"',
        'android:roundIcon="@mipmap/ic_sable_text_reader_round"',
        1,
    )
    for token in (
        'android:icon="@mipmap/ic_sable_text_reader"',
        'android:roundIcon="@mipmap/ic_sable_text_reader_round"',
    ):
        if token not in manifest_text:
            fail(f"SABLE_TEXT_READER_ICON_WIRING=FAIL missing={token}")

    app_anchor = "    <application\n"
    if app_anchor not in manifest_text:
        fail("SABLE_TEXT_READER_MANIFEST_APPLICATION_ANCHOR=FAIL")
    manifest_text = manifest_text.replace(
        app_anchor,
        '    <queries>\n'
        '        <provider android:authorities="org.sableos.appearance" />\n'
        '    </queries>\n\n'
        + app_anchor,
        1,
    )

    launcher_filter = """            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
"""
    if manifest_text.count(launcher_filter) != 1:
        fail("SABLE_TEXT_READER_LAUNCHER_FILTER_ANCHOR=FAIL")
    view_filter = launcher_filter + """
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
                <data android:scheme="content" />
                <data android:scheme="file" />
            </intent-filter>
"""
    manifest_text = manifest_text.replace(launcher_filter, view_filter, 1)
    manifest_text = manifest_text.replace(
        'android:label="Read in Vaachak"',
        'android:label="Read in Sable Text Reader"',
        1,
    )
    manifest.write_text(manifest_text, encoding="utf-8")

    replace_once(
        strings,
        '<string name="app_name">Vaachak Text Reader</string>',
        '<string name="app_name">Sable Text Reader</string>',
        "SABLE_TEXT_READER_BRANDING",
    )

    main_text = main.read_text(encoding="utf-8")
    if "import androidx.compose.foundation.isSystemInDarkTheme\n" not in main_text:
        fail("SABLE_TEXT_READER_THEME_IMPORT_ANCHOR=FAIL")
    main_text = main_text.replace(
        "import androidx.compose.foundation.isSystemInDarkTheme\n",
        "import org.sableos.design.SableGlobalTheme\n",
        1,
    )
    theme_old = """        setContent {
            val isDark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
"""
    theme_new = """        setContent {
            SableGlobalTheme(window = window) {
"""
    if main_text.count(theme_old) != 1:
        fail("SABLE_TEXT_READER_THEME_ANCHOR=FAIL")
    main_text = main_text.replace(theme_old, theme_new, 1)

    translation_ui = """                Spacer(modifier = Modifier.height(24.dp))

                ProfessionalTranslationRow(currentText) { translated, tag ->
                    setCurrentText(translated)
                    setCurrentTtsLocale(Locale.forLanguageTag(tag))
                }

                Spacer(modifier = Modifier.height(24.dp))
"""
    if main_text.count(translation_ui) != 1:
        fail("SABLE_TEXT_READER_TRANSLATION_UI_ANCHOR=FAIL")
    main_text = main_text.replace(
        translation_ui,
        '                Spacer(modifier = Modifier.height(24.dp))\n',
        1,
    )
    main_text = main_text.replace(
        'Text("V A A C H A K",',
        'Text("S A B L E   T E X T   R E A D E R",',
        1,
    )
    intent_old = """        } else if (intent?.action == Intent.ACTION_PROCESS_TEXT) {
            sharedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: ""
        }
"""
    intent_new = """        } else if (intent?.action == Intent.ACTION_PROCESS_TEXT) {
            sharedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: ""
        } else if (intent?.action == Intent.ACTION_VIEW) {
            sharedText = intent.data?.let { uri ->
                runCatching {
                    contentResolver.openInputStream(uri)?.use {
                        TextReaderUtil.readTextFromStream(it)
                    } ?: ""
                }.getOrDefault("")
            } ?: ""
        }
"""
    if main_text.count(intent_old) != 1:
        fail("SABLE_TEXT_READER_VIEW_HANDLER_ANCHOR=FAIL")
    main_text = main_text.replace(intent_old, intent_new, 1)

    speak_old = """                        onSpeakText = { text, locale ->
                            if (isTtsReady && text.isNotBlank()) {
                                tts.language = locale
                                // Pass a unique Utterance ID so our listener can track it
                                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "play_text")
                            }
                        },
"""
    speak_new = """                        onSpeakText = { text, locale ->
                            if (isTtsReady && text.isNotBlank()) {
                                tts.language = locale
                                if (selectLocalVoice(tts, locale)) {
                                    tts.speak(
                                        text,
                                        TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        "play_text",
                                    )
                                } else {
                                    Toast.makeText(
                                        this,
                                        "No local text-to-speech voice is available.",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        },
"""
    if main_text.count(speak_old) != 1:
        fail("SABLE_TEXT_READER_LOCAL_TTS_PLAYBACK_ANCHOR=FAIL")
    main_text = main_text.replace(speak_old, speak_new, 1)

    export_old = """            ttsEngine.language = currentTtsLocale
            ttsEngine.synthesizeToFile(currentText, params, tempFile, "export_audio")
"""
    export_new = """            ttsEngine.language = currentTtsLocale
            if (selectLocalVoice(ttsEngine, currentTtsLocale)) {
                ttsEngine.synthesizeToFile(
                    currentText,
                    params,
                    tempFile,
                    "export_audio",
                )
            } else {
                Toast.makeText(
                    context,
                    "No local text-to-speech voice is available.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
"""
    if main_text.count(export_old) != 1:
        fail("SABLE_TEXT_READER_LOCAL_TTS_EXPORT_ANCHOR=FAIL")
    main_text = main_text.replace(export_old, export_new, 1)

    main.write_text(main_text, encoding="utf-8")

    shared.write_text(
        """package org.vaachak.textreader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class Lang(
    val name: String,
    val code: String,
    val ttsTag: String,
)

@Composable
fun ActionIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(icon, contentDescription = label)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
""",
        encoding="utf-8",
    )

    text_util = (
        root
        / "app/src/main/java/org/vaachak/textreader/TextReaderUtil.kt"
    )
    text_util.write_text(
        """package org.vaachak.textreader

import java.io.FilterInputStream
import java.io.InputStream

object TextReaderUtil {
    private const val MAX_TEXT_BYTES = 2 * 1024 * 1024L

    fun readTextFromStream(inputStream: InputStream?): String {
        if (inputStream == null) return "Error: File stream is null."

        return try {
            BoundedInputStream(inputStream, MAX_TEXT_BYTES)
                .bufferedReader()
                .use { it.readText() }
                .trim()
        } catch (e: Exception) {
            "Error reading file: ${e.message}"
        }
    }

    private class BoundedInputStream(
        input: InputStream,
        private var remaining: Long,
    ) : FilterInputStream(input) {
        override fun read(): Int {
            if (remaining <= 0) return -1
            val value = super.read()
            if (value >= 0) remaining -= 1
            return value
        }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            if (remaining <= 0) return -1
            val allowed = minOf(length.toLong(), remaining).toInt()
            val count = super.read(buffer, offset, allowed)
            if (count > 0) remaining -= count.toLong()
            return count
        }
    }
}
""",
        encoding="utf-8",
    )

    tts_settings = (
        root
        / "app/src/main/java/org/vaachak/textreader/TtsSettingsView.kt"
    )
    tts_text = tts_settings.read_text(encoding="utf-8")
    settings_marker = """// ---------------------------------------------------------------------------
// TTS Settings Dialog Composable
// ---------------------------------------------------------------------------
"""
    local_voice_helper = """fun selectLocalVoice(
    ttsEngine: TextToSpeech,
    locale: Locale,
): Boolean {
    val localVoices =
        ttsEngine.voices
            ?.filterNot { it.isNetworkConnectionRequired }
            .orEmpty()
    val preferred =
        localVoices.firstOrNull { it.locale == locale }
            ?: localVoices.firstOrNull { it.locale.language == locale.language }
    if (preferred == null) return false
    ttsEngine.voice = preferred
    return true
}

""" + settings_marker
    if tts_text.count(settings_marker) != 1:
        fail("SABLE_TEXT_READER_TTS_SETTINGS_MARKER=FAIL")
    tts_text = tts_text.replace(settings_marker, local_voice_helper, 1)
    voice_filter_old = """            val filtered = ttsEngine.voices?.filter { v ->
                if (v.locale.language == "en") v.locale.country in listOf("US", "GB")
                else v.locale.language == currentLocale.language
            }?.sortedBy { it.name } ?: emptyList()
"""
    voice_filter_new = """            val filtered = ttsEngine.voices?.filter { v ->
                !v.isNetworkConnectionRequired &&
                    if (v.locale.language == "en") {
                        v.locale.country in listOf("US", "GB")
                    } else {
                        v.locale.language == currentLocale.language
                    }
            }?.sortedBy { it.name } ?: emptyList()
"""
    if tts_text.count(voice_filter_old) != 1:
        fail("SABLE_TEXT_READER_TTS_FILTER_ANCHOR=FAIL")
    tts_text = tts_text.replace(voice_filter_old, voice_filter_new, 1)

    selected_old = """            setVoices(filtered)
            setSelected(ttsEngine.voice)
"""
    selected_new = """            setVoices(filtered)
            val localSelection =
                ttsEngine.voice?.takeUnless {
                    it.isNetworkConnectionRequired || it !in filtered
                } ?: filtered.firstOrNull()
            setSelected(localSelection)
            localSelection?.let { ttsEngine.voice = it }
"""
    if tts_text.count(selected_old) != 1:
        fail("SABLE_TEXT_READER_TTS_SELECTION_ANCHOR=FAIL")
    tts_text = tts_text.replace(selected_old, selected_new, 1)
    tts_settings.write_text(tts_text, encoding="utf-8")

    unit_test = (
        root
        / "app/src/test/java/org/vaachak/textreader/TextReaderUtilTest.kt"
    )
    if unit_test.is_file():
        unit_test_text = unit_test.read_text(encoding="utf-8")
        unit_test_anchor = """    // ---------------------------------------------------------------------------
    // Language & Locale Tests
    // ---------------------------------------------------------------------------
"""
        bounded_test = """    @Test
    fun `readTextFromStream bounds oversized input`() {
        val bytes = ByteArray(2 * 1024 * 1024 + 256) { 'a'.code.toByte() }
        val result = TextReaderUtil.readTextFromStream(ByteArrayInputStream(bytes))
        assertEquals(2 * 1024 * 1024, result.length)
    }

""" + unit_test_anchor
        if unit_test_text.count(unit_test_anchor) != 1:
            fail("SABLE_TEXT_READER_BOUNDED_TEST_ANCHOR=FAIL")
        unit_test.write_text(
            unit_test_text.replace(unit_test_anchor, bounded_test, 1),
            encoding="utf-8",
        )

    android_ui_test = (
        root
        / "app/src/androidTest/java/org/vaachak/textreader/MainScreenTest.kt"
    )
    if android_ui_test.is_file():
        ui_test_text = android_ui_test.read_text(encoding="utf-8")
        ui_test_text = ui_test_text.replace(
            'onNodeWithText("V A A C H A K")',
            'onNodeWithText("S A B L E   T E X T   R E A D E R")',
        )
        android_ui_test.write_text(ui_test_text, encoding="utf-8")

    design_source = (
        repo_root()
        / "src/android/shared/sabledesign/src/main/java/org/sableos/design"
    )
    design_destination = (
        root / "app/src/main/java/org/sableos/design"
    )
    design_destination.mkdir(parents=True, exist_ok=True)
    for name in (
        "SableTheme.kt",
        "SableGlobalAppearance.kt",
        "SableSystemBars.kt",
    ):
        source = design_source / name
        if not source.is_file():
            fail(f"SABLE_TEXT_READER_DESIGN_SOURCE=FAIL_MISSING path={source}")
        shutil.copy2(source, design_destination / name)

    # Launcher icon generated by sable-src/tools/gen_first_party_icons.py (row textreader): legacy vector,
    # adaptive layers and mipmap-anydpi-v26/ic_sable_text_reader{,_round}.xml with a monochrome layer.
    icon_source = pathlib.Path(__file__).resolve().parent / "res"
    icon_files = sorted(icon_source.rglob("*.xml"))
    if not (icon_source / "mipmap-anydpi-v26/ic_sable_text_reader.xml").is_file():
        fail(f"SABLE_TEXT_READER_ICON=FAIL_MISSING_ADAPTIVE path={icon_source}")
    for source in icon_files:
        destination = root / "app/src/main/res" / source.relative_to(icon_source)
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)

    print(f"SABLE_TEXT_READER_UPSTREAM_COMMIT=PASS value={actual}")
    print("SABLE_TEXT_READER_APPLICATION_ID=PASS")
    print("SABLE_TEXT_READER_MIN_SDK=PASS_30")
    print("SABLE_TEXT_READER_BRANDING=PASS")
    print("SABLE_TEXT_READER_GLOBAL_APPEARANCE=PASS")
    print("SABLE_TEXT_READER_INTERNET_MERGE_RULE=PASS_REMOVE")
    print("SABLE_TEXT_READER_INTERNET_PERMISSION=PASS_FINAL_APK_ABSENT_REQUIRED")
    print("SABLE_TEXT_READER_TRANSLATION=PASS_RETIRED")
    print("SABLE_TEXT_READER_VIEW_SEND_PROCESS_TEXT=PASS")
    print("SABLE_TEXT_READER_VIEW_HANDLER=PASS_BOUNDED_LOCAL_TEXT")
    print("SABLE_TEXT_READER_TEXT_INPUT=PASS_BOUNDED_2_MIB")
    print("SABLE_TEXT_READER_TTS=PASS")
    print("SABLE_TEXT_READER_TTS_NETWORK_VOICES=PASS_FILTERED")
    print("SABLE_TEXT_READER_OCR=PASS_CAMERA_GALLERY_LATIN_DEVANAGARI")
    print("SABLE_TEXT_READER_FLAVOR_PATCH=PASS")


if __name__ == "__main__":
    main()
