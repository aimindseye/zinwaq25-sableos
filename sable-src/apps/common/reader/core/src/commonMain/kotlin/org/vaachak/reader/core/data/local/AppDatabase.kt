package org.vaachak.reader.core.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import org.sableos.reader.core.data.local.CollectionDao
import org.sableos.reader.core.data.local.ItemBookmarkDao
import org.sableos.reader.core.data.local.ItemViewSettingsDao
import org.sableos.reader.core.domain.model.CollectionEntity
import org.sableos.reader.core.domain.model.CollectionItemEntity
import org.sableos.reader.core.domain.model.ItemBookmarkEntity
import org.sableos.reader.core.domain.model.ItemViewSettingsEntity
import org.vaachak.reader.core.domain.model.BookEntity
import org.vaachak.reader.core.domain.model.HighlightEntity
import org.vaachak.reader.core.domain.model.ProfileEntity

@Database(
    entities = [
        BookEntity::class,
        HighlightEntity::class,
        ProfileEntity::class,
        CollectionEntity::class,
        CollectionItemEntity::class,
        ItemBookmarkEntity::class,
        ItemViewSettingsEntity::class
    ],
    // v4 = Sable Reader v2 (typed library items, collections, page/time bookmarks).
    // v5 = per-title viewer settings (comic reading mode, audiobook playback preferences).
    // Upgrades use explicit additive migrations only; destructive migration is forbidden.
    version = 5,
    exportSchema = false
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun highlightDao(): HighlightDao
    abstract fun profileDao(): ProfileDao
    abstract fun collectionDao(): CollectionDao
    abstract fun itemBookmarkDao(): ItemBookmarkDao
    abstract fun itemViewSettingsDao(): ItemViewSettingsDao

    companion object {
        const val DATABASE_NAME = "vaachak_db.db"
    }
}

expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}