/*
 *  Copyright (c) 2026 Piyush Daiya
 *  *
 *  * Permission is hereby granted, free of charge, to any person obtaining a copy
 *  * of this software and associated documentation files (the "Software"), to deal
 *  * in the Software without restriction, including without limitation the rights
 *  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  * copies of the Software, and to permit persons to whom the Software is
 *  * furnished to do so, subject to the following conditions:
 *  *
 *  * The above copyright notice and this permission notice shall be included in all
 *  * copies or substantial portions of the Software.
 *  *
 *  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  * SOFTWARE.
 */

package org.vaachak.reader.core.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.vaachak.reader.core.domain.model.OpdsEntity

/**
 * Data Access Object (DAO) for OPDS feeds.
 *
 * This interface defines the database interactions for the `opds_feeds` table,
 * providing methods to query, insert, update, and delete OPDS feed entries.
 */
@Dao
interface OpdsDao {
    /**
     * Retrieves all OPDS feeds from the database, sorted to show predefined feeds first,
     * then alphabetically by title.
     *
     * @return A [Flow] emitting a list of all [OpdsEntity] objects.
     */
    @Query("SELECT * FROM opds_feeds ORDER BY isPredefined DESC, title ASC")
    fun getAllFeeds(): Flow<List<OpdsEntity>>

    /**
     * Inserts a new OPDS feed into the database. If a feed with the same primary key
     * already exists, it will be replaced.
     *
     * @param feed The [OpdsEntity] to insert or replace.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeed(feed: OpdsEntity)

    /**
     * Deletes a specific OPDS feed from the database.
     *
     * @param feed The [OpdsEntity] to delete.
     */
    @Delete
    suspend fun deleteFeed(feed: OpdsEntity)

    /**
     * Retrieves a single OPDS feed by its unique URL.
     *
     * @param url The URL of the feed to retrieve.
     * @return The matching [OpdsEntity], or `null` if no feed with the given URL is found.
     */
    @Query("SELECT * FROM opds_feeds WHERE url = :url LIMIT 1")
    suspend fun getFeedByUrl(url: String): OpdsEntity?

    /**
     * Updates an existing OPDS feed in the database.
     *
     * @param feed The [OpdsEntity] with updated information to save.
     */
    @Update
    suspend fun updateFeed(feed: OpdsEntity)

    // UI rewrite
    @Query("DELETE FROM opds_feeds WHERE id = :id")
    suspend fun deleteFeedById(id: Long)

    @Query("SELECT COUNT(*) FROM opds_feeds")
    suspend fun getFeedsCount(): Int
}
