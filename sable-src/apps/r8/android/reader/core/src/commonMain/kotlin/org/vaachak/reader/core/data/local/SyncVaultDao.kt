package org.vaachak.reader.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.vaachak.reader.core.domain.model.SyncVaultEntity

@Dao
interface SyncVaultDao {

    // 1. Grabs only the pending uploads for the CURRENT active user
    @Query("SELECT * FROM sync_vault_queue WHERE profileId = :profileId AND needsPush = 1")
    fun getPendingUploads(profileId: String): Flow<List<SyncVaultEntity>>

    // 2. Inserts or updates a payload (from local changes OR incoming cloud syncs)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncPayload(payload: SyncVaultEntity)

    // 3. Batch insert for incoming Cloudflare syncs
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncPayloads(payloads: List<SyncVaultEntity>)

    // 4. Deletes a specific payload for a specific user
    @Query("DELETE FROM sync_vault_queue WHERE profileId = :profileId AND entryKey = :entryKey")
    suspend fun deletePayload(profileId: String, entryKey: String)

    // 5. Marks items as successfully synced to Cloudflare so they don't upload again
    @Query("UPDATE sync_vault_queue SET needsPush = 0 WHERE profileId = :profileId AND entryKey IN (:keys)")
    suspend fun markAsSynced(profileId: String, keys: List<String>)

    // 6. Gets the entire vault for a user (useful for initial device setup)
    @Query("SELECT * FROM sync_vault_queue WHERE profileId = :profileId")
    suspend fun getAllPayloads(profileId: String): List<SyncVaultEntity>
}