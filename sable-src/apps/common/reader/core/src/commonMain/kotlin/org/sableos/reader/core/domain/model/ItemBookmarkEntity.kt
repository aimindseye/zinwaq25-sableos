package org.sableos.reader.core.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.vaachak.reader.core.domain.model.BookEntity
import org.vaachak.reader.core.utils.generateUuid
import org.vaachak.reader.core.utils.getCurrentTimeMillis

/**
 * Page or time bookmark for non-EPUB publications (PDF, comics, audiobooks). [locatorJson] is a
 * LocatorCodec-encoded PageLocator or TimeLocator. EPUB bookmarks remain Readium highlights.
 */
@Entity(
    tableName = "item_bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["bookHash", "profileId"],
            childColumns = ["bookHash", "profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookHash", "profileId"])]
)
data class ItemBookmarkEntity(
    @PrimaryKey
    val bookmarkId: String = generateUuid(),
    val bookHash: String,
    val profileId: String,
    val locatorJson: String,
    val label: String,
    val createdAt: Long = getCurrentTimeMillis()
)
