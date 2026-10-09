package org.vaachak.reader.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.sableos.reader.core.data.local.CollectionDao
import org.sableos.reader.core.data.local.ItemBookmarkDao
import org.sableos.reader.core.data.local.ItemViewSettingsDao
import org.sableos.reader.model.storage.ReaderMigrations
import org.vaachak.reader.core.data.local.AppDatabase
import org.vaachak.reader.core.data.local.BookDao
import org.vaachak.reader.core.data.local.HighlightDao
import org.vaachak.reader.core.data.local.ProfileDao
import org.vaachak.reader.core.data.local.createDataStore
import org.vaachak.reader.core.data.local.getDatabaseBuilder
import org.vaachak.reader.core.data.repository.VaultRepository
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Applies a list of SQL statements. Production uses a [BundledSQLiteDriver], for which Room calls
 * `migrate(SQLiteConnection)` (the legacy `SupportSQLiteDatabase` overload is unreachable there and
 * would throw). Unit tests run Room on the framework SQLite, which calls the legacy overload, so both
 * entry points execute the same statements.
 */
private class StatementMigration(
    start: Int,
    end: Int,
    private val statements: List<String>
) : Migration(start, end) {
    override fun migrate(connection: SQLiteConnection) {
        statements.forEach { connection.execSQL(it) }
    }

    override fun migrate(db: SupportSQLiteDatabase) {
        statements.forEach { db.execSQL(it) }
    }
}

internal val MIGRATION_2_3: Migration = StatementMigration(2, 3, ReaderMigrations.V2_TO_V3)

/** Sable Reader v2: additive typed-library schema; drops only the retired remote-feature tables. */
internal val MIGRATION_3_4: Migration = StatementMigration(
    ReaderMigrations.LEGACY_VERSION,
    ReaderMigrations.SABLE_P5_VERSION,
    ReaderMigrations.V3_TO_V4
)

/** Sable Reader v2.1: one additive table for per-title viewer settings. */
internal val MIGRATION_4_5: Migration = StatementMigration(
    ReaderMigrations.SABLE_P5_VERSION,
    ReaderMigrations.SABLE_P5C_VERSION,
    ReaderMigrations.V4_TO_V5
)

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        // No destructive-migration fallback: an unmigratable database must fail loudly, never wipe the library.
        return getDatabaseBuilder(context)
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    @Provides
    fun provideHighlightDao(database: AppDatabase): HighlightDao {
        return database.highlightDao()
    }

    @Provides
    fun provideBookDao(database: AppDatabase): BookDao {
        return database.bookDao()
    }

    @Provides
    fun provideProfileDao(database: AppDatabase): ProfileDao {
        return database.profileDao()
    }

    @Provides
    fun provideCollectionDao(database: AppDatabase): CollectionDao {
        return database.collectionDao()
    }

    @Provides
    fun provideItemBookmarkDao(database: AppDatabase): ItemBookmarkDao {
        return database.itemBookmarkDao()
    }

    @Provides
    fun provideItemViewSettingsDao(database: AppDatabase): ItemViewSettingsDao {
        return database.itemViewSettingsDao()
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
