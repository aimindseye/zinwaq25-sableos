package org.vaachak.reader.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.vaachak.reader.core.data.local.AppDatabase
import org.vaachak.reader.core.data.local.BookDao
import org.vaachak.reader.core.data.local.HighlightDao
import org.vaachak.reader.core.data.local.OpdsDao
import org.vaachak.reader.core.data.local.ProfileDao
import org.vaachak.reader.core.data.local.SyncVaultDao
import org.vaachak.reader.core.data.local.createDataStore
import org.vaachak.reader.core.data.local.getDatabaseBuilder
import org.vaachak.reader.core.data.repository.VaultRepository
import org.vaachak.reader.core.security.CryptoManager
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS opds_feeds_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                url TEXT NOT NULL,
                username TEXT,
                password TEXT,
                isPredefined INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO opds_feeds_new (id, title, url, username, password, isPredefined)
            SELECT id, title, url, username, password, isPredefined
            FROM opds_feeds
            """.trimIndent()
        )

        db.execSQL("DROP TABLE opds_feeds")
        db.execSQL("ALTER TABLE opds_feeds_new RENAME TO opds_feeds")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return getDatabaseBuilder(context)
            .addMigrations(MIGRATION_2_3)
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    @Provides
    fun provideHighlightDao(database: AppDatabase): HighlightDao {
        return database.highlightDao()
    }

    @Provides
    fun provideOpdsDao(database: AppDatabase): OpdsDao {
        return database.opdsDao()
    }

    @Provides
    @Singleton
    fun provideCryptoManager(): CryptoManager {
        return CryptoManager()
    }

    @Provides
    fun provideSyncVaultDao(database: AppDatabase): SyncVaultDao {
        return database.syncVaultDao()
    }

    @Provides
    fun provideBookDao(database: AppDatabase): BookDao {
        return database.bookDao()
    }

    @Provides
    fun provideProfileDao(database: AppDatabase): ProfileDao {
        return database.profileDao()
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VaultPreferences

@Module
@InstallIn(SingletonComponent::class)
object VaultModule {

    @Provides
    @Singleton
    @VaultPreferences
    fun provideGlobalDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return createDataStore(
            producePath = {
                File(context.filesDir, "datastore/vault_prefs.preferences_pb").absolutePath
            }
        )
    }

    @Provides
    @Singleton
    fun provideVaultRepository(
        @VaultPreferences dataStore: DataStore<Preferences>
    ): VaultRepository {
        return VaultRepository(dataStore)
    }
}