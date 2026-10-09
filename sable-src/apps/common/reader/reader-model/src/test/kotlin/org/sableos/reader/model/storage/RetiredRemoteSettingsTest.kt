package org.sableos.reader.model.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetiredRemoteSettingsTest {
    @Test
    fun secretsAndRemoteEndpointsArePurged() {
        listOf("gemini_api_key", "cloudflare_token", "sync_password", "sync_username", "sync_cloud_url")
            .forEach { assertTrue(it, RetiredRemoteSettings.isRetired(it)) }
    }

    @Test
    fun localReadingPreferencesAreKept() {
        listOf("reader_font_size", "reader_theme", "theme_mode", "tts_default_speed", "dictionary_folder")
            .forEach { assertFalse(it, RetiredRemoteSettings.isRetired(it)) }
    }
}
