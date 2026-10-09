package org.vaachak.reader.core.data.di

import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.sableos.reader.core.data.local.CollectionDao
import org.sableos.reader.core.data.local.ItemViewSettingsDao
import org.sableos.reader.model.storage.ReaderMigrations
import org.vaachak.reader.core.data.local.AppDatabase
import org.vaachak.reader.core.data.local.getDatabaseBuilder

class DatabaseModuleTest {

    @Test
    fun migrationTwoToThreeRecreatesOpdsTableWithIsPredefinedColumn() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)

        MIGRATION_2_3.migrate(db)

        verify {
            db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS opds_feeds_new") })
            db.execSQL(match { it.contains("isPredefined INTEGER NOT NULL DEFAULT 0") })
            db.execSQL(match { it.contains("INSERT INTO opds_feeds_new") })
            db.execSQL("DROP TABLE opds_feeds")
            db.execSQL("ALTER TABLE opds_feeds_new RENAME TO opds_feeds")
        }
    }

    @Test
    fun migrationThreeToFourExecutesEveryStatementInOrder() {
        val executed = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any()) } answers { executed += firstArg<String>() }

        MIGRATION_3_4.migrate(db)

        assertEquals(ReaderMigrations.V3_TO_V4, executed)
    }

    @Test
    fun migrationsCoverTheLegacyToSableVersionPath() {
        val chain = listOf(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
        assertEquals(listOf(2, 3, 4), chain.map { it.startVersion })
        assertEquals(listOf(3, 4, 5), chain.map { it.endVersion })
        // Every step must start where the previous one ended, or Room cannot walk from v2 to the current schema.
        assertEquals(chain.map { it.startVersion }.drop(1), chain.map { it.endVersion }.dropLast(1))
    }

    @Test
    fun provideAppDatabaseAppliesMigrationsAndDriverBeforeBuild() {
        val context = mockk<Context>()
        val builder = mockk<RoomDatabase.Builder<AppDatabase>>()
        val db = mockk<AppDatabase>()

        mockkStatic("org.vaachak.reader.core.data.local.DatabaseBuilder_androidKt")
        try {
            every { getDatabaseBuilder(context) } returns builder
            every { builder.addMigrations(any(), any(), any()) } returns builder
            every { builder.setDriver(any()) } returns builder
            every { builder.build() } returns db

            val provided = DatabaseModule.provideAppDatabase(context)

            assertSame(db, provided)
            verify(exactly = 1) { builder.addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5) }
            verify(exactly = 1) { builder.setDriver(any()) }
            verify(exactly = 1) { builder.build() }
        } finally {
            unmockkStatic("org.vaachak.reader.core.data.local.DatabaseBuilder_androidKt")
        }
    }

    @Test
    fun migrationFourToFiveAddsOnlyTheViewSettingsTable() {
        val executed = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any()) } answers { executed += firstArg<String>() }

        MIGRATION_4_5.migrate(db)

        assertEquals(ReaderMigrations.V4_TO_V5, executed)
        assertEquals(4, MIGRATION_4_5.startVersion)
        assertEquals(5, MIGRATION_4_5.endVersion)
    }

    @Test
    fun provideItemViewSettingsDaoDelegatesToDatabase() {
        val database = mockk<AppDatabase>()
        val dao = mockk<ItemViewSettingsDao>()
        every { database.itemViewSettingsDao() } returns dao

        assertSame(dao, DatabaseModule.provideItemViewSettingsDao(database))
    }

    @Test
    fun provideCollectionDaoDelegatesToDatabase() {
        val database = mockk<AppDatabase>()
        val dao = mockk<CollectionDao>()
        every { database.collectionDao() } returns dao

        assertSame(dao, DatabaseModule.provideCollectionDao(database))
    }
}
