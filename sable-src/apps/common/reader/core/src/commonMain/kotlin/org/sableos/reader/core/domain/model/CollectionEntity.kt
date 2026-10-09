package org.sableos.reader.core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.vaachak.reader.core.domain.model.BookEntity
import org.vaachak.reader.core.utils.generateUuid
import org.vaachak.reader.core.utils.getCurrentTimeMillis

/** A user-defined group of library items (any publication kind). */
@Entity(
    tableName = "collections",
    indices = [Index(value = ["profileId"])]
)
data class CollectionEntity(
    @PrimaryKey
    val collectionId: String = generateUuid(),
    @ColumnInfo(defaultValue = "default")
    val profileId: String,
    val name: String,
    val createdAt: Long = getCurrentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0
)

/** Membership of one library item in one collection. Deleting either side removes the row. */
@Entity(
    tableName = "collection_items",
    primaryKeys = ["collectionId", "bookHash", "profileId"],
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["collectionId"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["bookHash", "profileId"],
            childColumns = ["bookHash", "profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookHash", "profileId"])]
)
data class CollectionItemEntity(
    val collectionId: String,
    val bookHash: String,
    val profileId: String,
    val addedAt: Long = getCurrentTimeMillis()
)
