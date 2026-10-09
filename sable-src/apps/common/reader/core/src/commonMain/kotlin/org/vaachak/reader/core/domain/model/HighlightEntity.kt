/*
 * Copyright (c) 2026 Piyush Daiya
 * ...
 */

package org.vaachak.reader.core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import org.vaachak.reader.core.utils.generateUuid
import org.vaachak.reader.core.utils.getCurrentTimeMillis

@Serializable
@Entity(
    tableName = "highlights",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["bookHash", "profileId"], // The composite key in Books
            childColumns = ["bookHashId", "profileId"], // The matching columns here
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        // 1. Required by Room for the Foreign Key
        Index(value = ["bookHashId", "profileId"]),
        // 2. OPTIMIZATION: Required for fast "All Highlights" querying
        Index(value = ["profileId"])
    ]
)
data class HighlightEntity(
    @PrimaryKey
    val id: String = generateUuid(),

    val bookHashId: String,

    @ColumnInfo(defaultValue = "default")
    val profileId: String, // <-- THE MULTI-TENANT COLUMN

    val locatorJson: String,
    val text: String,
    val color: Int,
    val tag: String? = null,

    // Sync fields KSP was looking for
    val created: Long = getCurrentTimeMillis(),
    val updatedAt: Long = getCurrentTimeMillis(),
    val isDeleted: Boolean = false,
    val isDirty: Boolean = true
)