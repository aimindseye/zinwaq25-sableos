package org.vaachak.reader.core.data.di

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.vaachak.reader.core.data.local.AppDatabase
import org.vaachak.reader.core.data.local.OpdsDao
import org.vaachak.reader.core.data.local.getDatabaseBuilder

class DatabaseModuleTest {

    @Test
    fun migration_2_3_recreatesOpdsTableWithIsPredefinedColumn() {
        val migration = migration_2_3()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)

        migration.migrate(db)

        verify {
            db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS opds_feeds_new") })
            db.execSQL(match { it.contains("isPredefined INTEGER NOT NULL DEFAULT 0") })
            db.execSQL(match { it.contains("INSERT INTO opds_feeds_new") })
            db.execSQL("DROP TABLE opds_feeds")
            db.execSQL("ALTER TABLE opds_feeds_new RENAME TO opds_feeds")
        }
    }

    @Test
    fun provideAppDatabase_appliesMigrationAndDriverBeforeBuild() {
        val context = mockk<Context>()
        val builder = mockk<RoomDatabase.Builder<AppDatabase>>()
        val db = mockk<AppDatabase>()

        mockkStatic("org.vaachak.reader.core.data.local.DatabaseBuilder_androidKt")
        try {
            every { getDatabaseBuilder(context) } returns builder
            every { builder.addMigrations(any()) } returns builder
            every { builder.setDriver(any()) } returns builder
            every { builder.build() } returns db

            val provided = DatabaseModule.provideAppDatabase(context)

            assertNotNull(provided)
            assertSame(db, provided)
            verify(exactly = 1) { builder.addMigrations(any()) }
            verify(exactly = 1) { builder.setDriver(any()) }
            verify(exactly = 1) { builder.build() }
        } finally {
            unmockkStatic("org.vaachak.reader.core.data.local.DatabaseBuilder_androidKt")
        }
    }

    @Test
    fun provideOpdsDao_delegatesToDatabase() {
        val database = mockk<AppDatabase>()
        val dao = mockk<OpdsDao>()
        every { database.opdsDao() } returns dao

        val provided = DatabaseModule.provideOpdsDao(database)

        assertSame(dao, provided)
    }

    private fun migration_2_3(): Migration {
        val holder = Class.forName("org.vaachak.reader.core.data.di.DatabaseModuleKt")
        val field = holder.getDeclaredField("MIGRATION_2_3")
        field.isAccessible = true
        return field.get(null) as Migration
    }
}
