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

    // Legacy change-tracking columns. Sable Reader has no cloud sync; they are retained only so the
    // pre-P5 table keeps its shape (no destructive migration).
    val updatedAt: Long = getCurrentTimeMillis(),
    val isDirty: Boolean = true,

    // --- Sable Reader v2 generic library item fields (schema v4) ---
    // PublicationKind.stableValue: EPUB | PDF | COMIC | AUDIOBOOK. Every pre-v4 row is an EPUB.
    @ColumnInfo(defaultValue = "EPUB")
    val kind: String = "EPUB",
    val subtitle: String? = null,
    val seriesIndex: Double? = null,
    // Media type of the source, e.g. application/epub+zip or application/pdf.
    val format: String? = null,
    @ColumnInfo(defaultValue = "0")
    val finished: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val favorite: Boolean = false,
    // LocatorCodec JSON of the typed ReadingProgress locator (null until a v2 engine records progress).
    val progressJson: String? = null
)