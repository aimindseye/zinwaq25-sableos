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
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.vaachak.reader.core.domain.model.HighlightEntity

@Dao
interface HighlightDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(highlight: HighlightEntity)

    // publicationId is now bookHashId
    @Query("SELECT * FROM highlights WHERE bookHashId = :bookHash AND profileId = :profileId ORDER BY created DESC")
    fun getHighlightsForBook(bookHash: String, profileId: String): Flow<List<HighlightEntity>>

    // id is now a UUID String, not a Long
    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteHighlightById(id: String)

    // Update this:
    @Query("SELECT * FROM highlights WHERE profileId = :profileId ORDER BY created DESC")
    fun getAllHighlights(profileId: String): Flow<List<HighlightEntity>>

    // Update this:
    @Query("SELECT DISTINCT tag FROM highlights WHERE tag IS NOT NULL AND tag != '' AND profileId = :profileId")
    fun getAllUniqueTags(profileId: String): Flow<List<String>>

    // Requires profileId to know which books to mark with the favorite/bookmark star
    @Query("SELECT DISTINCT bookHashId FROM highlights WHERE tag = 'BOOKMARK' AND profileId = :profileId")
    fun getBooksWithBookmarks(profileId: String): Flow<List<String>>
}

