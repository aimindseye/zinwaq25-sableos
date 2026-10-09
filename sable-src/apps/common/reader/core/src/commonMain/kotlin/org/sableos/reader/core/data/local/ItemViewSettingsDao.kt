package org.sableos.reader.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.sableos.reader.core.domain.model.ItemViewSettingsEntity

@Dao
interface ItemViewSettingsDao {
    @Query("SELECT * FROM item_view_settings WHERE bookHash = :bookHash AND profileId = :profileId")
    suspend fun get(bookHash: String, profileId: String): ItemViewSettingsEntity?

    @Query("SELECT * FROM item_view_settings WHERE profileId = :profileId")
    suspend fun getAllOnce(profileId: String): List<ItemViewSettingsEntity>

    // REPLACE is safe here: this table has no dependants, so replacing a row cannot cascade anywhere.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: ItemViewSettingsEntity)

    @Query("DELETE FROM item_view_settings WHERE bookHash = :bookHash AND profileId = :profileId")
    suspend fun delete(bookHash: String, profileId: String)
}
