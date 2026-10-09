package org.sableos.reader.backup

/** What the library already holds about one title. */
data class ExistingItem(val hash: String, val lastOpenedAt: Long, val favorite: Boolean)

/** Identities already present, so a restore only adds what is missing and can be repeated safely. */
data class ExistingState(
    val items: List<ExistingItem> = emptyList(),
    val bookmarkIds: Set<String> = emptySet(),
    val highlightIds: Set<String> = emptySet(),
    val collectionIds: Set<String> = emptySet(),
    /** (collection id, item hash) pairs. */
    val memberships: Set<Pair<String, String>> = emptySet(),
    /** Item hash to the `updatedAt` of its stored view settings. */
    val settingsUpdatedAt: Map<String, Long> = emptyMap(),
)

/** What a restore will do. Applying it and planning again yields an empty plan: restores are idempotent. */
data class RestorePlan(
    /** Titles not in the library: added as "needs file" entries (no source), relinked by choosing the file. */
    val newItems: List<BackupItem>,
    /** Titles already present where the backup is newer: progress, finished, favorite and last-opened move forward. */
    val progressUpdates: List<BackupItem>,
    /** Titles already present where the backup marks a favorite the library does not (favorites are never lost). */
    val favoriteHashes: List<String>,
    val bookmarks: List<BackupBookmark>,
    val highlights: List<BackupHighlight>,
    val newCollections: List<BackupCollection>,
    val memberships: List<Pair<String, String>>,
    val viewSettings: List<BackupViewSettings>,
) {
    val isEmpty: Boolean
        get() = listOf(
            newItems, progressUpdates, favoriteHashes, bookmarks, highlights, newCollections, memberships, viewSettings,
        ).all { it.isEmpty() }
}

/**
 * Merge policy of a restore: nothing is ever deleted or overwritten with older data. A title's position follows the
 * more recently opened side; favorites are a union; bookmarks, highlights and collections are added when missing; view
 * settings follow the newer `updatedAt`. Pure, so every rule is tested without a database.
 */
object RestorePlanner {
    fun plan(existing: ExistingState, backup: BackupDocument): RestorePlan {
        val byHash = existing.items.associateBy { it.hash }
        val present = backup.items.mapNotNull { item -> byHash[item.hash]?.let { item to it } }
        val newCollectionIds = backup.collections.map { it.id }.filter { it !in existing.collectionIds }.toSet()
        return RestorePlan(
            newItems = backup.items.filter { it.hash !in byHash },
            progressUpdates = present.filter { (item, local) -> item.lastOpenedAt > local.lastOpenedAt }
                .map { it.first },
            favoriteHashes = present.filter { (item, local) -> item.favorite && !local.favorite }.map { it.first.hash },
            bookmarks = backup.bookmarks.filter { it.id !in existing.bookmarkIds },
            highlights = backup.highlights.filter { it.id !in existing.highlightIds },
            newCollections = backup.collections.filter { it.id in newCollectionIds },
            memberships = backup.collections.flatMap { c -> c.itemHashes.map { c.id to it } }
                .filter { it !in existing.memberships },
            viewSettings = backup.viewSettings.filter { newer(it, existing) },
        )
    }

    private fun newer(settings: BackupViewSettings, existing: ExistingState): Boolean =
        settings.updatedAt > (existing.settingsUpdatedAt[settings.itemHash] ?: -1L)
}

/** Rules for attaching a file to a restored "needs file" title. */
object Relink {
    const val FOLDER_FORMAT: String = "inode/directory"

    /** Folder-based titles (comic or audio folders) are relinked by choosing the folder; others by the file. */
    fun isFolder(format: String?): Boolean = format == FOLDER_FORMAT

    /** A chosen file is the right one only when its content identity matches the backup's. */
    fun sameContent(expectedHash: String, actualHash: String): Boolean =
        expectedHash.equals(actualHash, ignoreCase = true)
}
