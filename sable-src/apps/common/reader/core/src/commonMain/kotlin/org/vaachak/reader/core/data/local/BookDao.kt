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
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.vaachak.reader.core.domain.model.BookEntity
import org.vaachak.reader.core.utils.getCurrentTimeMillis

@Dao
interface BookDao {
    // --- MULTI-TENANT READS ---
    @Query("SELECT * FROM books WHERE profileId = :profileId ORDER BY lastRead DESC")
    fun getAllBooks(profileId: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE profileId = :profileId ORDER BY lastRead DESC")
    fun getAllBooksSortedByRecent(profileId: String): Flow<List<BookEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM books WHERE title = :title AND profileId = :profileId LIMIT 1)")
    suspend fun isBookExists(title: String, profileId: String): Boolean

    @Query("SELECT * FROM books WHERE localUri = :localUri AND profileId = :profileId LIMIT 1")
    suspend fun getBookByUri(localUri: String, profileId: String): BookEntity?

    @Query("SELECT * FROM books WHERE bookHash = :bookHash AND profileId = :profileId LIMIT 1")
    suspend fun getBookByHash(bookHash: String, profileId: String): BookEntity?

    @Query("SELECT bookHash FROM books WHERE profileId = :profileId")
    suspend fun getAllBookHashes(profileId: String): List<String>

    // --- WRITES & DELETES ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBook(book: BookEntity)

    @Query("DELETE FROM books WHERE bookHash = :bookHash AND profileId = :profileId")
    suspend fun deleteBook(bookHash: String, profileId: String)

    @Query("DELETE FROM books WHERE localUri = :localUri AND profileId = :profileId")
    suspend fun deleteBookByUri(localUri: String, profileId: String)

    // --- PROGRESS UPDATES ---
    @Query("UPDATE books SET lastRead = :timestamp, updatedAt = :timestamp, isDirty = 1 WHERE localUri = :localUri AND profileId = :profileId")
    suspend fun updateLastRead(localUri: String, profileId: String, timestamp: Long = getCurrentTimeMillis())

    // --- SABLE READER v2: TYPED LIBRARY ITEMS ---
    @Query("SELECT * FROM books WHERE profileId = :profileId")
    suspend fun getAllBooksOnce(profileId: String): List<BookEntity>

    @Query("SELECT * FROM books WHERE profileId = :profileId AND kind = :kind ORDER BY lastRead DESC")
    fun getBooksByKind(profileId: String, kind: String): Flow<List<BookEntity>>

    // @Upsert (not REPLACE): replacing a book row would cascade-delete its highlights, bookmarks
    // and collection memberships.
    @Upsert
    suspend fun upsertItem(book: BookEntity)

    @Query(
        """
        UPDATE books
        SET progress = :progress,
            progressJson = :progressJson,
            finished = CASE WHEN :finished = 1 THEN 1 ELSE finished END,
            lastRead = :timestamp,
            updatedAt = :timestamp
        WHERE bookHash = :bookHash AND profileId = :profileId
        """
    )
    suspend fun updateTypedProgress(
        bookHash: String,
        profileId: String,
        progress: Double,
        progressJson: String?,
        finished: Boolean,
        timestamp: Long
    )

    @Query(
        """
        UPDATE books
        SET progress = :progress,
            lastCfiLocation = :cfiLocation,
            progressJson = :progressJson,
            finished = CASE WHEN :finished = 1 THEN 1 ELSE finished END,
            lastRead = :timestamp,
            updatedAt = :timestamp
        WHERE localUri = :localUri AND profileId = :profileId
        """
    )
    suspend fun updateEpubProgressByUri(
        localUri: String,
        profileId: String,
        progress: Double,
        cfiLocation: String,
        progressJson: String?,
        finished: Boolean,
        timestamp: Long
    )

    @Query("UPDATE books SET lastRead = :timestamp WHERE bookHash = :bookHash AND profileId = :profileId")
    suspend fun markOpened(bookHash: String, profileId: String, timestamp: Long)

    @Query("UPDATE books SET favorite = :favorite WHERE bookHash = :bookHash AND profileId = :profileId")
    suspend fun setFavorite(bookHash: String, profileId: String, favorite: Boolean)

    @Query("UPDATE books SET finished = :finished WHERE bookHash = :bookHash AND profileId = :profileId")
    suspend fun setFinished(bookHash: String, profileId: String, finished: Boolean)

    @Query(
        """
        UPDATE books
        SET title = :title, subtitle = :subtitle, author = :author,
            seriesName = :seriesName, seriesIndex = :seriesIndex
        WHERE bookHash = :bookHash AND profileId = :profileId
        """
    )
    suspend fun updateMetadata(
        bookHash: String,
        profileId: String,
        title: String,
        subtitle: String?,
        author: String,
        seriesName: String?,
        seriesIndex: Double?
    )

    @Query("UPDATE books SET coverPath = :coverPath WHERE bookHash = :bookHash AND profileId = :profileId")
    suspend fun setCover(bookHash: String, profileId: String, coverPath: String?)

    /** Restores the Readium location of an EPUB read before typed progress existed. */
    @Query("UPDATE books SET lastCfiLocation = :location WHERE bookHash = :bookHash AND profileId = :profileId")
    suspend fun setLastLocation(bookHash: String, profileId: String, location: String)

    /** Re-points an item at a freshly selected content URI (restore/relink after a backup import). */
    @Query("UPDATE books SET localUri = :localUri WHERE bookHash = :bookHash AND profileId = :profileId")
    suspend fun relinkSource(bookHash: String, profileId: String, localUri: String?)
}
