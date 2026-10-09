package org.sableos.reader.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.sableos.reader.core.domain.model.CollectionEntity
import org.sableos.reader.core.domain.model.CollectionItemEntity

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections WHERE profileId = :profileId ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun getCollections(profileId: String): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collection_items WHERE profileId = :profileId")
    fun getMemberships(profileId: String): Flow<List<CollectionItemEntity>>

    @Query("SELECT * FROM collection_items WHERE profileId = :profileId")
    suspend fun getMembershipsOnce(profileId: String): List<CollectionItemEntity>

    @Query("SELECT * FROM collections WHERE profileId = :profileId")
    suspend fun getCollectionsOnce(profileId: String): List<CollectionEntity>

    // @Upsert (not REPLACE): replacing a parent row would cascade-delete its memberships.
    @Upsert
    suspend fun upsertCollection(collection: CollectionEntity)

    @Query("UPDATE collections SET name = :name WHERE collectionId = :collectionId AND profileId = :profileId")
    suspend fun renameCollection(collectionId: String, profileId: String, name: String)

    @Query("DELETE FROM collections WHERE collectionId = :collectionId AND profileId = :profileId")
    suspend fun deleteCollection(collectionId: String, profileId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addItem(item: CollectionItemEntity)

    @Query(
        "DELETE FROM collection_items " +
            "WHERE collectionId = :collectionId AND bookHash = :bookHash AND profileId = :profileId"
    )
    suspend fun removeItem(collectionId: String, bookHash: String, profileId: String)
}
