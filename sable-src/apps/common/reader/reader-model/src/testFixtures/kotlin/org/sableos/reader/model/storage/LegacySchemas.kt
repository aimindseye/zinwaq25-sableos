package org.sableos.reader.model.storage

/**
 * Room-equivalent DDL for the databases shipped by the Vaachak-derived Reader, reconstructed from
 * the v3 entity definitions (the legacy build did not export Room schema JSON).
 */
object LegacySchemas {
    val V3: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `books` (`bookHash` TEXT NOT NULL, " +
            "`profileId` TEXT NOT NULL DEFAULT 'default', `title` TEXT NOT NULL, `author` TEXT NOT NULL, " +
            "`seriesName` TEXT, `language` TEXT, `localUri` TEXT, `coverPath` TEXT, `progress` REAL NOT NULL, " +
            "`lastCfiLocation` TEXT, `addedDate` INTEGER NOT NULL, `lastRead` INTEGER NOT NULL, " +
            "`lastLocationJson` TEXT, `updatedAt` INTEGER NOT NULL, `isDirty` INTEGER NOT NULL, " +
            "PRIMARY KEY(`bookHash`, `profileId`))",
        "CREATE INDEX IF NOT EXISTS `index_books_profileId` ON `books` (`profileId`)",
        "CREATE TABLE IF NOT EXISTS `highlights` (`id` TEXT NOT NULL, `bookHashId` TEXT NOT NULL, " +
            "`profileId` TEXT NOT NULL DEFAULT 'default', `locatorJson` TEXT NOT NULL, `text` TEXT NOT NULL, " +
            "`color` INTEGER NOT NULL, `tag` TEXT, `created` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
            "`isDeleted` INTEGER NOT NULL, `isDirty` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
            "FOREIGN KEY(`bookHashId`, `profileId`) REFERENCES `books`(`bookHash`, `profileId`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_highlights_bookHashId_profileId` " +
            "ON `highlights` (`bookHashId`, `profileId`)",
        "CREATE INDEX IF NOT EXISTS `index_highlights_profileId` ON `highlights` (`profileId`)",
        "CREATE TABLE IF NOT EXISTS `opds_feeds` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`title` TEXT NOT NULL, `url` TEXT NOT NULL, `username` TEXT, `password` TEXT, " +
            "`isPredefined` INTEGER NOT NULL DEFAULT 0)",
        "CREATE TABLE IF NOT EXISTS `sync_vault_queue` (`profileId` TEXT NOT NULL, `entryKey` TEXT NOT NULL, " +
            "`encryptedBlob` TEXT NOT NULL, `iv` TEXT NOT NULL, `remoteUpdatedAt` INTEGER NOT NULL, " +
            "`needsPush` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `entryKey`))",
        "CREATE TABLE IF NOT EXISTS `reader_profiles` (`profileId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
            "`isGuest` INTEGER NOT NULL, `pinHash` TEXT, `avatarColorHex` TEXT, " +
            "`lastActiveTimestamp` INTEGER NOT NULL, PRIMARY KEY(`profileId`))",
        "CREATE INDEX IF NOT EXISTS `index_reader_profiles_lastActiveTimestamp` " +
            "ON `reader_profiles` (`lastActiveTimestamp`)",
    )

    /** Schema 2 differs from 3 only in the OPDS table, which the 2->3 migration rebuilds. */
    val V2: List<String> = V3.map {
        if (it.contains("`opds_feeds`")) {
            "CREATE TABLE IF NOT EXISTS `opds_feeds` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `url` TEXT NOT NULL, `username` TEXT, `password` TEXT, " +
                "`isPredefined` INTEGER NOT NULL)"
        } else {
            it
        }
    }
}

/** Hand-derived description of what Room expects the v4 entities to look like. */
object ExpectedV4 {
    data class Column(val name: String, val type: String, val notNull: Boolean, val default: String?, val pk: Int)

    val BOOKS_ADDED_COLUMNS = listOf(
        Column("kind", "TEXT", true, "'EPUB'", 0),
        Column("subtitle", "TEXT", false, null, 0),
        Column("seriesIndex", "REAL", false, null, 0),
        Column("format", "TEXT", false, null, 0),
        Column("finished", "INTEGER", true, "0", 0),
        Column("favorite", "INTEGER", true, "0", 0),
        Column("progressJson", "TEXT", false, null, 0),
    )

    val TABLES: Map<String, List<Column>> = mapOf(
        "collections" to listOf(
            Column("collectionId", "TEXT", true, null, 1),
            Column("profileId", "TEXT", true, "'default'", 0),
            Column("name", "TEXT", true, null, 0),
            Column("createdAt", "INTEGER", true, null, 0),
            Column("sortOrder", "INTEGER", true, "0", 0),
        ),
        "collection_items" to listOf(
            Column("collectionId", "TEXT", true, null, 1),
            Column("bookHash", "TEXT", true, null, 2),
            Column("profileId", "TEXT", true, null, 3),
            Column("addedAt", "INTEGER", true, null, 0),
        ),
        "item_bookmarks" to listOf(
            Column("bookmarkId", "TEXT", true, null, 1),
            Column("bookHash", "TEXT", true, null, 0),
            Column("profileId", "TEXT", true, null, 0),
            Column("locatorJson", "TEXT", true, null, 0),
            Column("label", "TEXT", true, null, 0),
            Column("createdAt", "INTEGER", true, null, 0),
        ),
    )
}

/** What Room expects the v5 `item_view_settings` entity to look like. */
object ExpectedV5 {
    val ITEM_VIEW_SETTINGS = listOf(
        ExpectedV4.Column("bookHash", "TEXT", true, null, 1),
        ExpectedV4.Column("profileId", "TEXT", true, null, 2),
        ExpectedV4.Column("settingsJson", "TEXT", true, null, 0),
        ExpectedV4.Column("updatedAt", "INTEGER", true, null, 0),
    )
}
