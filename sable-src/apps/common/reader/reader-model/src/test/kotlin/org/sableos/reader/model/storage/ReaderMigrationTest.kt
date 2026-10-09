package org.sableos.reader.model.storage

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Executes the real migration SQL against a real SQLite engine, starting from Room-equivalent
 * legacy schemas that already hold EPUB library data.
 */
class ReaderMigrationTest {
    private lateinit var db: Connection

    @Before
    fun open() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        db.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
    }

    @After
    fun close() = db.close()

    private fun exec(sql: String) = db.createStatement().use { it.execute(sql) }

    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): List<T> =
        db.createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                val rows = mutableListOf<T>()
                while (rs.next()) rows += read(rs)
                rows
            }
        }

    private fun seedV3() {
        LegacySchemas.V3.forEach(::exec)
        exec(
            "INSERT INTO books VALUES ('h-done','default','Finished Book','Ann','Series',null," +
                "'content://a','/c/a.png',0.995,'{\"href\":\"x\"}',100,200,'{\"loc\":1}',300,1)",
        )
        exec(
            "INSERT INTO books VALUES ('h-mid','default','Half Book','Bob',null,'en','content://b',null," +
                "0.4,'cfi',110,210,null,310,0)",
        )
        exec(
            "INSERT INTO books VALUES ('h-p2','profile_2','Other Profile','Cy',null,'en','content://c',null," +
                "0.0,null,120,0,null,320,1)",
        )
        exec("INSERT INTO highlights VALUES ('hl1','h-mid','default','{\"l\":1}','quote',-256,null,1,2,0,1)")
        exec("INSERT INTO highlights VALUES ('bm1','h-mid','default','{\"l\":2}','mark',0,'BOOKMARK',3,4,0,1)")
        exec("INSERT INTO reader_profiles VALUES ('default','Default',0,'pinhash','#fff',500)")
        exec("INSERT INTO opds_feeds VALUES (1,'Feed','https://example.invalid','user','secret',0)")
        exec("INSERT INTO sync_vault_queue VALUES ('default','book_1','blob','iv',1,1,0)")
    }

    private fun migrateV3toV4() = ReaderMigrations.V3_TO_V4.forEach(::exec)

    private fun tableNames(): Set<String> =
        query("SELECT name FROM sqlite_master WHERE type='table'") { it.getString(1) }.toSet()

    private data class Col(val name: String, val type: String, val notNull: Boolean, val dflt: String?, val pk: Int)

    private fun columns(table: String): List<Col> = query("PRAGMA table_info($table)") {
        Col(
            it.getString("name"),
            it.getString("type"),
            it.getInt("notnull") == 1,
            it.getString("dflt_value"),
            it.getInt("pk"),
        )
    }

    @Test
    fun existingEpubLibraryRowsSurviveWithEveryLegacyField() {
        seedV3()
        migrateV3toV4()
        val rows = query("SELECT * FROM books ORDER BY bookHash") {
            listOf(
                it.getString("bookHash"), it.getString("profileId"), it.getString("title"), it.getString("author"),
                it.getString("seriesName"), it.getString("language"), it.getString("localUri"),
                it.getString("coverPath"), it.getDouble("progress"), it.getString("lastCfiLocation"),
                it.getLong("addedDate"), it.getLong("lastRead"), it.getString("lastLocationJson"),
                it.getLong("updatedAt"), it.getInt("isDirty"),
            )
        }
        assertEquals(3, rows.size)
        assertEquals(
            listOf(
                "h-done", "default", "Finished Book", "Ann", "Series", null, "content://a", "/c/a.png",
                0.995, "{\"href\":\"x\"}", 100L, 200L, "{\"loc\":1}", 300L, 1,
            ),
            rows[0],
        )
        assertEquals("Half Book", rows[1][2])
        assertEquals("profile_2", rows[2][1])
    }

    @Test
    fun migratedBooksBecomeTypedEpubItems() {
        seedV3()
        migrateV3toV4()
        val sql = "SELECT bookHash, kind, format, finished, favorite, progressJson, subtitle, seriesIndex FROM books"
        val kinds = query(sql) {
            listOf(
                it.getString(1), it.getString(2), it.getString(3), it.getInt(4), it.getInt(5),
                it.getString(6), it.getString(7), it.getObject(8),
            )
        }.associateBy { it[0] }
        kinds.values.forEach {
            assertEquals("EPUB", it[1])
            assertEquals("application/epub+zip", it[2])
            assertEquals(0, it[4])
            assertEquals(null, it[5])
            assertEquals(null, it[6])
            assertEquals(null, it[7])
        }
        assertEquals(1, kinds.getValue("h-done")[3])
        assertEquals(0, kinds.getValue("h-mid")[3])
    }

    @Test
    fun highlightsBookmarksAndProfilesSurvive() {
        seedV3()
        migrateV3toV4()
        assertEquals(listOf("bm1", "hl1"), query("SELECT id FROM highlights ORDER BY id") { it.getString(1) })
        assertEquals(
            listOf("BOOKMARK"),
            query("SELECT tag FROM highlights WHERE id='bm1'") { it.getString(1) },
        )
        assertEquals(
            listOf("pinhash"),
            query("SELECT pinHash FROM reader_profiles WHERE profileId='default'") { it.getString(1) },
        )
    }

    @Test
    fun retiredRemoteTablesAreDroppedAndNothingElseIs() {
        seedV3()
        migrateV3toV4()
        val tables = tableNames()
        assertFalse("opds_feeds" in tables)
        assertFalse("sync_vault_queue" in tables)
        assertTrue(setOf("books", "highlights", "reader_profiles").all { it in tables })
    }

    @Test
    fun newTablesMatchTheEntityShapeRoomValidates() {
        seedV3()
        migrateV3toV4()
        ExpectedV4.TABLES.forEach { (table, expected) ->
            val actual = columns(table).map { ExpectedV4.Column(it.name, it.type, it.notNull, it.dflt, it.pk) }
            assertEquals("columns of $table", expected.toSet(), actual.toSet())
        }
        val books = columns("books").associateBy { it.name }
        ExpectedV4.BOOKS_ADDED_COLUMNS.forEach {
            val actual = books.getValue(it.name)
            assertEquals(it.type, actual.type)
            assertEquals(it.notNull, actual.notNull)
            assertEquals(it.default, actual.dflt)
        }
    }

    @Test
    fun foreignKeysAndIndicesMatchTheEntityDeclarations() {
        seedV3()
        migrateV3toV4()
        val itemsFks = query("PRAGMA foreign_key_list(collection_items)") {
            listOf(it.getString("table"), it.getString("from"), it.getString("to"), it.getString("on_delete"))
        }
        assertTrue(itemsFks.contains(listOf("collections", "collectionId", "collectionId", "CASCADE")))
        assertTrue(itemsFks.contains(listOf("books", "bookHash", "bookHash", "CASCADE")))
        assertTrue(itemsFks.contains(listOf("books", "profileId", "profileId", "CASCADE")))
        val bmFks = query("PRAGMA foreign_key_list(item_bookmarks)") { it.getString("table") }
        assertEquals(listOf("books", "books"), bmFks)
        val indexSql = "SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'index_%'"
        val indices = query(indexSql) { it.getString(1) }
        assertTrue(
            indices.containsAll(
                listOf(
                    "index_collections_profileId",
                    "index_collection_items_bookHash_profileId",
                    "index_item_bookmarks_bookHash_profileId",
                    "index_books_profileId",
                ),
            ),
        )
    }

    @Test
    fun deletingABookCascadesToCollectionMembershipAndBookmarksButNotOtherBooks() {
        seedV3()
        migrateV3toV4()
        exec("INSERT INTO collections VALUES ('col1','default','Shelf',1,0)")
        exec("INSERT INTO collection_items VALUES ('col1','h-mid','default',1)")
        exec("INSERT INTO collection_items VALUES ('col1','h-done','default',1)")
        exec("INSERT INTO item_bookmarks VALUES ('b1','h-mid','default','{}','x',1)")
        exec("DELETE FROM books WHERE bookHash='h-mid' AND profileId='default'")
        assertEquals(listOf("h-done"), query("SELECT bookHash FROM collection_items") { it.getString(1) })
        assertEquals(0, query("SELECT 1 FROM item_bookmarks") { 1 }.size)
        assertEquals(1, query("SELECT 1 FROM collections") { 1 }.size)
    }

    @Test
    fun foreignKeyIntegrityHoldsAfterMigration() {
        seedV3()
        migrateV3toV4()
        assertEquals(0, query("PRAGMA foreign_key_check") { 1 }.size)
    }

    @Test
    fun migrationIsOnlyAdditiveForUserDataTables() {
        val destructive = ReaderMigrations.V3_TO_V4.filter { it.trim().uppercase().startsWith("DROP") }
        assertEquals(
            listOf("DROP TABLE IF EXISTS opds_feeds", "DROP TABLE IF EXISTS sync_vault_queue"),
            destructive,
        )
        assertTrue(ReaderMigrations.V3_TO_V4.none { it.uppercase().contains("DELETE FROM") })
    }

    @Test
    fun versionTwoDatabasesStillReachVersionFour() {
        LegacySchemas.V2.forEach(::exec)
        exec(
            "INSERT INTO books VALUES ('h1','default','Old','Ann',null,'en','content://a',null,0.5,null,1,2,null,3,0)",
        )
        exec("INSERT INTO opds_feeds VALUES (1,'Feed','https://example.invalid',null,null,1)")
        ReaderMigrations.V2_TO_V3.forEach(::exec)
        migrateV3toV4()
        assertEquals(listOf("Old"), query("SELECT title FROM books") { it.getString(1) })
        assertFalse("opds_feeds" in tableNames())
    }

    private fun migrateV4toV5() = ReaderMigrations.V4_TO_V5.forEach(::exec)

    private fun bookSummary() =
        query("SELECT bookHash, title, kind FROM books") { Triple(it.getString(1), it.getString(2), it.getString(3)) }

    @Test
    fun versionFiveAddsOnlyTheViewSettingsTableAndKeepsEveryRow() {
        seedV3()
        migrateV3toV4()
        val before = bookSummary()
        val tablesBefore = tableNames()
        migrateV4toV5()
        assertEquals(tablesBefore + "item_view_settings", tableNames())
        assertEquals(before, bookSummary())
        val actual = columns("item_view_settings")
            .map { ExpectedV4.Column(it.name, it.type, it.notNull, it.dflt, it.pk) }
        assertEquals(ExpectedV5.ITEM_VIEW_SETTINGS, actual)
    }

    @Test
    fun viewSettingsFollowTheirBookAndNeverOutliveIt() {
        seedV3()
        migrateV3toV4()
        migrateV4toV5()
        val key = query("SELECT bookHash, profileId FROM books LIMIT 1") { it.getString(1) to it.getString(2) }.first()
        exec("INSERT INTO item_view_settings VALUES ('${key.first}','${key.second}','{}',1)")
        exec("DELETE FROM books WHERE bookHash = '${key.first}' AND profileId = '${key.second}'")
        assertEquals(0, query("SELECT 1 FROM item_view_settings") { 1 }.size)
        assertTrue(query("PRAGMA foreign_key_check") { 1 }.isEmpty())
    }

    @Test
    fun versionFiveMigrationIsStrictlyAdditive() {
        assertTrue(ReaderMigrations.V4_TO_V5.all { it.trim().uppercase().startsWith("CREATE TABLE IF NOT EXISTS") })
        assertEquals(5, ReaderMigrations.SABLE_P5C_VERSION)
    }
}
