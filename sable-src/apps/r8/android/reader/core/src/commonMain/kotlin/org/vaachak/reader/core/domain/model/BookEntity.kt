/*
 * Copyright (c) 2026 Piyush Daiya
 * ...
 */

package org.vaachak.reader.core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import org.vaachak.reader.core.utils.getCurrentTimeMillis

@Entity(
    tableName = "books",
    primaryKeys = ["bookHash", "profileId"],
    indices = [
        // OPTIMIZATION: Index specifically for rapid profile filtering
        Index(value = ["profileId"])
    ]
)
data class BookEntity(
    val bookHash: String, // MD5/SHA256 of the EPUB (Universal cross-platform ID)
    @ColumnInfo(defaultValue = "default")
    val profileId: String,
    val title: String,
    val author: String,

    // UI Grouping Fields (New)
    val seriesName: String? = null,
    val language: String? = "en", // e.g., "en", "gu"

    // Local Device File Pointer (Can be null if synced but not downloaded yet)
    val localUri: String? = null,
    val coverPath: String? = null,

    // Reading State
    val progress: Double = 0.0,
    val lastCfiLocation: String? = null,
    val addedDate: Long = getCurrentTimeMillis(),
    val lastRead: Long = getCurrentTimeMillis(),
    val lastLocationJson: String? = null,

    // Sync Triggers
    val updatedAt: Long = getCurrentTimeMillis(),
    val isDirty: Boolean = true // True if local changes haven't been pushed to the vault
)