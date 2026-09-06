package com.comicify.core.storage

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ComicEntity::class,
        ReadingStateEntity::class,
        PageDetectionEntity::class,
        ComicSettingsEntity::class,
        ReadingSessionEntity::class,
        BookmarkEntity::class,
    ],
    version = 15,
    exportSchema = false,
)
abstract class KapowDatabase : RoomDatabase() {
    abstract fun comicDao(): ComicDao
    abstract fun readingStateDao(): ReadingStateDao
    abstract fun pageDetectionDao(): PageDetectionDao
    abstract fun comicSettingsDao(): ComicSettingsDao
    abstract fun readingSessionDao(): ReadingSessionDao
    abstract fun bookmarkDao(): BookmarkDao
}
