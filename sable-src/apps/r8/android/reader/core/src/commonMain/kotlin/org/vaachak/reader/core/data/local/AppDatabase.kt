package org.vaachak.reader.core.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import org.vaachak.reader.core.domain.model.BookEntity
import org.vaachak.reader.core.domain.model.HighlightEntity
import org.vaachak.reader.core.domain.model.OpdsEntity
import org.vaachak.reader.core.domain.model.ProfileEntity
import org.vaachak.reader.core.domain.model.SyncVaultEntity

@Database(
    entities = [
        BookEntity::class,
        HighlightEntity::class,
        OpdsEntity::class,
        SyncVaultEntity::class,
        ProfileEntity::class
    ],
    version = 3,
    exportSchema = false
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun highlightDao(): HighlightDao
    abstract fun opdsDao(): OpdsDao
    abstract fun syncVaultDao(): SyncVaultDao
    abstract fun profileDao(): ProfileDao

    companion object {
        const val DATABASE_NAME = "vaachak_db.db"
    }
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}