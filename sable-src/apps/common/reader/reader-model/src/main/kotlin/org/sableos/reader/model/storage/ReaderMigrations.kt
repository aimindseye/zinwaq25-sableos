package org.sableos.reader.model.storage

/**
 * Explicit, additive Room migrations for the Sable Reader library database.
 *
 * Destructive migration is forbidden: every statement here preserves existing EPUB library rows,
 * reading progress, bookmarks (highlights tagged BOOKMARK), highlights and profiles. The SQL lives
 * in plain Kotlin so a JVM test can execute it against a real SQLite engine.
 */
object ReaderMigrations {
    /** Last schema shipped by the Vaachak-derived Reader. */
    const val LEGACY_VERSION: Int = 3

    /** First Sable Reader v2 schema (typed library, collections, page/time bookmarks). */
    const val SABLE_P5_VERSION: Int = 4

    /** P5C adds per-title viewer settings (comic reading mode, later audiobook playback preferences). */
    const val SABLE_P5C_VERSION: Int = 5

    /** Pre-v3 OPDS feed table rebuild, kept only so very old installs still reach v3. */
    val V2_TO_V3: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS opds_feeds_new (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            title TEXT NOT NULL,
            url TEXT NOT NULL,
            username TEXT,
            password TEXT,
            isPredefined INTEGER NOT NULL DEFAULT 0
        )
        """.trimIndent(),
        """
        INSERT INTO opds_feeds_new (id, title, url, username, password, isPredefined)
        SELECT id, title, url, username, password, isPredefined
        FROM opds_feeds
        """.trimIndent(),
        "DROP TABLE opds_feeds",
        "ALTER TABLE opds_feeds_new RENAME TO opds_feeds",
    )

    /** Fraction of an EPUB at or beyond which a migrated book is marked finished. */
    const val MIGRATED_FINISHED_FRACTION: String = "0.99"

    val V3_TO_V4: List<String> = listOf(
        // Generic library item fields on the existing books table (every existing row is an EPUB).
        "ALTER TABLE books ADD COLUMN kind TEXT NOT NULL DEFAULT 'EPUB'",
        "ALTER TABLE books ADD COLUMN subtitle TEXT",
        "ALTER TABLE books ADD COLUMN seriesIndex REAL",
        "ALTER TABLE books ADD COLUMN format TEXT",
        "ALTER TABLE books ADD COLUMN finished INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE books ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE books ADD COLUMN progressJson TEXT",
        "UPDATE books SET format = 'application/epub+zip' WHERE format IS NULL",
        "UPDATE books SET finished = 1 WHERE progress >= $MIGRATED_FINISHED_FRACTION",
        // Collections.
        """
        CREATE TABLE IF NOT EXISTS collections (
            collectionId TEXT NOT NULL,
            profileId TEXT NOT NULL DEFAULT 'default',
            name TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            sortOrder INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY(collectionId)
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS index_collections_profileId ON collections (profileId)",
        """
        CREATE TABLE IF NOT EXISTS collection_items (
            collectionId TEXT NOT NULL,
            bookHash TEXT NOT NULL,
            profileId TEXT NOT NULL,
            addedAt INTEGER NOT NULL,
            PRIMARY KEY(collectionId, bookHash, profileId),
            FOREIGN KEY(collectionId) REFERENCES collections(collectionId)
                ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(bookHash, profileId) REFERENCES books(bookHash, profileId)
                ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS index_collection_items_bookHash_profileId " +
            "ON collection_items (bookHash, profileId)",
        // Page / time bookmarks for PDF, comics and audiobooks.
        """
        CREATE TABLE IF NOT EXISTS item_bookmarks (
            bookmarkId TEXT NOT NULL,
            bookHash TEXT NOT NULL,
            profileId TEXT NOT NULL,
            locatorJson TEXT NOT NULL,
            label TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            PRIMARY KEY(bookmarkId),
            FOREIGN KEY(bookHash, profileId) REFERENCES books(bookHash, profileId)
                ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS index_item_bookmarks_bookHash_profileId " +
            "ON item_bookmarks (bookHash, profileId)",
        // Remote-feature tables retired from Sable Reader v2 (OPDS credentials, cloud sync queue).
        "DROP TABLE IF EXISTS opds_feeds",
        "DROP TABLE IF EXISTS sync_vault_queue",
    )

    /** Additive only: one new table, no existing data touched. */
    val V4_TO_V5: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS item_view_settings (
            bookHash TEXT NOT NULL,
            profileId TEXT NOT NULL,
            settingsJson TEXT NOT NULL,
            updatedAt INTEGER NOT NULL,
            PRIMARY KEY(bookHash, profileId),
            FOREIGN KEY(bookHash, profileId) REFERENCES books(bookHash, profileId)
                ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
    )
}
