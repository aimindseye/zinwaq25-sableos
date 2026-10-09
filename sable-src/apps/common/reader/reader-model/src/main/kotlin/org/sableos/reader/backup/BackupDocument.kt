package org.sableos.reader.backup

import kotlinx.serialization.Serializable

/**
 * The versioned, metadata-only backup of a Sable Reader library. It carries what a person would hate to lose: what is
 * in the library, where they are in each title, bookmarks, highlights, collections and per-title viewer settings.
 * It never carries book files, covers, file locations, credentials or anything from outside the app.
 */
@Serializable
data class BackupDocument(
    val format: String = FORMAT_ID,
    val version: Int = CURRENT_VERSION,
    val createdAt: Long = 0L,
    val items: List<BackupItem> = emptyList(),
    val collections: List<BackupCollection> = emptyList(),
    val bookmarks: List<BackupBookmark> = emptyList(),
    val highlights: List<BackupHighlight> = emptyList(),
    val viewSettings: List<BackupViewSettings> = emptyList(),
) {
    companion object {
        const val FORMAT_ID: String = "sable-reader-backup"

        /** Bumped only for incompatible changes; older versions stay readable through a migration step. */
        const val CURRENT_VERSION: Int = 1
        const val MIN_VERSION: Int = 1
    }
}

/** One library title. [hash] is the content identity used by the library; the file itself is never included. */
@Serializable
data class BackupItem(
    val hash: String,
    val kind: String,
    val title: String,
    val author: String = "",
    val subtitle: String? = null,
    val series: String? = null,
    val seriesIndex: Double? = null,
    val format: String? = null,
    val language: String? = null,
    val progress: Double = 0.0,
    val progressJson: String? = null,
    /** The Readium location of an EPUB last read before typed progress existed. */
    val lastLocation: String? = null,
    val finished: Boolean = false,
    val favorite: Boolean = false,
    val addedAt: Long = 0L,
    val lastOpenedAt: Long = 0L,
)

@Serializable
data class BackupCollection(
    val id: String,
    val name: String,
    val createdAt: Long = 0L,
    val sortOrder: Int = 0,
    val itemHashes: List<String> = emptyList(),
)

@Serializable
data class BackupBookmark(
    val id: String,
    val itemHash: String,
    val locatorJson: String,
    val label: String,
    val createdAt: Long = 0L,
)

@Serializable
data class BackupHighlight(
    val id: String,
    val itemHash: String,
    val locatorJson: String,
    val text: String,
    val color: Int = 0,
    val tag: String? = null,
    val created: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class BackupViewSettings(
    val itemHash: String,
    val settingsJson: String,
    val updatedAt: Long = 0L,
)
