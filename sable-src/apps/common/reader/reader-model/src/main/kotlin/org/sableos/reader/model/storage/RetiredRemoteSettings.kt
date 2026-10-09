package org.sableos.reader.model.storage

/**
 * Preference keys written by the Vaachak-derived Reader for remote AI, account/cloud sync and
 * catalog features that Sable Reader v2 does not ship. They are purged from existing per-profile
 * DataStore files on first launch so stale API keys and sync passwords do not stay on disk.
 */
object RetiredRemoteSettings {
    val KEY_NAMES: Set<String> = setOf(
        "is_ai_enabled",
        "gemini_api_key",
        "cloudflare_url",
        "cloudflare_token",
        "auto_save_recaps",
        "is_sync_enabled",
        "sync_cloud_url",
        "use_local_server",
        "local_server_url",
        "sync_device_id",
        "last_sync_timestamp",
        "sync_username",
        "sync_password",
        "device_name",
        "offline_mode",
        "is_offline_mode",
        "show_sync_status",
    )

    fun isRetired(keyName: String): Boolean = keyName in KEY_NAMES
}
