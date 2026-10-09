package org.vaachak.reader.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.vaachak.reader.core.domain.model.HighlightEntity

object SyncDto {

    // 1. The raw data that gets serialized into JSON BEFORE encryption
    @Serializable
    data class CleartextPayload(
        val bookHash: String,
        val progress: Double,
        val lastCfiLocation: String?,
        val updatedAt: Long,
        val highlights: List<HighlightEntity>
    )

    // 2. The exact JSON structure for a single row matching the Cloudflare KV schema
    @Serializable
    data class VaultEntry(
        @SerialName("entry_key") val entryKey: String,
        @SerialName("encrypted_payload") val encryptedPayload: String, // Format: "iv:ciphertext"
        @SerialName("updated_at") val updatedAt: Long,
        val deleted: Boolean
    )
    @Serializable
    data class AuthDto(
        val username: String,
        val password: String
    )
    // 3. The payload sent TO Cloudflare
    @Serializable
    data class SyncRequest(
        val auth: AuthDto, // Assuming you have this defined for username/password
        @SerialName("device_id") val deviceId: String,
        @SerialName("last_sync_timestamp") val lastSyncTimestamp: Long,
        @SerialName("vault_entries") val vaultEntries: List<VaultEntry>
    )

    // 4. The payload received FROM Cloudflare
    @Serializable
    data class SyncResponse(
        @SerialName("new_sync_timestamp") val newSyncTimestamp: Long,
        @SerialName("vault_entries") val vaultEntries: List<VaultEntry>
    )
}

// The credentials sent with every sync request
