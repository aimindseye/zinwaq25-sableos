package org.sableos.reader.core.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.sableos.reader.model.storage.LegacySchemas
import org.sableos.reader.core.domain.model.CollectionEntity
import org.sableos.reader.core.domain.model.CollectionItemEntity
import org.sableos.reader.core.domain.model.ItemViewSettingsEntity
import org.vaachak.reader.core.data.di.MIGRATION_2_3
import org.vaachak.reader.core.data.di.MIGRATION_3_4
import org.vaachak.reader.core.data.di.MIGRATION_4_5
import org.vaachak.reader.core.data.local.AppDatabase

/**
 * Opens a real pre-P5 (schema v3) database that already holds EPUB library state through Room and
 * the shipped migrations, proving the existing Reader data survives the Sable Reader v2 upgrade.
 * The same SQL is also executed against SQLite by :reader-model's JVM ReaderMigrationTest.
 */
@RunWith(AndroidJUnit4::class)
class MigrationV3ToV4Test {
    private lateinit var context: Context
    private val dbName = "legacy-v3-migration-test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        createLegacyDatabase()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun createLegacyDatabase() {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            LegacySchemas.V3.forEach { db.execSQL(it) }
            db.execSQL(
                "INSERT INTO books VALUES ('h-done','default','Finished Book','Ann','Saga',null,'content://a'," +
                    "'/c/a.png',0.995,'{\"href\":\"x\"}',100,200,'{\"loc\":1}',300,1)"
            )
            db.execSQL(
                "INSERT INTO books VALUES ('h-mid','default','Half Book','Bob',null,'en','content://b',null," +
                    "0.4,'cfi',110,210,null,310,0)"
            )
            db.execSQL("INSERT INTO highlights VALUES ('hl1','h-mid','default','{\"l\":1}','quote',-256,null,1,2,0,1)")
            db.execSQL(
                "INSERT INTO highlights VALUES ('bm1','h-mid','default','{\"l\":2}','mark',0,'BOOKMARK',3,4,0,1)"
            )
            db.execSQL("INSERT INTO reader_profiles VALUES ('default','Default',0,'pinhash','#fff',500)")
            db.execSQL("INSERT INTO opds_feeds VALUES (1,'Feed','https://example.invalid','user','secret',0)")
            db.version = 3
        }
    }

    private fun openMigrated(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, context.getDatabasePath(dbName).absolutePath)
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

    @Test
    fun existingEpubLibraryProgressBookmarksHighlightsAndProfilesSurvive() = runTest {
        val db = openMigrated()
        try {
            val done = db.bookDao().getBookByHash("h-done", "default")!!
            assertEquals("Finished Book", done.title)
            assertEquals("Ann", done.author)
            assertEquals("Saga", done.seriesName)
            assertEquals("content://a", done.localUri)
            assertEquals(0.995, done.progress, 0.0)
            assertEquals("{\"href\":\"x\"}", done.lastCfiLocation)
            assertEquals("{\"loc\":1}", done.lastLocationJson)
            assertEquals("EPUB", done.kind)
            assertEquals("application/epub+zip", done.format)
            assertTrue(done.finished)
            assertFalse(done.favorite)

            val mid = db.bookDao().getBookByHash("h-mid", "default")!!
            assertEquals(0.4, mid.progress, 0.0)
            assertFalse(mid.finished)

            db.highlightDao().getHighlightsForBook("h-mid", "default").test {
                val rows = awaitItem()
                assertEquals(setOf("hl1", "bm1"), rows.map { it.id }.toSet())
                assertEquals("BOOKMARK", rows.single { it.id == "bm1" }.tag)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals("pinhash", db.profileDao().getProfileById("default")!!.pinHash)
        } finally {
            db.close()
        }
    }

    @Test
    fun newCollectionAndBookmarkTablesAreUsableAfterMigration() = runTest {
        val db = openMigrated()
        try {
            db.collectionDao().upsertCollection(CollectionEntity("c1", "default", "Shelf"))
            db.collectionDao().addItem(CollectionItemEntity("c1", "h-mid", "default"))
            assertEquals(1, db.collectionDao().getMembershipsOnce("default").size)
            assertTrue(db.itemBookmarkDao().getAllOnce("default").isEmpty())
            assertEquals(2, db.bookDao().getAllBooks("default").first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun viewSettingsPersistPerTitleAndAreRemovedWithTheirBook() = runTest {
        val db = openMigrated()
        try {
            val settings = db.itemViewSettingsDao()
            settings.upsert(ItemViewSettingsEntity("h-mid", "default", "{\"mode\":\"WEBTOON\"}"))
            settings.upsert(ItemViewSettingsEntity("h-mid", "default", "{\"mode\":\"PAGED_RTL\"}"))
            assertEquals("{\"mode\":\"PAGED_RTL\"}", settings.get("h-mid", "default")?.settingsJson)
            assertEquals(null, settings.get("h-done", "default"))
            db.bookDao().deleteBook("h-mid", "default")
            assertEquals(null, settings.get("h-mid", "default"))
        } finally {
            db.close()
        }
    }
}
