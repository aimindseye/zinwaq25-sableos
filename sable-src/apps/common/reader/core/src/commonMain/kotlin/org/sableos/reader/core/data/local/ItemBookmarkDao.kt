package org.sableos.reader.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.sableos.reader.core.domain.model.ItemBookmarkEntity

@Dao
interface ItemBookmarkDao {
    @Query(
        "SELECT * FROM item_bookmarks " +
            "WHERE bookHash = :bookHash AND profileId = :profileId ORDER BY createdAt ASC"
    )
    fun getBookmarks(bookHash: String, profileId: String): Flow<List<ItemBookmarkEntity>>

    @Query("SELECT * FROM item_bookmarks WHERE profileId = :profileId ORDER BY createdAt DESC")
    suspend fun getAllOnce(profileId: String): List<ItemBookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(bookmark: ItemBookmarkEntity)

    @Query("DELETE FROM item_bookmarks WHERE bookmarkId = :bookmarkId")
    suspend fun deleteBookmark(bookmarkId: String)
}
