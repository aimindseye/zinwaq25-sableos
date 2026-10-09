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

    @Query("SELECT * FROM books WHERE profileId = :profileId AND lastRead > :timestamp")
    suspend fun getBooksModifiedSince(profileId: String, timestamp: Long): List<BookEntity>

    // --- WRITES & DELETES ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBook(book: BookEntity)

    @Query("DELETE FROM books WHERE bookHash = :bookHash AND profileId = :profileId")
    suspend fun deleteBook(bookHash: String, profileId: String)

    @Query("DELETE FROM books WHERE localUri = :localUri AND profileId = :profileId")
    suspend fun deleteBookByUri(localUri: String, profileId: String)

    // --- PROGRESS UPDATES ---
    @Query("""
        UPDATE books 
        SET progress = :progress, 
            lastCfiLocation = :cfiLocation, 
            lastRead = :timestamp,
            updatedAt = :timestamp,
            isDirty = 1
        WHERE localUri = :localUri AND profileId = :profileId
    """)
    suspend fun updateBookProgressByUri(localUri: String, profileId: String, progress: Double, cfiLocation: String, timestamp: Long)

    @Query("""
        UPDATE books 
        SET progress = :progress,
            lastCfiLocation = :cfiLocation, 
            lastRead = :timestamp,
            updatedAt = :timestamp,
            isDirty = 1
        WHERE bookHash = :bookHash AND profileId = :profileId
    """)
    suspend fun updateProgressFromCloud(bookHash: String, profileId: String, progress: Double, cfiLocation: String, timestamp: Long)

    @Query("UPDATE books SET lastRead = :timestamp, updatedAt = :timestamp, isDirty = 1 WHERE localUri = :localUri AND profileId = :profileId")
    suspend fun updateLastRead(localUri: String, profileId: String, timestamp: Long = getCurrentTimeMillis())

    // --- VAULT SYNC ---
    @Query("""
        UPDATE books 
        SET lastCfiLocation = :cfi, 
            progress = :progress,
            lastRead = :timestamp,
            updatedAt = :timestamp,
            isDirty = 0
        WHERE bookHash = :bookHash AND profileId = :profileId AND updatedAt < :timestamp
    """)
    suspend fun updateBookMetadataFromSync(bookHash: String, profileId: String, cfi: String, progress: Double, timestamp: Long)
}
