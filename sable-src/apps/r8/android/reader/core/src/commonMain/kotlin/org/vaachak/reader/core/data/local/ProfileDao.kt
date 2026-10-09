package org.vaachak.reader.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.vaachak.reader.core.domain.model.ProfileEntity

@Dao
interface ProfileDao {
    @Query("SELECT * FROM reader_profiles ORDER BY lastActiveTimestamp DESC")
    fun getAllProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM reader_profiles WHERE profileId = :profileId LIMIT 1")
    suspend fun getProfileById(profileId: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity)

    // Note: SQLite defaults don't map cleanly in KMP, so we pass the time explicitly
    @Query("UPDATE reader_profiles SET lastActiveTimestamp = :timestamp WHERE profileId = :profileId")
    suspend fun updateLastActive(profileId: String, timestamp: Long)

    @Query("UPDATE reader_profiles SET pinHash = :newPinHash WHERE profileId = :profileId")
    suspend fun updatePin(profileId: String, newPinHash: String?)
}

